package cn.hospital.eph.transfer.service;

import cn.hospital.eph.common.crypto.CryptoSupport;
import cn.hospital.eph.common.enums.FulfillmentStatus;
import cn.hospital.eph.common.event.Events;
import cn.hospital.eph.common.mq.MqTopology;
import cn.hospital.eph.common.mq.OutboxService;
import cn.hospital.eph.transfer.adapter.AdapterContext;
import cn.hospital.eph.transfer.adapter.ExternalStatusMapping;
import cn.hospital.eph.transfer.adapter.OrderCreateResult;
import cn.hospital.eph.transfer.adapter.PharmacyAdapter;
import cn.hospital.eph.transfer.adapter.PharmacyAdapterRouter;
import cn.hospital.eph.transfer.adapter.StockCheckResult;
import cn.hospital.eph.transfer.adapter.StockHoldResult;
import cn.hospital.eph.transfer.entity.DispatchAttempt;
import cn.hospital.eph.transfer.entity.Pharmacy;
import cn.hospital.eph.transfer.entity.PharmacyOrder;
import cn.hospital.eph.transfer.entity.PharmacyOrderLog;
import cn.hospital.eph.transfer.mapper.DispatchAttemptMapper;
import cn.hospital.eph.transfer.mapper.PharmacyMapper;
import cn.hospital.eph.transfer.mapper.PharmacyOrderLogMapper;
import cn.hospital.eph.transfer.mapper.PharmacyOrderMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/** 派单履约：按优先级选店 → 库存校验 → 预占 → 下单；状态推进并广播履约事件 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderDispatchService {

    private final PharmacyMapper pharmacyMapper;
    private final PharmacyOrderMapper orderMapper;
    private final PharmacyOrderLogMapper orderLogMapper;
    private final DispatchAttemptMapper attemptMapper;
    private final PharmacyAdapterRouter router;
    private final OutboxService outboxService;
    private final StringRedisTemplate redis;

    @Value("${eph.transfer.max-pharmacies:3}")
    private int maxPharmacies;

    /** 消费 rx.effective：派单 */
    @Transactional
    public void dispatch(Events.RxEffective rx) {
        // 幂等：已有订单直接跳过
        Long existing = orderMapper.selectCount(new QueryWrapper<PharmacyOrder>().eq("rx_no", rx.rxNo()));
        if (existing != null && existing > 0) {
            log.info("处方已派单，跳过 rxNo={}", rx.rxNo());
            return;
        }
        List<Pharmacy> pharmacies = pharmacyMapper.selectList(new QueryWrapper<Pharmacy>()
                .eq("status", 1).orderByAsc("priority").last("limit " + maxPharmacies));

        for (Pharmacy pharmacy : pharmacies) {
            PharmacyAdapter adapter = router.route(pharmacy);
            AdapterContext ctx = AdapterContext.of(pharmacy, rx.rxNo(), rx.rxVersion(), null);
            StockCheckResult stock = adapter.checkStock(ctx, rx.items());
            if (!stock.allAvailable()) {
                recordAttempt(rx.rxNo(), pharmacy.getId(), "OUT_OF_STOCK", stock.detail());
                log.info("药店 {} 库存不足 rxNo={} {}", pharmacy.getCode(), rx.rxNo(), stock.detail());
                continue;
            }
            recordAttempt(rx.rxNo(), pharmacy.getId(), "STOCK_OK", stock.detail());
            placeOrder(rx, pharmacy, adapter);
            return;
        }
        // 全部药店缺货 → 发 REJECTED，处方进入 TRANSFER_FAILED 待人工介入
        emitStatus(rx.rxNo(), null, null, FulfillmentStatus.REJECTED.code(),
                FulfillmentStatus.REJECTED,
                "所有合作药店库存不足，等待改派", "ALL_NO_STOCK_" + UUID.randomUUID());
        log.warn("处方 {} 所有药店均无库存", rx.rxNo());
    }

    private void placeOrder(Events.RxEffective rx, Pharmacy pharmacy, PharmacyAdapter adapter) {
        String orderNo = generateOrderNo();
        String idempotencyKey = rx.rxNo() + "#" + pharmacy.getCode() + "#v" + rx.rxVersion();
        AdapterContext ctx = AdapterContext.of(pharmacy, rx.rxNo(), rx.rxVersion(), orderNo);

        PharmacyOrder order = new PharmacyOrder();
        order.setOrderNo(orderNo);
        order.setRxNo(rx.rxNo());
        order.setRxVersion(rx.rxVersion());
        order.setPharmacyId(pharmacy.getId());
        order.setPharmacyCode(pharmacy.getCode());
        order.setAdapterType(pharmacy.getAdapterType());
        order.setIdempotencyKey(idempotencyKey);
        order.setAttempts(0);
        LocalDateTime now = LocalDateTime.now();
        order.setCreatedAt(now);
        order.setUpdatedAt(now);

        order.setStatus(FulfillmentStatus.STOCK_CHECKING.code());
        orderMapper.insert(order);
        logOrder(orderNo, null, FulfillmentStatus.STOCK_CHECKING.code(), "开始库存校验");

        StockHoldResult hold = adapter.hold(ctx, rx.items());
        if (!hold.success()) {
            throw new IllegalStateException("库存预占失败: " + hold.detail());
        }
        order.setStockHoldNo(hold.holdNo());
        order.setStatus(FulfillmentStatus.STOCK_HELD.code());
        orderMapper.updateById(order);
        logOrder(orderNo, FulfillmentStatus.STOCK_CHECKING.code(),
                FulfillmentStatus.STOCK_HELD.code(), hold.detail());
        emitStatus(rx.rxNo(), orderNo, pharmacy, FulfillmentStatus.STOCK_HELD.code(),
                FulfillmentStatus.STOCK_HELD, hold.detail(), "HOLD_" + UUID.randomUUID());

        OrderCreateResult created = adapter.createOrder(ctx, rx.items(), idempotencyKey);
        if (!created.success()) {
            throw new IllegalStateException("药店下单失败: " + created.detail());
        }
        order.setExternalOrderNo(created.externalOrderNo());
        order.setStatus(FulfillmentStatus.ORDER_PLACED.code());
        order.setAcceptedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        orderMapper.updateById(order);
        logOrder(orderNo, FulfillmentStatus.STOCK_HELD.code(),
                FulfillmentStatus.ORDER_PLACED.code(), created.detail());
        emitStatus(rx.rxNo(), orderNo, pharmacy, FulfillmentStatus.ORDER_PLACED.code(),
                FulfillmentStatus.ORDER_PLACED, created.detail(), "ORDER_" + UUID.randomUUID());
        log.info("处方 {} 已下发药店 {} orderNo={}", rx.rxNo(), pharmacy.getCode(), orderNo);
    }

    /** 药店回调（已验签）：状态单调推进并广播 */
    @Transactional
    public void applyCallback(String rxNo, String orderNo, String externalStatus,
                              String externalEventId, String statusText) {
        FulfillmentStatus target = ExternalStatusMapping.map(externalStatus);
        PharmacyOrder order = orderMapper.selectOne(new QueryWrapper<PharmacyOrder>()
                .eq("order_no", orderNo).last("limit 1"));
        if (order == null) {
            throw new IllegalArgumentException("订单不存在: " + orderNo);
        }
        String from = order.getStatus();
        FulfillmentStatus current;
        try {
            current = FulfillmentStatus.of(from);
        } catch (Exception e) {
            current = null;
        }
        if (current != null && current.ordinal() > target.ordinal()
                && target != FulfillmentStatus.REJECTED && target != FulfillmentStatus.CANCELLED) {
            log.warn("订单状态回退忽略 orderNo={} {}→{}", orderNo, from, target.code());
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        order.setStatus(target.code());
        if (target == FulfillmentStatus.DISPENSED) order.setDispensedAt(now);
        if (target == FulfillmentStatus.PICKED_UP) order.setPickedUpAt(now);
        order.setUpdatedAt(now);
        orderMapper.updateById(order);
        logOrder(orderNo, from, target.code(), statusText);

        Pharmacy pharmacy = pharmacyMapper.selectById(order.getPharmacyId());
        emitStatus(rxNo == null ? order.getRxNo() : rxNo, orderNo, pharmacy,
                target.code(), target, statusText, externalEventId);
    }

    public void emitStatus(String rxNo, String orderNo, Pharmacy pharmacy,
                           String statusCode, FulfillmentStatus status,
                           String text, String externalEventId) {
        Events.FulfillmentStatus payload = new Events.FulfillmentStatus(
                rxNo, orderNo,
                pharmacy == null ? null : pharmacy.getId(),
                pharmacy == null ? null : pharmacy.getCode(),
                pharmacy == null ? null : pharmacy.getName(),
                statusCode,
                text != null ? text : ExternalStatusMapping.text(status),
                externalEventId,
                OffsetDateTime.now(ZoneOffset.ofHours(8)).toString());
        outboxService.enlist(MqTopology.RK_FULFILLMENT_STATUS, rxNo, null, payload);
    }

    /** Mock 药店测试用：按外部订单号推进到下一状态 */
    @Transactional
    public void advanceMockOrder(String orderNo) {
        PharmacyOrder order = orderMapper.selectOne(new QueryWrapper<PharmacyOrder>()
                .eq("order_no", orderNo).last("limit 1"));
        if (order == null || !"MOCK".equals(order.getAdapterType())) {
            throw new IllegalArgumentException("Mock 订单不存在: " + orderNo);
        }
        String next = switch (FulfillmentStatus.of(order.getStatus())) {
            case ORDER_PLACED, STOCK_HELD -> "ACCEPTED";
            case PHARMACY_ACCEPTED -> "DISPENSING";
            case DISPENSING -> "DISPENSED";
            case DISPENSED -> "READY";
            case READY_FOR_PICKUP -> "PICKED_UP";
            default -> throw new IllegalStateException("当前状态不可推进: " + order.getStatus());
        };
        applyCallback(order.getRxNo(), orderNo, next,
                "MOCK_" + next + "_" + UUID.randomUUID(), null);
    }

    private void recordAttempt(String rxNo, long pharmacyId, String result, String detail) {
        DispatchAttempt a = new DispatchAttempt();
        a.setRxNo(rxNo);
        a.setPharmacyId(pharmacyId);
        a.setResult(result);
        a.setDetail(detail);
        a.setCreatedAt(LocalDateTime.now());
        attemptMapper.insert(a);
    }

    private void logOrder(String orderNo, String from, String to, String detail) {
        PharmacyOrderLog l = new PharmacyOrderLog();
        l.setOrderNo(orderNo);
        l.setFromStatus(from);
        l.setToStatus(to);
        l.setDetail(detail);
        l.setCreatedAt(LocalDateTime.now());
        orderLogMapper.insert(l);
    }

    private String generateOrderNo() {
        String day = LocalDateTime.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        Long seq = redis.opsForValue().increment("order:seq:" + day);
        return "PO" + day + String.format("%06d", seq == null ? 1 : seq);
    }

    /** 回调 HMAC 验签：sha256Hex(timestamp + "\n" + nonce + "\n" + sha256(body)) */
    public boolean verifyCallbackSignature(Pharmacy pharmacy, String timestamp,
                                           String nonce, String rawBody, String signature) {
        if (pharmacy == null || pharmacy.getSignSecret() == null) return false;
        String bodyHash = CryptoSupport.sha256Hex(rawBody == null ? "" : rawBody);
        String expected = CryptoSupport.hmacSha256Hex(
                pharmacy.getSignSecret(), timestamp + "\n" + nonce + "\n" + bodyHash);
        return CryptoSupport.constantTimeEquals(expected, signature);
    }
}
