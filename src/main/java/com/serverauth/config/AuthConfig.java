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
package com.serverauth.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class AuthConfig {
    private static final Logger LOGGER = LogManager.getLogger("AuthConfig");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Path CONFIG_DIR = FMLPaths.CONFIGDIR.get().resolve("serverauth");
    private static final Path WHITELIST_FILE = CONFIG_DIR.resolve("whitelist.json");
    private static final Path BLACKLIST_FILE = CONFIG_DIR.resolve("blacklist.json");
    private static final Path PLAYER_ID_MAP_FILE = CONFIG_DIR.resolve("player_id_map.json");
    // Device fingerprint records: playerUUID -> DeviceRecord
    private static final Path DEVICE_FINGERPRINT_FILE = CONFIG_DIR.resolve("device_fingerprints.json");

    // Whitelist: allowed player IDs
    private static final List<Integer> WHITELIST = new CopyOnWriteArrayList<>();
    // Blacklist: banned player IDs
    private static final List<Integer> BLACKLIST = new CopyOnWriteArrayList<>();
    // Player UUID to ID mapping
    private static final Map<String, Integer> PLAYER_ID_MAP = new HashMap<>();
    // Device fingerprint storage: playerUUID -> DeviceRecord
    private static final Map<String, DeviceRecord> DEVICE_RECORDS = new HashMap<>();

    public static void load() {
        try {
            Files.createDirectories(CONFIG_DIR);
            loadListFromFile(WHITELIST_FILE, WHITELIST);
            loadListFromFile(BLACKLIST_FILE, BLACKLIST);
            loadMapFromFile(PLAYER_ID_MAP_FILE, PLAYER_ID_MAP);
            loadDeviceRecords();
            LOGGER.info("Config loaded! Whitelist: {}, Blacklist: {}, Registered: {}, Devices: {}",
                    WHITELIST.size(), BLACKLIST.size(), PLAYER_ID_MAP.size(), DEVICE_RECORDS.size());
        } catch (Exception e) {
            LOGGER.error("Failed to load config", e);
        }
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_DIR);
            saveListToFile(WHITELIST_FILE, WHITELIST);
            saveListToFile(BLACKLIST_FILE, BLACKLIST);
            saveMapToFile(PLAYER_ID_MAP_FILE, PLAYER_ID_MAP);
            saveDeviceRecords();
            LOGGER.info("Config saved");
        } catch (Exception e) {
            LOGGER.error("Failed to save config", e);
        }
    }

    // ========== Whitelist Operations ==========

    public static List<Integer> getWhitelist() {
        return Collections.unmodifiableList(WHITELIST);
    }

    public static boolean isInWhitelist(int playerId) {
        return WHITELIST.contains(playerId);
    }

    public static boolean addToWhitelist(int playerId) {
        if (!WHITELIST.contains(playerId)) {
            WHITELIST.add(playerId);
            BLACKLIST.remove((Integer) playerId);
            save();
            return true;
        }
        return false;
    }

    public static boolean removeFromWhitelist(int playerId) {
        boolean removed = WHITELIST.remove((Integer) playerId);
        if (removed) save();
        return removed;
    }

    // ========== Blacklist Operations ==========

    public static List<Integer> getBlacklist() {
        return Collections.unmodifiableList(BLACKLIST);
    }

    public static boolean isInBlacklist(int playerId) {
        return BLACKLIST.contains(playerId);
    }

    public static boolean addToBlacklist(int playerId) {
        if (!BLACKLIST.contains(playerId)) {
            BLACKLIST.add(playerId);
            WHITELIST.remove((Integer) playerId);
            save();
            return true;
        }
        return false;
    }

    public static boolean removeFromBlacklist(int playerId) {
        boolean removed = BLACKLIST.remove((Integer) playerId);
        if (removed) save();
        return removed;
    }

    // ========== Player ID Mapping ==========

    public static int getOrAssignPlayerId(String playerUUID) {
        if (PLAYER_ID_MAP.containsKey(playerUUID)) {
            return PLAYER_ID_MAP.get(playerUUID);
        }
        int newId = 1;
        Collection<Integer> allUsedIds = new HashSet<>(PLAYER_ID_MAP.values());
        while (allUsedIds.contains(newId)) {
            newId++;
        }
        PLAYER_ID_MAP.put(playerUUID, newId);
        save();
        return newId;
    }

    public static int getPlayerId(String playerUUID) {
        return PLAYER_ID_MAP.getOrDefault(playerUUID, -1);
    }

    public static String getPlayerUUIDById(int playerId) {
        for (Map.Entry<String, Integer> entry : PLAYER_ID_MAP.entrySet()) {
            if (entry.getValue() == playerId) {
                return entry.getKey();
            }
        }
        return null;
    }

    public static boolean setPlayerId(String playerUUID, int playerId) {
        String existingPlayer = getPlayerUUIDById(playerId);
        if (existingPlayer != null && !existingPlayer.equals(playerUUID)) {
            return false;
        }
        PLAYER_ID_MAP.put(playerUUID, playerId);
        save();
        return true;
    }

    // ========== Check Logic ==========

    public static CheckResult checkPlayerAllowed(int playerId) {
        if (isInBlacklist(playerId)) {
            return new CheckResult(false, "Your ID (#" + playerId + ") is blacklisted!");
        }
        if (!WHITELIST.isEmpty() && !isInWhitelist(playerId)) {
            return new CheckResult(false, "Your ID (#" + playerId + ") is not whitelisted!");
        }
        return new CheckResult(true, "Auth passed!");
    }

    public static class CheckResult {
        private final boolean allowed;
        private final String reason;

        public CheckResult(boolean allowed, String reason) {
            this.allowed = allowed;
            this.reason = reason;
        }

        public boolean isAllowed() { return allowed; }
        public String getReason() { return reason; }
    }

    // ========== Device Fingerprint Management ==========

    /**
     * Record a device fingerprint for a player
     */
    public static DeviceRecord recordDeviceFingerprint(String playerUUID, String fingerprint, 
                                                         String accountType, boolean deviceChanged) {
        DeviceRecord existing = DEVICE_RECORDS.get(playerUUID);
        long now = System.currentTimeMillis();
        
        if (existing != null) {
            existing.deviceFingerprints.add(fingerprint);
            existing.accountType = accountType;
            existing.lastSeen = now;
            if (deviceChanged) {
                existing.deviceChangeCount++;
                existing.firstSeenCurrent = now;
            }
        } else {
            existing = new DeviceRecord();
            existing.playerUUID = playerUUID;
            existing.deviceFingerprints = new ArrayList<>();
            existing.deviceFingerprints.add(fingerprint);
            existing.accountType = accountType;
            existing.firstSeen = now;
            existing.firstSeenCurrent = now;
            existing.lastSeen = now;
            existing.deviceChangeCount = 0;
        }
        DEVICE_RECORDS.put(playerUUID, existing);
        saveDeviceRecords();
        return existing;
    }

    /**
     * Get device record for a player
     */
    public static DeviceRecord getDeviceRecord(String playerUUID) {
        return DEVICE_RECORDS.get(playerUUID);
    }

    /**
     * Check if a player is premium (online) or offline
     */
    public static boolean isPlayerPremium(String playerUUID) {
        DeviceRecord record = DEVICE_RECORDS.get(playerUUID);
        if (record == null) return false;
        return "MSA".equals(record.accountType) || "MOJANG".equals(record.accountType);
    }

    /**
     * Get device change history for a player
     */
    public static List<String> getDeviceHistory(String playerUUID) {
        DeviceRecord record = DEVICE_RECORDS.get(playerUUID);
        return record != null ? record.deviceFingerprints : Collections.emptyList();
    }

    public static int getDeviceChangeCount(String playerUUID) {
        DeviceRecord record = DEVICE_RECORDS.get(playerUUID);
        return record != null ? record.deviceChangeCount : 0;
    }

    /**
     * Device record per player
     */
    public static class DeviceRecord {
        public String playerUUID;
        public List<String> deviceFingerprints;
        public String accountType;
        public long firstSeen;
        public long firstSeenCurrent;
        public long lastSeen;
        public int deviceChangeCount;
    }

    // ========== File IO Helpers ==========

    private static void loadListFromFile(Path file, List<Integer> list) {
        list.clear();
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                List<Integer> loaded = GSON.fromJson(reader, new TypeToken<List<Integer>>(){}.getType());
                if (loaded != null) {
                    list.addAll(loaded);
                }
            } catch (Exception e) {
                LOGGER.error("Failed to read: {}", file, e);
            }
        }
    }

    private static void saveListToFile(Path file, List<Integer> list) {
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(list, writer);
        } catch (Exception e) {
            LOGGER.error("Failed to write: {}", file, e);
        }
    }

    private static void loadMapFromFile(Path file, Map<String, Integer> map) {
        map.clear();
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                Map<String, Integer> loaded = GSON.fromJson(reader,
                        new TypeToken<Map<String, Integer>>(){}.getType());
                if (loaded != null) {
                    map.putAll(loaded);
                }
            } catch (Exception e) {
                LOGGER.error("Failed to read: {}", file, e);
            }
        }
    }

    private static void saveMapToFile(Path file, Map<String, Integer> map) {
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(map, writer);
        } catch (Exception e) {
            LOGGER.error("Failed to write: {}", file, e);
        }
    }

    private static void loadDeviceRecords() {
        DEVICE_RECORDS.clear();
        if (Files.exists(DEVICE_FINGERPRINT_FILE)) {
            try (Reader reader = Files.newBufferedReader(DEVICE_FINGERPRINT_FILE, StandardCharsets.UTF_8)) {
                Map<String, DeviceRecord> loaded = GSON.fromJson(reader,
                        new TypeToken<Map<String, DeviceRecord>>(){}.getType());
                if (loaded != null) {
                    DEVICE_RECORDS.putAll(loaded);
                }
            } catch (Exception e) {
                LOGGER.error("Failed to load device records: {}", e.getMessage());
            }
        }
    }

    private static void saveDeviceRecords() {
        try (Writer writer = Files.newBufferedWriter(DEVICE_FINGERPRINT_FILE, StandardCharsets.UTF_8)) {
            GSON.toJson(DEVICE_RECORDS, writer);
        } catch (Exception e) {
            LOGGER.error("Failed to save device records: {}", e.getMessage());
        }
    }
}
