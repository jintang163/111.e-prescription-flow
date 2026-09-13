package cn.hospital.eph.user.ca;

import cn.hospital.eph.common.crypto.CryptoSupport;
import cn.hospital.eph.user.entity.CaCertificate;
import cn.hospital.eph.user.entity.UserSigningKey;
import cn.hospital.eph.user.mapper.CaCertificateMapper;
import cn.hospital.eph.user.mapper.UserSigningKeyMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/** 简易 CA：根 CA 首启自签（私钥落本地文件），用户证书由根 CA 签发，用户私钥主密钥包裹入库 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CaService {

    private final CaCertificateMapper certMapper;
    private final UserSigningKeyMapper keyMapper;

    @Value("${eph.master-key}")
    private String masterKey;

    @Value("${eph.ca.key-dir}")
    private String keyDir;

    private volatile KeyPair rootKeyPair;
    private volatile X509Certificate rootCert;

    /** 首启确保根 CA 存在 */
    @Transactional
    public synchronized void ensureRootCa() {
        CaCertificate existing = certMapper.selectOne(new QueryWrapper<CaCertificate>()
                .eq("cert_type", "ROOT").last("limit 1"));
        if (existing != null) {
            // 从磁盘恢复根私钥
            try {
                Path keyFile = Path.of(keyDir, "ca-private.der");
                Path certFile = Path.of(keyDir, "ca-cert.pem");
                if (Files.exists(keyFile)) {
                    byte[] pkcs8 = Files.readAllBytes(keyFile);
                    PrivateKey pk = java.security.KeyFactory.getInstance("RSA")
                            .generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(pkcs8));
                    this.rootCert = pemToCert(Files.readString(certFile));
                    this.rootKeyPair = new KeyPair(rootCert.getPublicKey(), pk);
                }
            } catch (Exception e) {
                log.warn("根 CA 私钥恢复失败，将保留数据库证书但无法签发新证: {}", e.getMessage());
            }
            return;
        }
        try {
            KeyPair kp = CryptoSupport.generateRsaKeyPair(4096);
            X500Name subject = new X500Name("CN=互联网医院电子处方根CA,O=互联网医院,C=CN");
            BigInteger serial = serialFromUuid();
            Date notBefore = Date.from(LocalDateTime.now().minusDays(1).toInstant(ZoneOffset.ofHours(8)));
            Date notAfter = Date.from(LocalDateTime.now().plusYears(10).toInstant(ZoneOffset.ofHours(8)));
            X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                    subject, serial, notBefore, notAfter, subject, kp.getPublic());
            builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
            builder.addExtension(Extension.keyUsage, true,
                    new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
            ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(kp.getPrivate());
            X509Certificate cert = new JcaX509CertificateConverter()
                    .getCertificate(builder.build(signer));

            File dir = new File(keyDir);
            dir.mkdirs();
            Files.write(Path.of(keyDir, "ca-private.der"), kp.getPrivate().getEncoded());
            new File(keyDir, "ca-private.der").setReadable(false, false);
            new File(keyDir, "ca-private.der").setReadable(true, true);
            String pem = certToPem(cert);
            Files.writeString(Path.of(keyDir, "ca-cert.pem"), pem, StandardCharsets.UTF_8);

            CaCertificate entity = new CaCertificate();
            String serialHex = serial.toString(16);
            entity.setSubjectCn("互联网医院电子处方根CA");
            entity.setCertSerial(serialHex);
            entity.setCertPem(pem);
            entity.setCertType("ROOT");
            entity.setIssuerSerial(serialHex);
            entity.setNotBefore(LocalDateTime.ofInstant(notBefore.toInstant(), ZoneOffset.UTC));
            entity.setNotAfter(LocalDateTime.ofInstant(notAfter.toInstant(), ZoneOffset.UTC));
            entity.setRevoked(0);
            entity.setCreatedAt(LocalDateTime.now());
            certMapper.insert(entity);

            this.rootKeyPair = kp;
            this.rootCert = cert;
            log.warn("已生成开发用根 CA（私钥存于 {}），生产环境必须替换为 KMS/HSM", keyDir);
        } catch (Exception e) {
            throw new IllegalStateException("根 CA 初始化失败", e);
        }
    }

    /** 为用户签发证书并托管私钥，返回证书序列号 */
    @Transactional
    public String issueUserCertificate(long userId, String realName, String ou) {
        ensureRootCa();
        if (rootKeyPair == null) {
            throw new IllegalStateException("根 CA 私钥不可用，无法签发证书");
        }
        Integer maxVersion = keyMapper.selectObjs(new QueryWrapper<UserSigningKey>()
                .select("coalesce(max(key_version),0) as v")
                .eq("user_id", userId)).stream().findFirst()
                .map(o -> ((Number) o).intValue()).orElse(0);
        int newVersion = maxVersion + 1;

        try {
            KeyPair userKp = CryptoSupport.generateRsaKeyPair(2048);
            X500Name subject = new X500Name(
                    "UID=" + userId + ",CN=" + realName + ",OU=" + ou + ",O=互联网医院,C=CN");
            BigInteger serial = serialFromUuid();
            Date notBefore = new Date();
            Date notAfter = Date.from(LocalDateTime.now().plusYears(2).toInstant(ZoneOffset.ofHours(8)));
            X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                    new X500Name(rootCert.getSubjectX500Principal().getName()), serial, notBefore, notAfter, subject, userKp.getPublic());
            builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
            builder.addExtension(Extension.keyUsage, true,
                    new KeyUsage(KeyUsage.digitalSignature | KeyUsage.nonRepudiation));
            builder.addExtension(Extension.extendedKeyUsage, false,
                    new ExtendedKeyUsage(KeyPurposeId.id_kp_clientAuth));
            ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(rootKeyPair.getPrivate());
            X509CertificateHolder holder = builder.build(signer);
            X509Certificate cert = new JcaX509CertificateConverter().getCertificate(holder);

            String serialHex = serial.toString(16);
            String pem = certToPem(cert);
            CaCertificate certEntity = new CaCertificate();
            certEntity.setSubjectCn(realName);
            certEntity.setCertSerial(serialHex);
            certEntity.setCertPem(pem);
            certEntity.setCertType("USER");
            certEntity.setOwnerUserId(userId);
            certEntity.setIssuerSerial(rootCert.getSerialNumber().toString(16));
            certEntity.setNotBefore(LocalDateTime.ofInstant(notBefore.toInstant(), ZoneOffset.UTC));
            certEntity.setNotAfter(LocalDateTime.ofInstant(notAfter.toInstant(), ZoneOffset.UTC));
            certEntity.setRevoked(0);
            certEntity.setCreatedAt(LocalDateTime.now());
            certMapper.insert(certEntity);

            // 旧密钥轮换
            List<UserSigningKey> olds = keyMapper.selectList(new QueryWrapper<UserSigningKey>()
                    .eq("user_id", userId).eq("status", "ACTIVE"));
            for (UserSigningKey old : olds) {
                old.setStatus("ROTATED");
                old.setRotatedAt(LocalDateTime.now());
                keyMapper.updateById(old);
            }

            UserSigningKey key = new UserSigningKey();
            key.setUserId(userId);
            key.setKeyVersion(newVersion);
            key.setAlg("RSA");
            key.setKeySize(2048);
            key.setCertSerial(serialHex);
            key.setPrivateKeyEnc(CryptoSupport.aesGcmWrap(masterKey, userKp.getPrivate().getEncoded()));
            key.setStatus("ACTIVE");
            key.setActivatedAt(LocalDateTime.now());
            keyMapper.insert(key);
            return serialHex;
        } catch (Exception e) {
            throw new IllegalStateException("用户证书签发失败", e);
        }
    }

    /** 托管签名：取用户 ACTIVE 私钥完成 SHA256withRSA */
    public SignResult sign(long userId, byte[] content) {
        UserSigningKey key = keyMapper.selectOne(new QueryWrapper<UserSigningKey>()
                .eq("user_id", userId).eq("status", "ACTIVE").last("limit 1"));
        if (key == null) {
            throw new cn.hospital.eph.common.web.BizException(cn.hospital.eph.common.web.ErrorCode.CERT_NOT_FOUND);
        }
        CaCertificate cert = certMapper.selectOne(new QueryWrapper<CaCertificate>()
                .eq("cert_serial", key.getCertSerial()).last("limit 1"));
        if (cert == null || Integer.valueOf(1).equals(cert.getRevoked())) {
            throw new cn.hospital.eph.common.web.BizException(cn.hospital.eph.common.web.ErrorCode.SIGN_CERT_REVOKED);
        }
        byte[] pkcs8 = CryptoSupport.aesGcmUnwrap(masterKey, key.getPrivateKeyEnc());
        PrivateKey privateKey = CryptoSupport.loadPkcs8PrivateKey(Base64.getEncoder().encodeToString(pkcs8));
        byte[] sig = CryptoSupport.signSha256Rsa(privateKey, content);
        return new SignResult(Base64.getEncoder().encodeToString(sig), cert.getCertSerial(), cert.getCertPem());
    }

    public PublicKey publicKeyOfCert(String certPem) {
        try {
            return pemToCert(certPem).getPublicKey();
        } catch (Exception e) {
            throw new IllegalArgumentException("证书解析失败", e);
        }
    }

    public boolean isRevoked(String certSerial) {
        CaCertificate cert = certMapper.selectOne(new QueryWrapper<CaCertificate>()
                .eq("cert_serial", certSerial).last("limit 1"));
        return cert == null || Integer.valueOf(1).equals(cert.getRevoked());
    }

    private static BigInteger serialFromUuid() {
        return new BigInteger(UUID.randomUUID().toString().replace("-", ""), 16);
    }

    static String certToPem(X509Certificate cert) throws Exception {
        String b64 = Base64.getEncoder().encodeToString(cert.getEncoded());
        StringBuilder sb = new StringBuilder("-----BEGIN CERTIFICATE-----\n");
        for (int i = 0; i < b64.length(); i += 64) {
            sb.append(b64, i, Math.min(i + 64, b64.length())).append('\n');
        }
        return sb.append("-----END CERTIFICATE-----\n").toString();
    }

    static X509Certificate pemToCert(String pem) throws Exception {
        String b64 = pem.replaceAll("-----[^-]+-----", "").replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(b64);
        return (X509Certificate) java.security.cert.CertificateFactory.getInstance("X.509")
                .generateCertificate(new java.io.ByteArrayInputStream(der));
    }

    public record SignResult(String signatureBase64, String certSerial, String certPem) {
    }
}
