package cn.hospital.eph.prescription.consumer;

import cn.hospital.eph.common.enums.RxStatus;
import cn.hospital.eph.common.mq.InboxService;
import cn.hospital.eph.prescription.entity.Prescription;
import cn.hospital.eph.prescription.entity.RxStatusLog;
import cn.hospital.eph.prescription.mapper.PrescriptionMapper;
import cn.hospital.eph.prescription.mapper.RxStatusLogMapper;
import cn.hospital.eph.prescription.service.PrescriptionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 履约事件 → 处方主状态机：逐跳推进、跳事件、乱序/重复单调守卫、REJECTED/CANCELLED 分支 */
class FulfillmentStatusConsumerTest {

    private static final String RX_NO = "RX20260913000001";
    private static final String ORDER_NO = "PO20260913000001";

    private InboxService inboxService;
    private PrescriptionMapper rxMapper;
    private RxStatusLogMapper logMapper;
    private PrescriptionService prescriptionService;
    private FulfillmentStatusConsumer consumer;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        inboxService = mock(InboxService.class);
        rxMapper = mock(PrescriptionMapper.class);
        logMapper = mock(RxStatusLogMapper.class);
        prescriptionService = mock(PrescriptionService.class);
        TransactionTemplate txTemplate = mock(TransactionTemplate.class);
        consumer = new FulfillmentStatusConsumer(inboxService, rxMapper, logMapper,
                prescriptionService, txTemplate, new ObjectMapper());

