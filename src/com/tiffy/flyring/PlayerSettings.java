package com.tiffy.flyring;

import java.io.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PlayerSettings - Per-player settings storage (CSV format)
 * Format: uuid,backpackEnabled
 */
public class PlayerSettings {

    private static final String CONFIG_DIR = "mods/tiffy-illegalrings";
    private static final String OLD_CONFIG_DIR = "mods/tiffy"; // Migration
    private static final String SETTINGS_FILE = "player_settings.csv";
    private static final Map<UUID, Boolean> backpackEnabled = new ConcurrentHashMap<>();
    private static File gameDirectory;

    public static void load(File gameDir) {
        gameDirectory = gameDir;

        // Migration: Move old settings to new location
        migrateOldSettings(gameDir);

        File configDir = new File(gameDir, CONFIG_DIR);
        File settingsFile = new File(configDir, SETTINGS_FILE);

        if (settingsFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(settingsFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;

                    String[] parts = line.split(",");
                    if (parts.length >= 2) {
                        try {
                            UUID uuid = UUID.fromString(parts[0].trim());
                            boolean enabled = Boolean.parseBoolean(parts[1].trim());
                            backpackEnabled.put(uuid, enabled);
                        } catch (Exception ignored) {}
                    }
                }
            } catch (IOException e) {
                System.err.println("[PlayerSettings] Failed to load: " + e.getMessage());
            }
        }
    }

    public static void save() {
        if (gameDirectory == null) return;

        File configDir = new File(gameDirectory, CONFIG_DIR);
        File settingsFile = new File(configDir, SETTINGS_FILE);

        try {
            if (!configDir.exists()) configDir.mkdirs();
            try (PrintWriter writer = new PrintWriter(new FileWriter(settingsFile))) {
                writer.println("# UUID,BackpackEnabled");
                for (Map.Entry<UUID, Boolean> entry : backpackEnabled.entrySet()) {
                    writer.println(entry.getKey().toString() + "," + entry.getValue());
                }
            }
        } catch (IOException e) {
            System.err.println("[PlayerSettings] Failed to save: " + e.getMessage());
        }
    }

    /**
     * Check if backpack is enabled for a player
     * Default: true (backpack counts as inventory)
     */
    public static boolean isBackpackEnabled(UUID uuid) {
        return backpackEnabled.getOrDefault(uuid, true);
    }

    /**
     * Toggle backpack setting for a player
     * @return new state
     */
    public static boolean toggleBackpack(UUID uuid) {
        boolean current = isBackpackEnabled(uuid);
        boolean newState = !current;
        backpackEnabled.put(uuid, newState);
        save();
        return newState;
    }

    /**
     * Migrate old settings from mods/tiffy to mods/tiffy-illegalrings
     */
    private static void migrateOldSettings(File gameDir) {
        File oldConfigDir = new File(gameDir, OLD_CONFIG_DIR);
        File oldSettingsFile = new File(oldConfigDir, SETTINGS_FILE);

        if (!oldSettingsFile.exists()) return;

        File newConfigDir = new File(gameDir, CONFIG_DIR);
        File newSettingsFile = new File(newConfigDir, SETTINGS_FILE);

        if (newSettingsFile.exists()) return;

        try {
            if (!newConfigDir.exists()) newConfigDir.mkdirs();

            java.nio.file.Files.copy(
                oldSettingsFile.toPath(),
                newSettingsFile.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING
            );

            System.out.println("[PlayerSettings] Migrated settings from " + OLD_CONFIG_DIR + " to " + CONFIG_DIR);
        } catch (IOException e) {
            System.err.println("[PlayerSettings] Migration failed: " + e.getMessage());
        }
    }
}
