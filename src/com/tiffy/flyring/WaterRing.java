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
            plugin.getLogger().atInfo().log("WaterRing handler initialized.");
        }
    }

    private void setup() {
        // Event Listeners
        plugin.getEventRegistry().registerGlobal(LivingEntityInventoryChangeEvent.class, this::onInventoryChange);
        plugin.getEventRegistry().registerGlobal(PlayerConnectEvent.class, this::onPlayerConnect);
        plugin.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, this::onPlayerDisconnect);

        plugin.getLogger().atInfo().log("WaterRing handler initialized with damage filtering!");
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
            waterImmunePlayers.add(uuid);
            player.sendMessage(com.hypixel.hytale.server.core.Message
                    .raw("[WaterRing] The ring feels cool and moist... you can't drown!"));
            plugin.getLogger().atInfo().log(
                    "[WaterRing] " + player.getPlayerRef().getUsername() + " equipped water ring (immunity enabled)");
        } else if (!hasWaterRing && wasImmune) {
            waterImmunePlayers.remove(uuid);
            player.sendMessage(com.hypixel.hytale.server.core.Message
                    .raw("[WaterRing] Your lungs feel tight again. Be careful underwater!"));
            plugin.getLogger().atInfo().log(
                    "[WaterRing] " + player.getPlayerRef().getUsername() + " removed water ring (immunity disabled)");
        }
    }

    private boolean hasRingInInventory(Player player) {
        Inventory inv = player.getInventory();
        if (inv == null) {
            return false;
        }

        return checkContainerForItem(inv.getHotbar(), WATER_RING_ITEM_ID) ||
                checkContainerForItem(inv.getStorage(), WATER_RING_ITEM_ID);
    }

    private boolean checkContainerForItem(com.hypixel.hytale.server.core.inventory.container.ItemContainer container,
            String itemId) {
        if (container == null)
            return false;

        for (short i = 0; i < container.getCapacity(); i++) {
            ItemStack stack = container.getItemStack(i);
            if (stack != null && !stack.isEmpty()) {
                if (itemId.equals(stack.getItemId())) {
                    return true;
                }
            }
        }
        return false;
    }

    public Set<UUID> getWaterImmunePlayers() {
        return waterImmunePlayers;
    }
}
