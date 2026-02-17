package com.tiffy.flyring;

import java.io.*;
import java.nio.file.Files;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * PedestalRegistry - Persists pedestal ownership to CSV file.
 *
 * Tracks which player placed which pedestal at what coordinates.
 * Data is stored in: mods/tiffy-illegalrings/pedestals.csv
 *
 * CSV Format: x,y,z,ownerUuid,ownerName,item,placed
 */
public class PedestalRegistry {

    private static final String DATA_DIR = "mods/tiffy-illegalrings";
    private static final String FILE_NAME = "pedestals.csv";
    private static final String CSV_HEADER = "ownerName,ownerUuid,x,y,z,item,placed,verified";

    // In-memory cache: "x,y,z" -> PedestalData
    private static final Map<String, PedestalData> pedestals = new ConcurrentHashMap<>();

    // Dirty flag for debounced saving
    private static final AtomicBoolean dirty = new AtomicBoolean(false);

    // Async save scheduler
    private static ScheduledExecutorService saveScheduler;

    private static File dataFile;
    private static boolean initialized = false;

    public static class PedestalData {
        public int x, y, z;
        public String ownerUuid;
        public String ownerName;
        public String item;       // Item ID in pedestal (or empty if none)
        public long placed;       // Timestamp when placed
        public boolean verified;  // True if multiblock structure is complete

        public PedestalData() {}

        public PedestalData(int x, int y, int z, UUID owner, String ownerName, long placed) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.ownerUuid = owner.toString();
            this.ownerName = ownerName;
            this.item = "";
            this.placed = placed;
            this.verified = false;
        }

        public String toCsv() {
            // Escape commas in ownerName if present
            String safeName = ownerName != null ? ownerName.replace(",", ";") : "";
            String safeItem = item != null ? item : "";
            return safeName + "," + ownerUuid + "," + x + "," + y + "," + z + "," + safeItem + "," + placed + "," + verified;
        }

        public static PedestalData fromCsv(String line) {
            String[] parts = line.split(",");
            if (parts.length < 7) return null;

            try {
                PedestalData data = new PedestalData();
                data.ownerName = parts[0].trim();
                data.ownerUuid = parts[1].trim();
                data.x = Integer.parseInt(parts[2].trim());
                data.y = Integer.parseInt(parts[3].trim());
                data.z = Integer.parseInt(parts[4].trim());
                data.item = parts[5].trim();
                data.placed = Long.parseLong(parts[6].trim());
                // Handle verified field (optional for backwards compatibility)
                data.verified = parts.length > 7 && Boolean.parseBoolean(parts[7].trim());
                return data;
            } catch (Exception e) {
                return null;
            }
        }
    }

    /**
     * Initialize the registry. Call once at mod startup.
     */
    public static void init() {
        if (initialized) return;

        try {
            // Create data directory
            File dir = new File(DATA_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            dataFile = new File(dir, FILE_NAME);

            // Load existing data
            if (dataFile.exists()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(dataFile))) {
                    String line;
                    boolean header = true;
                    while ((line = reader.readLine()) != null) {
                        if (header) {
                            header = false;
                            continue; // Skip header
                        }
                        if (line.trim().isEmpty()) continue;

                        PedestalData data = PedestalData.fromCsv(line);
                        if (data != null) {
                            pedestals.put(posKey(data.x, data.y, data.z), data);
                        }
                    }
                }
            }

            // Start async save scheduler (saves every 0.5 seconds if dirty)
            saveScheduler = Executors.newSingleThreadScheduledExecutor();
            saveScheduler.scheduleAtFixedRate(() -> {
                if (dirty.compareAndSet(true, false)) {
                    saveSync();
                }
            }, 500, 500, TimeUnit.MILLISECONDS);

            initialized = true;
            System.out.println("[PedestalRegistry] Loaded " + pedestals.size() + " pedestals");
        } catch (Exception e) {
            System.err.println("[PedestalRegistry] Failed to initialize: " + e.getMessage());
        }
    }

    /**
     * Shutdown the registry. Call at mod shutdown.
     */
    public static void shutdown() {
        if (saveScheduler != null) {
            saveScheduler.shutdown();
        }
        // Final save
        if (dirty.get()) {
            saveSync();
        }
    }

    /**
     * Register a pedestal placement.
     */
    public static void register(int x, int y, int z, UUID ownerUuid, String ownerName) {
        String key = posKey(x, y, z);
        PedestalData data = new PedestalData(x, y, z, ownerUuid, ownerName, System.currentTimeMillis());
        pedestals.put(key, data);
        markDirty();
    }

    /**
     * Unregister a pedestal (when broken).
     */
    public static void unregister(int x, int y, int z) {
        String key = posKey(x, y, z);
        if (pedestals.remove(key) != null) {
            markDirty();
        }
    }

    /**
     * Update the item stored in a pedestal.
     */
    public static void setItem(int x, int y, int z, String itemId) {
        String key = posKey(x, y, z);
        PedestalData data = pedestals.get(key);
        if (data != null) {
            data.item = itemId != null ? itemId : "";
            markDirty();
        }
    }

    /**
     * Set the verified status of a pedestal.
     */
    public static void setVerified(int x, int y, int z, boolean verified) {
        String key = posKey(x, y, z);
        PedestalData data = pedestals.get(key);
        if (data != null) {
            data.verified = verified;
            markDirty();
        }
    }

    /**
     * Check if a pedestal is verified (multiblock complete).
     */
    public static boolean isVerified(int x, int y, int z) {
        PedestalData data = pedestals.get(posKey(x, y, z));
        return data != null && data.verified;
    }

    /**
     * Get pedestal data at position.
     */
    public static PedestalData get(int x, int y, int z) {
        return pedestals.get(posKey(x, y, z));
    }

    /**
     * Check if a pedestal exists at position.
     */
    public static boolean exists(int x, int y, int z) {
        return pedestals.containsKey(posKey(x, y, z));
    }

    /**
     * Get the owner UUID of a pedestal.
     */
    public static UUID getOwner(int x, int y, int z) {
        PedestalData data = pedestals.get(posKey(x, y, z));
        if (data != null && data.ownerUuid != null && !data.ownerUuid.isEmpty()) {
            try {
                return UUID.fromString(data.ownerUuid);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Check if a player owns a pedestal.
     */
    public static boolean isOwner(int x, int y, int z, UUID playerUuid) {
        UUID owner = getOwner(x, y, z);
        return owner != null && owner.equals(playerUuid);
    }

    /**
     * Get all registered pedestals.
     */
    public static Map<String, PedestalData> getAll() {
        return new ConcurrentHashMap<>(pedestals);
    }

    /**
     * Get count of registered pedestals.
     */
    public static int count() {
        return pedestals.size();
    }

    private static String posKey(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    private static void markDirty() {
        dirty.set(true);
    }

    /**
     * Force immediate save (blocking).
     */
    public static void saveNow() {
        dirty.set(false);
        saveSync();
    }

    private static synchronized void saveSync() {
        if (dataFile == null) return;

        try {
            // Write to temp file first, then rename (atomic)
            File tempFile = new File(dataFile.getParent(), FILE_NAME + ".tmp");
            try (PrintWriter writer = new PrintWriter(new FileWriter(tempFile))) {
                writer.println(CSV_HEADER);
                for (PedestalData data : pedestals.values()) {
                    writer.println(data.toCsv());
                }
            }

            // Rename temp to actual file
            Files.move(tempFile.toPath(), dataFile.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE);

        } catch (Exception e) {
            System.err.println("[PedestalRegistry] Failed to save: " + e.getMessage());
        }
    }
}
