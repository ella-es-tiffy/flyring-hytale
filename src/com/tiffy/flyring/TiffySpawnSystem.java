package com.tiffy.flyring;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;

import javax.annotation.Nonnull;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TiffySpawnSystem - Spawns Tiffy NPC in Forgotten Temple instances.
 * Spawns once per world instance that contains "Forgotten_Temple" in its name.
 */
public class TiffySpawnSystem extends EntityTickingSystem<EntityStore> {

    private final IllegalRings plugin;

    // Track which worlds we've already spawned in
    private static final Set<String> spawnedWorlds = ConcurrentHashMap.newKeySet();

    // Tiffy's spawn position in Forgotten Temple
    private static final double SPAWN_X = 4994.0;
    private static final double SPAWN_Y = 157.0;
    private static final double SPAWN_Z = 4989.0;

    public TiffySpawnSystem(IllegalRings plugin) {
        this.plugin = plugin;
    }

    @Override
    @Nonnull
    public Query<EntityStore> getQuery() {
        return Query.and(Player.getComponentType());
    }

    @Override
    public void tick(float delta, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
            @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> buffer) {
        try {
            // Check if spawning is enabled in config
            ModConfig.Config cfg = ModConfig.getInstance();
            if (cfg != null && !cfg.spawnTiffyNpc) {
                return; // Disabled in config - server owner uses /npc spawn manually
            }

            // Get the world from store
            EntityStore entityStore = (EntityStore) store.getExternalData();
            if (entityStore == null)
                return;

            World world = entityStore.getWorld();
            if (world == null)
                return;

            String worldName = world.getName();
            if (worldName == null)
                return;

            // Only spawn in Forgotten Temple instances
            if (!worldName.contains("Forgotten_Temple") && !worldName.contains("forgotten_temple")) {
                return;
            }

            // Check if we already spawned in this world
            if (spawnedWorlds.contains(worldName)) {
                return;
            }

            // Mark this world as spawned
            if (!spawnedWorlds.add(worldName)) {
                return; // Another thread added it first
            }

            // Spawn Tiffy at the configured position
            buffer.run(s -> {
                try {
                    Vector3d position = new Vector3d(SPAWN_X, SPAWN_Y, SPAWN_Z);
                    Vector3f rotation = Vector3f.ZERO;

                    NPCPlugin.get().spawnNPC(s, "Tiffy_Merchant", null, position, rotation);

                    Log.info(plugin, "[Tiffy] Spawned in world '" + worldName + "' at " + SPAWN_X + ", " + SPAWN_Y
                            + ", " + SPAWN_Z);
                } catch (Exception e) {
                    Log.info(plugin, "[Tiffy] Failed to spawn in '" + worldName + "': " + e.getMessage());
                    spawnedWorlds.remove(worldName); // Allow retry
                }
            });
        } catch (Exception e) {
            // Ignore - world not ready yet
        }
    }
}
