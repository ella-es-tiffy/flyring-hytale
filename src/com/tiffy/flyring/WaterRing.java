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
import java.awt.Color;
import java.util.concurrent.ConcurrentHashMap;
import java.awt.Color;

/**
 * WaterRing - Handler for the Water Ring.
 * Manages the list of immune players based on inventory contents.
 * Damage filtering (drowning) is performed by RingDamageSystem.
 */
public class WaterRing {

    private static final String WATER_RING_ITEM_ID = "Jewelry_Water_Ring";

    private final JavaPlugin plugin;
    private final Set<UUID> waterImmunePlayers = ConcurrentHashMap.newKeySet();

    public WaterRing(JavaPlugin plugin, boolean registerEvents) {
        this.plugin = plugin;

        if (registerEvents) {
            setup();
        } else {
            Log.setup(plugin, "WaterRing handler initialized.");
        }
    }

    private void setup() {
        // Event Listeners
        plugin.getEventRegistry().registerGlobal(LivingEntityInventoryChangeEvent.class, this::onInventoryChange);
        plugin.getEventRegistry().registerGlobal(PlayerConnectEvent.class, this::onPlayerConnect);
        plugin.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, this::onPlayerDisconnect);

        Log.setup(plugin, "WaterRing handler initialized with damage filtering!");
    }

    public void shutdown() {
        waterImmunePlayers.clear();
    }

    public void onPlayerConnect(PlayerConnectEvent event) {
        // Connection handled via inventory trigger
    }

    public void onPlayerDisconnect(PlayerDisconnectEvent event) {
        UUID uuid = event.getPlayerRef().getUuid();
        waterImmunePlayers.remove(uuid);
    }

    public void onInventoryChange(LivingEntityInventoryChangeEvent event) {
        if (event.getEntity() instanceof Player player) {
            updateWaterImmunityStatus(player);
        }
    }

    private void updateWaterImmunityStatus(Player player) {
        if (player == null)
            return;

        boolean hasWaterRing = hasRingInInventory(player);
        UUID uuid = player.getPlayerRef().getUuid();
        boolean wasImmune = waterImmunePlayers.contains(uuid);

        if (hasWaterRing && !wasImmune) {
            // Check if WaterRing is disabled
            if (ModConfig.getInstance() != null && ModConfig.getInstance().enabled != null
                    && !ModConfig.getInstance().enabled.waterRing) {
                player.sendMessage(com.hypixel.hytale.server.core.Message
                        .raw("[WaterRing] DISABLED by server").color(Color.RED));
                return;
            }

            waterImmunePlayers.add(uuid);
            player.sendMessage(com.hypixel.hytale.server.core.Message
                    .raw("[WaterRing] The ring feels cool and moist... you can't drown!"));
            Log.info(plugin,
                    "[WaterRing] " + player.getPlayerRef().getUsername() + " equipped water ring (immunity enabled)");
        } else if (!hasWaterRing && wasImmune) {
            waterImmunePlayers.remove(uuid);
            player.sendMessage(com.hypixel.hytale.server.core.Message
                    .raw("[WaterRing] Your lungs feel tight again. Be careful underwater!"));
            Log.info(plugin,
                    "[WaterRing] " + player.getPlayerRef().getUsername() + " removed water ring (immunity disabled)");
        }
    }

    private boolean hasRingInInventory(Player player) {
        return RingUtils.hasRing(player, WATER_RING_ITEM_ID);
    }

    public Set<UUID> getWaterImmunePlayers() {
        return waterImmunePlayers;
    }
}
