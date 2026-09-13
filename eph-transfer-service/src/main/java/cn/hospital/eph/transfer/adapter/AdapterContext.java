package cn.hospital.eph.transfer.adapter;

import cn.hospital.eph.transfer.entity.Pharmacy;

/** 一次药店调用的上下文：药店配置 + 处方/订单标识 */
public record AdapterContext(Pharmacy pharmacy, String rxNo, int rxVersion, String orderNo) {

    public static AdapterContext of(Pharmacy pharmacy, String rxNo, int rxVersion, String orderNo) {
        return new AdapterContext(pharmacy, rxNo, rxVersion, orderNo);
    }
}
