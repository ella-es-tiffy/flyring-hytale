package com.tiffy.flyring;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

/**
 * Utility class for ring-related checks.
 */
public class RingUtils {

    private static final String GAIA_MEDALLION_ID = "Jewelry_Gaia_Medallion";

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

        // Gaia Medallion = All rings active
        if (hasItem(player, inv, GAIA_MEDALLION_ID))
            return true;

        // Check all standard containers
        if (checkContainer(inv.getHotbar(), ringId))
            return true;
        if (checkContainer(inv.getStorage(), ringId))
            return true;

        // Check backpack (per-player setting)
        try {
            java.util.UUID uuid = player.getPlayerRef().getUuid();
            if (PlayerSettings.isBackpackEnabled(uuid) && checkContainer(inv.getBackpack(), ringId))
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

    /**
     * Check if player has a specific item (used for Gaia Medallion check)
     */
    private static boolean hasItem(Player player, Inventory inv, String itemId) {
        // Check all containers
        if (checkContainer(inv.getHotbar(), itemId)) return true;
        if (checkContainer(inv.getStorage(), itemId)) return true;

        // Backpack (per-player setting)
        try {
            java.util.UUID uuid = player.getPlayerRef().getUuid();
            if (PlayerSettings.isBackpackEnabled(uuid) && checkContainer(inv.getBackpack(), itemId))
                return true;
        } catch (Throwable ignored) {}

        // Armor/Jewelry
        try {
            if (checkContainer(inv.getArmor(), itemId)) return true;
        } catch (Throwable ignored) {}

        // Utility
        try {
            if (checkContainer(inv.getUtility(), itemId)) return true;
        } catch (Throwable ignored) {}

        return false;
    }
}
