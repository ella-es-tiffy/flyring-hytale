package com.tiffy.flyring;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * PedestalBlockSystem - Tracks pedestal placement and breaking.
 *
 * Registers pedestals when placed by a player and unregisters when broken.
 * Uses PedestalRegistry for persistence.
 */
public class PedestalBlockSystem {

    private static final String ALTAR_DARK_ID = "Ring_Altar_Dark";
    private static final String ALTAR_LIGHT_ID = "Ring_Altar_Light";

    private static boolean isAltar(String id) {
        return ALTAR_DARK_ID.equals(id) || ALTAR_LIGHT_ID.equals(id);
    }

    private final IllegalRings plugin;

    public PedestalBlockSystem(IllegalRings plugin) {
        this.plugin = plugin;
    }

    /**
     * System for handling PlaceBlockEvent
     */
    public static class PlaceSystem extends EntityEventSystem<EntityStore, PlaceBlockEvent> {

        private final IllegalRings plugin;

        public PlaceSystem(IllegalRings plugin) {
            super(PlaceBlockEvent.class);
            this.plugin = plugin;
        }

        @Override
        @Nonnull
        public Query<EntityStore> getQuery() {
            return Archetype.of(PlayerRef.getComponentType());
        }

        @Override
        public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                           @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> buffer,
                           @Nonnull PlaceBlockEvent event) {
            try {
                ItemStack item = event.getItemInHand();
                if (item == null) return;

                String itemId = item.getItemId();
                if (!isAltar(itemId)) return;

                Vector3i pos = event.getTargetBlock();
                if (pos == null) return;

                // Get player info from the chunk/entity
                Ref<EntityStore> ref = chunk.getReferenceTo(index);
                if (ref == null || !ref.isValid()) return;

                UUIDComponent uuidComp = (UUIDComponent) store.getComponent(ref, UUIDComponent.getComponentType());
                Player player = (Player) store.getComponent(ref, Player.getComponentType());

                if (uuidComp == null) return;

                UUID playerUuid = uuidComp.getUuid();
                String playerName = player != null ? RingUtils.getUsername(player) : "Unknown";

                // Register the pedestal
                PedestalRegistry.register(pos.x, pos.y, pos.z, playerUuid, playerName);

                boolean debug = ModConfig.getInstance() != null && ModConfig.getInstance().debugLogging;
                if (debug) {
                    Log.info(plugin, "[Pedestal] Registered at " + pos.x + "," + pos.y + "," + pos.z + " by " + playerName);
                }
            } catch (Exception e) {
                // Silent fail
            }
        }
    }

    /**
     * System for handling BreakBlockEvent
     */
    public static class BreakSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {

        private final IllegalRings plugin;

        public BreakSystem(IllegalRings plugin) {
            super(BreakBlockEvent.class);
            this.plugin = plugin;
        }

        @Override
        @Nonnull
        public Query<EntityStore> getQuery() {
            return Archetype.of(PlayerRef.getComponentType());
        }

        @Override
        public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                           @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> buffer,
                           @Nonnull BreakBlockEvent event) {
            try {
                // Check if this is a pedestal being broken
                String blockTypeId = event.getBlockType() != null ? event.getBlockType().getId() : null;

                // Debug: always log what block type is being broken
                boolean debug = ModConfig.getInstance() != null && ModConfig.getInstance().debugLogging;
                if (debug) {
                    Log.info(plugin, "[Pedestal] BreakBlockEvent - blockTypeId: " + blockTypeId);
                }

                if (blockTypeId == null) return;

                // BlockType ID should match the pedestal
                if (!isAltar(blockTypeId)) return;

                Vector3i pos = event.getTargetBlock();
                if (pos == null) return;

                // Unregister the pedestal
                if (PedestalRegistry.exists(pos.x, pos.y, pos.z)) {
                    PedestalRegistry.unregister(pos.x, pos.y, pos.z);

                    // Also remove display entity if present
                    PedestalDisplaySystem.queueDespawn(pos.x, pos.y, pos.z);

                    if (debug) {
                        Log.info(plugin, "[Pedestal] Unregistered at " + pos.x + "," + pos.y + "," + pos.z);
                    }
                }
            } catch (Exception e) {
                // Silent fail
            }
        }
    }
}
