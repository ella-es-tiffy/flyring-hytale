package com.tiffy.flyring;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.ColorLight;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.component.DynamicLight;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.Constants;

import java.awt.Color;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Utility class for ring-related checks.
 */
public class RingUtils {

    private static final String GAIA_MEDALLION_ID = "Jewelry_Gaia_Medallion";
    private static final Set<UUID> activeNightVisionPlayers = new HashSet<>();

    public static class RingSnapshot {
        public boolean hasFly;
        public boolean hasFire;
        public boolean hasWater;
        public boolean hasHeal;
        public boolean hasPeaceful;
        public boolean hasGaia;
        public int triggerItemCount;

        public boolean hasRing(String ringId) {
            if (hasGaia)
                return true;
            return switch (ringId) {
                case "Jewelry_Fly_Ring" -> hasFly;
                case "Jewelry_Fire_Ring" -> hasFire;
                case "Jewelry_Water_Ring" -> hasWater;
                case "Jewelry_Heal_Ring" -> hasHeal;
                case "Jewelry_Peacefull_Ring" -> hasPeaceful;
                default -> false;
            };
        }
    }

    /**
     * Modern replacement for player.getUuid() which is deprecated.
     * Uses the ECS UUIDComponent.
     */
    public static UUID getUUID(Player player) {
        if (player == null || player.getReference() == null)
            return null;
        UUIDComponent uuidComp = player.getReference().getStore().getComponent(player.getReference(),
                UUIDComponent.getComponentType());
        return (uuidComp != null) ? uuidComp.getUuid() : null;
    }

    /**
     * Modern replacement for player.getPlayerRef().getUsername() which is
     * deprecated.
     * Uses CommandSender.getDisplayName().
     */
    public static String getUsername(Player player) {
        if (player == null)
            return "Unknown";
        return player.getDisplayName();
    }

    /**
     * Checks if the player has the specified ring in any of their inventory
     * containers
     * (Hotbar, Storage, Backpack, Armor, Utility).
     *
     * @param player the player to check
     * @param ringId the ID of the ring to look for
     * @return true if the player has the ring, false otherwise
     */

    /**
     * Performs a single-pass scan of the player's inventory to find all mod-related
     * items. Also checks pedestals owned by this player.
     */
    public static RingSnapshot getRingSnapshot(Player player) {
        RingSnapshot snapshot = new RingSnapshot();
        if (player == null)
            return snapshot;

        Inventory inv = player.getInventory();
        if (inv == null)
            return snapshot;

        String triggerId = "Furniture_Crude_Torch";
        try {
            if (ModConfig.getInstance() != null && ModConfig.getInstance().gameplay != null) {
                triggerId = ModConfig.getInstance().gameplay.nightVisionTriggerItem;
            }
        } catch (Throwable ignored) {
        }

        UUID uuid = getUUID(player);
        boolean backpackEnabled = (uuid != null) && PlayerSettings.isBackpackEnabled(uuid);

        // Scan all containers in one go
        scanContainer(inv.getHotbar(), snapshot, triggerId);
        scanContainer(inv.getStorage(), snapshot, triggerId);
        if (backpackEnabled)
            scanContainer(inv.getBackpack(), snapshot, triggerId);

        try {
            scanContainer(inv.getArmor(), snapshot, triggerId);
        } catch (Throwable ignored) {
        }
        try {
            scanContainer(inv.getUtility(), snapshot, triggerId);
        } catch (Throwable ignored) {
        }

        // Also check pedestals owned by this player
        if (uuid != null) {
            scanPedestals(uuid, snapshot);
        }

        return snapshot;
    }

    /**
     * Scans all VERIFIED pedestals owned by the player and adds their ring effects.
     * Only pedestals with complete multiblock structures grant effects.
     */
    private static void scanPedestals(UUID playerUuid, RingSnapshot snapshot) {
        try {
            String playerUuidStr = playerUuid.toString();
            for (PedestalRegistry.PedestalData pedestal : PedestalRegistry.getAll().values()) {
                if (pedestal.ownerUuid == null || pedestal.item == null || pedestal.item.isEmpty()) {
                    continue;
                }
                if (!playerUuidStr.equals(pedestal.ownerUuid)) {
                    continue;
                }
                // Only verified pedestals grant effects
                if (!pedestal.verified) {
                    continue;
                }
                // This verified pedestal belongs to the player and has an item
                switch (pedestal.item) {
                    case "Jewelry_Fly_Ring" -> snapshot.hasFly = true;
                    case "Jewelry_Fire_Ring" -> snapshot.hasFire = true;
                    case "Jewelry_Water_Ring" -> snapshot.hasWater = true;
                    case "Jewelry_Heal_Ring" -> snapshot.hasHeal = true;
                    case "Jewelry_Peacefull_Ring" -> snapshot.hasPeaceful = true;
                    case "Jewelry_Gaia_Medallion" -> snapshot.hasGaia = true;
                }
            }
        } catch (Throwable ignored) {
            // PedestalRegistry might not be initialized
        }
    }

