package com.tiffy.flyring;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nullable;

/**
 * RingLootSystem - Handles ring drops from NPC deaths.
 *
 * Uses NPC-centric config: each NPC maps directly to its ring + drop rates.
 * No intermediate mapping needed - config.loot.npcs IS the lookup table.
 */
public class RingLootSystem {

    private static final String FRAGMENT_ITEM = "Ingredient_Stud_Iron";
    private final IllegalRings plugin;

    public RingLootSystem(IllegalRings plugin) {
        this.plugin = plugin;
        logMappings();
    }

    /**
     * Logs the current NPC-to-Ring mappings from config.
     * Called at startup and can be called after config refresh.
     */
    public void logMappings() {
        ModConfig.Config config = ModConfig.getInstance();
        if (config == null || config.loot == null || !config.loot.enabled) {
            Log.info(plugin, "[RingLoot] Loot system disabled in config");
            return;
        }

        int count = config.loot.npcs != null ? config.loot.npcs.size() : 0;
        Log.info(plugin, "[RingLoot] Loaded " + count + " NPC loot entries");
    }

    /**
     * Maps ring type string to actual item ID.
     */
    private String getRingItemId(String ringType) {
        return switch (ringType.toLowerCase()) {
            case "fly" -> "Jewelry_Fly_Ring";
            case "fire" -> "Jewelry_Fire_Ring";
            case "water" -> "Jewelry_Water_Ring";
            case "heal" -> "Jewelry_Heal_Ring";
            case "peaceful" -> "Jewelry_Peacefull_Ring";
            case "gaia" -> "Jewelry_Gaia_Medallion";
            default -> null;
        };
    }

    /**
     * Called when an NPC dies (detected via damage system).
     * @param killerRef The player who killed the NPC (for sound playback), can be null
     */
    public void onNpcDeath(String roleName, Vector3d position, Store<EntityStore> store, @Nullable Ref<EntityStore> killerRef) {
        ModConfig.Config config = ModConfig.getInstance();
        if (config == null || config.loot == null || !config.loot.enabled || config.loot.npcs == null) {
            return;
        }

        boolean debug = config.debugLogging;

        if (roleName == null || roleName.isEmpty()) {
            if (debug) Log.info(plugin, "[RingLoot] NPC has no role name");
            return;
        }

        if (debug) Log.info(plugin, "[RingLoot] Processing death: " + roleName);

        ThreadLocalRandom random = ThreadLocalRandom.current();
        boolean found = false;

        // Iterate all entries - same NPC can drop multiple rings
        for (ModConfig.NpcLootEntry npcLoot : config.loot.npcs) {
            if (npcLoot.npc == null || !npcLoot.npc.equals(roleName) || npcLoot.ring == null) continue;
            found = true;

            // 1. Check for direct ring drop
            if (random.nextDouble() < npcLoot.directDropRate) {
                String ringItemId = getRingItemId(npcLoot.ring);
                if (ringItemId != null) {
                    LootDropTickSystem.queueDrop(ringItemId, 1, position, killerRef, true);
                    Log.info(plugin, "[RingLoot] RING DROP! " + roleName + " dropped " + ringItemId);
                    continue;  // Got ring for this entry, skip fragment but check next entry
                }
            }

            // 2. Check for fragment drop (hardcoded Iron Stud)
            if (random.nextDouble() < npcLoot.fragmentDropRate) {
                LootDropTickSystem.queueDrop(FRAGMENT_ITEM, 1, position, killerRef, false);
                if (debug) {
                    Log.info(plugin, "[RingLoot] FRAGMENT DROP! " + roleName + " dropped " + FRAGMENT_ITEM);
                }
            }
        }

        if (!found && debug) {
            Log.info(plugin, "[RingLoot] NPC " + roleName + " not in loot table");
        }
    }

    /**
     * Check if loot system is enabled.
     */
    public boolean isEnabled() {
        ModConfig.Config config = ModConfig.getInstance();
        return config != null && config.loot != null && config.loot.enabled;
    }

    /**
     * Get the number of registered NPC loot entries.
     */
    public int getMappingCount() {
        ModConfig.Config config = ModConfig.getInstance();
        if (config == null || config.loot == null || config.loot.npcs == null) return 0;
        return config.loot.npcs.size();
    }
}
