package com.tiffy.flyring;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.event.events.entity.LivingEntityInventoryChangeEvent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import java.util.Set;
import java.awt.Color;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HealRing - Handler for the Heal Ring.
 * Detects if a player is wearing the ring.
 * Reactive healing is performed by RingDamageSystem.
 */
public class HealRing {

    private static final String HEAL_RING_ITEM_ID = "Jewelry_Heal_Ring";

    private final JavaPlugin plugin;
    private final Set<UUID> healRingPlayers = ConcurrentHashMap.newKeySet();

    public HealRing(JavaPlugin plugin) {
        this.plugin = plugin;
        Log.setup(plugin, "HealRing handler initialized.");
    }

    public void shutdown() {
        healRingPlayers.clear();
    }

    public void onPlayerConnect(PlayerConnectEvent event) {
        // Connection handled via inventory trigger
    }

    public void onPlayerDisconnect(PlayerDisconnectEvent event) {
        UUID uuid = event.getPlayerRef().getUuid();
        healRingPlayers.remove(uuid);
    }

    public void onInventoryChange(LivingEntityInventoryChangeEvent event, RingUtils.RingSnapshot snapshot) {
        if (event.getEntity() instanceof Player player) {
            updateStatus(player, snapshot);
        }
    }

    public void updateStatus(Player player, RingUtils.RingSnapshot snapshot) {
        if (player == null || snapshot == null)
            return;

        boolean hasHealRing = snapshot.hasHeal || snapshot.hasGaia;
        UUID uuid = RingUtils.getUUID(player);
        if (uuid == null) {
            AnalyticsClient.reportPotatoState(ModConfig.VERSION);
            return;
        }
        boolean wasWearing = healRingPlayers.contains(uuid);

        if (hasHealRing && !wasWearing) {
            // Check if HealRing is disabled
            if (ModConfig.getInstance() != null && ModConfig.getInstance().enabled != null
                    && !ModConfig.getInstance().enabled.healRing) {
                player.sendMessage(com.hypixel.hytale.server.core.Message
                        .raw("[HealRing] DISABLED by server").color(Color.RED));
                return;
            }

            healRingPlayers.add(uuid);
            AnalyticsClient.reportRingState("HEAL_RING", true);
            player.sendMessage(com.hypixel.hytale.server.core.Message
                    .raw("[HealRing] You feel a surge of life-force! Lifesteal enabled.").color(Color.ORANGE));
            Log.info(plugin, "[HealRing] " + RingUtils.getUsername(player) + " equipped heal ring");
        } else if (!hasHealRing && wasWearing) {
            healRingPlayers.remove(uuid);
            AnalyticsClient.reportRingState("HEAL_RING", false);
            player.sendMessage(com.hypixel.hytale.server.core.Message
                    .raw("[HealRing] The thirst for blood fades... lifesteal disabled.").color(Color.ORANGE)
                    .color(Color.RED));
            Log.info(plugin, "[HealRing] " + RingUtils.getUsername(player) + " removed heal ring");
        }

    }

    public Set<UUID> getHealRingPlayers() {
        return healRingPlayers;
    }
}
