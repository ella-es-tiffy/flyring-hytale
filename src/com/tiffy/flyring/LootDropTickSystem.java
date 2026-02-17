package com.tiffy.flyring;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.protocol.packets.interface_.ShowEventTitle;
import com.hypixel.hytale.protocol.FormattedMessage;

import java.util.List;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * LootDropTickSystem - Processes pending loot drops on the World Thread.
 *
 * This system runs every tick and spawns any queued items safely
 * since it runs on the correct thread.
 */
public class LootDropTickSystem extends EntityTickingSystem<EntityStore> {

    private final IllegalRings plugin;

    // Static queue so RingLootSystem can add drops
    private static final ConcurrentLinkedQueue<PendingDrop> pendingDrops = new ConcurrentLinkedQueue<>();

    // Sound queue for altar activation sounds (from PedestalFilter)
    private static final ConcurrentLinkedQueue<PendingSound> pendingSounds = new ConcurrentLinkedQueue<>();

    // Texture update queue for pedestal texture changes (delayed to avoid CloseWindow override)
    private static final ConcurrentLinkedQueue<PendingTextureUpdate> pendingTextureUpdates = new ConcurrentLinkedQueue<>();

    // Track last processed tick to avoid processing multiple times per tick
    private static final AtomicLong lastProcessedTick = new AtomicLong(-1);
    private static long currentTick = 0;

    // Periodic pedestal texture sync interval (every 40 ticks = ~2 seconds)
    private static final int TEXTURE_SYNC_INTERVAL = 40;
    private static long textureSyncCounter = 0;

    // Sound event name for ring drops (custom sound from mod assets)
    private static final String RING_DROP_SOUND = "SFX_Ring_Drop";
    private static int ringDropSoundIndex = -1;

    // Altar discovery sounds (custom mod sounds)
    private static final String LIGHT_ALTAR_SOUND = "SFX_Light_Altar";
    private static final String DARK_ALTAR_SOUND = "SFX_Dark_Altar";
    private static int lightAltarSoundIndex = -1;
    private static int darkAltarSoundIndex = -1;

    public static class PendingDrop {
        public final String itemId;
        public final int quantity;
        public final Vector3d position;
        @Nullable
        public final Ref<EntityStore> killerRef;
        public final boolean isRing;

        public PendingDrop(String itemId, int quantity, Vector3d position, @Nullable Ref<EntityStore> killerRef, boolean isRing) {
            this.itemId = itemId;
            this.quantity = quantity;
            this.position = position;
            this.killerRef = killerRef;
            this.isRing = isRing;
        }
    }

    /**
     * Pending sound to be played on the world thread.
     */
    public static class PendingSound {
        public final PlayerRef playerRef;
        public final String soundType;  // "LIGHT", "DARK", or "SACRED"
        public final Vector3d position;

        public PendingSound(PlayerRef playerRef, String soundType, Vector3d position) {
            this.playerRef = playerRef;
            this.soundType = soundType;
            this.position = position;
        }
    }

    /**
     * Pending texture update for pedestal blocks.
     * Checks CSV for item to determine on/off state.
     */
    public static class PendingTextureUpdate {
        public final int x, y, z;
        public final int delayTicks;  // Wait this many ticks before applying
        public int ticksRemaining;

