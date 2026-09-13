package cn.hospital.eph.common.web;

import lombok.Getter;

/** 业务错误码：AUTH 鉴权 / RX 处方状态 / SIGN 签名 / PHARM 药店 / SYS 系统 */
@Getter
public enum ErrorCode {
    UNAUTHORIZED(40100, "未登录或登录已过期"),
    FORBIDDEN(40300, "无操作权限"),
    SIGN_GRANT_REQUIRED(40101, "签名前请先完成重认证"),
    BAD_REQUEST(40000, "请求参数错误"),
    NOT_FOUND(40400, "资源不存在"),
    RX_STATE_ILLEGAL(40901, "处方当前状态不允许该操作"),
    RX_VERSION_CONFLICT(40902, "处方已被修改，请刷新后重试"),
    SIGN_INVALID(42201, "数字签名校验失败"),
    SIGN_CERT_REVOKED(42202, "签名证书已吊销"),
    CERT_NOT_FOUND(42203, "用户尚未签发签名证书"),
    PHARMACY_REJECT(52201, "药店拒绝订单"),
    PHARMACY_NO_STOCK(52202, "药店库存不足"),
    PHARMACY_CALLBACK_UNSIGNED(40110, "药店回调验签失败"),
    SYS_ERROR(50000, "系统繁忙，请稍后再试");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
