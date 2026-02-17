package com.tiffy.flyring;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.entity.LivingEntityInventoryChangeEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

import java.util.UUID;
import java.awt.Color;
import java.util.Set;
import java.awt.Color;
import java.util.concurrent.ConcurrentHashMap;
import java.awt.Color;

/**
 * PeacefullRing - Handler for the Peaceful Ring.
 * Currently only tracks if a player has the ring equipped.
 */
public class PeacefullRing {

    private static final String PEACEFULL_RING_ITEM_ID = "Jewelry_Peacefull_Ring";

    private final JavaPlugin plugin;
    private final Set<UUID> peacefulPlayers = ConcurrentHashMap.newKeySet();

    public PeacefullRing(JavaPlugin plugin) {
        this.plugin = plugin;
        Log.setup(plugin, "PeacefullRing handler initialized.");
    }

    public void onPlayerConnect(PlayerConnectEvent event) {
        // Handled centrally in IllegalRings
    }

    public void onPlayerDisconnect(PlayerDisconnectEvent event) {
        if (event.getPlayerRef() != null) {
            peacefulPlayers.remove(event.getPlayerRef().getUuid());
        }
    }

    public void onInventoryChange(LivingEntityInventoryChangeEvent event) {
        // Handled centrally in IllegalRings
    }

    public void updateStatus(Player player, RingUtils.RingSnapshot snapshot) {
        if (player == null || snapshot == null)
            return;

        UUID uuid = RingUtils.getUUID(player);
        if (uuid == null) {
            AnalyticsClient.reportPotatoState(ModConfig.VERSION);
            return;
        }
        boolean hasRing = snapshot.hasPeaceful || snapshot.hasGaia;

        if (hasRing) {
            if (peacefulPlayers.add(uuid)) {
                AnalyticsClient.reportRingState("PEACEFUL_RING", true);
                // Check if Ring is disabled
                if (ModConfig.getInstance() != null && ModConfig.getInstance().enabled != null
                        && !ModConfig.getInstance().enabled.peacefulRing) {
                    player.sendMessage(com.hypixel.hytale.server.core.Message
                            .raw("[PeacefulRing] DISABLED by server").color(Color.RED));
                    return;
                }

                player.sendMessage(com.hypixel.hytale.server.core.Message
                        .raw("[PeacefulRing] A soothing aura surrounds you... creatures will ignore you.")
                        .color(Color.ORANGE));
                Log.info(plugin, "[PeacefullRing] " + RingUtils.getUsername(player) + " equipped peaceful ring");
            }
        } else {
            if (peacefulPlayers.remove(uuid)) {
                AnalyticsClient.reportRingState("PEACEFUL_RING", false);
                player.sendMessage(com.hypixel.hytale.server.core.Message
                        .raw("[PeacefulRing] The soothing aura fades. Monsters are aggressive again.")
                        .color(Color.ORANGE).color(Color.RED));
                Log.info(plugin, "[PeacefullRing] " + RingUtils.getUsername(player) + " removed peaceful ring");
            }
        }

        // Check Night Vision (Peaceful Ring + Torches)
        RingUtils.checkNightVision(player);
    }

    public Set<UUID> getPeacefulPlayers() {
        return peacefulPlayers;
    }
}
