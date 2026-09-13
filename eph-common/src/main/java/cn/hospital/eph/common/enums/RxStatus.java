package cn.hospital.eph.common.enums;

/** 处方主状态（法律状态） */
public enum RxStatus {
    DRAFT,                  // 草稿
    SUBMITTED,              // 医生已签名提交
    REVIEWING,              // 药师审核中
    REJECTED,               // 驳回（终态，可复制新开）
    AMENDMENT_REQUESTED,    // 要求补正，待医生修改
    EFFECTIVE,              // 双签齐备生效
    DISPATCHING,            // 派单/库存校验中
    DISPATCHED,             // 已下发药店
    FULFILLING,             // 药店配药中
    DISPENSED,              // 已配药
    READY_FOR_PICKUP,       // 待取药/已发药
    PICKED_UP,              // 已取药（终态）
    TRANSFER_FAILED,        // 流转失败，待人工介入
    CANCELLED;              // 已取消/作废

    /** 合法状态转移守卫，保证状态机单调前进 */
    public boolean canTransitionTo(RxStatus target) {
        return switch (this) {
            case DRAFT, AMENDMENT_REQUESTED -> target == SUBMITTED || target == CANCELLED;
            case SUBMITTED -> target == REVIEWING || target == CANCELLED;
            case REVIEWING -> target == EFFECTIVE || target == REJECTED || target == AMENDMENT_REQUESTED;
            case EFFECTIVE -> target == DISPATCHING || target == CANCELLED;
            case DISPATCHING -> target == DISPATCHED || target == TRANSFER_FAILED;
            case DISPATCHED -> target == FULFILLING || target == DISPATCHING; // 改派回 DISPATCHING
            case FULFILLING -> target == DISPENSED;
            case DISPENSED -> target == READY_FOR_PICKUP;
            case READY_FOR_PICKUP -> target == PICKED_UP;
            case TRANSFER_FAILED -> target == DISPATCHING || target == CANCELLED;
            default -> false;
        };
    }
}
