package com.tiffy.flyring;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.DespawnComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * PedestalDisplaySystem - Spawns and manages display item entities above pedestals.
 *
 * When a Ring_Display item is placed in a pedestal, this system spawns a
 * corresponding item entity floating above the pedestal block. When the
 * display item is removed, the entity is despawned.
 */
public class PedestalDisplaySystem extends EntityTickingSystem<EntityStore> {

    private final IllegalRings plugin;

    // Track active display entities: "x,y,z" -> entity ref
    private static final Map<String, Ref<EntityStore>> activeDisplays = new ConcurrentHashMap<>();

    // Pending spawn/despawn operations (thread-safe queue)
    private static final ConcurrentLinkedQueue<DisplayOp> pendingOps = new ConcurrentLinkedQueue<>();

    // Tick deduplication
    private static final AtomicLong lastProcessedTick = new AtomicLong(-1);
    private static long currentTick = 0;

    public static class DisplayOp {
        public enum Type { SPAWN, DESPAWN }
        public final Type type;
        public final int x, y, z;
        public final String itemId;

        public DisplayOp(Type type, int x, int y, int z, String itemId) {
            this.type = type;
            this.x = x;
            this.y = y;
            this.z = z;
            this.itemId = itemId;
        }
    }

    public PedestalDisplaySystem(IllegalRings plugin) {
        this.plugin = plugin;
    }

    /**
     * Queue a display entity spawn above a pedestal.
     * Safe to call from any thread.
     */
    public static void queueSpawn(int x, int y, int z, String itemId) {
        pendingOps.add(new DisplayOp(DisplayOp.Type.SPAWN, x, y, z, itemId));
    }

    /**
     * Queue removal of a display entity above a pedestal.
     * Safe to call from any thread.
     */
    public static void queueDespawn(int x, int y, int z) {
        pendingOps.add(new DisplayOp(DisplayOp.Type.DESPAWN, x, y, z, null));
    }

    private static String posKey(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    @Override
    @Nonnull
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public void tick(float delta, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                     @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> buffer) {
        currentTick++;
        long last = lastProcessedTick.get();
        if (last >= currentTick) return;
        if (!lastProcessedTick.compareAndSet(last, currentTick)) return;

        if (pendingOps.isEmpty()) return;

        DisplayOp op;
        while ((op = pendingOps.poll()) != null) {
            String key = posKey(op.x, op.y, op.z);

            if (op.type == DisplayOp.Type.SPAWN) {
                // Don't double-spawn
                Ref<EntityStore> existing = activeDisplays.get(key);
                if (existing != null && existing.isValid()) continue;

                spawnDisplay(op, store, buffer, key);
            } else {
                // DESPAWN
                Ref<EntityStore> ref = activeDisplays.remove(key);
                if (ref != null && ref.isValid()) {
                    buffer.removeEntity(ref, RemoveReason.REMOVE);
                    boolean debug = ModConfig.getInstance() != null && ModConfig.getInstance().debugLogging;
                    if (debug) {
                        Log.info(plugin, "[PedestalDisplay] Removed display at " + key);
                    }
                }
            }
        }
    }

    private void spawnDisplay(DisplayOp op, Store<EntityStore> store,
                              CommandBuffer<EntityStore> buffer, String key) {
        try {
            ItemStack stack = new ItemStack(op.itemId, 1);

            // Spawn at center of block, 1 block above pedestal
            Vector3d position = new Vector3d(op.x + 0.5, op.y + 1.0, op.z + 0.5);

            Holder<EntityStore> holder = ItemComponent.generateItemDrop(
                (ComponentAccessor<EntityStore>) store,
                stack,
                position,
                Vector3f.ZERO,
                0f, 0f, 0f  // No velocity
            );

            // Prevent pickup
            ItemComponent itemComponent = (ItemComponent) holder.getComponent(ItemComponent.getComponentType());
            if (itemComponent != null) {
                itemComponent.setPickupDelay(Float.MAX_VALUE);
            }

            // Remove DespawnComponent to prevent automatic despawn
            holder.removeComponent(DespawnComponent.getComponentType());

            // Add to world and track the ref
            Ref<EntityStore> ref = buffer.addEntity(holder, AddReason.SPAWN);
            activeDisplays.put(key, ref);

            boolean debug = ModConfig.getInstance() != null && ModConfig.getInstance().debugLogging;
            if (debug) {
                Log.info(plugin, "[PedestalDisplay] Spawned '" + op.itemId + "' above " + key);
            }
        } catch (Exception e) {
            Log.info(plugin, "[PedestalDisplay] Error spawning at " + key + ": " + e.getMessage());
        }
    }
}
