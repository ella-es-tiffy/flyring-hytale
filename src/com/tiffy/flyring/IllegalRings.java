package com.tiffy.flyring;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.event.events.entity.LivingEntityInventoryChangeEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.entity.entities.Player;

import java.io.File;

/**
 * IllegalRings - Main wrapper that orchestrates all ring handlers.
 */
public class IllegalRings extends JavaPlugin {

    private FlyRing flyRingHandler;
    private FireRing fireRingHandler;
    private WaterRing waterRingHandler;
    private HealRing healRingHandler;
    private PeacefullRing peacefulRingHandler;

    public IllegalRings(JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        getLogger().atInfo().log("IllegalRings Mod v0.2.6 initializing...");

        // Load mod config
        try {
            ModConfig.load(new File(".").getAbsoluteFile());
            getLogger().atInfo().log("[IllegalRings] Config loaded successfully!");

            // Log config values
            ModConfig.Config cfg = ModConfig.getInstance();
            if (cfg != null) {
                getLogger().atInfo().log("[IllegalRings] Lifesteal: " + (cfg.gameplay.lifestealPercent * 100) + "%");
                getLogger().atInfo()
                        .log("[IllegalRings] Enabled - Fly:" + cfg.enabled.flyRing + " Fire:"
                                + cfg.enabled.fireRing +
                                " Water:" + cfg.enabled.waterRing + " Heal:" + cfg.enabled.healRing + " Peaceful:"
                                + cfg.enabled.peacefulRing);
            }
        } catch (Exception e) {
            getLogger().atInfo().log("[IllegalRings] Failed to load config: " + e.getMessage());
        }

        // Initialize ring handlers
        flyRingHandler = new FlyRing(this, false);
        fireRingHandler = new FireRing(this, false);
        waterRingHandler = new WaterRing(this, false);
        healRingHandler = new HealRing(this, false);
        peacefulRingHandler = new PeacefullRing(this, false);

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

        getLogger().atInfo().log("IllegalRings Mod v0.2.6 initialized! (Fly/Fire/Water/Heal/Peaceful Rings)");
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
        getLogger().atInfo().log("IllegalRings Mod shut down.");
    }
}
