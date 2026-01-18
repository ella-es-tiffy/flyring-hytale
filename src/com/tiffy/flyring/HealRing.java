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
 * HealRing - Handler for the Heal Ring.
 * Detects if a player is wearing the ring.
 * Reactive healing is performed by RingDamageSystem.
 */
public class HealRing {

    private static final String HEAL_RING_ITEM_ID = "Jewelry_Heal_Ring";

    private final JavaPlugin plugin;
    private final Set<UUID> healRingPlayers = ConcurrentHashMap.newKeySet();

    public HealRing(JavaPlugin plugin, boolean registerEvents) {
        this.plugin = plugin;

        if (registerEvents) {
            setup();
        } else {
            plugin.getLogger().atInfo().log("HealRing handler initialized.");
        }
    }

    private void setup() {
        plugin.getEventRegistry().registerGlobal(LivingEntityInventoryChangeEvent.class, this::onInventoryChange);
        plugin.getEventRegistry().registerGlobal(PlayerConnectEvent.class, this::onPlayerConnect);
        plugin.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, this::onPlayerDisconnect);

        plugin.getLogger().atInfo().log("HealRing handler initialized!");
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

    public void onInventoryChange(LivingEntityInventoryChangeEvent event) {
        if (event.getEntity() instanceof Player player) {
            updateHealRingStatus(player);
        }
    }

    private void updateHealRingStatus(Player player) {
        if (player == null)
            return;

        boolean hasHealRing = hasRingInInventory(player);
        UUID uuid = player.getPlayerRef().getUuid();
        boolean wasWearing = healRingPlayers.contains(uuid);

        if (hasHealRing && !wasWearing) {
            // Check if HealRing is disabled
            if (ModConfig.getInstance() != null && ModConfig.getInstance().enabled != null
                    && !ModConfig.getInstance().enabled.healRing) {
                player.sendMessage(com.hypixel.hytale.server.core.Message
                        .raw("§c[HealRing] DISABLED by server"));
                return;
            }

            healRingPlayers.add(uuid);
            player.sendMessage(com.hypixel.hytale.server.core.Message
                    .raw("[HealRing] You feel a surge of life-force! High-speed recovery enabled."));
            plugin.getLogger().atInfo().log(
                    "[HealRing] " + player.getPlayerRef().getUsername() + " equipped heal ring");
        } else if (!hasHealRing && wasWearing) {
            healRingPlayers.remove(uuid);
            player.sendMessage(com.hypixel.hytale.server.core.Message
                    .raw("[HealRing] The life-force fades... recovery back to normal."));
            plugin.getLogger().atInfo().log(
                    "[HealRing] " + player.getPlayerRef().getUsername() + " removed heal ring");
        }
    }

    private boolean hasRingInInventory(Player player) {
        Inventory inv = player.getInventory();
        if (inv == null) {
            return false;
        }

        return checkContainerForItem(inv.getHotbar(), HEAL_RING_ITEM_ID) ||
                checkContainerForItem(inv.getStorage(), HEAL_RING_ITEM_ID);
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

    public Set<UUID> getHealRingPlayers() {
        return healRingPlayers;
    }
}
