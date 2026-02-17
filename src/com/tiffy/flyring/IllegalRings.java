package com.tiffy.flyring;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.event.events.entity.LivingEntityInventoryChangeEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseButtonEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.entity.EntityRemoveEvent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.Entity;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import java.io.File;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

/**
 * IllegalRings - Main wrapper that orchestrates all ring handlers.
 */
public class IllegalRings extends JavaPlugin {

    private static IllegalRings instance;

    private FlyRing flyRingHandler;
    private FireRing fireRingHandler;
    private WaterRing waterRingHandler;
    private HealRing healRingHandler;
    private PeacefullRing peacefulRingHandler;
    private PeacefulAttitudeSystem peacefulAttitudeSystem;
    private RingLootSystem lootSystem;
    private PedestalFilter pedestalFilter;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final AtomicBoolean recipesFinalized = new AtomicBoolean(false);
    private final AtomicBoolean bootSuccessReported = new AtomicBoolean(false);
    private final AtomicBoolean stableReported = new AtomicBoolean(false);
    private final AtomicBoolean heartbeatStarted = new AtomicBoolean(false);

    // VisPet state
    private ComponentType<EntityStore, VisPetComponent> visPetComponentType;
    private final ConcurrentHashMap<UUID, Ref<EntityStore>> visPetOwnerRefs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Ref<EntityStore>> visPetNpcRefs = new ConcurrentHashMap<>();
    private final Set<UUID> visPetActiveBuddies = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<UUID, World> visPetWorlds = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, VisPetToggleSystem.PetType> visPetActiveTypes = new ConcurrentHashMap<>();
    private final AtomicReference<World> visPetWorldRef = new AtomicReference<>();
    private VisPetStorage visPetStorage;
    private VisPetToggleSystem visPetToggleSystem;

    // FreePet state
    private FreePetStorage freePetStorage;
    private final ConcurrentHashMap<UUID, Long> freePetSpawnTimes = new ConcurrentHashMap<>();

    public IllegalRings(JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        instance = this;

        // Initialize analytics early (defaults to production endpoint)
        AnalyticsClient.init(false);

        // Load mod config with status tracking
        ConfigLoadData configResult = null;
        try {
            File gameDir = new File(".").getAbsoluteFile();
            configResult = ModConfig.loadWithStatus(gameDir);
            PlayerSettings.load(gameDir);

            Log.setup(this, "[IllegalRings] Config loaded successfully!");

            // Update analytics with user settings if config loaded
            ModConfig.Config cfg = configResult.config;
            if (cfg != null) {
                AnalyticsClient.init(cfg.testserver);
            }

            if (cfg != null && cfg.debugLogging) {
                Log.setup(this, "[IllegalRings] Lifesteal: " + (cfg.gameplay.lifestealPercent * 100) + "%");
                Log.setup(this,
                        "[IllegalRings] Enabled - Fly:" + cfg.enabled.flyRing + " Fire:" + cfg.enabled.fireRing +
                                " Water:" + cfg.enabled.waterRing + " Heal:" + cfg.enabled.healRing + " Peaceful:"
                                + cfg.enabled.peacefulRing);
            }
        } catch (Throwable e) {
            Log.setup(this, "[IllegalRings] CRITICAL: Failed to load config: " + e.getMessage());
            e.printStackTrace();
        }

        // Initialize PedestalRegistry for persistence
        PedestalRegistry.init();

        flyRingHandler = new FlyRing(this);
        fireRingHandler = new FireRing(this);
        waterRingHandler = new WaterRing(this);
        healRingHandler = new HealRing(this);
        peacefulRingHandler = new PeacefullRing(this);
        pedestalFilter = new PedestalFilter(this);

        // Register Commands
        try {
            com.hypixel.hytale.server.core.HytaleServer.get().getCommandManager()
                    .register(new BackpackToggleCommand(flyRingHandler));
            this.getCommandRegistry().registerCommand(new AdminCommands());
        } catch (Exception e) {
            Log.severe(this, "Failed to register commands: " + e.getMessage());
        }

        // Initialize Loot System
        lootSystem = new RingLootSystem(this);

        // Centralized event registration
        getEventRegistry().registerGlobal(LivingEntityInventoryChangeEvent.class, this::onInventoryChange);
        getEventRegistry().registerGlobal(PlayerConnectEvent.class, this::onPlayerConnect);
        getEventRegistry().registerGlobal(PlayerReadyEvent.class, this::onPlayerReady);
        getEventRegistry().registerGlobal(PlayerMouseButtonEvent.class, this::onPlayerMouseButton);
        getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, this::onPlayerDisconnect);
        getEventRegistry().registerGlobal(EntityRemoveEvent.class, this::onEntityRemove);

