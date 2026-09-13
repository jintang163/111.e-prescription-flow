package cn.hospital.eph.prescription.storage;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** 本地文件系统存储（默认），key 即相对路径 */
@Slf4j
@Service
@ConditionalOnProperty(name = "eph.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalFsStorageService implements StorageService {

    @Value("${eph.storage.local-dir}")
    private String baseDir;

    @PostConstruct
    void init() throws IOException {
        Files.createDirectories(Path.of(baseDir));
    }

    @Override
    public void putObject(String key, byte[] content, String contentType) {
        try {
            Path target = Path.of(baseDir, key);
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException e) {
            throw new IllegalStateException("本地存储写入失败: " + key, e);
        }
    }

    @Override
    public byte[] fetch(String key) {
        try {
            return Files.readAllBytes(Path.of(baseDir, key));
        } catch (IOException e) {
            throw new IllegalArgumentException("文件不存在: " + key, e);
        }
    }

    @Override
    public String resolveUrl(String key) {
        return "/api/prescriptions/file/" + key;
    }
}
