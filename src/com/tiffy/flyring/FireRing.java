package com.tiffy.flyring;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.event.events.entity.LivingEntityInventoryChangeEvent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.awt.Color;

/**
 * FireRing - Handler for the Fire Ring.
 * Manages the list of immune players based on inventory contents.
 * Damage filtering is performed by RingDamageSystem.
 */
public class FireRing {

    private static final String FIRE_RING_ITEM_ID = "Jewelry_Fire_Ring";

    private final JavaPlugin plugin;

    private final Set<java.util.UUID> fireImmunePlayers = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public FireRing(JavaPlugin plugin) {
        this.plugin = plugin;
        Log.setup(plugin, "FireRing handler initialized.");
    }

    public void shutdown() {
    }

    public void onPlayerConnect(PlayerConnectEvent event) {
        // Connection handled via inventory trigger or connect trigger
    }

    public void onPlayerDisconnect(PlayerDisconnectEvent event) {
    }

    public void onInventoryChange(LivingEntityInventoryChangeEvent event, RingUtils.RingSnapshot snapshot) {
        if (event.getEntity() instanceof Player player) {
            updateStatus(player, snapshot);
        }
    }

    public void updateStatus(Player player, RingUtils.RingSnapshot snapshot) {
        if (player == null || snapshot == null)
            return;

        boolean hasFireRing = snapshot.hasFire || snapshot.hasGaia;
        if (hasFireRing) {
            ModConfig.Config mcfg = ModConfig.getInstance();
            if (mcfg != null && mcfg.enabled != null && !mcfg.enabled.fireRing) {
                hasFireRing = false;
            }
        }
        UUID uuid = RingUtils.getUUID(player);
        if (uuid == null) {
            AnalyticsClient.reportPotatoState(ModConfig.VERSION);
            return;
        }

        if (hasFireRing) {
            if (fireImmunePlayers.add(uuid)) {
                AnalyticsClient.reportRingState("FIRE_RING", true);
                Log.info(plugin, "[FireRing] " + RingUtils.getUsername(player) + " equipped fire ring");
            }
        } else {
            if (fireImmunePlayers.remove(uuid)) {
                AnalyticsClient.reportRingState("FIRE_RING", false);
                Log.info(plugin, "[FireRing] " + RingUtils.getUsername(player) + " removed fire ring");
            }
        }
    }

    public Set<java.util.UUID> getFireImmunePlayers() {
        return fireImmunePlayers;
    }

}
