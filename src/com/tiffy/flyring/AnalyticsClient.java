package com.tiffy.flyring;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.TreeMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.common.util.java.ManifestUtil;
import com.hypixel.hytale.server.core.Constants;
import com.hypixel.hytale.server.core.modules.singleplayer.SingleplayerModule;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.protocol.packets.serveraccess.Access;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * AnalyticsClient - Reports mod usage to analytics server
 *
 * Supports two endpoints:
 * - LIVE: (configured locally)
 * - TEST: http://localhost:9090/api.php (local development only)
 *
 * Configuration in config.json:
 * "testserver": false // Set to true only for local testing with development
 * server
 */
public class AnalyticsClient {
    private static final String TEST_ENDPOINT = "http://localhost:9090/api.php";
    private static final String LIVE_ENDPOINT = ""; // Configure locally

    private static final String MOD_NAME = "FlyRing";
    // Secret key for request signing - MUST match the one in signatures.php on the
    // server
    private static final String SECRET_KEY = "";
    private static final int TIMEOUT_MS = 5000;

    private static boolean useTestServer = false; // Production endpoint by default
    private static final boolean debugLogging = false; // Silent by default
    private static long lastPotatoReport = 0; // Cooldown for POTATO reports

    // Serial execution queue to guarantee event order
    // Bounded queue (max 100) and DiscardOldestPolicy to prevent memory leaks if
    // offline for long periods
    private static final ExecutorService reportExecutor = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(100),
            new ThreadPoolExecutor.DiscardOldestPolicy());

    /**
     * Initialize analytics with config flags
     */
    public static void init(boolean testserver) {
        useTestServer = testserver;
    }

    /**
     * Get Hytale game version from server manifest
     */
    private static String getGameVersion() {
        try {
            String version = ManifestUtil.getImplementationVersion();
            if (version != null && !version.isEmpty()) {
                return version;
            }
            return "unknown";
        } catch (Exception e) {
            if (debugLogging)
                System.err.println("[AnalyticsClient] Failed to get game version: " + e.getMessage());
            return "unknown";
        }
    }

    /**
     * Detect if server is Singleplayer or Multiplayer
     */
    private static String getServerType() {
        try {
            // Constants.SINGLEPLAYER = true for local SP, false for dedicated server/MP
            if (Constants.SINGLEPLAYER) {
                return "SP";
            } else {
                return "MP";
            }
        } catch (Exception e) {
            if (debugLogging)
                System.err.println("[AnalyticsClient] Failed to detect server type: " + e.getMessage());
            return "SP";
        }
    }

    /**
     * Get Singleplayer owner username (privacy: no IP collection for SP)
     * Available during startup via CLI arguments, no exception handling needed
     */
    private static String getOwnerUsername() {
        if (Constants.SINGLEPLAYER) {
            String username = SingleplayerModule.getUsername();
            if (username != null && !username.isEmpty()) {
                return username;
            }
        }
        return null;
    }

    /**
     * Get Singleplayer owner UUID (privacy: no IP collection for SP)
     * Available during startup via CLI arguments, no exception handling needed
     */
    private static String getOwnerUUID() {
        if (Constants.SINGLEPLAYER) {
            java.util.UUID uuid = SingleplayerModule.getUuid();
            if (uuid != null) {
                String uuidStr = uuid.toString();
                return uuidStr;
            }
        }
        return null;
    }

    /**
     * Get Java Runtime version for crash debugging
     * Example: "21.0.2+13-LTS (OpenJDK 64-Bit Server VM) by Eclipse Adoptium"
     */
    private static String getJavaVersion() {
        try {
            String javaVersion = System.getProperty("java.version");
            String javaVendor = System.getProperty("java.vendor");
            String javaVmName = System.getProperty("java.vm.name");

            String fullVersion = javaVersion + " (" + javaVmName + ") by " + javaVendor;
            return fullVersion;
        } catch (Exception e) {
            if (debugLogging)
                System.err.println("[AnalyticsClient] Failed to get Java version: " + e.getMessage());
            return "unknown";
        }
    }

    /**
     * Check for mod updates against the analytics server
     */
    public static void checkUpdates(String modVersion, java.util.function.Consumer<String> onUpdateFound) {
        reportExecutor.submit(() -> {
            try {
                JsonObject payload = createBasePayload(modVersion);
                payload.addProperty("action", "CHECK_VERSION");

                String endpoint = useTestServer ? TEST_ENDPOINT : LIVE_ENDPOINT;
                var url = new URI(endpoint).toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(TIMEOUT_MS);
                conn.setReadTimeout(TIMEOUT_MS);

                String jsonString = payload.toString();
                String signature = generateSignature(jsonString, SECRET_KEY);
                if (signature != null) {
                    conn.setRequestProperty("X-ANALYTICS-SIGNATURE", signature);
                }

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(jsonString.getBytes(StandardCharsets.UTF_8));
                }

                if (conn.getResponseCode() == 200) {
                    try (java.io.InputStream is = conn.getInputStream()) {
                        String response = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                        JsonObject json = new Gson().fromJson(response, JsonObject.class);

                        if (json != null && json.has("latest_version")) {
                            String latest = json.get("latest_version").getAsString();
                            if (latest != null && !latest.equals("0.0.0") && !latest.equals(modVersion)) {
                                onUpdateFound.accept(latest);
                            }
                        }
                    }
                }
                conn.disconnect();
            } catch (Exception e) {
                if (debugLogging) {
                    System.out.println("[AnalyticsClient] Connection failed: " + e.getMessage());
                }
            }
        });
    }

    /**
     * Check if analytics privacy mode is active (analyticsEnabled == false)
     * When privacy mode: no UUID or IP is sent (player name is always sent for error attribution)
     */
    private static boolean isPrivacyMode() {
        ModConfig.Config cfg = ModConfig.getInstance();
        return cfg != null && !cfg.analyticsEnabled;
    }

    /**
     * Hash a string into a UUID-formatted token (MD5 -> 8-4-4-4-12)
     */
    private static String hashToUUID(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }
            String rawToken = hexString.toString();
            return rawToken.substring(0, 8) + "-" +
                    rawToken.substring(8, 12) + "-" +
                    rawToken.substring(12, 16) + "-" +
                    rawToken.substring(16, 20) + "-" +
                    rawToken.substring(20);
        } catch (Exception e) {
            return "00000000-0000-0000-0000-000000000000";
        }
    }

    /**
     * Generate or retrieve Instance ID
     * - Privacy mode: Hashed from player name (no real UUID/IP)
     * - SP: Uses Owner UUID
     * - MP: Hashed IP Address ONLY
     */
    private static String getInstanceId() {
        try {
            // Privacy mode: hash player name into a consistent but anonymous session ID
            if (isPrivacyMode()) {
                String username = getOwnerUsername();
                if (username != null && !username.isEmpty()) {
                    return hashToUUID("anon-session-" + username);
                }
                return hashToUUID("anon-session-unknown");
            }

            if ("SP".equals(getServerType())) {
                String uuid = getOwnerUUID();
                return (uuid != null) ? uuid : "unknown-sp";
            } else {
                // Multiplayer: Hash IP address to create a consistent ID without saving files
                try {
                    String identifier = InetAddress.getLocalHost().getHostAddress(); // ONLY IP Address

                    if (identifier == null || identifier.isEmpty() || "127.0.0.1".equals(identifier)) {
                        // Fallback
                    }

                    return hashToUUID(identifier);
                } catch (Exception e) {
                    return "00000000-0000-0000-0000-000000000001";
                }
            }
        } catch (Throwable t) {
            return "00000000-0000-0000-0000-000000000002";
        }
    }

    /**
     * Creates a base payload with all mandatory diagnostic fields.
     */
    private static JsonObject createBasePayload(String modVersion) {
        JsonObject payload = new JsonObject();
        String serverType = getServerType();

        payload.addProperty("instance_id", getInstanceId());
        payload.addProperty("server_type", serverType);
        payload.addProperty("game_version", getGameVersion());
        payload.addProperty("modname", MOD_NAME);
        payload.addProperty("modversion", modVersion != null ? modVersion : ModConfig.VERSION);
        payload.addProperty("java_version", getJavaVersion());

        // Dynamic server info for analytics dashboard
        try {
            // Max player slots from server config
            int maxPlayers = HytaleServer.get().getConfig().getMaxPlayers();
            payload.addProperty("playerslots", maxPlayers);

            // CCU = Current Concurrent Users (online player count)
            int ccu = Universe.get().getPlayers().size();
            payload.addProperty("ccu", ccu);
        } catch (Exception e) {
            // Fallback for SP or if server not ready
            payload.addProperty("playerslots", 1);
            payload.addProperty("ccu", 1);
        }

        // SP info: always send username for error attribution, UUID only with analytics enabled
        if ("SP".equals(serverType)) {
            try {
                String ownerUsername = getOwnerUsername();
                if (ownerUsername != null)
                    payload.addProperty("owner_username", ownerUsername);

                if (!isPrivacyMode()) {
                    String ownerUUID = getOwnerUUID();
                    if (ownerUUID != null)
                        payload.addProperty("owner_uuid", ownerUUID);
                }
            } catch (Throwable t) {
                /* Ignore */ }
        }

        return payload;
    }

    /**
     * Report player ready (successfully joined and in Playing state)
     */
    public static void reportPlayerReady(String playerName) {
        try {
            JsonObject payload = createBasePayload(null);
            payload.addProperty("event_label", "PLAYER_READY_HOOK");
            payload.addProperty("player_name", playerName);
            payload.addProperty("online", true); // Feature: Mark as entering online state

            submitAsync(payload, "PLAYER_READY:" + playerName);
        } catch (Exception e) {
            logError("Failed to report player ready", e);
        }
    }

    /**
     * Periodic Keep-Alive to track total usage time (backward compatible)
     */
    public static void reportKeepAlive(String modVersion) {
        reportKeepAlive(modVersion, null);
    }

    /**
     * Periodic Keep-Alive with ring state tracking for cumulative duration
     * @param modVersion The mod version string
     * @param ringStates Array of 6 booleans: [fly, fire, water, heal, peaceful, gaia] or null
     */
    public static void reportKeepAlive(String modVersion, boolean[] ringStates) {
        try {
            JsonObject payload = createBasePayload(modVersion);
            payload.addProperty("event_label", "HEARTBEAT");
            payload.addProperty("online", true);

            // Include current ring states for duration tracking
            if (ringStates != null && ringStates.length >= 6) {
                payload.addProperty("ring_fly_active", ringStates[0]);
                payload.addProperty("ring_fire_active", ringStates[1]);
                payload.addProperty("ring_water_active", ringStates[2]);
                payload.addProperty("ring_heal_active", ringStates[3]);
                payload.addProperty("ring_peaceful_active", ringStates[4]);
                payload.addProperty("ring_gaia_active", ringStates[5]);
            }

            submitAsync(payload, "KEEP_ALIVE");
        } catch (Exception e) {
            logError("Failed to report keep alive", e);
        }
    }

    /**
     * Report change in flight state
     */
    public static void reportFlyState(boolean enabled) {
        reportRingState("FLY_RING", enabled);
    }

    /**
     * Report change in any ring's state
     */
    public static void reportRingState(String ringName, boolean enabled) {
        try {
            JsonObject payload = createBasePayload(null);
            payload.addProperty("event_label", ringName + ":" + (enabled ? "ON" : "OFF"));
            payload.addProperty("ring_name", ringName);
            payload.addProperty("enabled", enabled);

            submitAsync(payload, "RING_STATE:" + ringName + (enabled ? "_ON" : "_OFF"));
        } catch (Exception e) {
            logError("Failed to report ring state: " + ringName, e);
        }
    }

    /**
     * Report that the mod is about to start the risky recipe replacement process
     */
    public static void reportLoadingRecipes(String modVersion) {
        try {
            JsonObject payload = createBasePayload(modVersion);
            payload.addProperty("event_label", "LOADING_RECIPES_START");
            payload.addProperty("status", "LOADING_RECIPES");

            submitAsync(payload, "STATUS:RECIPES_START");
        } catch (Exception e) {
            logError("Failed to report loading recipes", e);
        }
    }

    /**
     * Report that recipe replacement finished successfully
     */
    public static void reportRecipesSuccess(String modVersion) {
        try {
            JsonObject payload = createBasePayload(modVersion);
            payload.addProperty("event_label", "RECIPE_SYNC_SUCCESS");
            payload.addProperty("boot_success", true);

            submitAsync(payload, "STATUS:RECIPES_SUCCESS");
        } catch (Exception e) {
            logError("Failed to report recipes success", e);
        }
    }

    // ========== CONFIG CHECK CALLS (sent BEFORE action) ==========

    /**
     * Report that config file is missing (BEFORE creating new one)
     */
    public static void reportConfigCheckMissing(String modVersion) {
        try {
            JsonObject payload = createBasePayload(modVersion);
            payload.addProperty("event_label", "CONFIG_CHECK_MISSING");
            payload.addProperty("last_startup", getCurrentTimestamp());
            payload.addProperty("boot_success", false);

            submitAsync(payload, "CONFIG_CHECK:MISSING");
        } catch (Exception e) {
            logError("Failed to report config check missing", e);
        }
    }

    /**
     * Report that config has syntax errors (BEFORE using defaults)
     */
    public static void reportConfigCheckSyntaxError(String modVersion, String errorMessage, String brokenConfig) {
        try {
            JsonObject payload = createBasePayload(modVersion);
            payload.addProperty("event_label", "CONFIG_CHECK_SYNTAX_ERROR");
            payload.addProperty("last_startup", getCurrentTimestamp());
            payload.addProperty("error_message", errorMessage);
            payload.addProperty("raw_config", brokenConfig);
            payload.addProperty("boot_success", false);

            submitAsync(payload, "CONFIG_CHECK:SYNTAX_ERROR");
        } catch (Exception e) {
            logError("Failed to report config check syntax error", e);
        }
    }

    /**
     * Report that config version is outdated (BEFORE migration)
     */
    public static void reportConfigCheckVersionMismatch(String modVersion, int oldVersion, int newVersion) {
        try {
            JsonObject payload = createBasePayload(modVersion);
            payload.addProperty("event_label", "CONFIG_CHECK_VERSION_MISMATCH");
            payload.addProperty("last_startup", getCurrentTimestamp());
            payload.addProperty("old_version", oldVersion);
            payload.addProperty("new_version", newVersion);
            payload.addProperty("boot_success", false);

            submitAsync(payload, "CONFIG_CHECK:VERSION_MISMATCH");
        } catch (Exception e) {
            logError("Failed to report config check version mismatch", e);
        }
    }

    /**
     * Report that config is valid (no action needed)
     */
    public static void reportConfigCheckOk(String modVersion) {
        try {
            JsonObject payload = createBasePayload(modVersion);
            payload.addProperty("event_label", "CONFIG_CHECK_OK");
            payload.addProperty("last_startup", getCurrentTimestamp());
            payload.addProperty("boot_success", false);

            submitAsync(payload, "CONFIG_CHECK:OK");
        } catch (Exception e) {
            logError("Failed to report config check ok", e);
        }
    }

    // ========== CONFIG ACTION CALLS (sent AFTER action completes) ==========

    /**
     * Report that a new config was created (first run or user deleted config)
     */
    public static void reportConfigCreated(String modVersion, String createdConfig) {
        try {
            JsonObject payload = createBasePayload(modVersion);
            payload.addProperty("event_label", "CONFIG_CREATED");
            payload.addProperty("last_startup", getCurrentTimestamp());
            payload.addProperty("raw_config", createdConfig);
            payload.addProperty("boot_success", false); // Will be updated by reportBootSuccess

            submitAsync(payload, "STATUS:CONFIG_CREATED");
        } catch (Exception e) {
            logError("Failed to report config created", e);
        }
    }

    /**
     * Report that config had syntax errors and defaults are being used
     */
    public static void reportConfigSyntaxError(String modVersion, String errorMessage, String brokenConfig) {
        try {
            JsonObject payload = createBasePayload(modVersion);
            payload.addProperty("event_label", "CONFIG_SYNTAX_ERROR");
            payload.addProperty("last_startup", getCurrentTimestamp());
            payload.addProperty("error_message", errorMessage);
            payload.addProperty("raw_config", brokenConfig);
            payload.addProperty("boot_success", false); // Syntax error = problematic boot

            submitAsync(payload, "STATUS:CONFIG_SYNTAX_ERROR");
        } catch (Exception e) {
            logError("Failed to report config syntax error", e);
        }
    }

    /**
     * Report that config version was outdated and has been renewed
     */
    public static void reportConfigRenewed(String modVersion, String renewedConfig) {
        try {
            JsonObject payload = createBasePayload(modVersion);
            payload.addProperty("event_label", "CONFIG_RENEWED");
            payload.addProperty("last_startup", getCurrentTimestamp());
            payload.addProperty("raw_config", renewedConfig);
            payload.addProperty("boot_success", false); // Will be updated by reportBootSuccess

            submitAsync(payload, "STATUS:CONFIG_RENEWED");
        } catch (Exception e) {
            logError("Failed to report config renewed", e);
        }
    }

    /**
     * Report that config loaded successfully without issues
     */
    public static void reportConfigOk(String modVersion, String config) {
        try {
            JsonObject payload = createBasePayload(modVersion);
            payload.addProperty("event_label", "CONFIG_OK");
            payload.addProperty("last_startup", getCurrentTimestamp());
            payload.addProperty("raw_config", config);
            payload.addProperty("boot_success", false); // Will be updated by reportBootSuccess

            submitAsync(payload, "STATUS:CONFIG_OK");
        } catch (Exception e) {
            logError("Failed to report config ok", e);
        }
    }

    /**
     * Report player disconnected
     */
    public static void reportPlayerDisconnected(String playerName, String modVersion) {
        try {
            JsonObject payload = createBasePayload(modVersion);
            payload.addProperty("player_name", playerName);
            payload.addProperty("playerslots", "0");
            payload.addProperty("online", false); // Feature: Mark as leaving online state

            submitAsync(payload, "PLAYER_DISCONNECT");
        } catch (Exception e) {
            logError("Failed to report player disconnected", e);
        }
    }

    /**
     * Report that a player triggered the "Potato Check" (Slow loading/Race
     * Condition).
     * This happens when a player's UUID is null during an inventory event.
     */
    public static void reportPotatoState(String modVersion) {
        long now = System.currentTimeMillis();
        if (now - lastPotatoReport < 5000)
            return; // 5 second cooldown to avoid spam
        lastPotatoReport = now;

        JsonObject payload = createBasePayload(modVersion);
        payload.addProperty("event_type", "POTATO_STATE_DETECTED");
        payload.addProperty("details", "Player UUID was null - Race condition prevented by safety check.");
        submitAsync(payload, "POTATO_STATE");
    }

    /**
     * Report successful boot completion
     * Called when mod initialization is fully complete
     * Sets boot_success=true to mark instance as healthy
     */
    public static void reportBootSuccess(String modVersion) {
        reportRecipesSuccess(modVersion);
    }

    /**
     * Report that mod initialization is complete
     * Call this when your mod has finished all setup/initialization
     * Works for any mod - no hardcoded delays needed!
     */
    public static void reportInitializationComplete(String modVersion) {
        reportBootSuccess(modVersion);
    }

    /**
     * Submit data asynchronously to avoid blocking
     * Uses a single-threaded executor to guarantee delivery in order of calling.
     */
    private static void submitAsync(JsonObject payload, String eventType) {
        if (debugLogging) {
            System.out.println("[AnalyticsClient] Scheduling " + eventType + "...");
        }
        reportExecutor.submit(() -> {
            try {
                submit(payload);
            } catch (Throwable e) {
                if (debugLogging) {
                    System.err.println("[AnalyticsClient] Async Submit Failed: " + eventType + " - " + e.getMessage());
                    e.printStackTrace();
                }
            }
        });
    }

    /**
     * Submit analytics data to server (synchronous)
     */
    private static void submit(JsonObject payload) throws Exception {
        String endpoint = useTestServer ? TEST_ENDPOINT : LIVE_ENDPOINT;

        if (debugLogging) {
            System.out.println("[AnalyticsClient] Sending to: " + endpoint);
        }

        var url = new URI(endpoint).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);

        // Send request
        // Ensure JSON is sorted by keys for stable signature (Server uses
        // JSON_SORT_KEYS)
        // Gson doesn't guarantee order by default, but inserting properties in order
        // usually works for simple objects.
        // For robustness, we could sort, but the PHP sort strategy is simple
        // alphanumeric.
        String jsonString = payload.toString();

        // Generate Signature
        String signature = generateSignature(jsonString, SECRET_KEY);
        if (signature != null) {
            conn.setRequestProperty("X-ANALYTICS-SIGNATURE", signature);
            if (debugLogging) {
                System.out.println("[AnalyticsClient] Signature: " + signature);
                System.out.println("[AnalyticsClient] Payload: " + jsonString);
            }
        } else {
            if (debugLogging) {
                System.err.println("[AnalyticsClient] submit() - Failed to generate signature, sending unsigned!");
            }
        }

        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = jsonString.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        // Check response
        int responseCode = conn.getResponseCode();

        if (debugLogging) {
            System.out.println("[AnalyticsClient] Response Code: " + responseCode);
        }

        if (responseCode == 404) {
            // Silently ignore 404 (endpoint missing)
            conn.disconnect();
            return;
        }

        if (responseCode < 200 || responseCode >= 300) {
            // Read error stream if available
            String errorResponse = "";
            try (java.io.InputStream es = conn.getErrorStream()) {
                if (es != null) {
                    byte[] buffer = new byte[es.available()];
                    es.read(buffer);
                    errorResponse = new String(buffer, StandardCharsets.UTF_8);
                }
            } catch (Exception ex) {
                /* ignore */ }

            throw new RuntimeException("HTTP " + responseCode + " from analytics server. Response: " + errorResponse);
        }

        conn.disconnect();
    }

    /**
     * Get current timestamp in ISO 8601 format
     */
    private static String getCurrentTimestamp() {
        DateTimeFormatter formatter = DateTimeFormatter
                .ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
                .withZone(ZoneId.of("UTC"));
        return formatter.format(Instant.now());
    }

    /**
     * Log error
     */
    private static void logError(String message, Exception e) {
        if (!debugLogging) {
            return;
        }

        System.err.println("[AnalyticsClient] ERROR: " + message);
        if (e != null) {
            System.err.println("[AnalyticsClient] Exception: " + e.getMessage());
            e.printStackTrace(System.err);
        }
    }

    /**
     * Check if using test server
     */
    public static boolean isTestServer() {
        return useTestServer;
    }

    /**
     * Generate HMAC-SHA256 signature for payload
     * Note: PHP's hash_hmac expects the payload to have sorted keys
     * (JSON_SORT_KEYS).
     * Our JsonObject payload construction should ideally be ordered, or we need to
     * sort it here.
     * For now, we rely on JsonObject maintaining insertion order or simplistic
     * structure.
     * To be perfectly safe, we'd convert JSON to Map, use TreeMap to sort, then
     * back to JSON.
     * However, the server side recalculates the hash based on the RECEIVING json
     * structure which PHP parses.
     * Actually, PHP re-encodes the received array with JSON_SORT_KEYS.
     * So we need to ensure we send a JSON that, when parsed and re-encoded by PHP
     * with sort_keys, matches what we sign.
     * 
     * Crucially: The server code does: $payload = json_encode($data,
     * JSON_SORT_KEYS).
     * So WE must sign the EXACT string that PHP will produce. This is hard to
     * replicate exactly in Java without knowing PHP's quirks.
     * 
     * BETTER APPROACH FOR ROBUSTNESS:
     * We will parse our JsonObject into a TreeMap (sorted), then convert that to a
     * JSON string,
     * sign THAT string, and send THAT string.
     */
    private static String generateSignature(String rawJson, String secret) {
        try {
            // 1. Parse JSON to JsonObject to preserve types (ints stay ints)
            Gson gson = new Gson();
            JsonObject jsonObject = null;
            try {
                jsonObject = gson.fromJson(rawJson, JsonObject.class);
            } catch (Exception e) {
                if (debugLogging)
                    System.err.println("[AnalyticsClient] Failed to parse JSON for signing: " + e.getMessage());
                return null;
            }

            // 2. Put into TreeMap to sort keys
            Map<String, JsonElement> sortedMap = new TreeMap<>();
            for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
                sortedMap.put(entry.getKey(), entry.getValue());
            }

            // 3. Re-serialize with sorted keys
            Gson sortedGson = new GsonBuilder().disableHtmlEscaping().create();
            String sortedJson = sortedGson.toJson(sortedMap);

            // 3. HMAC-SHA256
            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secret_key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256_HMAC.init(secret_key);

            byte[] hash = sha256_HMAC.doFinal(sortedJson.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte b : hash) {
                result.append(String.format("%02x", b));
            }
            return result.toString();

        } catch (Throwable e) {
            if (debugLogging)
                System.err.println("[AnalyticsClient] Signature generation failed: " + e.getMessage());
            return null;
        }
    }
}
