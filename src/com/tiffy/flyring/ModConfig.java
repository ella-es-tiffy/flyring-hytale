package com.tiffy.flyring;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
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
        public double lifestealPercent = 0.05; // 5% lifesteal (Insane Mode)
    }

    public static class Config {
        public RingEnabled enabled = new RingEnabled();
        public RingEnabled craftable = new RingEnabled();
        public GameplayValues gameplay = new GameplayValues();
        public boolean debugLogging = false; // Toggle for mod-specific debug logs
        public List<RecipeOverride> recipeOverrides = new ArrayList<>();
    }

    public static class RecipeOverride {
        public String targetItemId;
        public List<Ingredient> ingredients = new ArrayList<>();
    }

    public static class Ingredient {
        public String id;
        public int amount;

        public Ingredient() {
        }

        public Ingredient(String id, int amount) {
            this.id = id;
            this.amount = amount;
        }
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

                // Initialize default overrides if empty
                if (instance.recipeOverrides.isEmpty()) {
                    addInsaneDefaults();
                    save(gameDirectory);
                }

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
        addInsaneDefaults();
        save(gameDirectory);

        return instance;
    }

    public static void addInsaneDefaults() {
        if (instance == null || !instance.recipeOverrides.isEmpty())
            return;

        // Fly Ring
        instance.recipeOverrides.add(createOverride("Jewelry_Fly_Ring",
                new Ingredient("Ingredient_Bar_Iron", 300),
                new Ingredient("Ingredient_Life_Essence", 100),
                new Ingredient("Ingredient_Bar_Thorium", 10),
                new Ingredient("Ingredient_Feathers_Light", 1)));

        // Fire Ring
        instance.recipeOverrides.add(createOverride("Jewelry_Fire_Ring",
                new Ingredient("Ingredient_Bar_Iron", 100),
                new Ingredient("Ingredient_Fire_Essence", 100),
                new Ingredient("Ingredient_Bar_Gold", 10),
                new Ingredient("Ingredient_Bar_Adamantite", 1)));

        // Water Ring
        instance.recipeOverrides.add(createOverride("Jewelry_Water_Ring",
                new Ingredient("Ingredient_Bar_Iron", 100),
                new Ingredient("Ingredient_Life_Essence", 25),
                new Ingredient("Ingredient_Stick", 50),
                new Ingredient("Ingredient_Crystal_Blue", 1)));

        // Heal Ring
        instance.recipeOverrides.add(createOverride("Jewelry_Heal_Ring",
                new Ingredient("Ingredient_Bar_Iron", 300),
                new Ingredient("Ingredient_Life_Essence", 100),
                new Ingredient("Ingredient_Bar_Thorium", 30),
                new Ingredient("Ingredient_Bar_Cobalt", 30)));

        // Peaceful Ring
        instance.recipeOverrides.add(createOverride("Jewelry_Peacefull_Ring",
                new Ingredient("Rock_Stone_Cobble", 200),
                new Ingredient("Ingredient_Life_Essence", 10),
                new Ingredient("Ingredient_Fibre", 100),
                new Ingredient("Plant_Fruit_Apple", 10)));
    }

    private static RecipeOverride createOverride(String id, Ingredient... ingredients) {
        RecipeOverride ro = new RecipeOverride();
        ro.targetItemId = id;
        for (Ingredient ing : ingredients) {
            ro.ingredients.add(ing);
        }
        return ro;
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
