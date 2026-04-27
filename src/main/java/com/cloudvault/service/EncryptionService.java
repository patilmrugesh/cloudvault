package com.cloudvault.service;

import org.springframework.stereotype.Service;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Service
public class EncryptionService {

    private static final String ALGORITHM = "AES";

    /**
     * Generates a unique AES key for a new file.
     * We will store this key in the PostgreSQL metadata.
     */
    public String generateKey() throws Exception {
        KeyGenerator keyGen = KeyGenerator.getInstance(ALGORITHM);
        keyGen.init(256);
        SecretKey secretKey = keyGen.generateKey();
        return Base64.getEncoder().encodeToString(secretKey.getEncoded());
    }

    /**
     * Encrypts a byte array (file chunk) using the provided key.
     */
    public byte[] encrypt(byte[] data, String base64Key) throws Exception {
        SecretKeySpec keySpec = decodeKey(base64Key);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        return cipher.doFinal(data);
    }

    /**
     * Decrypts an encrypted byte array back to original data.
     */
    public byte[] decrypt(byte[] encryptedData, String base64Key) throws Exception {
        SecretKeySpec keySpec = decodeKey(base64Key);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, keySpec);
        return cipher.doFinal(encryptedData);
    }

    private SecretKeySpec decodeKey(String base64Key) {
        byte[] decodedKey = Base64.getDecoder().decode(base64Key);
        return new SecretKeySpec(decodedKey, ALGORITHM);
    }
}