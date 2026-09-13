package cn.hospital.eph.prescription.consumer;

import cn.hospital.eph.common.enums.FulfillmentStatus;
import cn.hospital.eph.common.enums.RxStatus;
import cn.hospital.eph.common.event.Events;
import cn.hospital.eph.common.mq.InboxService;
import cn.hospital.eph.common.mq.MqTopology;
import cn.hospital.eph.prescription.entity.Prescription;
import cn.hospital.eph.prescription.entity.RxStatusLog;
import cn.hospital.eph.prescription.mapper.PrescriptionMapper;
import cn.hospital.eph.prescription.mapper.RxStatusLogMapper;
import cn.hospital.eph.prescription.service.PrescriptionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

/** 消费履约状态事件，物化到处方主表供患者/医生一次性查询 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FulfillmentStatusConsumer {

    private static final String HANDLER = "prescription-fulfillment";

    private final InboxService inboxService;
    private final PrescriptionMapper rxMapper;
    private final RxStatusLogMapper logMapper;
    private final PrescriptionService prescriptionService;
    private final TransactionTemplate txTemplate;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = MqTopology.Q_PRESCRIPTION_FULFILLMENT, concurrency = "1")
    public void onMessage(Map<String, Object> msg) {
        String eventId = (String) msg.get("eventId");
        if (!inboxService.firstTime(eventId, HANDLER)) {
            return;
        }
        Events.FulfillmentStatus fs =
                objectMapper.convertValue(msg.get("payload"), Events.FulfillmentStatus.class);
        txTemplate.executeWithoutResult(s -> apply(fs));
    }

    private void apply(Events.FulfillmentStatus fs) {
        Prescription rx = prescriptionService.mustGet(fs.rxNo());
        FulfillmentStatus newStatus;
        try {
            newStatus = FulfillmentStatus.of(fs.status());
        } catch (IllegalArgumentException e) {
            log.warn("未知履约状态，忽略: {}", fs.status());
            return;
        }
        // 单调守卫：同一订单的乱序/重复事件不回退已物化的履约状态（REJECTED/CANCELLED 终态随时可到达）
        FulfillmentStatus current = materializedStatus(rx, fs.orderNo());
        if (current != null && newStatus.ordinal() < current.ordinal()
                && newStatus != FulfillmentStatus.REJECTED && newStatus != FulfillmentStatus.CANCELLED) {
            log.info("履约状态回退忽略 rxNo={} {}→{}", fs.rxNo(), current.code(), newStatus.code());
            return;
        }
        rx.setFulfillmentStatus(newStatus.code());
        rx.setCurrentPharmacyId(fs.pharmacyId());
        rx.setCurrentPharmacyName(fs.pharmacyName());
        rx.setCurrentOrderNo(fs.orderNo());

        // 沿履约链逐跳推进处方主状态（事件可能跳着到，如直接收到 STOCK_HELD）
        RxStatus target = mapRxStatus(newStatus);
        if (target != null) {
            RxStatus cur = RxStatus.valueOf(rx.getRxStatus());
            while (cur != target && cur.canTransitionTo(nextHop(cur, target))) {
                RxStatus next = nextHop(cur, target);
                if (next == null || next == cur) break;
                prescriptionService.transition(rx, next, fs.pharmacyId(), "PHARMACY",
                        "FULFILLMENT:" + newStatus.code(), fs.statusText());
                cur = next;
            }
        }
        rxMapper.updateById(rx);

        RxStatusLog l = new RxStatusLog();
        l.setRxId(rx.getId());
        l.setRxNo(rx.getRxNo());
        l.setFromStatus(null);
        l.setToStatus(rx.getRxStatus());
        l.setActorId(fs.pharmacyId());
        l.setActorRole("PHARMACY");
        l.setAction("FULFILLMENT_STATUS");
        l.setComment(fs.statusText());
        l.setCreatedAt(LocalDateTime.now());
        logMapper.insert(l);
    }

    /** 同一订单已物化的履约状态；新订单（改派）或无历史状态返回 null */
    private FulfillmentStatus materializedStatus(Prescription rx, String orderNo) {
        if (rx.getFulfillmentStatus() == null || !Objects.equals(rx.getCurrentOrderNo(), orderNo)) {
            return null;
        }
        try {
            return FulfillmentStatus.of(rx.getFulfillmentStatus());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** 履约状态 → 处方主状态联动 */
    private RxStatus mapRxStatus(FulfillmentStatus s) {
        return switch (s) {
            case STOCK_CHECKING -> RxStatus.DISPATCHING;
            case ORDER_PLACED, STOCK_HELD, PHARMACY_ACCEPTED -> RxStatus.DISPATCHED;
            case DISPENSING -> RxStatus.FULFILLING;
            case DISPENSED -> RxStatus.DISPENSED;
            case READY_FOR_PICKUP, DELIVERING -> RxStatus.READY_FOR_PICKUP;
            case PICKED_UP -> RxStatus.PICKED_UP;
            case REJECTED -> RxStatus.TRANSFER_FAILED;
            case CANCELLED -> RxStatus.CANCELLED;
            default -> null;
        };
    }

    private static final RxStatus[] FULFILL_CHAIN = {
            RxStatus.EFFECTIVE, RxStatus.DISPATCHING, RxStatus.DISPATCHED, RxStatus.FULFILLING,
            RxStatus.DISPENSED, RxStatus.READY_FOR_PICKUP, RxStatus.PICKED_UP};

    /** 沿履约链或异常分支取下一跳；目标不领先于当前状态（重复/乱序）或不可达时返回 null */
    private RxStatus nextHop(RxStatus cur, RxStatus target) {
        if (target == RxStatus.TRANSFER_FAILED) {
            return switch (cur) {
                case EFFECTIVE -> RxStatus.DISPATCHING;
                case DISPATCHED, FULFILLING, DISPENSED, READY_FOR_PICKUP -> RxStatus.DISPATCHING;
                case DISPATCHING -> RxStatus.TRANSFER_FAILED;
                default -> null;
            };
        }
        if (target == RxStatus.CANCELLED) {
            return switch (cur) {
                case EFFECTIVE, TRANSFER_FAILED -> RxStatus.CANCELLED;
                case DISPATCHING -> RxStatus.TRANSFER_FAILED;
                case DISPATCHED -> RxStatus.DISPATCHING;
                default -> null; // 配药中及以后不可取消，保持原状态
            };
        }
        int targetIdx = chainIndex(target);
        if (targetIdx < 0) {
            return null;
        }
        if (cur == RxStatus.TRANSFER_FAILED) {
            return RxStatus.DISPATCHING; // 改派重新进入履约链
        }
        int curIdx = chainIndex(cur);
        if (curIdx < 0 || curIdx >= targetIdx) {
            return null; // 不在履约链上，或事件重复/乱序（单调守卫）
        }
        return FULFILL_CHAIN[curIdx + 1];
    }

    private static int chainIndex(RxStatus s) {
        for (int i = 0; i < FULFILL_CHAIN.length; i++) {
            if (FULFILL_CHAIN[i] == s) {
                return i;
            }
        }
        return -1;
    }
}
