package com.tiffy.flyring;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

/**
 * ModConfig - Manages mod configuration
 * - Enable/disable recipes
 * - Configure gameplay values (lifesteal percentage, etc.)
 */
public class ModConfig {

    public static class RingEnabled {
        public boolean flyRing = true;
        public boolean fireRing = true;
        public boolean waterRing = true;
        public boolean healRing = true;
        public boolean peacefulRing = true;
    }

    public static class GameplayValues {
        public double lifestealPercent = 0.35; // 35% lifesteal for Heal Ring
    }

    public static class Config {
        public RingEnabled enabled = new RingEnabled();
        public GameplayValues gameplay = new GameplayValues();
        public boolean debugLogging = false; // Toggle for mod-specific debug logs
    }

    private static final String CONFIG_DIR = "mods/tiffy";
    private static final String CONFIG_FILE = "config.json";
    private static Config instance;

    public static Config load(File gameDirectory) {
        if (instance != null) {
            return instance;
        }

        File configDir = new File(gameDirectory, CONFIG_DIR);
        File configFile = new File(configDir, CONFIG_FILE);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        if (configFile.exists()) {
            try (FileReader reader = new FileReader(configFile)) {
                instance = gson.fromJson(reader, Config.class);

                // --- AUTO-UPDATE LOGIC ---
                // Save immediately after loading. GSON keeps existing values
                // but adds missing fields with their default values.
                save(gameDirectory);

                if (instance != null && instance.debugLogging) {
                    System.out.println("[ModConfig] Loaded and updated config from: " + configFile.getAbsolutePath());
                }
                return instance;
            } catch (IOException e) {
                System.err.println("[ModConfig] Failed to load config: " + e.getMessage());
            }
        }

        // Create default config
        instance = new Config();
        save(gameDirectory);

        return instance;
    }

    /**
     * Saves the current instance to the config file.
     */
    public static void save(File gameDirectory) {
        if (instance == null)
            return;

        File configDir = new File(gameDirectory, CONFIG_DIR);
        File configFile = new File(configDir, CONFIG_FILE);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        try {
            if (!configDir.exists())
                configDir.mkdirs();
            try (FileWriter writer = new FileWriter(configFile)) {
                gson.toJson(instance, writer);
            }
        } catch (IOException e) {
            System.err.println("[ModConfig] Failed to save config: " + e.getMessage());
        }
    }

    public static Config getInstance() {
        return instance;
    }
}
