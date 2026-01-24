package com.tiffy.flyring;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

/**
 * Utility class for ring-related checks.
 */
public class RingUtils {

    /**
     * Checks if the player has the specified ring in any of their inventory
     * containers
     * (Hotbar, Storage, Backpack, Armor, Utility).
     *
     * @param player the player to check
     * @param ringId the ID of the ring to look for
     * @return true if the player has the ring, false otherwise
     */
    public static boolean hasRing(Player player, String ringId) {
        if (player == null)
            return false;

        Inventory inv = player.getInventory();
        if (inv == null)
            return false;

        // Check all standard containers
        if (checkContainer(inv.getHotbar(), ringId))
            return true;
        if (checkContainer(inv.getStorage(), ringId))
            return true;

        // Check backpack
        try {
            if (checkContainer(inv.getBackpack(), ringId))
                return true;
        } catch (Throwable ignored) {
            // Some entities might not have a backpack
        }

        // Check armor/jewelry slots
        try {
            if (checkContainer(inv.getArmor(), ringId))
                return true;
        } catch (Throwable ignored) {
        }

        // Check utility slots
        try {
            if (checkContainer(inv.getUtility(), ringId))
                return true;
        } catch (Throwable ignored) {
        }

        return false;
    }

    /**
     * Helper to check a specific container for an item.
     */
    private static boolean checkContainer(ItemContainer container, String itemId) {
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
}
