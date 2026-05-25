package com.hsbc.sel.emgr.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class AesCryptoUtil {

    private static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";
    private static final int IV_LENGTH = 16;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private AesCryptoUtil() {
    }

    public static String encrypt(String plainText, String keyMaterial) {
        try {
            byte[] key = deriveKey(keyMaterial);
            byte[] iv = new byte[IV_LENGTH];
            SECURE_RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception ex) {
            throw new IllegalStateException("Password encryption failed", ex);
        }
    }

    public static String decrypt(String encryptedBase64, String keyMaterial) {
        try {
            byte[] key = deriveKey(keyMaterial);
            byte[] combined = Base64.getDecoder().decode(encryptedBase64);

            if (combined.length <= IV_LENGTH) {
                throw new IllegalStateException("Encrypted payload is invalid");
            }

            byte[] iv = Arrays.copyOfRange(combined, 0, IV_LENGTH);
            byte[] cipherText = Arrays.copyOfRange(combined, IV_LENGTH, combined.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
            byte[] plain = cipher.doFinal(cipherText);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Password decryption failed", ex);
        }
    }

    private static byte[] deriveKey(String keyMaterial) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return digest.digest(keyMaterial.getBytes(StandardCharsets.UTF_8));
    }
}