    private static void scanContainer(ItemContainer container, RingSnapshot snapshot, String triggerId) {
        if (container == null)
            return;

        for (short i = 0; i < container.getCapacity(); i++) {
            ItemStack stack = container.getItemStack(i);
            if (stack != null && !stack.isEmpty()) {
                String id = stack.getItemId();
                if (id == null)
                    continue;

                switch (id) {
                    case "Jewelry_Fly_Ring" -> snapshot.hasFly = true;
                    case "Jewelry_Fire_Ring" -> snapshot.hasFire = true;
                    case "Jewelry_Water_Ring" -> snapshot.hasWater = true;
                    case "Jewelry_Heal_Ring" -> snapshot.hasHeal = true;
                    case "Jewelry_Peacefull_Ring" -> snapshot.hasPeaceful = true;
                    case "Jewelry_Gaia_Medallion" -> snapshot.hasGaia = true;
                    default -> {
                        if (id.equals(triggerId)) {
                            snapshot.triggerItemCount = 1; // For now binary, can be changed to count
                        }
                    }
                }
            }
        }
    }

    /**
     * Check if player should have Night Vision (has ring + trigger item).
     * Only works in Singleplayer - disabled in SMP due to world-wide light effect.
     */
    public static void checkNightVision(Player player, RingSnapshot snapshot) {
        // Night Vision only allowed in Singleplayer (DynamicLight affects all players)
        if (!Constants.SINGLEPLAYER) {
            setNightVision(player, 0);
            return;
        }

        // Check if Night Vision is enabled in config
        boolean nvEnabled = true;
        try {
            if (ModConfig.getInstance() != null && ModConfig.getInstance().gameplay != null) {
                nvEnabled = ModConfig.getInstance().gameplay.nightVisionEnabled;
            }
        } catch (Throwable ignored) {
        }

        if (!nvEnabled) {
            setNightVision(player, 0);
            return;
        }

        boolean hasEffectRing = snapshot.hasPeaceful || snapshot.hasHeal || snapshot.hasGaia;
        boolean shouldHave = hasEffectRing && snapshot.triggerItemCount > 0;

        setNightVision(player, shouldHave ? 1 : 0);
    }

    public static void checkNightVision(Player player) {
        checkNightVision(player, getRingSnapshot(player));
    }

    /**
     * Apply or remove Night Vision effect.
     */
    public static void setNightVision(Player player, int triggerItemCount) {
        UUID uuid = getUUID(player);
        if (uuid == null)
            return;

        Ref<EntityStore> playerRef = player.getReference();
        if (playerRef == null || !playerRef.isValid())
            return;

        Store<EntityStore> store = playerRef.getStore();
        World world = ((EntityStore) store.getExternalData()).getWorld();

        boolean currentlyHas = activeNightVisionPlayers.contains(uuid);

        world.execute(() -> {
            if (triggerItemCount > 0) {
                // Trigger item present = Full Bright
                ColorLight nightVisionLight = new ColorLight((byte) 15, (byte) 255, (byte) 255, (byte) 255);
                store.putComponent(playerRef, DynamicLight.getComponentType(), new DynamicLight(nightVisionLight));

                if (!currentlyHas) {
                    activeNightVisionPlayers.add(uuid);
                    player.sendMessage(
                            Message.raw("[Rings] Trigger item detected: Night Vision ACTIVE").color(Color.CYAN));
                }
            } else {
                if (currentlyHas) {
                    activeNightVisionPlayers.remove(uuid);
                    store.tryRemoveComponent(playerRef, DynamicLight.getComponentType());
                    player.sendMessage(Message.raw("[Rings] Trigger item removed: Night Vision OFF").color(Color.GRAY));
                }
            }
        });
    }

    public static void cleanupNightVision(UUID uuid) {
        activeNightVisionPlayers.remove(uuid);
    }

    /**
     * @deprecated Use RingSnapshot where possible for performance.
     */
    @Deprecated
    public static boolean hasRing(Player player, String ringId) {
        return getRingSnapshot(player).hasRing(ringId);
    }
}
