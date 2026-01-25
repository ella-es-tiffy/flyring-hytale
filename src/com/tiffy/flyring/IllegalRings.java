package com.tiffy.flyring;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.event.events.entity.LivingEntityInventoryChangeEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.entity.entities.Player;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * IllegalRings - Main wrapper that orchestrates all ring handlers.
 */
public class IllegalRings extends JavaPlugin {

    private FlyRing flyRingHandler;
    private FireRing fireRingHandler;
    private WaterRing waterRingHandler;
    private HealRing healRingHandler;
    private PeacefullRing peacefulRingHandler;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public IllegalRings(JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        Log.setup(this, "IllegalRings Mod v0.3.2 initializing...");

        // Load mod config
        try {
            File gameDir = new File(".").getAbsoluteFile();
            ModConfig.load(gameDir);
            PlayerSettings.load(gameDir);
            Log.setup(this, "[IllegalRings] Config loaded successfully!");

            // Log config values
            ModConfig.Config cfg = ModConfig.getInstance();
            if (cfg != null && cfg.debugLogging) {
                Log.setup(this, "[IllegalRings] Lifesteal: " + (cfg.gameplay.lifestealPercent * 100) + "%");
                Log.setup(this, "[IllegalRings] Enabled - Fly:" + cfg.enabled.flyRing + " Fire:"
                        + cfg.enabled.fireRing +
                        " Water:" + cfg.enabled.waterRing + " Heal:" + cfg.enabled.healRing + " Peaceful:"
                        + cfg.enabled.peacefulRing);
            }
        } catch (Exception e) {
            Log.setup(this, "[IllegalRings] Failed to load config: " + e.getMessage());
        }

        // Initialize ring handlers
        flyRingHandler = new FlyRing(this, true);  // true = setup() will be called
        fireRingHandler = new FireRing(this, false);
        waterRingHandler = new WaterRing(this, false);
        healRingHandler = new HealRing(this, false);
        peacefulRingHandler = new PeacefullRing(this, false);

        // Register Command properly via HytaleServer CommandManager
        try {
            com.hypixel.hytale.server.core.HytaleServer.get().getCommandManager()
                    .register(new BackpackToggleCommand(flyRingHandler));
        } catch (Exception e) {
            Log.severe(this, "Failed to register FlyRing command: " + e.getMessage());
        }

        // Centralized event registration
        getEventRegistry().registerGlobal(LivingEntityInventoryChangeEvent.class, this::onInventoryChange);
        getEventRegistry().registerGlobal(PlayerConnectEvent.class, this::onPlayerConnect);
        getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, this::onPlayerDisconnect);

        // Register the central RingDamageSystem for all elemental ring immunities
        getEntityStoreRegistry()
                .registerSystem(new RingDamageSystem(this, fireRingHandler, waterRingHandler, healRingHandler,
                        peacefulRingHandler));

        // Register PeacefulTargetClearSystem
        getEntityStoreRegistry().registerSystem(new PeacefulTargetClearSystem(peacefulRingHandler));

        // Register PeacefulAttitudeSystem (Ensures NPCs ignore the player fully)
        getEntityStoreRegistry().registerSystem(new PeacefulAttitudeSystem(peacefulRingHandler));

        // Initialize and setup DebugEventListener
        DebugEventListener debugListener = new DebugEventListener(this);
        debugListener.setup();

        Log.setup(this, "IllegalRings Mod v0.3.5 initialized! (Fly/Fire/Water/Heal/Peaceful Rings)");
    }

    @Override
    protected void start() {
        // Initial apply of recipe overrides - no broadcast at first to avoid startup
        // errors
        applyRecipeOverrides(false);

        // Retries to ensure we catch everything during late asset loading
        // Second call with broadcast=true to update any early-joining clients
        scheduler.schedule(() -> applyRecipeOverrides(true), 1, TimeUnit.SECONDS);
        scheduler.schedule(() -> applyRecipeOverrides(true), 5, TimeUnit.SECONDS);
        scheduler.schedule(() -> applyRecipeOverrides(true), 15, TimeUnit.SECONDS);
    }

    private void onInventoryChange(LivingEntityInventoryChangeEvent event) {
        flyRingHandler.onInventoryChange(event);
        fireRingHandler.onInventoryChange(event);
        waterRingHandler.onInventoryChange(event);
        healRingHandler.onInventoryChange(event);

        if (event.getEntity() instanceof Player player) {
            peacefulRingHandler.checkInventory(player);
        }
    }

    private void onPlayerConnect(PlayerConnectEvent event) {
        flyRingHandler.onPlayerConnect(event);
        fireRingHandler.onPlayerConnect(event);
        waterRingHandler.onPlayerConnect(event);
        healRingHandler.onPlayerConnect(event);
        peacefulRingHandler.onPlayerConnect(event);
    }

    private void onPlayerDisconnect(PlayerDisconnectEvent event) {
        flyRingHandler.onPlayerDisconnect(event);
        fireRingHandler.onPlayerDisconnect(event);
        waterRingHandler.onPlayerDisconnect(event);
        healRingHandler.onPlayerDisconnect(event);
        peacefulRingHandler.onPlayerDisconnect(event);
    }

    private void applyRecipeOverrides(boolean broadcast) {
        ModConfig.Config cfg = ModConfig.getInstance();
        if (cfg != null) {
            RecipeManager.applyOverrides(cfg.recipeOverrides, cfg.craftable, broadcast);
        }
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

                String state = newState ? "§aENABLED" : "§cDISABLED";
                context.sendMessage(com.hypixel.hytale.server.core.Message
                        .raw("§6[FlyRing]§f Backpack ring detection: " + state));

                // Refresh flight status
                com.hypixel.hytale.server.core.universe.PlayerRef playerRef = com.hypixel.hytale.server.core.universe.Universe
                        .get().getPlayer(uuid);
                if (playerRef != null) {
                    Player player = playerRef.getComponent(Player.getComponentType());
                    if (player != null) {
                        flyRingHandler.updateFlightStatus(player);
                    }
                }
            } catch (Exception e) {
                context.sendMessage(com.hypixel.hytale.server.core.Message
                        .raw("§c[FlyRing] Error: " + e.getMessage()));
            }
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }
    }
}
