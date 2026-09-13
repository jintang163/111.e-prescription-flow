package cn.hospital.eph.transfer.adapter;

import cn.hospital.eph.common.enums.FulfillmentStatus;

import java.util.Map;

/** 外部药店状态码 → 内部履约状态集中映射（新增药店只需扩展映射） */
public final class ExternalStatusMapping {

    private static final Map<String, FulfillmentStatus> MAPPING = Map.ofEntries(
            Map.entry("ACCEPTED", FulfillmentStatus.PHARMACY_ACCEPTED),
            Map.entry("DISPENSING", FulfillmentStatus.DISPENSING),
            Map.entry("DISPENSED", FulfillmentStatus.DISPENSED),
            Map.entry("DELIVERING", FulfillmentStatus.DELIVERING),
            Map.entry("READY", FulfillmentStatus.READY_FOR_PICKUP),
            Map.entry("PICKED_UP", FulfillmentStatus.PICKED_UP),
            Map.entry("REJECTED", FulfillmentStatus.REJECTED),
            Map.entry("CANCELLED", FulfillmentStatus.CANCELLED));

    private static final Map<FulfillmentStatus, String> TEXT = Map.ofEntries(
            Map.entry(FulfillmentStatus.ORDER_PLACED, "订单已下发药店"),
            Map.entry(FulfillmentStatus.PHARMACY_ACCEPTED, "药店已受理"),
            Map.entry(FulfillmentStatus.DISPENSING, "药店配药中"),
            Map.entry(FulfillmentStatus.DISPENSED, "药品已配齐"),
            Map.entry(FulfillmentStatus.DELIVERING, "药品配送中"),
            Map.entry(FulfillmentStatus.READY_FOR_PICKUP, "待取药"),
            Map.entry(FulfillmentStatus.PICKED_UP, "已取药"),
            Map.entry(FulfillmentStatus.REJECTED, "药店拒绝/缺货"),
            Map.entry(FulfillmentStatus.CANCELLED, "订单已取消"));

    private ExternalStatusMapping() {
    }

    public static FulfillmentStatus map(String externalCode) {
        FulfillmentStatus s = MAPPING.get(externalCode);
        if (s == null) {
            throw new IllegalArgumentException("未知药店状态码: " + externalCode);
        }
        return s;
    }

    public static String text(FulfillmentStatus s) {
        return TEXT.getOrDefault(s, s.code());
    }
}
