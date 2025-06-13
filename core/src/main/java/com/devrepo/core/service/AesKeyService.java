package com.devrepo.core.service;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.nio.charset.StandardCharsets;

public class AesKeyService {
    private static final Logger log = LoggerFactory.getLogger(AesKeyService.class);

    public static String decrypt(String encrypted, String key) throws Exception {

        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length != 16) {
            throw new IllegalArgumentException("Key must be 16 bytes for AES-128");
        }

        SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");

        cipher.init(Cipher.DECRYPT_MODE, secretKey);
        byte[] decodedValue = Base64.getDecoder().decode(encrypted);
        byte[] decrypted = cipher.doFinal(decodedValue);

        log.info(new String(decrypted, StandardCharsets.UTF_8));
        return new String(decrypted, StandardCharsets.UTF_8);
    }
}

