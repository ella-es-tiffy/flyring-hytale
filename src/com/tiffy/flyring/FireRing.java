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
 * FireRing - Handler for the Fire Ring.
 * Manages the list of immune players based on inventory contents.
 * Damage filtering is performed by FireRingDamageSystem.
 */
public class FireRing {

    private static final String FIRE_RING_ITEM_ID = "Jewelry_Fire_Ring";

    private final JavaPlugin plugin;
    private final Set<UUID> fireImmunePlayers = ConcurrentHashMap.newKeySet();

    public FireRing(JavaPlugin plugin, boolean registerEvents) {
        this.plugin = plugin;

        if (registerEvents) {
            setup();
        } else {
            plugin.getLogger().atInfo().log("FireRing handler initialized.");
        }
    }

    private void setup() {
        // Event Listeners
        plugin.getEventRegistry().registerGlobal(LivingEntityInventoryChangeEvent.class, this::onInventoryChange);
        plugin.getEventRegistry().registerGlobal(PlayerConnectEvent.class, this::onPlayerConnect);
        plugin.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, this::onPlayerDisconnect);

        plugin.getLogger().atInfo().log("FireRing handler initialized with damage filtering!");
    }

    public void shutdown() {
        fireImmunePlayers.clear();
    }

    public void onPlayerConnect(PlayerConnectEvent event) {
        // Connection handled via inventory trigger or connect trigger
    }

    public void onPlayerDisconnect(PlayerDisconnectEvent event) {
        UUID uuid = event.getPlayerRef().getUuid();
        fireImmunePlayers.remove(uuid);
    }

    public void onInventoryChange(LivingEntityInventoryChangeEvent event) {
        if (event.getEntity() instanceof Player player) {
            updateFireImmunityStatus(player);
        }
    }

    private void updateFireImmunityStatus(Player player) {
        if (player == null)
            return;

        boolean hasFireRing = hasRingInInventory(player);
        UUID uuid = player.getPlayerRef().getUuid();
        boolean wasImmune = fireImmunePlayers.contains(uuid);

        if (hasFireRing && !wasImmune) {
            fireImmunePlayers.add(uuid);
            player.sendMessage(com.hypixel.hytale.server.core.Message
                    .raw("[FireRing] The ring glows with heat... you're immune to fire!"));
            plugin.getLogger().atInfo().log(
                    "[FireRing] " + player.getPlayerRef().getUsername() + " equipped fire ring (immunity enabled)");
        } else if (!hasFireRing && wasImmune) {
            fireImmunePlayers.remove(uuid);
            player.sendMessage(com.hypixel.hytale.server.core.Message
                    .raw("[FireRing] The flame fades. You're vulnerable again."));
            plugin.getLogger().atInfo().log(
                    "[FireRing] " + player.getPlayerRef().getUsername() + " removed fire ring (immunity disabled)");
        }
    }

    private boolean hasRingInInventory(Player player) {
        Inventory inv = player.getInventory();
        if (inv == null) {
            return false;
        }

        return checkContainerForItem(inv.getHotbar(), FIRE_RING_ITEM_ID) ||
                checkContainerForItem(inv.getStorage(), FIRE_RING_ITEM_ID);
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

    public boolean isFireImmune(UUID playerUuid) {
        return fireImmunePlayers.contains(playerUuid);
    }

    public Set<UUID> getFireImmunePlayers() {
        return fireImmunePlayers;
    }
}
