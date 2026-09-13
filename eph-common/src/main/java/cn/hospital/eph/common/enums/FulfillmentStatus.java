package cn.hospital.eph.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;

/** 药店订单履约状态（独立于处方主状态） */
public enum FulfillmentStatus {
    NONE("NONE"),
    STOCK_CHECKING("STOCK_CHECKING"),
    STOCK_HELD("STOCK_HELD"),
    ORDER_PLACED("ORDER_PLACED"),
    PHARMACY_ACCEPTED("PHARMACY_ACCEPTED"),
    DISPENSING("DISPENSING"),
    DISPENSED("DISPENSED"),
    READY_FOR_PICKUP("READY_FOR_PICKUP"),
    DELIVERING("DELIVERING"),
    PICKED_UP("PICKED_UP"),
    REJECTED("REJECTED"),
    CANCELLED("CANCELLED");

    @EnumValue
    private final String code;

    FulfillmentStatus(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static FulfillmentStatus of(String code) {
        for (FulfillmentStatus v : values()) {
            if (v.code.equals(code)) return v;
        }
        throw new IllegalArgumentException("unknown fulfillment status: " + code);
    }
}
