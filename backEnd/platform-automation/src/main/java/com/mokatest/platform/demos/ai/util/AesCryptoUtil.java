package com.mokatest.platform.demos.ai.util;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * API Key 等敏感配置的对称加解密工具（AES-GCM）
 *
 * 密钥来源：JVM 启动参数 -Dmokatest.ai.secret=xxx 或环境变量 MOKATEST_AI_SECRET，
 * 未配置时使用内置默认密钥（仅适合开发环境，生产必须显式配置）。
 * 密文格式：Base64( iv(12B) + cipherText )
 */
public class AesCryptoUtil {

    private static final String DEFAULT_SECRET = "mokatest-ai-config-default-secret";
    private static final int IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;

    private static volatile SecretKeySpec keySpec;

    private static SecretKeySpec keySpec() {
        if (keySpec == null) {
            synchronized (AesCryptoUtil.class) {
                if (keySpec == null) {
                    String secret = System.getProperty("mokatest.ai.secret");
                    if (secret == null || secret.isEmpty()) {
                        secret = System.getenv("MOKATEST_AI_SECRET");
                    }
                    if (secret == null || secret.isEmpty()) {
                        secret = DEFAULT_SECRET;
                    }
                    try {
                        // 任意长度密钥统一 SHA-256 收敛为 32 字节
                        MessageDigest digest = MessageDigest.getInstance("SHA-256");
                        keySpec = new SecretKeySpec(digest.digest(secret.getBytes(StandardCharsets.UTF_8)), "AES");
                    } catch (Exception e) {
                        throw new IllegalStateException("AES 密钥初始化失败", e);
                    }
                }
            }
        }
        return keySpec;
    }

    /** 加密，返回 Base64 密文；原文为空时原样返回 */
    public static String encrypt(String plain) {
        if (plain == null || plain.isEmpty()) {
            return plain;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] merged = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, merged, 0, iv.length);
            System.arraycopy(encrypted, 0, merged, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(merged);
        } catch (Exception e) {
            throw new IllegalStateException("敏感配置加密失败", e);
        }
    }

    /** 解密；密文为空或无法解密时原样返回（兼容历史明文数据） */
    public static String decrypt(String cipherText) {
        if (cipherText == null || cipherText.isEmpty()) {
            return cipherText;
        }
        try {
            byte[] merged = Base64.getDecoder().decode(cipherText);
            if (merged.length <= IV_LENGTH) {
                return cipherText;
            }
            byte[] iv = new byte[IV_LENGTH];
            byte[] encrypted = new byte[merged.length - IV_LENGTH];
            System.arraycopy(merged, 0, iv, 0, IV_LENGTH);
            System.arraycopy(merged, IV_LENGTH, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            // 兼容历史明文：解不开就当明文返回
            return cipherText;
        }
    }

    /** 打码展示：仅保留前 4 位，其余以 * 代替 */
    public static String mask(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String plain = decrypt(value);
        if (plain.length() <= 4) {
            return "****";
        }
        return plain.substring(0, 4) + "****";
    }
}
