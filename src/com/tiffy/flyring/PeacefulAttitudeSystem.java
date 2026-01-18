package com.tiffy.flyring;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.blackboard.Blackboard;
import com.hypixel.hytale.server.npc.blackboard.view.attitude.AttitudeView;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PeacefulAttitudeSystem - Installs the PeacefulAttitudeProvider into the
 * Blackboard.
 */
public class PeacefulAttitudeSystem extends EntityTickingSystem<EntityStore> {

    private final PeacefullRing handler;
    private final Set<AttitudeView> installedViews = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final PeacefulAttitudeProvider attitudeProvider;

    public PeacefulAttitudeSystem(PeacefullRing handler) {
        this.handler = handler;
        this.attitudeProvider = new PeacefulAttitudeProvider(handler);
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public void tick(float delta, int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
            CommandBuffer<EntityStore> buffer) {
        // We only need to run this logic once per tick per world, or even less
        // frequently.
        // running for every entity is wasteful, but required if we rely on
        // EntityTickingSystem for injection.
        // We can optimize by checking only once per chunk or similar, but checking
        // Blackboard existence is fast.

        try {
            // Get Blackboard resource from the store (World)
            Blackboard blackboard = store.getResource(Blackboard.getResourceType());

            if (blackboard != null) {
                // Install provider for all views
                blackboard.forEachView(AttitudeView.class, view -> {
                    if (installedViews.add(view)) {
                        // Register provider with priority 0
                        view.registerProvider(0, attitudeProvider);
                    }
                });
            }
        } catch (Exception e) {
            // Ignore errors
        }
    }
}
