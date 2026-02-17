package com.tiffy.flyring;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * FreePetStorage - Persistent per-player storage for the free trial pet timer.
 * Timer only counts down while the pet NPC is actively spawned.
 * Each Loot_Fox_Free item has a unique serial burned into its metadata.
 * Storage keyed by player UUID, serial identifies the specific item.
 */
public class FreePetStorage {

    private static final Logger LOG = Logger.getLogger("FreePet");
    private static final Path DATA_DIR = Paths.get("data", "freepet");
    static final long DURATION_MS = 3L * 24 * 60 * 60 * 1000; // 3 days
    public static final String META_SERIAL = "serial";

    public FreePetStorage() {
        try {
            Files.createDirectories(DATA_DIR);
        } catch (IOException e) {
            LOG.severe("[ERR-1020] FreePetStorage createDirectories: " + e.getMessage());
        }
    }

    public FreePetData load(UUID uuid) {
        Path file = DATA_DIR.resolve(uuid.toString() + ".properties");
        if (!Files.exists(file)) return new FreePetData();

        Properties props = new Properties();
        try (BufferedReader r = Files.newBufferedReader(file)) {
            props.load(r);
        } catch (IOException e) {
            return new FreePetData();
        }

        FreePetData data = new FreePetData();
        data.received = Boolean.parseBoolean(props.getProperty("received", "false"));
        data.remainingMs = parseLong(props.getProperty("remainingMs", "0"));
        data.serial = props.getProperty("serial", null);
        // Migration: convert old expiresAt/grantedAt to remainingMs
        if (data.remainingMs == 0 && props.containsKey("expiresAt")) {
            long expiresAt = parseLong(props.getProperty("expiresAt", "0"));
            if (expiresAt > 0) {
                long remaining = expiresAt - System.currentTimeMillis();
                data.remainingMs = Math.max(0, remaining);
            }
        }
        return data;
    }

    public void save(UUID uuid, FreePetData data) {
        Path file = DATA_DIR.resolve(uuid.toString() + ".properties");
        Properties props = new Properties();
        props.setProperty("received", String.valueOf(data.received));
        props.setProperty("remainingMs", String.valueOf(data.remainingMs));
        if (data.serial != null) {
            props.setProperty("serial", data.serial);
        }
        try (BufferedWriter w = Files.newBufferedWriter(file)) {
            props.store(w, "FreePet timer data");
        } catch (IOException e) {
            LOG.severe("[ERR-1021] FreePetStorage save: " + e.getMessage());
        }
    }

    /**
     * Reset a player's free pet data (allows re-granting).
     */
    public void reset(UUID uuid) {
        Path file = DATA_DIR.resolve(uuid.toString() + ".properties");
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            LOG.severe("[ERR-1022] FreePetStorage reset: " + e.getMessage());
        }
    }

    /**
     * Reset ALL players' free pet data. Returns number of cleared entries.
     */
    public int resetAll() {
        int count = 0;
        try (java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(DATA_DIR, "*.properties")) {
            for (Path file : stream) {
                Files.deleteIfExists(file);
                count++;
            }
        } catch (IOException e) {
            LOG.severe("[ERR-1023] FreePetStorage resetAll: " + e.getMessage());
        }
        return count;
    }

    private static long parseLong(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static class FreePetData {
        public boolean received;
        public long remainingMs;
        public String serial; // Unique ID burned into the item's metadata

        public boolean isExpired() {
            return received && remainingMs <= 0;
        }
    }
}
