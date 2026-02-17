package com.tiffy.flyring;

/**
 * Result status from config loading operation
 */
public enum ConfigLoadResult {
    /** Config file did not exist - created new default config */
    MISSING,

    /** Config file had JSON syntax errors - using defaults */
    SYNTAX_ERROR,

    /** Config version mismatch - migrated and renewed */
    VERSION_MISMATCH,

    /** Config loaded successfully without issues */
    OK
}
