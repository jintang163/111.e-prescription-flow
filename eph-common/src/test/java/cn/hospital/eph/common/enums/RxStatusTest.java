package cn.hospital.eph.common.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RxStatusTest {

    @Test
    void happyPath() {
        RxStatus s = RxStatus.DRAFT;
        for (RxStatus next : new RxStatus[]{RxStatus.SUBMITTED, RxStatus.REVIEWING, RxStatus.EFFECTIVE,
                RxStatus.DISPATCHING, RxStatus.DISPATCHED, RxStatus.FULFILLING,
                RxStatus.DISPENSED, RxStatus.READY_FOR_PICKUP, RxStatus.PICKED_UP}) {
            assertTrue(s.canTransitionTo(next), s + " -> " + next + " 应合法");
            s = next;
        }
    }

    @Test
    void amendmentLoop() {
        assertTrue(RxStatus.REVIEWING.canTransitionTo(RxStatus.AMENDMENT_REQUESTED));
        assertTrue(RxStatus.AMENDMENT_REQUESTED.canTransitionTo(RxStatus.SUBMITTED));
        // 驳回后不可复活
        assertFalse(RxStatus.REJECTED.canTransitionTo(RxStatus.SUBMITTED));
        // 终态不可推进
        assertFalse(RxStatus.PICKED_UP.canTransitionTo(RxStatus.DISPATCHED));
        // 生效前必须经审核
        assertFalse(RxStatus.SUBMITTED.canTransitionTo(RxStatus.EFFECTIVE));
    }
}
