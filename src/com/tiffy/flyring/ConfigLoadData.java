package com.tiffy.flyring;

/**
 * Wrapper for config loading result with status and loaded config
 */
public class ConfigLoadData {
    public final ConfigLoadResult status;
    public final ModConfig.Config config;
    public final String rawJson;
    public final String errorMessage;
    public final int oldVersion; // For VERSION_MISMATCH tracking

    public ConfigLoadData(ConfigLoadResult status, ModConfig.Config config, String rawJson, String errorMessage,
            int oldVersion) {
        this.status = status;
        this.config = config;
        this.rawJson = rawJson;
        this.errorMessage = errorMessage;
        this.oldVersion = oldVersion;
    }
}
