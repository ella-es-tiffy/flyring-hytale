package com.tiffy.flyring;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.component.Ref;

import java.util.UUID;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class FireRingDamageSystem extends DamageEventSystem {

    private final Set<UUID> fireImmunePlayers;

    public FireRingDamageSystem(IllegalRings plugin, Set<UUID> fireImmunePlayers) {
        this.fireImmunePlayers = fireImmunePlayers;
    }

    @Override
    @Nonnull
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    @Nullable
    public SystemGroup<EntityStore> getGroup() {
        DamageModule module = DamageModule.get();
        return (module != null) ? module.getFilterDamageGroup() : null;
    }

    @Override
    public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> chunk, @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> buffer, @Nonnull Damage event) {
        try {
            Ref<EntityStore> ref = chunk.getReferenceTo(index);
            UUIDComponent uuidComp = (UUIDComponent) store.getComponent(ref, UUIDComponent.getComponentType());

            if (uuidComp != null) {
                UUID uuid = uuidComp.getUuid();

                if (fireImmunePlayers.contains(uuid)) {
                    DamageCause cause = event.getCause();
                    String causeId = (cause != null) ? cause.getId().toLowerCase() : "unknown";

                    if (causeId.contains("fire") || causeId.contains("lava") || causeId.contains("magma")
                            || causeId.contains("burn") || causeId.contains("hot")) {
                        // Set amount to 0 and cancel the event
                        event.setAmount(0.0f);
                        event.setCancelled(true);
                    }
                }
            }
        } catch (Exception e) {
            // Robust skip
        }
    }
}
