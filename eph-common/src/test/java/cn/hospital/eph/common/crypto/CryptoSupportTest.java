package cn.hospital.eph.common.crypto;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CryptoSupportTest {

    @Test
    void rsaSignAndVerifyRoundtrip() {
        KeyPair kp = CryptoSupport.generateRsaKeyPair(2048);
        byte[] content = "{\"rxNo\":\"RX1\"}".getBytes(StandardCharsets.UTF_8);
        byte[] sig = CryptoSupport.signSha256Rsa(kp.getPrivate(), content);
        assertTrue(CryptoSupport.verifySha256Rsa(kp.getPublic(), content, sig));

        byte[] tampered = "{\"rxNo\":\"RX2\"}".getBytes(StandardCharsets.UTF_8);
        assertFalse(CryptoSupport.verifySha256Rsa(kp.getPublic(), tampered, sig), "篡改后验签必须失败");
    }

    @Test
    void aesGcmWrapUnwrap() {
        String master = "master-secret";
        KeyPair kp = CryptoSupport.generateRsaKeyPair(2048);
        String wrapped = CryptoSupport.aesGcmWrap(master, kp.getPrivate().getEncoded());
        byte[] back = CryptoSupport.aesGcmUnwrap(master, wrapped);
        assertTrue(new java.math.BigInteger(1, back).compareTo(
                new java.math.BigInteger(1, kp.getPrivate().getEncoded())) == 0);
        // 错误主密钥不可解密
        try {
            CryptoSupport.aesGcmUnwrap("wrong", wrapped);
            throw new AssertionError("错误主密钥应解密失败");
        } catch (IllegalStateException expected) {
        }
    }

    @Test
    void hmacConstantTime() {
        String sig = CryptoSupport.hmacSha256Hex("secret", "abc");
        assertTrue(CryptoSupport.constantTimeEquals(sig, CryptoSupport.hmacSha256Hex("secret", "abc")));
        assertFalse(CryptoSupport.constantTimeEquals(sig, "x"));
    }
}