        when(inboxService.firstTime(any(), any())).thenReturn(true);
        // 事务模板直接同步执行
        doAnswer(inv -> {
            ((Consumer<TransactionStatus>) inv.getArgument(0)).accept(null);
            return null;
        }).when(txTemplate).executeWithoutResult(any());
        // transition 模拟真实服务：把目标状态写回内存中的处方
        doAnswer(inv -> {
            Prescription rx = inv.getArgument(0);
            rx.setRxStatus(inv.getArgument(1, RxStatus.class).name());
            return null;
        }).when(prescriptionService).transition(any(), any(), any(), any(), any(), any());
    }

    private Prescription givenRx(String rxStatus, String fulfillmentStatus, String orderNo) {
        Prescription rx = new Prescription();
        rx.setId(1L);
        rx.setRxNo(RX_NO);
        rx.setRxStatus(rxStatus);
        rx.setFulfillmentStatus(fulfillmentStatus);
        rx.setCurrentOrderNo(orderNo);
        rx.setVersion(1);
        when(prescriptionService.mustGet(RX_NO)).thenReturn(rx);
        return rx;
    }

    private void send(String eventId, String orderNo, String status) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("rxNo", RX_NO);
        payload.put("orderNo", orderNo);
        payload.put("pharmacyId", 7L);
        payload.put("pharmacyCode", "MOCK01");
        payload.put("pharmacyName", "Mock 药店");
        payload.put("status", status);
        payload.put("statusText", status);
        payload.put("externalEventId", "EXT-" + eventId);
        payload.put("occurredAt", "2026-09-13T10:00:00+08:00");
        consumer.onMessage(Map.of("eventId", eventId, "payload", payload));
    }

    /** 实际发生过的主状态转移序列 */
    private List<RxStatus> hops() {
        ArgumentCaptor<RxStatus> captor = ArgumentCaptor.forClass(RxStatus.class);
        verify(prescriptionService, atLeast(0)).transition(any(Prescription.class), captor.capture(),
                any(), any(), any(), any());
        return captor.getAllValues();
    }

    // ---------- 正常推进 ----------

    @Test
    void stockHeldFromEffectiveWalksToDispatched() {
        Prescription rx = givenRx("EFFECTIVE", null, null);
        send("e1", ORDER_NO, "STOCK_HELD");
        assertEquals(List.of(RxStatus.DISPATCHING, RxStatus.DISPATCHED), hops());
        assertEquals("DISPATCHED", rx.getRxStatus());
        assertEquals("STOCK_HELD", rx.getFulfillmentStatus());
        assertEquals(ORDER_NO, rx.getCurrentOrderNo());
    }

    @Test
    void skippedEventsWalkWholeChain() {
        Prescription rx = givenRx("EFFECTIVE", null, null);
        send("e1", ORDER_NO, "DISPENSED");
        assertEquals(List.of(RxStatus.DISPATCHING, RxStatus.DISPATCHED,
                RxStatus.FULFILLING, RxStatus.DISPENSED), hops());
        assertEquals("DISPENSED", rx.getRxStatus());
    }

    @Test
    void pickedUpCompletesChain() {
        Prescription rx = givenRx("READY_FOR_PICKUP", "READY_FOR_PICKUP", ORDER_NO);
        send("e1", ORDER_NO, "PICKED_UP");
        assertEquals(List.of(RxStatus.PICKED_UP), hops());
        assertEquals("PICKED_UP", rx.getRxStatus());
    }

    // ---------- 单调守卫：乱序/重复事件 ----------

    @Test
    void staleEventDoesNotRegressMaterializedStatus() {
        Prescription rx = givenRx("DISPATCHED", "ORDER_PLACED", ORDER_NO);
        send("e1", ORDER_NO, "STOCK_HELD"); // 乱序到达的旧事件
        assertEquals(List.of(), hops());
        assertEquals("DISPATCHED", rx.getRxStatus());
        assertEquals("ORDER_PLACED", rx.getFulfillmentStatus()); // 物化状态不回退
        verify(rxMapper, never()).updateById(any(Prescription.class));
    }

    @Test
    void duplicateSameLevelEventIsIdempotent() {
        Prescription rx = givenRx("DISPATCHED", "ORDER_PLACED", ORDER_NO);
        send("e1", ORDER_NO, "ORDER_PLACED");
        assertEquals(List.of(), hops());
        assertEquals("DISPATCHED", rx.getRxStatus());
        assertEquals("ORDER_PLACED", rx.getFulfillmentStatus());
    }

    @Test
    void terminalStateIgnoresStaleEvent() {
        Prescription rx = givenRx("PICKED_UP", "PICKED_UP", ORDER_NO);
        send("e1", ORDER_NO, "DELIVERING");
        assertEquals(List.of(), hops());
        assertEquals("PICKED_UP", rx.getRxStatus());
        verify(rxMapper, never()).updateById(any(Prescription.class));
    }

    // ---------- REJECTED / CANCELLED 分支 ----------

    @Test
    void rejectedWalksToTransferFailed() {
        Prescription rx = givenRx("DISPATCHED", "ORDER_PLACED", ORDER_NO);
        send("e1", ORDER_NO, "REJECTED");
        assertEquals(List.of(RxStatus.DISPATCHING, RxStatus.TRANSFER_FAILED), hops());
        assertEquals("TRANSFER_FAILED", rx.getRxStatus());
    }

    @Test
    void rejectedAllOutOfStockFromEffective() {
        Prescription rx = givenRx("EFFECTIVE", null, null);
        send("e1", null, "REJECTED"); // 全店缺货：无订单号
        assertEquals(List.of(RxStatus.DISPATCHING, RxStatus.TRANSFER_FAILED), hops());
        assertEquals("TRANSFER_FAILED", rx.getRxStatus());
        assertEquals("REJECTED", rx.getFulfillmentStatus());
    }

    @Test
    void cancelledFromDispatchedCancelsViaTransferFailed() {
        Prescription rx = givenRx("DISPATCHED", "ORDER_PLACED", ORDER_NO);
        send("e1", ORDER_NO, "CANCELLED");
        // 修复前：沿履约链错误推进到 PICKED_UP
        assertEquals(List.of(RxStatus.DISPATCHING, RxStatus.TRANSFER_FAILED, RxStatus.CANCELLED), hops());
        assertEquals("CANCELLED", rx.getRxStatus());
    }

    @Test
    void cancelledFromEffectiveCancelsDirectly() {
        Prescription rx = givenRx("EFFECTIVE", null, null);
        send("e1", ORDER_NO, "CANCELLED");
        assertEquals(List.of(RxStatus.CANCELLED), hops());
        assertEquals("CANCELLED", rx.getRxStatus());
    }

    @Test
    void cancelledFromFulfillingKeepsRxStatus() {
        Prescription rx = givenRx("FULFILLING", "DISPENSING", ORDER_NO);
        send("e1", ORDER_NO, "CANCELLED"); // 配药中不可取消：主状态不动，履约状态照常物化
        assertEquals(List.of(), hops());
        assertEquals("FULFILLING", rx.getRxStatus());
        assertEquals("CANCELLED", rx.getFulfillmentStatus());
    }

    // ---------- 改派 ----------

    @Test
    void redispatchFromTransferFailedReentersChain() {
        Prescription rx = givenRx("TRANSFER_FAILED", "REJECTED", null);
        send("e1", "PO20260913000002", "STOCK_HELD"); // 人工改派后的新订单
        assertEquals(List.of(RxStatus.DISPATCHING, RxStatus.DISPATCHED), hops());
        assertEquals("DISPATCHED", rx.getRxStatus());
        assertEquals("STOCK_HELD", rx.getFulfillmentStatus());
        assertEquals("PO20260913000002", rx.getCurrentOrderNo());
    }

    // ---------- 边界 ----------

    @Test
    void noneStatusOnlyMaterializes() {
        Prescription rx = givenRx("EFFECTIVE", null, null);
        send("e1", ORDER_NO, "NONE");
        assertEquals(List.of(), hops());
        assertEquals("EFFECTIVE", rx.getRxStatus());
        assertEquals("NONE", rx.getFulfillmentStatus());
    }

    @Test
    void unknownStatusIgnored() {
        givenRx("EFFECTIVE", null, null);
        send("e1", ORDER_NO, "WHATEVER");
        assertEquals(List.of(), hops());
        verify(rxMapper, never()).updateById(any(Prescription.class));
        verify(logMapper, never()).insert(any(RxStatusLog.class));
    }

    @Test
    void inboxDuplicateSkipped() {
        when(inboxService.firstTime(any(), any())).thenReturn(false);
        send("e1", ORDER_NO, "STOCK_HELD");
        verify(prescriptionService, never()).mustGet(any());
    }
}
