package com.cloudvault.service;

import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption.
 *
 * The encrypted output format is:   [ 12-byte IV ][ ciphertext + 16-byte auth tag ]
 * The IV is prepended to the ciphertext so each chunk has its own unique IV,
 * which is required for GCM to be secure.
 *
 * Common 500-on-download causes this class fixes:
 *  - Reusing the same IV across chunks (breaks GCM authentication)
 *  - Storing IV separately from ciphertext (requires extra DB column)
 *  - Using ECB mode (no IV, deterministic, insecure)
 *  - Storing raw key bytes instead of Base64 (DB encoding issues)
 */
@Service
public class EncryptionService {

    private static final String ALGORITHM  = "AES/GCM/NoPadding";
    private static final int    KEY_BITS   = 256;
    private static final int    IV_BYTES   = 12;   // 96-bit IV — GCM standard
    private static final int    TAG_BITS   = 128;  // 128-bit auth tag

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Generate a new AES-256 key, returned as a Base64 string for DB storage.
     */
    public String generateKey() throws Exception {
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(KEY_BITS, secureRandom);
        SecretKey key = keyGen.generateKey();
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    /**
     * Encrypt plaintext bytes.
     * Output: [ 12-byte IV ][ GCM ciphertext + 16-byte auth tag ]
     */
    public byte[] encrypt(byte[] plaintext, String base64Key) throws Exception {
        SecretKey key = decodeKey(base64Key);

        // Fresh random IV per chunk — critical for GCM security
        byte[] iv = new byte[IV_BYTES];
        secureRandom.nextBytes(iv);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
        byte[] ciphertext = cipher.doFinal(plaintext);

        // Prepend IV so decrypt() can extract it without extra storage
        byte[] output = new byte[IV_BYTES + ciphertext.length];
        System.arraycopy(iv,         0, output, 0,         IV_BYTES);
        System.arraycopy(ciphertext, 0, output, IV_BYTES,  ciphertext.length);
        return output;
    }

    /**
     * Decrypt bytes produced by encrypt().
     * Extracts the IV from the first 12 bytes, then decrypts the rest.
     */
    public byte[] decrypt(byte[] encryptedWithIv, String base64Key) throws Exception {
        if (encryptedWithIv == null || encryptedWithIv.length <= IV_BYTES) {
            throw new IllegalArgumentException(
                    "Encrypted data is too short — likely corrupt or empty chunk");
        }

        SecretKey key = decodeKey(base64Key);

        // Split IV and ciphertext
        byte[] iv         = new byte[IV_BYTES];
        byte[] ciphertext = new byte[encryptedWithIv.length - IV_BYTES];
        System.arraycopy(encryptedWithIv, 0,        iv,         0, IV_BYTES);
        System.arraycopy(encryptedWithIv, IV_BYTES, ciphertext, 0, ciphertext.length);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
        return cipher.doFinal(ciphertext);
    }

    private SecretKey decodeKey(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalArgumentException(
                    "Encryption key is null or blank — check @JsonIgnore and DB column");
        }
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        return new SecretKeySpec(keyBytes, "AES");
    }
}