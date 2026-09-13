package cn.hospital.eph.user.ca;

import cn.hospital.eph.common.crypto.CryptoSupport;
import cn.hospital.eph.user.entity.CaCertificate;
import cn.hospital.eph.user.entity.UserSigningKey;
import cn.hospital.eph.user.mapper.CaCertificateMapper;
import cn.hospital.eph.user.mapper.UserSigningKeyMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfDictionary;
import com.lowagie.text.pdf.PdfName;
import com.lowagie.text.pdf.PdfPKCS7;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfSignature;
import com.lowagie.text.pdf.PdfSignatureAppearance;
import com.lowagie.text.pdf.PdfStamper;
import com.lowagie.text.pdf.PdfString;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Calendar;
import java.util.HashMap;

/** 用托管私钥对处方 PDF 追加 PKCS#7 detached 数字签章（追加模式支持医生/药师顺序双签） */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdfSigningService {

    /** Contents 以 hex 字符串写入，预留空间 = 2 * 签名数组长度 + 2 */
    private static final int SIG_CAPACITY = 16384;
    private static final int CONTENTS_RESERVE = SIG_CAPACITY * 2 + 2;

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private final UserSigningKeyMapper keyMapper;
    private final CaCertificateMapper certMapper;

    @Value("${eph.master-key}")
    private String masterKey;

    public byte[] signDetached(long userId, byte[] pdf, String fieldName, String reason, boolean leftSide) {
        UserSigningKey key = keyMapper.selectOne(new QueryWrapper<UserSigningKey>()
                .eq("user_id", userId).eq("status", "ACTIVE").last("limit 1"));
        if (key == null) {
            throw new IllegalStateException("用户无 ACTIVE 签名密钥: " + userId);
        }
        CaCertificate certEntity = certMapper.selectOne(new QueryWrapper<CaCertificate>()
                .eq("cert_serial", key.getCertSerial()).last("limit 1"));
        if (certEntity == null || Integer.valueOf(1).equals(certEntity.getRevoked())) {
            throw new IllegalStateException("证书不存在或已吊销");
        }
        try {
            byte[] pkcs8 = CryptoSupport.aesGcmUnwrap(masterKey, key.getPrivateKeyEnc());
            PrivateKey privateKey = CryptoSupport.loadPkcs8PrivateKey(Base64.getEncoder().encodeToString(pkcs8));
            X509Certificate userCert = (X509Certificate) java.security.cert.CertificateFactory.getInstance("X.509")
                    .generateCertificate(new java.io.ByteArrayInputStream(
                            Base64.getMimeDecoder().decode(
                                    certEntity.getCertPem().replaceAll("-----[^-]+-----", ""))));
            java.security.cert.Certificate[] chain = {userCert};

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PdfReader reader = new PdfReader(pdf);
            // append=true 保留既有签名
            PdfStamper stamper = PdfStamper.createSignature(reader, out, '\0', null, true);
            PdfSignatureAppearance appearance = stamper.getSignatureAppearance();
            appearance.setReason(reason);
            appearance.setLocation("互联网医院");
            appearance.setVisibleSignature(
                    new Rectangle(leftSide ? 36 : 305, 36, leftSide ? 290 : 559, 110),
                    reader.getNumberOfPages(), fieldName);
            appearance.setLayer2Text(reason + "\n" + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                    .format(Calendar.getInstance().getTime()));

            PdfSignature dic = new PdfSignature(PdfName.ADOBE_PPKLITE, PdfName.ADBE_PKCS7_DETACHED);
            dic.setReason(reason);
            dic.setLocation("互联网医院");
            appearance.setSignDate(Calendar.getInstance());
            appearance.setCryptoDictionary(dic);

            HashMap<PdfName, Integer> exclusions = new HashMap<>();
            exclusions.put(PdfName.CONTENTS, CONTENTS_RESERVE);
            appearance.preClose(exclusions);

            // 计算 ByteRange 覆盖内容摘要
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (InputStream rg = appearance.getRangeStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = rg.read(buf)) > 0) {
                    md.update(buf, 0, n);
                }
            }
            PdfPKCS7 sgn = new PdfPKCS7(privateKey, chain, null, "SHA256", null, false);
            byte[] hash = md.digest();
            Calendar signDate = Calendar.getInstance();
            appearance.setSignDate(signDate);
            // 标准 PAdES 流程：对已认证属性做签名，再组装 PKCS#7
            byte[] authenticatedAttrs = sgn.getAuthenticatedAttributeBytes(hash, signDate, null);
            sgn.update(authenticatedAttrs, 0, authenticatedAttrs.length);
            byte[] encodedSig = sgn.getEncodedPKCS7(hash, signDate, null, null);

            byte[] padded = new byte[SIG_CAPACITY];
            System.arraycopy(encodedSig, 0, padded, 0, encodedSig.length);
            PdfDictionary sigDic = new PdfDictionary();
            sigDic.put(PdfName.CONTENTS, new PdfString(padded).setHexWriting(true));
            appearance.close(sigDic);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("PDF 数字签章失败 userId={}", userId, e);
            throw new IllegalStateException("PDF 签章失败", e);
        }
    }
}
