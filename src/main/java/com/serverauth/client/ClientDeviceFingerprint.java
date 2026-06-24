/*
 * ServerAuthMod - Minecraft Forge Server Authentication Mod
 * Copyright (C) 2024
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
package com.serverauth.client;

import com.serverauth.crypto.CryptoUtil;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@OnlyIn(Dist.CLIENT)
public class ClientDeviceFingerprint {
    private static final Logger LOGGER = LogManager.getLogger("DeviceFingerprint");
    
    private static final Path FINGERPRINT_FILE = FMLPaths.CONFIGDIR.get().resolve("serverauth").resolve("device.dat");
    
    private static String cachedFingerprint = null;
    private static long lastChangeDetected = 0;

    public static String generateFingerprint() {
        StringBuilder sb = new StringBuilder();
        
        // OS info
        sb.append("OS:").append(System.getProperty("os.name")).append("|");
        sb.append("OS_ARCH:").append(System.getProperty("os.arch")).append("|");
        sb.append("OS_VER:").append(System.getProperty("os.version")).append("|");
        
        // Java info
        sb.append("JAVA:").append(System.getProperty("java.version")).append("|");
        sb.append("JAVA_VENDOR:").append(System.getProperty("java.vendor")).append("|");
        
        // Hardware
        sb.append("CPU:").append(Runtime.getRuntime().availableProcessors()).append("|");
        sb.append("RAM:").append(Runtime.getRuntime().maxMemory()).append("|");
        
        // User info (hashed for privacy)
        String userName = System.getProperty("user.name", "unknown");
        String computerName = getComputerName();
        sb.append("USER:").append(CryptoUtil.sha256(userName)).append("|");
        sb.append("COMPUTER:").append(CryptoUtil.sha256(computerName)).append("|");
        
        // Minecraft-specific
        sb.append("MC_DIR:").append(CryptoUtil.sha256(getMcDir())).append("|");
        
        // Account type
        String accountType = detectAccountType();
        sb.append("ACCOUNT:").append(accountType);
        
        // Hash the entire fingerprint
        String rawFingerprint = sb.toString();
        String deviceId = CryptoUtil.sha256(rawFingerprint);
        
        LOGGER.info("Generated device fingerprint: {}", deviceId.substring(0, 12) + "...");
        return deviceId;
    }

    private static String detectAccountType() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getUser() != null) {
                return mc.getUser().getType().name();
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to detect account type: {}", e.getMessage());
        }
        return "UNKNOWN";
    }

    private static String getComputerName() {
        try {
            return System.getenv("COMPUTERNAME");
        } catch (Exception e) {
            return "unknown";
        }
    }

    private static String getMcDir() {
        try {
            return Minecraft.getInstance().gameDirectory.getAbsolutePath();
        } catch (Exception e) {
            return "unknown";
        }
    }

    public static String getOrCreateFingerprint() {
        if (cachedFingerprint != null) {
            return cachedFingerprint;
        }
        
        String currentFingerprint = generateFingerprint();
        String savedFingerprint = loadSavedFingerprint();
        
        if (savedFingerprint != null) {
            if (!savedFingerprint.equals(currentFingerprint)) {
                LOGGER.warn("Device fingerprint changed! Old: {}, New: {}", 
                    savedFingerprint.substring(0, Math.min(12, savedFingerprint.length())) + "...",
                    currentFingerprint.substring(0, 12) + "...");
                lastChangeDetected = System.currentTimeMillis();
            }
        }
        
        saveFingerprint(currentFingerprint);
        cachedFingerprint = currentFingerprint;
        return currentFingerprint;
    }

    public static boolean hasDeviceChanged() {
        if (cachedFingerprint == null) {
            getOrCreateFingerprint();
        }
        String saved = loadSavedFingerprint();
        if (saved == null) return false;
        return !saved.equals(cachedFingerprint);
    }

    public static long getLastChangeDetected() {
        return lastChangeDetected;
    }

    private static void saveFingerprint(String fingerprint) {
        try {
            Files.createDirectories(FINGERPRINT_FILE.getParent());
            String record = fingerprint + "|" + System.currentTimeMillis() + "|" + 
                (loadSavedCounter() + 1);
            String encrypted = CryptoUtil.encrypt(record);
            Files.write(FINGERPRINT_FILE, encrypted.getBytes(StandardCharsets.UTF_8));
            LOGGER.debug("Fingerprint saved to {}", FINGERPRINT_FILE);
        } catch (Exception e) {
            LOGGER.error("Failed to save fingerprint: {}", e.getMessage());
        }
    }

    private static String loadSavedFingerprint() {
        try {
            if (Files.exists(FINGERPRINT_FILE)) {
                String encrypted = new String(Files.readAllBytes(FINGERPRINT_FILE), StandardCharsets.UTF_8);
                String decrypted = CryptoUtil.decrypt(encrypted);
                String[] parts = decrypted.split("\\|");
                if (parts.length >= 1) {
                    return parts[0];
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to load saved fingerprint: {}", e.getMessage());
        }
        return null;
    }

    private static int loadSavedCounter() {
        try {
            if (Files.exists(FINGERPRINT_FILE)) {
                String encrypted = new String(Files.readAllBytes(FINGERPRINT_FILE), StandardCharsets.UTF_8);
                String decrypted = CryptoUtil.decrypt(encrypted);
                String[] parts = decrypted.split("\\|");
                if (parts.length >= 3) {
                    return Integer.parseInt(parts[2]);
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return 0;
    }

    public static void clearCache() {
        cachedFingerprint = null;
    }
}
