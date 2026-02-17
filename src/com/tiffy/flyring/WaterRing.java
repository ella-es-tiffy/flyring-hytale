package com.tiffy.flyring;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.event.events.entity.LivingEntityInventoryChangeEvent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WaterRing - Handler for the Water Ring.
 * Manages the list of immune players based on inventory contents.
 * Damage filtering (drowning) is performed by RingDamageSystem.
 */
public class WaterRing {

    private static final String WATER_RING_ITEM_ID = "Jewelry_Water_Ring";

    private final JavaPlugin plugin;
    private final Set<UUID> waterImmunePlayers = ConcurrentHashMap.newKeySet();

    public WaterRing(JavaPlugin plugin) {
        this.plugin = plugin;
        Log.setup(plugin, "WaterRing handler initialized.");
    }

    public void shutdown() {
    }

    public void onPlayerConnect(PlayerConnectEvent event) {
    }

    public void onPlayerDisconnect(PlayerDisconnectEvent event) {
        waterImmunePlayers.remove(event.getPlayerRef().getUuid());
    }

    public void onInventoryChange(LivingEntityInventoryChangeEvent event, RingUtils.RingSnapshot snapshot) {
        if (event.getEntity() instanceof Player player) {
            updateStatus(player, snapshot);
        }
    }

    public void updateStatus(Player player, RingUtils.RingSnapshot snapshot) {
        if (player == null || snapshot == null)
            return;

        boolean hasWaterRing = snapshot.hasWater || snapshot.hasGaia;
        if (hasWaterRing) {
            ModConfig.Config mcfg = ModConfig.getInstance();
            if (mcfg != null && mcfg.enabled != null && !mcfg.enabled.waterRing) {
                hasWaterRing = false;
            }
        }
        UUID uuid = RingUtils.getUUID(player);
        if (uuid == null) {
            AnalyticsClient.reportPotatoState(ModConfig.VERSION);
            return;
        }

        if (hasWaterRing) {
            if (waterImmunePlayers.add(uuid)) {
                AnalyticsClient.reportRingState("WATER_RING", true);
                Log.info(plugin, "[WaterRing] " + RingUtils.getUsername(player) + " equipped water ring");
            }
        } else {
            if (waterImmunePlayers.remove(uuid)) {
                AnalyticsClient.reportRingState("WATER_RING", false);
                Log.info(plugin, "[WaterRing] " + RingUtils.getUsername(player) + " removed water ring");
            }
        }
    }

    public Set<UUID> getWaterImmunePlayers() {
        return waterImmunePlayers;
    }

}
