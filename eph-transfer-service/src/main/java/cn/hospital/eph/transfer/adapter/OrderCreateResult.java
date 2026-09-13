package cn.hospital.eph.transfer.adapter;

public record OrderCreateResult(boolean success, String externalOrderNo, String detail) {
}
