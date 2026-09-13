package cn.hospital.eph.common.crypto;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;

/** 加密/签名/摘要基础工具（RSA-SHA256、AES-GCM 包裹、HMAC、常量时间比较） */
public final class CryptoSupport {

    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LEN = 12;
    private static final int GCM_TAG_BITS = 128;

    private CryptoSupport() {
    }

    public static KeyPair generateRsaKeyPair(int keySize) {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(keySize);
            return kpg.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("RSA 密钥对生成失败", e);
        }
    }

    public static byte[] signSha256Rsa(PrivateKey privateKey, byte[] content) {
        try {
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initSign(privateKey);
            sig.update(content);
            return sig.sign();
        } catch (Exception e) {
            throw new IllegalStateException("RSA 签名失败", e);
        }
    }

    public static boolean verifySha256Rsa(PublicKey publicKey, byte[] content, byte[] signature) {
        try {
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initVerify(publicKey);
            sig.update(content);
            return sig.verify(signature);
        } catch (Exception e) {
            return false;
        }
    }

    public static String sha256Hex(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            return toHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 失败", e);
        }
    }

    public static String sha256Hex(String text) {
        return sha256Hex(text.getBytes(StandardCharsets.UTF_8));
    }

    public static String hmacSha256Hex(String secret, String content) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return toHex(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC 计算失败", e);
        }
    }

    /** 常量时间比较，防时序侧信道 */
    public static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    /** 用主密钥 AES-GCM 包裹用户私钥；输出 base64(iv|ciphertext) */
    public static String aesGcmWrap(String masterKey, byte[] plain) {
        try {
            byte[] iv = randomIv();
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.ENCRYPT_MODE, deriveAesKey(masterKey), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plain);
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("AES-GCM 加密失败", e);
        }
    }

    public static byte[] aesGcmUnwrap(String masterKey, String wrappedBase64) {
        try {
            byte[] all = Base64.getDecoder().decode(wrappedBase64);
            byte[] iv = Arrays.copyOfRange(all, 0, GCM_IV_LEN);
            byte[] ct = Arrays.copyOfRange(all, GCM_IV_LEN, all.length);
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.DECRYPT_MODE, deriveAesKey(masterKey), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return cipher.doFinal(ct);
        } catch (Exception e) {
            throw new IllegalStateException("AES-GCM 解密失败", e);
        }
    }

    private static byte[] randomIv() {
        byte[] iv = new byte[GCM_IV_LEN];
        new java.security.SecureRandom().nextBytes(iv);
        return iv;
    }

    private static SecretKeySpec deriveAesKey(String masterKey) {
        byte[] keyBytes = sha256(masterKey.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(keyBytes, "AES");
    }

    private static byte[] sha256(byte[] content) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(content);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    public static String encodePkcs8Base64(PrivateKey key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    public static String encodeX509Base64(PublicKey key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    public static PrivateKey loadPkcs8PrivateKey(String base64) {
        try {
            byte[] der = Base64.getDecoder().decode(base64);
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("私钥加载失败", e);
        }
    }

    public static PublicKey loadX509PublicKey(String base64) {
        try {
            byte[] der = Base64.getDecoder().decode(base64);
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("公钥加载失败", e);
        }
    }
}
