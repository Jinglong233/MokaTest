package com.mokatest.platform.demos.ai.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AES 加解密工具单元测试
 */
class AesCryptoUtilTest {

    @Test
    void encryptDecryptRoundtrip() {
        String plain = "sk-test-api-key-1234567890";
        String encrypted = AesCryptoUtil.encrypt(plain);
        assertNotNull(encrypted);
        assertNotEquals(plain, encrypted);
        assertEquals(plain, AesCryptoUtil.decrypt(encrypted));
    }

    @Test
    void emptyValuesPassThrough() {
        assertNull(AesCryptoUtil.encrypt(null));
        assertEquals("", AesCryptoUtil.encrypt(""));
        assertNull(AesCryptoUtil.decrypt(null));
        assertEquals("", AesCryptoUtil.decrypt(""));
    }

    @Test
    void legacyPlaintextDecryptsAsIs() {
        // 历史明文数据（非 Base64 密文）解密失败时原样返回
        assertEquals("plain-key", AesCryptoUtil.decrypt("plain-key"));
    }

    @Test
    void maskKeepsOnlyFirstFour() {
        String masked = AesCryptoUtil.mask(AesCryptoUtil.encrypt("sk-abcdefgh"));
        assertEquals("sk-a****", masked);
        assertEquals("****", AesCryptoUtil.mask(AesCryptoUtil.encrypt("abc")));
        assertEquals("", AesCryptoUtil.mask(""));
    }

    @Test
    void eachEncryptionHasRandomIv() {
        String a = AesCryptoUtil.encrypt("same-plaintext");
        String b = AesCryptoUtil.encrypt("same-plaintext");
        assertNotEquals(a, b, "GCM 随机 IV，同明文密文应不同");
        assertEquals(AesCryptoUtil.decrypt(a), AesCryptoUtil.decrypt(b));
    }
}