        public PendingTextureUpdate(int x, int y, int z, int delayTicks) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.delayTicks = delayTicks;
            this.ticksRemaining = delayTicks;
        }
    }

    /**
     * Queue a pedestal texture update to happen after a delay.
     * The actual on/off state is read from CSV when applied.
     */
    public static void queueTextureUpdate(int x, int y, int z, int delayTicks) {
        pendingTextureUpdates.add(new PendingTextureUpdate(x, y, z, delayTicks));
    }

    /**
     * Queue an altar activation sound to be played on the world thread.
     * Called from PedestalFilter when an altar is activated.
     */
    public static void queueAltarSound(PlayerRef playerRef, String altarType, double x, double y, double z) {
        if (playerRef != null) {
            pendingSounds.add(new PendingSound(playerRef, altarType, new Vector3d(x, y, z)));
        }
    }

    /**
     * Add a drop to the queue (called from any thread).
     * @param killerRef The player who killed the NPC (for sound playback), can be null
     * @param isRing True if this is a ring drop (plays sound), false for crystals
     */
    public static void queueDrop(String itemId, int quantity, Vector3d position, @Nullable Ref<EntityStore> killerRef, boolean isRing) {
        pendingDrops.add(new PendingDrop(itemId, quantity, position, killerRef, isRing));
    }

    public LootDropTickSystem(IllegalRings plugin) {
        this.plugin = plugin;
    }

    @Override
    @Nonnull
    public Query<EntityStore> getQuery() {
        // Match any entity - we'll only process once per tick
        return Query.any();
    }

    @Override
    public void tick(float delta, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                     @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> buffer) {
        // Increment tick counter
        currentTick++;

        // Only process once per tick (first entity we see)
        long last = lastProcessedTick.get();
        if (last >= currentTick) {
            return;  // Already processed this tick
        }

        // Try to claim this tick for processing
        if (!lastProcessedTick.compareAndSet(last, currentTick)) {
            return;  // Another thread claimed it
        }

        // Initialize sound indexes if needed
        if (ringDropSoundIndex < 0) {
            try {
                ringDropSoundIndex = SoundEvent.getAssetMap().getIndex(RING_DROP_SOUND);
            } catch (Exception e) {
                Log.info(plugin, "[RingLoot] Could not load sound: " + RING_DROP_SOUND);
                ringDropSoundIndex = -2;  // Mark as failed, don't retry
            }
        }
        if (lightAltarSoundIndex < 0) {
            try {
                lightAltarSoundIndex = SoundEvent.getAssetMap().getIndex(LIGHT_ALTAR_SOUND);
            } catch (Exception e) {
                lightAltarSoundIndex = -2;
            }
        }
        if (darkAltarSoundIndex < 0) {
            try {
                darkAltarSoundIndex = SoundEvent.getAssetMap().getIndex(DARK_ALTAR_SOUND);
            } catch (Exception e) {
                darkAltarSoundIndex = -2;
            }
        }

        // Periodic pedestal texture sync: CSV is single source of truth
        // Re-applies every 40 ticks to correct CloseWindow overrides
        textureSyncCounter++;
        if (textureSyncCounter >= TEXTURE_SYNC_INTERVAL) {
            textureSyncCounter = 0;
            syncAllPedestalTextures(store);
        }

        // Process pending drops
        PendingDrop drop;
        while ((drop = pendingDrops.poll()) != null) {
            spawnItem(drop, store, buffer);
        }

        // Process pending altar sounds
        PendingSound sound;
        while ((sound = pendingSounds.poll()) != null) {
            playAltarSound(sound, store);
        }

        // Process pending texture updates (with delay countdown)
        processTextureUpdates(store);
    }

    /**
     * Process pending texture updates. Each update has a delay counter.
     * When delay reaches 0, check CSV and apply texture.
     */
    private void processTextureUpdates(Store<EntityStore> store) {
        // Collect ready updates, then process them
        java.util.List<PendingTextureUpdate> ready = new java.util.ArrayList<>();

        // Decrement all counters and collect ready ones
        for (PendingTextureUpdate update : pendingTextureUpdates) {
            update.ticksRemaining--;
            if (update.ticksRemaining <= 0) {
                ready.add(update);
            }
        }

        // Remove and process ready updates
        for (PendingTextureUpdate update : ready) {
            pendingTextureUpdates.remove(update);
            applyPedestalTexture(update.x, update.y, update.z, store);
        }
    }

    /**
     * Apply pedestal texture based on CSV item data.
     * Reads from CSV to determine if ring is inside -> on/off texture.
     */
    private void applyPedestalTexture(int x, int y, int z, Store<EntityStore> store) {
        try {
            // Check CSV for item at this position
            PedestalRegistry.PedestalData data = PedestalRegistry.get(x, y, z);
            String itemId = (data != null) ? data.item : "";
            boolean hasRing = itemId != null && !itemId.isEmpty();

            // Get world and chunk
            var world = store.getExternalData().getWorld();
            if (world == null) return;

            var chunk = world.getChunkIfInMemory(com.hypixel.hytale.math.util.ChunkUtil.indexChunkFromBlock(x, z));
            if (chunk == null) return;

            // Resolve BASE block type (state variants can't look up other states)
            var blockType = PedestalFilter.resolveBaseAltarType(world, x, y, z);
            if (blockType == null) return;

            // Set texture state based on CSV data
            String stateName = hasRing ? "Activated" : "default";
            chunk.setBlockInteractionState(x, y, z, blockType, stateName, true);
            chunk.markNeedsSaving();

        } catch (Exception e) {
            // Silent fail
        }
    }

    /**
     * Periodic sync: Apply textures for ALL pedestals from CSV.
     * CSV item field is the single source of truth for on/off state.
     */
    private void syncAllPedestalTextures(Store<EntityStore> store) {
        try {
            var allPedestals = PedestalRegistry.getAll();
            for (PedestalRegistry.PedestalData data : allPedestals.values()) {
                applyPedestalTexture(data.x, data.y, data.z, store);
            }
        } catch (Exception e) {
            // Silent fail - will retry next interval
        }
    }

    /**
     * Play an altar activation sound to a player.
     * Uses playSoundEvent2dToPlayer (same as biome discovery sounds).
     */
    private void playAltarSound(PendingSound sound, Store<EntityStore> store) {
        try {
            // Skip sound for SACRED (restore) - only play for new activations
            if ("SACRED".equals(sound.soundType)) {
                return;
            }

            // Determine which sound to play based on altar type
            int soundIndex;
            if ("DARK".equals(sound.soundType)) {
                soundIndex = darkAltarSoundIndex;
            } else {
                soundIndex = lightAltarSoundIndex;
            }

            if (soundIndex <= 0 || sound.playerRef == null) {
                return;
            }

            // Use playSoundEvent2dToPlayer - same method as biome discovery
            SoundUtil.playSoundEvent2dToPlayer(sound.playerRef, soundIndex, SoundCategory.UI);
        } catch (Exception e) {
            // Silent fail - sound is not critical
        }
    }

    private void spawnItem(PendingDrop drop, Store<EntityStore> store, CommandBuffer<EntityStore> buffer) {
        try {
            ItemStack stack = new ItemStack(drop.itemId, drop.quantity);

            // Create item drop with slight upward velocity
            Holder<EntityStore> itemHolder = ItemComponent.generateItemDrop(
                (ComponentAccessor<EntityStore>) store,
                stack,
                drop.position,
                Vector3f.ZERO,  // No directional velocity
                0f,             // X velocity
                0.5f,           // Y velocity (slight upward pop)
                0f              // Z velocity
            );

            // Add pickup delay so player can see it drop
            ItemComponent itemComponent = (ItemComponent) itemHolder.getComponent(ItemComponent.getComponentType());
            if (itemComponent != null) {
                itemComponent.setPickupDelay(0.5f);
            }

            // Use CommandBuffer instead of store.addEntity() to avoid "Store is currently processing" error
            // CommandBuffer defers the operation until after all systems finish processing
            buffer.addEntity(itemHolder, AddReason.SPAWN);

            // Play sound and show event title for killer if this is a ring drop
            if (drop.isRing && drop.killerRef != null && drop.killerRef.isValid()) {
                // Play sound
                if (ringDropSoundIndex >= 0) {
                    try {
                        SoundUtil.playSoundEvent3dToPlayer(
                            drop.killerRef,
                            ringDropSoundIndex,
                            SoundCategory.SFX,
                            drop.position,
                            (ComponentAccessor<EntityStore>) store
                        );
                    } catch (Exception e) {
                        // Sound playback failed, not critical
                    }
                }

                // Show event title to ALL players (server-wide announcement)
                try {
                    PlayerRef killerPlayerRef = (PlayerRef) drop.killerRef.getStore()
                        .getComponent(drop.killerRef, PlayerRef.getComponentType());
                    String playerName = killerPlayerRef != null ? killerPlayerRef.getUsername() : "Someone";
                    broadcastRingDrop(playerName, drop.itemId);
                } catch (Exception e) {
                    // Title display failed, not critical
                }
            }

            boolean debug = ModConfig.getInstance() != null && ModConfig.getInstance().debugLogging;
            if (debug) {
                Log.info(plugin, "[RingLoot] Spawned " + drop.itemId + " at " + drop.position);
            }

        } catch (Exception e) {
            Log.info(plugin, "[RingLoot] Error spawning " + drop.itemId + ": " + e.getMessage());
        }
    }

    /**
     * Broadcasts ring drop announcement to ALL players on the server.
     */
    private void broadcastRingDrop(String playerName, String itemId) {
        String ringName = getRingDisplayName(itemId);

        // Create title message (gold color) - epic announcement
        FormattedMessage title = new FormattedMessage();
        title.rawText = "LEGENDARY DROP!";
        title.color = "#FFD700";  // Gold

        // Create subtitle with player name and ring (white)
        FormattedMessage subtitle = new FormattedMessage();
        subtitle.rawText = playerName + " obtained " + ringName;
        subtitle.color = "#FFFFFF";

        // Create the packet
        ShowEventTitle packet = new ShowEventTitle(
            1.0f,     // fadeInDuration
            1.0f,     // fadeOutDuration
            4.0f,     // duration
            null,     // icon (null = no icon)
            true,     // isMajor (big style like biome discoveries)
            title,
            subtitle
        );

        // Send to ALL players on the server
        try {
            List<PlayerRef> allPlayers = Universe.get().getPlayers();
            for (PlayerRef playerRef : allPlayers) {
                try {
                    PacketUtil.sendPacket(playerRef, packet);
                } catch (Exception e) {
                    // Failed for this player, continue to others
                }
            }
        } catch (Exception e) {
            // Failed to get player list
        }
    }

    /**
     * Maps item ID to a nice display name.
     */
    private String getRingDisplayName(String itemId) {
        return switch (itemId) {
            case "Jewelry_Fly_Ring" -> "Fly Ring";
            case "Jewelry_Fire_Ring" -> "Fire Ring";
            case "Jewelry_Water_Ring" -> "Water Ring";
            case "Jewelry_Heal_Ring" -> "Heal Ring";
            case "Jewelry_Peacefull_Ring" -> "Peaceful Ring";
            case "Jewelry_Gaia_Medallion" -> "Gaia Medallion";
            default -> itemId.replace("Jewelry_", "").replace("_", " ");
        };
    }
}
