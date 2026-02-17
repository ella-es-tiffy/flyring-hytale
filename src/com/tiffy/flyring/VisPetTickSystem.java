package com.tiffy.flyring;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.math.vector.Vector3d;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import javax.annotation.Nonnull;

public class VisPetTickSystem extends EntityTickingSystem<EntityStore> {

    private static final Logger LOG = Logger.getLogger("VisPet");
    private static final double TELEPORT_DISTANCE = 15.0;

    private final ComponentType<EntityStore, VisPetComponent> petComponentType;
    private final ConcurrentHashMap<UUID, Ref<EntityStore>> ownerRefs;

    public VisPetTickSystem(ComponentType<EntityStore, VisPetComponent> petComponentType,
            ConcurrentHashMap<UUID, Ref<EntityStore>> ownerRefs) {
        this.petComponentType = petComponentType;
        this.ownerRefs = ownerRefs;
    }

    @Override
    @Nonnull
    public Query<EntityStore> getQuery() {
        return Query.and(NPCEntity.getComponentType(), petComponentType);
    }

    @Override
    public void tick(float delta, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
            @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> buffer) {
        try {
            // Remove orphaned NPCs (no active owner = leftover from previous session)
            VisPetComponent petComp = chunk.getComponent(index, petComponentType);
            UUID checkUuid = (petComp != null) ? petComp.getOwnerUuid() : null;
            if (checkUuid == null || !ownerRefs.containsKey(checkUuid)) {
                Ref<EntityStore> orphanRef = chunk.getReferenceTo(index);
                buffer.removeEntity(orphanRef, RemoveReason.REMOVE);
                LOG.info("[VisPet] Removed orphaned pet" +
                        (checkUuid != null ? " for " + checkUuid : ""));
                return;
            }

            // Slot 0 clearing every tick (instant - no delay)
            NPCEntity npc = chunk.getComponent(index, NPCEntity.getComponentType());
            if (npc != null) {
                ItemContainer hotbar = npc.getInventory().getHotbar();
                ItemStack heldItem = hotbar.getItemStack((short) 0);
                if (!ItemStack.isEmpty(heldItem)) {
                    short capacity = hotbar.getCapacity();
                    for (short s = 1; s < capacity; s++) {
                        if (ItemStack.isEmpty(hotbar.getItemStack(s))) {
                            hotbar.setItemStackForSlot(s, heldItem);
                            hotbar.setItemStackForSlot((short) 0, null);
                            break;
                        }
                    }
                }
            }

            // Teleport pet to owner if stuck (> 15 blocks away)
            Ref<EntityStore> ownerRef = ownerRefs.get(checkUuid);
            if (ownerRef != null && ownerRef.isValid()) {
                TransformComponent petTransform = chunk.getComponent(index, TransformComponent.getComponentType());
                TransformComponent ownerTransform = store.getComponent(ownerRef, TransformComponent.getComponentType());
                if (petTransform != null && ownerTransform != null) {
                    Vector3d petPos = petTransform.getPosition();
                    Vector3d ownerPos = ownerTransform.getPosition();
                    double dx = petPos.getX() - ownerPos.getX();
                    double dy = petPos.getY() - ownerPos.getY();
                    double dz = petPos.getZ() - ownerPos.getZ();
                    double distSq = dx * dx + dy * dy + dz * dz;
                    if (distSq > TELEPORT_DISTANCE * TELEPORT_DISTANCE) {
                        petTransform.setPosition(new Vector3d(ownerPos.getX() + 1, ownerPos.getY(), ownerPos.getZ() + 1));
                    }
                }
            }

            // Follow + Item pickup handled by native NPC AI (requires Adventure mode)
        } catch (Exception e) {
            LOG.severe("[ERR-1012] VisPetTickSystem tick: " + e.getMessage());
        }
    }
}
