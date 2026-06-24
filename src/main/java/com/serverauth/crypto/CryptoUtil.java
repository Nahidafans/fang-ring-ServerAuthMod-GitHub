/*
 * ServerAuthMod
 * Copyright (C) 2024  YourName
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.serverauth.crypto;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;

/**
 * AES-256 encryption utility for device fingerprint storage.
 * 
 * IMPORTANT: For production use, you MUST change the SECRET and SALT values
 * below to something unique to your server. The defaults shown here are
 * examples only and provide minimal security.
 * 
 * Change these values:
 *   - SECRET: A random string known only to you
 *   - SALT:   A different random string
 * 
 * You can generate them using any password generator.
 * 
 * Security note: The encryption key is derived from hardcoded strings.
 * This is sufficient for obfuscating device fingerprints in config files,
 * but does NOT provide cryptographic security against determined attackers.
 * For higher security requirements, implement a server-provided key exchange.
 */
public class CryptoUtil {
    
    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";
    
    // ====================================================================
    // !!! IMPORTANT: CHANGE THESE VALUES FOR YOUR SERVER DEPLOYMENT !!!
    // ====================================================================
    // Replace these with your own random strings before building the mod.
    // Keep them the same across all clients that connect to your server.
    // ====================================================================
    private static final String SECRET = "CHANGE_THIS_TO_YOUR_OWN_RANDOM_SECRET_KEY_64CHARS_MIN";
    private static final String SALT   = "CHANGE_THIS_TO_YOUR_OWN_RANDOM_SALT_VALUE_32CHARS_MIN";
    // ====================================================================

    // Derive AES key from password using PBKDF2
    private static SecretKey deriveKey() throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(SECRET.toCharArray(), SALT.getBytes(StandardCharsets.UTF_8), 65536, 256);
        SecretKey tmp = factory.generateSecret(spec);
        return new SecretKeySpec(tmp.getEncoded(), "AES");
    }

    /**
     * Encrypt plaintext and return Base64-encoded result (format: iv:encrypted)
     */
    public static String encrypt(String plaintext) {
        try {
            SecretKey key = deriveKey();
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            
            // Generate random IV
            byte[] iv = new byte[16];
            new SecureRandom().nextBytes(iv);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            
            cipher.init(Cipher.ENCRYPT_MODE, key, ivSpec);
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            
            // Combine IV + encrypted data, then Base64 encode
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    /**
     * Decrypt Base64-encoded ciphertext
     */
    public static String decrypt(String ciphertext) {
        try {
            SecretKey key = deriveKey();
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            
            byte[] combined = Base64.getDecoder().decode(ciphertext);
            
            // Extract IV (first 16 bytes)
            byte[] iv = new byte[16];
            System.arraycopy(combined, 0, iv, 0, 16);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            
            // Extract encrypted data
            byte[] encrypted = new byte[combined.length - 16];
            System.arraycopy(combined, 16, encrypted, 0, encrypted.length);
            
            cipher.init(Cipher.DECRYPT_MODE, key, ivSpec);
            byte[] decrypted = cipher.doFinal(encrypted);
            
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }

    /**
     * Hash a string to SHA-256 hex
     */
    public static String sha256(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("Hash failed", e);
        }
    }
}
