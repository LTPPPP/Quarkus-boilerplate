package com.example.app.util;

import io.quarkus.test.junit.QuarkusTest;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class EncryptionUtilTest {

    @Inject
    EncryptionUtil encryptionUtil;

    @Test
    void testEncryptDecryptRoundtrip() {
        String original = "Hello, World! This is secret data.";
        String encrypted = encryptionUtil.encrypt(original);
        String decrypted = encryptionUtil.decrypt(encrypted);

        assertNotNull(encrypted, "Encrypted string should not be null");
        assertNotEquals(original, encrypted, "Encrypted should differ from original");
        assertEquals(original, decrypted, "Decrypted should match original");
    }

    @Test
    void testEncryptProducesDifferentCiphertexts() {
        String original = "Same input";
        String encrypted1 = encryptionUtil.encrypt(original);
        String encrypted2 = encryptionUtil.encrypt(original);

        assertNotEquals(encrypted1, encrypted2,
                "Two encryptions of same plaintext should produce different ciphertexts (random IV)");

        assertEquals(original, encryptionUtil.decrypt(encrypted1));
        assertEquals(original, encryptionUtil.decrypt(encrypted2));
    }

    @Test
    void testEncryptEmptyString() {
        String encrypted = encryptionUtil.encrypt("");
        String decrypted = encryptionUtil.decrypt(encrypted);
        assertEquals("", decrypted);
    }

    @Test
    void testEncryptLongString() {
        String longString = "A".repeat(10000);
        String encrypted = encryptionUtil.encrypt(longString);
        String decrypted = encryptionUtil.decrypt(encrypted);
        assertEquals(longString, decrypted);
    }

    @Test
    void testEncryptUnicodeString() {
        String unicode = "Xin chào thế giới! 🌍 日本語テスト";
        String encrypted = encryptionUtil.encrypt(unicode);
        String decrypted = encryptionUtil.decrypt(encrypted);
        assertEquals(unicode, decrypted);
    }

    @Test
    void testDecryptInvalidCiphertextThrows() {
        assertThrows(IllegalStateException.class,
                () -> encryptionUtil.decrypt("not-valid-base64-!!"));
    }

    @Test
    void testDecryptTamperedCiphertextThrows() {
        String original = "Tamper test";
        String encrypted = encryptionUtil.encrypt(original);

        char[] chars = encrypted.toCharArray();
        chars[chars.length / 2] = (char) (chars[chars.length / 2] + 1);
        String tampered = new String(chars);

        assertThrows(IllegalStateException.class,
                () -> encryptionUtil.decrypt(tampered));
    }

    @Test
    void testEncryptedOutputIsBase64() {
        String encrypted = encryptionUtil.encrypt("test");
        assertTrue(encrypted.matches("^[A-Za-z0-9+/]+=*$"),
                "Encrypted output should be valid Base64");
    }
}