        // Register the central RingDamageSystem for all elemental ring immunities
        // Also connect it to loot system for NPC death detection
        RingDamageSystem damageSystem = new RingDamageSystem(this, flyRingHandler, fireRingHandler, waterRingHandler,
                healRingHandler, peacefulRingHandler);
        damageSystem.setLootSystem(lootSystem);
        getEntityStoreRegistry().registerSystem(damageSystem);

        // Register PeacefulTargetClearSystem
        getEntityStoreRegistry().registerSystem(new PeacefulTargetClearSystem(peacefulRingHandler));

        // Register PeacefulAttitudeSystem (Ensures NPCs ignore the player fully)
        getEntityStoreRegistry().registerSystem(new PeacefulAttitudeSystem(peacefulRingHandler));

        // Register LootDropTickSystem (processes queued item drops on WorldThread)
        getEntityStoreRegistry().registerSystem(new LootDropTickSystem(this));

        // Register TiffySpawnSystem (spawns Tiffy NPC at fixed location)
        getEntityStoreRegistry().registerSystem(new TiffySpawnSystem(this));

        // Register PedestalDisplaySystem (spawns display entities above pedestals)
        getEntityStoreRegistry().registerSystem(new PedestalDisplaySystem(this));

        // Register PedestalBlockSystem (tracks pedestal place/break events)
        getEntityStoreRegistry().registerSystem(new PedestalBlockSystem.PlaceSystem(this));
        getEntityStoreRegistry().registerSystem(new PedestalBlockSystem.BreakSystem(this));

        // VisPet initialization
        visPetStorage = new VisPetStorage(java.util.logging.Logger.getLogger("VisPet"));
        NPCPlugin.get().registerCoreComponentType("OpenLootInventory", VisPetBuilderActionOpenInventory::new);
        visPetComponentType = getEntityStoreRegistry().registerComponent(VisPetComponent.class, VisPetComponent::new);
        getEntityStoreRegistry().registerSystem(new VisPetTickSystem(visPetComponentType, visPetOwnerRefs));
        visPetToggleSystem = new VisPetToggleSystem(
                visPetComponentType, visPetOwnerRefs, visPetNpcRefs, visPetActiveBuddies,
                visPetStorage, visPetWorldRef, visPetWorlds, visPetActiveTypes);
        getEntityStoreRegistry().registerSystem(visPetToggleSystem);

        // FreePet initialization
        freePetStorage = new FreePetStorage();

        // Initialize and setup DebugEventListener
        DebugEventListener debugListener = new DebugEventListener(this);
        debugListener.setup();

