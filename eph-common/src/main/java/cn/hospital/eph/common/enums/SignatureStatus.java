package cn.hospital.eph.common.enums;

/** 签名记录在该处方版本上的状态 */
public enum SignatureStatus {
    ACTIVE,       // 当前有效
    SUPERSEDED    // 补正后旧版本签名失效（仅归档举证，不再支撑生效判定）
}
