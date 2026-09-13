package cn.hospital.eph.prescription.storage;

/** 对象存储抽象：默认本地文件系统实现，阿里云 OSS 通过 profile 替换 */
public interface StorageService {

    void putObject(String key, byte[] content, String contentType);

    byte[] fetch(String key);

    /** 本地实现返回下载路径；OSS 实现可返回预签名 URL */
    String resolveUrl(String key);
}