        Log.setup(this, "IllegalRings Mod v0.3.5 initialized! (Fly/Fire/Water/Heal/Peaceful Rings)");

    }

    @Override
    protected void start() {
        // Report startup reached start phase

        // Initial attempt to apply recipes (SILENT)
        if (applyRecipeOverrides(false)) {
            Log.setup(this, "[IllegalRings] Initial recipes applied successfully (Internal).");
        }
    }

    private void onInventoryChange(LivingEntityInventoryChangeEvent event) {
        if (event.getEntity() instanceof Player player) {
            pedestalFilter.onInventoryChange(event, player);

            RingUtils.RingSnapshot snapshot = RingUtils.getRingSnapshot(player);

            flyRingHandler.updateStatus(player, snapshot);
            fireRingHandler.updateStatus(player, snapshot);
            waterRingHandler.updateStatus(player, snapshot);
            healRingHandler.updateStatus(player, snapshot);
            peacefulRingHandler.updateStatus(player, snapshot);

            RingUtils.checkNightVision(player, snapshot);
        }
    }

    private void onPlayerMouseButton(PlayerMouseButtonEvent event) {
        // Method kept for potential future logic, but analytics removed.
    }

    private void triggerBootSuccess() {
        if (bootSuccessReported.compareAndSet(false, true)) {
            AnalyticsClient.reportInitializationComplete(ModConfig.VERSION);
        }
    }

    private void onPlayerConnect(PlayerConnectEvent event) {
        if (event.getHolder() != null
                && event.getHolder().getComponent(Player.getComponentType()) instanceof Player player) {
            RingUtils.RingSnapshot snapshot = RingUtils.getRingSnapshot(player);

            flyRingHandler.onPlayerConnect(event);
            fireRingHandler.updateStatus(player, snapshot);
            waterRingHandler.updateStatus(player, snapshot);
            healRingHandler.updateStatus(player, snapshot);
            peacefulRingHandler.updateStatus(player, snapshot);

            RingUtils.checkNightVision(player, snapshot);
        }
    }

    private void onPlayerReady(PlayerReadyEvent event) {
        // VisPet: capture world ref + clean stale effects
        visPetWorldRef.compareAndSet(null, event.getPlayer().getWorld());
        cleanStaleVisPetEffects(event);

        // FreePet: check expiry + grant on first login
        handleFreePetOnReady(event);

        try {
            flyRingHandler.onPlayerReady(event);
        } catch (Exception e) {
            Log.severe(this, "[FlyRing] onPlayerReady failed: " + e.getMessage());
        }
        // Trigger global recipe sync once a player is READY (in Playing state)
        if (recipesFinalized.compareAndSet(false, true)) {
            Log.setup(this, "[IllegalRings] Player ready! Triggering global recipe sync.");

            // 1. Loading Recipes (Batch Start)
            AnalyticsClient.reportLoadingRecipes(ModConfig.VERSION);

            scheduler.schedule(() -> {
                if (applyRecipeOverrides(true)) {
                    Log.setup(this, "[IllegalRings] Global recipe synchronization complete.");

                    // 2. Recipes Success (Batch Success)
                    triggerBootSuccess();
                } else {
                    recipesFinalized.set(false);
                }
            }, 2, TimeUnit.SECONDS);
        }

        // Report player ready to analytics
        try {
            AnalyticsClient.reportPlayerReady(event.getPlayer().getDisplayName());

            // NEW: Start 30s Heartbeat for Online Time + Ring Duration Tracking
            if (heartbeatStarted.compareAndSet(false, true)) {
                scheduler.scheduleAtFixedRate(() -> {
                    boolean[] ringStates = collectCurrentRingStates();
                    AnalyticsClient.reportKeepAlive(ModConfig.VERSION, ringStates);
                }, 30, 30, TimeUnit.SECONDS);
            }

            // NEW: Check for Updates (Always logged to console, Chat if enabled, 5s delay)
            scheduler.schedule(() -> {
                AnalyticsClient.checkUpdates(ModConfig.VERSION, (latest) -> {
                    // Notification content updated below using Message API directly

                    // 1. ALWAYS Server Log
                    Log.setup(this, "--- VERSION CHECK ---");
                    Log.setup(this, "Local: " + ModConfig.VERSION);
                    Log.setup(this, "Latest Stable: " + latest);
                    Log.setup(this, "----------------------");

                    // 2. Chat (only if notifications enabled)
                    if (ModConfig.getInstance().notification) {
                        Player player = event.getPlayer();
                        if (player != null) {
                            boolean isOlder = isVersionOlder(ModConfig.VERSION, latest);

                            java.awt.Color color = isOlder ? java.awt.Color.GREEN : java.awt.Color.MAGENTA;

                            String msg = isOlder
                                    ? "An update for Illegal Rings is available! Please install version " + latest
                                            + " for the latest features and bugfixes."
                                    : "You are running a pre-release version (" + ModConfig.VERSION
                                            + ") of Illegal Rings. The current stable version is " + latest + ".";

                            String link = "Visit CurseForge to download: https://www.curseforge.com/hytale/mods/fly-ring";

                            player.sendMessage(com.hypixel.hytale.server.core.Message.raw(msg).color(color));
                            player.sendMessage(
                                    com.hypixel.hytale.server.core.Message.raw(link).color(java.awt.Color.CYAN));
                        }
                    }
                });
            }, 5, TimeUnit.SECONDS);
        } catch (Exception e) {
            Log.severe(this, "[ERR-1000] onPlayerReady analytics: " + e.getMessage());
        }
    }

    private void onPlayerDisconnect(PlayerDisconnectEvent event) {
        // VisPet cleanup
        try {
            UUID petUuid = event.getPlayerRef().getUuid();
            if (visPetActiveBuddies.contains(petUuid)) {
                Ref<EntityStore> npcRef = visPetNpcRefs.remove(petUuid);
                World world = visPetWorlds.getOrDefault(petUuid, visPetWorldRef.get());
                if (npcRef != null && world != null) {
                    world.execute(() -> {
                        try {
                            Store<EntityStore> store = world.getEntityStore().getStore();
                            NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
                            if (npc != null) {
                                visPetStorage.saveInventory(petUuid, npc.getInventory().getHotbar());
                            }
                            store.removeEntity(npcRef, RemoveReason.REMOVE);
                        } catch (Exception e) {
                            Log.severe(this, "[ERR-1001] VisPet disconnect save/remove: " + e.getMessage());
                        }
                    });
                }
                visPetOwnerRefs.remove(petUuid);
                visPetActiveBuddies.remove(petUuid);
                visPetWorlds.remove(petUuid);
                visPetActiveTypes.remove(petUuid);
            }
        } catch (Exception e) {
            Log.severe(this, "[ERR-1002] VisPet disconnect outer: " + e.getMessage());
        }

        // FreePet cleanup: save remaining time on disconnect
        try {
            UUID fpUuid = event.getPlayerRef().getUuid();
            onFreePetDespawned(fpUuid);
        } catch (Exception e) {
            Log.severe(this, "[ERR-1003] FreePet disconnect cleanup: " + e.getMessage());
        }

        flyRingHandler.onPlayerDisconnect(event);
        fireRingHandler.onPlayerDisconnect(event);
        waterRingHandler.onPlayerDisconnect(event);
        healRingHandler.onPlayerDisconnect(event);
        peacefulRingHandler.onPlayerDisconnect(event);

        // Cleanup Night Vision
        if (event.getPlayerRef() != null) {
            RingUtils.cleanupNightVision(event.getPlayerRef().getUuid());
        }
    }

    private void cleanStaleVisPetEffects(PlayerReadyEvent event) {
        try {
            Ref<EntityStore> playerRef = event.getPlayerRef();
            World world = event.getPlayer().getWorld();
            if (world == null) return;
            world.execute(() -> {
                try {
                    Store<EntityStore> store = world.getEntityStore().getStore();
                    com.hypixel.hytale.server.core.universe.PlayerRef pRef = store.getComponent(playerRef,
                            com.hypixel.hytale.server.core.universe.PlayerRef.getComponentType());
                    if (pRef == null) return;
                    UUID uuid = pRef.getUuid();
                    if (visPetActiveBuddies.contains(uuid)) return;

                    EffectControllerComponent effectCtrl = store.getComponent(playerRef,
                            EffectControllerComponent.getComponentType());
                    if (effectCtrl != null) {
                        String[] activeEffectIds = {
                                "Loot_Fox_Active", "Loot_Cow_Active",
                                "Loot_Sheep_Active", "Loot_Hound_Active"
                        };
                        for (String effectId : activeEffectIds) {
                            int idx = EntityEffect.getAssetMap().getIndex(effectId);
                            if (idx != -1) {
                                effectCtrl.removeEffect(playerRef, idx,
                                        (ComponentAccessor<EntityStore>) store);
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.severe(this, "[ERR-1004] cleanStaleVisPetEffects inner: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            Log.severe(this, "[ERR-1005] cleanStaleVisPetEffects outer: " + e.getMessage());
        }
    }

    private void onEntityRemove(EntityRemoveEvent event) {
        // Note: Loot drops are now handled via RingDamageSystem.checkNpcDeath()
        // which detects NPC death when damage reduces HP to 0.
        // EntityRemoveEvent fires too late (entity already being removed).
    }

    /**
     * Public method to reload recipes with broadcast (called from IRConfigPage on
     * close).
     * Runs async via scheduler to avoid crashing the UI event handler thread.
     */
    public void reloadRecipes() {
        scheduler.schedule(() -> applyRecipeOverrides(true), 500, TimeUnit.MILLISECONDS);
    }

    private boolean applyRecipeOverrides(boolean broadcast) {
        ModConfig.Config cfg = ModConfig.getInstance();
        if (cfg != null) {
            return RecipeManager.applyOverrides(cfg.recipeOverrides, cfg.craftable, broadcast);
        }
        return false;
    }

    /**
     * Collects current ring states for analytics heartbeat.
     * Returns boolean[6]: [fly, fire, water, heal, peaceful, gaia]
     * Individual rings count only for players WITHOUT Gaia equipped.
     * Gaia counts separately. This ensures correct duration tracking.
     */
    private boolean[] collectCurrentRingStates() {
        // Get Gaia players set
        java.util.Set<java.util.UUID> gaiaSet = (flyRingHandler != null)
                ? flyRingHandler.getGaiaPlayers()
                : java.util.Collections.emptySet();
        boolean hasGaia = !gaiaSet.isEmpty();

        // For each ring: check if there are players with that ring who DON'T have Gaia
        boolean hasFly = hasPlayersWithoutGaia(
                flyRingHandler != null ? flyRingHandler.getFalldamageImmunePlayers() : null, gaiaSet);
        boolean hasFire = hasPlayersWithoutGaia(
                fireRingHandler != null ? fireRingHandler.getFireImmunePlayers() : null, gaiaSet);
        boolean hasWater = hasPlayersWithoutGaia(
                waterRingHandler != null ? waterRingHandler.getWaterImmunePlayers() : null, gaiaSet);
        boolean hasHeal = hasPlayersWithoutGaia(
                healRingHandler != null ? healRingHandler.getHealRingPlayers() : null, gaiaSet);
        boolean hasPeaceful = hasPlayersWithoutGaia(
                peacefulRingHandler != null ? peacefulRingHandler.getPeacefulPlayers() : null, gaiaSet);

        return new boolean[] { hasFly, hasFire, hasWater, hasHeal, hasPeaceful, hasGaia };
    }

    /**
     * Checks if there are players in ringSet who are NOT in gaiaSet.
     */
    private boolean hasPlayersWithoutGaia(java.util.Set<java.util.UUID> ringSet,
            java.util.Set<java.util.UUID> gaiaSet) {
        if (ringSet == null || ringSet.isEmpty())
            return false;
        for (java.util.UUID uuid : ringSet) {
            if (!gaiaSet.contains(uuid)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void shutdown() {
        if (flyRingHandler != null)
            flyRingHandler.shutdown();
        if (fireRingHandler != null)
            fireRingHandler.shutdown();
        if (waterRingHandler != null)
            waterRingHandler.shutdown();
        if (healRingHandler != null)
            healRingHandler.shutdown();
        scheduler.shutdown();

        // Shutdown PedestalRegistry (saves data)
        PedestalRegistry.shutdown();

        Log.setup(this, "IllegalRings Mod shut down.");
    }

    private static class BackpackToggleCommand extends com.hypixel.hytale.server.core.command.system.AbstractCommand {
        private final FlyRing flyRingHandler;

        public BackpackToggleCommand(FlyRing flyRingHandler) {
            super("frbackpack", "Toggle backpack ring detection (per player)");
            this.flyRingHandler = flyRingHandler;
        }

        @Override
        protected java.util.concurrent.CompletableFuture<Void> execute(
                com.hypixel.hytale.server.core.command.system.CommandContext context) {
            try {
                java.util.UUID uuid = context.sender().getUuid();
                boolean newState = PlayerSettings.toggleBackpack(uuid);

                String state = newState ? "&aENABLED" : "&cDISABLED";
                context.sendMessage(com.hypixel.hytale.server.core.Message
                        .raw("&6[FlyRing]&f Backpack ring detection: " + state));

                // Refresh flight status
                com.hypixel.hytale.server.core.universe.PlayerRef playerRef = com.hypixel.hytale.server.core.universe.Universe
                        .get().getPlayer(uuid);
                if (playerRef != null) {
                    Player player = playerRef.getComponent(Player.getComponentType());
                    if (player != null) {
                        flyRingHandler.updateStatus(player, RingUtils.getRingSnapshot(player));
                    }
                }
            } catch (Exception e) {
                context.sendMessage(com.hypixel.hytale.server.core.Message
                        .raw("&c[FlyRing] Error: " + e.getMessage()));
            }
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Get the singleton instance of IllegalRings.
     */
    public static IllegalRings getInstance() {
        return instance;
    }

    /**
     * Refresh ring status for a player by UUID (used by PedestalFilter for
     * real-time updates).
     */
    public void refreshRingStatusForPlayer(UUID playerUuid) {
        if (playerUuid == null)
            return;
        try {
            com.hypixel.hytale.server.core.universe.PlayerRef playerRef = com.hypixel.hytale.server.core.universe.Universe
                    .get().getPlayer(playerUuid);
            if (playerRef == null)
                return;

            Player player = playerRef.getComponent(Player.getComponentType());
            if (player == null)
                return;

            RingUtils.RingSnapshot snapshot = RingUtils.getRingSnapshot(player);

            flyRingHandler.updateStatus(player, snapshot);
            fireRingHandler.updateStatus(player, snapshot);
            waterRingHandler.updateStatus(player, snapshot);
            healRingHandler.updateStatus(player, snapshot);
            peacefulRingHandler.updateStatus(player, snapshot);

            RingUtils.checkNightVision(player, snapshot);

            boolean debug = ModConfig.getInstance() != null && ModConfig.getInstance().debugLogging;
            if (debug) {
                Log.info(this, "[Pedestal] Refreshed ring status for " + player.getDisplayName());
            }
        } catch (Exception e) {
            Log.severe(this, "[ERR-1006] refreshRingStatusForPlayer: " + e.getMessage());
        }
    }

    @SuppressWarnings("removal")
    private void handleFreePetOnReady(PlayerReadyEvent event) {
        try {
            Player player = event.getPlayer();
            UUID uuid = player.getPlayerRef().getUuid();
            FreePetStorage.FreePetData fpData = freePetStorage.load(uuid);

            // Check if expired → only act if the item is still in inventory
            if (fpData.received && fpData.isExpired() && fpData.serial != null) {
                // Scan all inventory containers for item with matching serial
                if (hasFreePetInInventory(player, fpData.serial)) {
                    expireFreePet(player, uuid);
                }
                // Either way, item is gone now (or was already gone). No repeated message.
                return;
            }

            // Show remaining time on login (only if item with serial is in inventory)
            if (fpData.received && fpData.remainingMs > 0 && fpData.serial != null) {
                if (hasFreePetInInventory(player, fpData.serial)) {
                    String timeStr = formatDuration(fpData.remainingMs);
                    player.sendMessage(com.hypixel.hytale.server.core.Message.raw(
                            "[Pet] Free Fox remaining: " + timeStr));
                }
            }

            // Check if should grant new free pet
            ModConfig.Config cfg = ModConfig.getInstance();
            if (cfg != null && cfg.noFreePet) return;
            if (fpData.received) return; // Already received

            // Grant free pet with unique serial (scans hotbar + storage for space)
            String serial = UUID.randomUUID().toString();
            com.hypixel.hytale.server.core.inventory.ItemStack stamped =
                    new com.hypixel.hytale.server.core.inventory.ItemStack("Loot_Fox_Free", 1)
                            .withMetadata(FreePetStorage.META_SERIAL, com.hypixel.hytale.codec.Codec.STRING, serial);
            if (!addToInventory(player, stamped)) return; // No space - try again next login
            fpData.received = true;
            fpData.remainingMs = FreePetStorage.DURATION_MS;
            fpData.serial = serial;
            freePetStorage.save(uuid, fpData);

            String timeStr = formatDuration(FreePetStorage.DURATION_MS);
            String msg = "[Pet] ";
            ModConfig.Config msgCfg = ModConfig.getInstance();
            if (msgCfg != null && msgCfg.freePetMessage != null && !msgCfg.freePetMessage.isEmpty()) {
                msg += msgCfg.freePetMessage.replace("{time}", timeStr);
            } else {
                msg += "Thank you for playing Illegal Rings, you are amazing <3 Here's a free Fox companion for " + timeStr + "!";
            }
            player.sendMessage(com.hypixel.hytale.server.core.Message.raw(msg)
                    .color(java.awt.Color.YELLOW));
            Log.info(this, "[FreePet] Granted free Fox to " + player.getDisplayName() + " serial=" + serial);
        } catch (Exception e) {
            Log.severe(this, "[ERR-1007] handleFreePetOnReady: " + e.getMessage());
        }
    }

    /**
     * Check if any inventory container (hotbar + storage) has a Loot_Fox_Free with matching serial.
     */
    private boolean hasFreePetInInventory(Player player, String serial) {
        com.hypixel.hytale.server.core.inventory.Inventory inv = player.getInventory();
        com.hypixel.hytale.server.core.inventory.container.ItemContainer[] containers = { inv.getHotbar(), inv.getStorage() };
        for (com.hypixel.hytale.server.core.inventory.container.ItemContainer container : containers) {
            if (container == null) continue;
            for (short s = 0; s < container.getCapacity(); s++) {
                com.hypixel.hytale.server.core.inventory.ItemStack stack = container.getItemStack(s);
                if (!com.hypixel.hytale.server.core.inventory.ItemStack.isEmpty(stack)
                        && "Loot_Fox_Free".equals(stack.getItemId())) {
                    String itemSerial = stack.getFromMetadataOrNull(FreePetStorage.META_SERIAL,
                            com.hypixel.hytale.codec.Codec.STRING);
                    if (serial.equals(itemSerial)) return true;
                }
            }
        }
        return false;
    }

    /**
     * Remove a Loot_Fox_Free with matching serial from any inventory container.
     */
    private boolean removeFreePetFromInventory(Player player, String serial) {
        com.hypixel.hytale.server.core.inventory.Inventory inv = player.getInventory();
        com.hypixel.hytale.server.core.inventory.container.ItemContainer[] containers = { inv.getHotbar(), inv.getStorage() };
        for (com.hypixel.hytale.server.core.inventory.container.ItemContainer container : containers) {
            if (container == null) continue;
            for (short s = 0; s < container.getCapacity(); s++) {
                com.hypixel.hytale.server.core.inventory.ItemStack stack = container.getItemStack(s);
                if (!com.hypixel.hytale.server.core.inventory.ItemStack.isEmpty(stack)
                        && "Loot_Fox_Free".equals(stack.getItemId())) {
                    String itemSerial = stack.getFromMetadataOrNull(FreePetStorage.META_SERIAL,
                            com.hypixel.hytale.codec.Codec.STRING);
                    if (serial.equals(itemSerial)) {
                        container.setItemStackForSlot(s, com.hypixel.hytale.server.core.inventory.ItemStack.EMPTY);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Add an ItemStack to the first empty slot in hotbar or storage.
     */
    private boolean addToInventory(Player player, com.hypixel.hytale.server.core.inventory.ItemStack stack) {
        com.hypixel.hytale.server.core.inventory.Inventory inv = player.getInventory();
        com.hypixel.hytale.server.core.inventory.container.ItemContainer[] containers = { inv.getHotbar(), inv.getStorage() };
        for (com.hypixel.hytale.server.core.inventory.container.ItemContainer container : containers) {
            if (container == null) continue;
            for (short s = 0; s < container.getCapacity(); s++) {
                if (com.hypixel.hytale.server.core.inventory.ItemStack.isEmpty(container.getItemStack(s))) {
                    container.setItemStackForSlot(s, stack);
                    return true;
                }
            }
        }
        return false;
    }

    /** Called by VisPetToggleSystem when a free pet is spawned */
    void onFreePetSpawned(UUID uuid) {
        freePetSpawnTimes.put(uuid, System.currentTimeMillis());
    }

    /** Called by VisPetToggleSystem when a free pet is despawned */
    void onFreePetDespawned(UUID uuid) {
        Long spawnTime = freePetSpawnTimes.remove(uuid);
        if (spawnTime != null) {
            long elapsed = System.currentTimeMillis() - spawnTime;
            FreePetStorage.FreePetData fpData = freePetStorage.load(uuid);
            fpData.remainingMs = Math.max(0, fpData.remainingMs - elapsed);
            freePetStorage.save(uuid, fpData);
        }
    }

    /** Called every ~1s by VisPetToggleSystem tick to check expiry */
    void tickFreePetTimer(UUID uuid, Player player, com.hypixel.hytale.component.Store<EntityStore> store,
                          com.hypixel.hytale.component.CommandBuffer<EntityStore> buffer) {
        Long spawnTime = freePetSpawnTimes.get(uuid);
        if (spawnTime == null) return;
        FreePetStorage.FreePetData fpData = freePetStorage.load(uuid);
        long elapsed = System.currentTimeMillis() - spawnTime;
        long remaining = fpData.remainingMs - elapsed;

        if (remaining <= 0) {
            // Expired while active - despawn immediately
            freePetSpawnTimes.remove(uuid);
            fpData.remainingMs = 0;
            freePetStorage.save(uuid, fpData);
            expireFreePet(player, uuid);
        }
    }

    boolean isFreePetItem(String itemId) {
        return "Loot_Fox_Free".equals(itemId);
    }

    boolean hasFreePetActive(UUID uuid) {
        return freePetSpawnTimes.containsKey(uuid);
    }

    private static String formatDuration(long ms) {
        long totalSec = ms / 1000;
        long days = totalSec / 86400;
        long hours = (totalSec % 86400) / 3600;
        long mins = (totalSec % 3600) / 60;
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + mins + "m";
        return mins + "m " + (totalSec % 60) + "s";
    }

    private void expireFreePet(Player player, UUID uuid) {
        try {
            // Remove Loot_Fox_Free with matching serial from all inventory containers
            FreePetStorage.FreePetData fpData = freePetStorage.load(uuid);
            if (fpData.serial != null) {
                removeFreePetFromInventory(player, fpData.serial);
            } else {
                // Fallback: remove any Loot_Fox_Free (legacy items without serial) from all containers
                com.hypixel.hytale.server.core.inventory.Inventory inv = player.getInventory();
                com.hypixel.hytale.server.core.inventory.container.ItemContainer[] containers = { inv.getHotbar(), inv.getStorage() };
                for (com.hypixel.hytale.server.core.inventory.container.ItemContainer container : containers) {
                    if (container == null) continue;
                    for (short s = 0; s < container.getCapacity(); s++) {
                        com.hypixel.hytale.server.core.inventory.ItemStack stack = container.getItemStack(s);
                        if (!com.hypixel.hytale.server.core.inventory.ItemStack.isEmpty(stack)
                                && "Loot_Fox_Free".equals(stack.getItemId())) {
                            container.setItemStackForSlot(s, com.hypixel.hytale.server.core.inventory.ItemStack.EMPTY);
                        }
                    }
                }
            }

            // If pet is active, despawn it
            if (visPetActiveBuddies.contains(uuid)) {
                Ref<EntityStore> npcRef = visPetNpcRefs.remove(uuid);
                World world = visPetWorlds.getOrDefault(uuid, visPetWorldRef.get());
                if (npcRef != null && world != null) {
                    world.execute(() -> {
                        try {
                            Store<EntityStore> store = world.getEntityStore().getStore();
                            store.removeEntity(npcRef, RemoveReason.REMOVE);
                        } catch (Exception e) {
                            Log.severe(this, "[ERR-1008] expireFreePet despawn: " + e.getMessage());
                        }
                    });
                }
                visPetOwnerRefs.remove(uuid);
                visPetActiveBuddies.remove(uuid);
                visPetWorlds.remove(uuid);
                visPetActiveTypes.remove(uuid);
            }

            player.sendMessage(com.hypixel.hytale.server.core.Message.raw(
                    "[Pet] Your free Fox companion has expired.").color(java.awt.Color.RED));
            Log.info(this, "[FreePet] Expired free Fox for " + player.getDisplayName());
        } catch (Exception e) {
            Log.severe(this, "[ERR-1009] expireFreePet outer: " + e.getMessage());
        }
    }

    // VisPet getters (package-private for command access)
    ComponentType<EntityStore, VisPetComponent> getVisPetComponentType() { return visPetComponentType; }
    ConcurrentHashMap<UUID, Ref<EntityStore>> getVisPetOwnerRefs() { return visPetOwnerRefs; }
    ConcurrentHashMap<UUID, Ref<EntityStore>> getVisPetNpcRefs() { return visPetNpcRefs; }
    Set<UUID> getVisPetActiveBuddies() { return visPetActiveBuddies; }
    VisPetStorage getVisPetStorage() { return visPetStorage; }
    ConcurrentHashMap<UUID, World> getVisPetWorlds() { return visPetWorlds; }
    ConcurrentHashMap<UUID, VisPetToggleSystem.PetType> getVisPetActiveTypes() { return visPetActiveTypes; }
    AtomicReference<World> getVisPetWorldRef() { return visPetWorldRef; }
    VisPetToggleSystem getVisPetToggleSystem() { return visPetToggleSystem; }
    FreePetStorage getFreePetStorage() { return freePetStorage; }

    private boolean isVersionOlder(String current, String latest) {
        try {
            String[] v1 = current.split("\\.");
            String[] v2 = latest.split("\\.");
            int len = Math.max(v1.length, v2.length);
            for (int i = 0; i < len; i++) {
                int n1 = i < v1.length ? Integer.parseInt(v1[i].replaceAll("[^0-9]", "")) : 0;
                int n2 = i < v2.length ? Integer.parseInt(v2[i].replaceAll("[^0-9]", "")) : 0;
                if (n1 < n2)
                    return true;
                if (n1 > n2)
                    return false;
            }
        } catch (Exception e) {
            Log.severe(this, "[ERR-1010] isVersionOlder: " + e.getMessage());
        }
        return false;
    }
}
