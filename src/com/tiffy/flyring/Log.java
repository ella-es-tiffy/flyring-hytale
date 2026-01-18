package com.tiffy.flyring;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;

/**
 * Log - Helper class for conditional logging based on config.
 */
public class Log {

    public static void info(JavaPlugin plugin, String message) {
        if (ModConfig.getInstance() != null && ModConfig.getInstance().debugLogging) {
            plugin.getLogger().atInfo().log(message);
        }
    }

    public static void severe(JavaPlugin plugin, String message) {
        // Severe errors are always logged for stability monitoring
        plugin.getLogger().atSevere().log(message);
    }

    /**
     * Internal setup logs that should always be shown during mod initialization.
     */
    public static void setup(JavaPlugin plugin, String message) {
        plugin.getLogger().atInfo().log(message);
    }
}
