package com.tiffy.flyring;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import javax.annotation.Nonnull;

/**
 * Detects toggle effects on players to spawn/despawn VisPet NPCs.
 * Also despawns if the summon item leaves the player's inventory.
 */
public class VisPetToggleSystem extends EntityTickingSystem<EntityStore> {

    private static final Logger LOG = Logger.getLogger("VisPet");

    public static class PetType {
        public final String itemId;
        public final String toggleEffectId;
        public final String activeEffectId;
        public final String npcRole;
        public final String appearance;
        public final float scale;
        public int toggleEffectIndex = -1;
        public int activeEffectIndex = -1;
        public EntityEffect activeEffectAsset = null;

        public PetType(String itemId, String toggleEffectId, String activeEffectId, String npcRole, String appearance,
                float scale) {
            this.itemId = itemId;
            this.toggleEffectId = toggleEffectId;
            this.activeEffectId = activeEffectId;
            this.npcRole = npcRole;
            this.appearance = appearance;
            this.scale = scale;
        }

        public void initIndices() {
            if (toggleEffectIndex == -1) {
                toggleEffectIndex = EntityEffect.getAssetMap().getIndex(toggleEffectId);
                activeEffectIndex = EntityEffect.getAssetMap().getIndex(activeEffectId);
                activeEffectAsset = (EntityEffect) EntityEffect.getAssetMap().getAsset(activeEffectId);
            }
        }
    }

    static final PetType[] PET_TYPES = {
            new PetType("Loot_Fox_Summon", "Loot_Fox_Toggle", "Loot_Fox_Active", "loot_buddy", "Fox", 1.0f),
            new PetType("Loot_Cow_Summon", "Loot_Cow_Toggle", "Loot_Cow_Active", "loot_buddy_cow", "Cow_Undead", 0.5f),
            new PetType("Loot_Sheep_Summon", "Loot_Sheep_Toggle", "Loot_Sheep_Active", "loot_buddy_sheep", "Lamb",
                    1.0f),
            new PetType("Loot_Hound_Summon", "Loot_Hound_Toggle", "Loot_Hound_Active", "loot_buddy_hound",
                    "Hound_Bleached", 1.0f)
    };

    private final ComponentType<EntityStore, VisPetComponent> petComponentType;
    private final ConcurrentHashMap<UUID, Ref<EntityStore>> ownerRefs;
    private final ConcurrentHashMap<UUID, Ref<EntityStore>> npcRefs;
    private final Set<UUID> activeBuddies;
    private final VisPetStorage storage;
    private final AtomicReference<World> worldRef;
    private final ConcurrentHashMap<UUID, World> petWorlds;
    private final ConcurrentHashMap<UUID, PetType> activePetTypes;
    private final Set<UUID> pendingSpawn = ConcurrentHashMap.newKeySet();
    private final Set<UUID> recentlyToggled = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<UUID, Integer> worldChangeGrace = new ConcurrentHashMap<>();
    private static final int WORLD_CHANGE_GRACE_TICKS = 100;
    private final ConcurrentHashMap<UUID, Integer> freePetTickCounter = new ConcurrentHashMap<>();
    private static final int FREE_PET_UPDATE_TICKS = 20; // ~1 second

    public VisPetToggleSystem(
            ComponentType<EntityStore, VisPetComponent> petComponentType,
            ConcurrentHashMap<UUID, Ref<EntityStore>> ownerRefs,
            ConcurrentHashMap<UUID, Ref<EntityStore>> npcRefs,
            Set<UUID> activeBuddies,
            VisPetStorage storage,
            AtomicReference<World> worldRef,
            ConcurrentHashMap<UUID, World> petWorlds,
            ConcurrentHashMap<UUID, PetType> activePetTypes) {
        this.petComponentType = petComponentType;
        this.ownerRefs = ownerRefs;
        this.npcRefs = npcRefs;
        this.activeBuddies = activeBuddies;
        this.storage = storage;
        this.worldRef = worldRef;
        this.petWorlds = petWorlds;
        this.activePetTypes = activePetTypes;
    }

    @Override
    @Nonnull
    public Query<EntityStore> getQuery() {
        return Query.and(Player.getComponentType(), EffectControllerComponent.getComponentType());
    }

    @Override
    public void tick(float delta, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
            @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> buffer) {
        try {
            Player player = chunk.getComponent(index, Player.getComponentType());
            if (player == null) return;

            PlayerRef playerRefComp = chunk.getComponent(index, PlayerRef.getComponentType());
            if (playerRefComp == null) return;
            UUID uuid = playerRefComp.getUuid();
            EffectControllerComponent effectCtrl = chunk.getComponent(index,
                    EffectControllerComponent.getComponentType());
            if (effectCtrl == null) return;

            // Initialize indices if needed
            for (PetType type : PET_TYPES) {
                type.initIndices();
            }

            // Check for toggle effects
            PetType toggledType = null;
            Int2ObjectMap<?> activeEffects = effectCtrl.getActiveEffects();
            if (activeEffects != null) {
                for (PetType type : PET_TYPES) {
                    if (type.toggleEffectIndex != -1 && activeEffects.containsKey(type.toggleEffectIndex)) {
                        toggledType = type;
                        break;
                    }
                }
            }

            if (pendingSpawn.contains(uuid)) return;

            // Handle Toggle
            if (toggledType != null) {
                if (!recentlyToggled.contains(uuid)) {
                    recentlyToggled.add(uuid);

                    if (activeBuddies.contains(uuid)) {
                        LOG.info("[VisPet] Despawning pet for " + uuid);
                        despawnBuddy(uuid, store, buffer);
                        player.sendMessage(Message.raw("Pet dismissed."));
                    } else {
                        World world = player.getWorld();
                        if (world == null) { recentlyToggled.remove(uuid); return; }

                        TransformComponent transform = chunk.getComponent(index, TransformComponent.getComponentType());
                        if (transform == null) { recentlyToggled.remove(uuid); return; }
                        Vector3d pos = new Vector3d(transform.getPosition());
                        Ref<EntityStore> playerRef = chunk.getReferenceTo(index);

                        pendingSpawn.add(uuid);
                        PetType finalToggledType = toggledType;
                        world.execute(() -> {
                            try {
                                Store<EntityStore> worldStore = world.getEntityStore().getStore();
                                doSpawnBuddy(uuid, pos, playerRef, worldStore, player, finalToggledType);
                                petWorlds.put(uuid, world);
                            } finally {
                                pendingSpawn.remove(uuid);
                            }
                        });
                    }
                }
                return;
            }

            recentlyToggled.remove(uuid);

            // World change detection
            if (activeBuddies.contains(uuid)) {
                World currentWorld = player.getWorld();
                World petWorld = petWorlds.get(uuid);
                if (currentWorld != null && petWorld != null && currentWorld != petWorld) {
                    handleWorldChange(uuid, player, currentWorld, petWorld, chunk, index, store);
                    return;
                }
            }

            // Check if buddy should be dismissed (item lost)
            if (activeBuddies.contains(uuid)) {
                Integer grace = worldChangeGrace.get(uuid);
                if (grace != null) {
                    if (grace <= 1) {
                        worldChangeGrace.remove(uuid);
                    } else {
                        worldChangeGrace.put(uuid, grace - 1);
                    }
                } else if (!hasAnySummonItem(player)) {
                    LOG.info("[VisPet] Summon item lost for " + uuid + ", despawning pet.");
                    despawnBuddy(uuid, store, buffer);
                    player.sendMessage(Message.raw("Pet dismissed (item lost)."));
                }

                // Free pet timer tick (~1s interval)
                IllegalRings ir = IllegalRings.getInstance();
                if (ir != null && ir.hasFreePetActive(uuid)) {
                    int counter = freePetTickCounter.getOrDefault(uuid, 0) + 1;
                    if (counter >= FREE_PET_UPDATE_TICKS) {
                        counter = 0;
                        ir.tickFreePetTimer(uuid, player, store, buffer);
                    }
                    freePetTickCounter.put(uuid, counter);
                }
            }
        } catch (Exception e) {
            LOG.severe("[VisPet] Toggle tick error: " + e.getMessage());
        }
    }

    private boolean hasAnySummonItem(Player player) {
        return containsAnyItem(player.getInventory().getHotbar())
                || containsAnyItem(player.getInventory().getStorage());
    }

    private static final String FREE_PET_ITEM_ID = "Loot_Fox_Free";

    private boolean containsAnyItem(ItemContainer container) {
        short capacity = container.getCapacity();
        for (short s = 0; s < capacity; s++) {
            ItemStack stack = container.getItemStack(s);
            if (!ItemStack.isEmpty(stack)) {
                String id = stack.getItemId();
                if (FREE_PET_ITEM_ID.equals(id)) return true;
                for (PetType type : PET_TYPES) {
                    if (type.itemId.equals(id)) return true;
                }
            }
        }
        return false;
    }

    void doSpawnBuddy(UUID ownerUuid, Vector3d pos, Ref<EntityStore> playerRef,
            Store<EntityStore> store, Player player, PetType type) {
        try {
            World storeWorld = ((EntityStore) store.getExternalData()).getWorld();
            LOG.info("[VisPet] doSpawnBuddy: owner=" + ownerUuid + " role=" + type.npcRole
                    + " world=" + (storeWorld != null ? storeWorld.getName() : "null")
                    + " playerWorld=" + (player.getWorld() != null ? player.getWorld().getName() : "null"));

            Vector3f rot = new Vector3f(0, 0, 0);
            int roleIndex = NPCPlugin.get().getIndex(type.npcRole);
            if (roleIndex == -1) {
                LOG.warning("[VisPet] Role '" + type.npcRole + "' not found.");
                return;
            }

            var npcPair = NPCPlugin.get().spawnEntity(store, roleIndex, pos, rot, null, (npc, holder, s) -> {
                npc.setInitialModelScale(type.scale);
                ModelAsset modelAsset = (ModelAsset) ModelAsset.getAssetMap().getAsset(type.appearance);
                if (modelAsset != null) {
                    Model scaledModel = Model.createScaledModel(modelAsset, type.scale);
                    holder.putComponent(PersistentModel.getComponentType(),
                            new PersistentModel(scaledModel.toReference()));
                    holder.putComponent(ModelComponent.getComponentType(), new ModelComponent(scaledModel));
                }
            }, null);
            Ref<EntityStore> npcRef = npcPair.first();
            NPCEntity npc = npcPair.second();

            npc.setInventorySize(54, 0, 0);
            VisPetComponent comp = new VisPetComponent();
            comp.setOwnerUuid(ownerUuid);
            store.putComponent(npcRef, petComponentType, comp);
            ownerRefs.put(ownerUuid, playerRef);
            npcRefs.put(ownerUuid, npcRef);

            storage.loadInventory(ownerUuid, npc.getInventory().getHotbar());
            activeBuddies.add(ownerUuid);
            activePetTypes.put(ownerUuid, type);

            if (type.activeEffectAsset != null) {
                EffectControllerComponent effectCtrl = store.getComponent(playerRef,
                        EffectControllerComponent.getComponentType());
                if (effectCtrl != null) {
                    effectCtrl.addEffect(playerRef, type.activeEffectAsset, 999999f, OverlapBehavior.OVERWRITE,
                            (ComponentAccessor<EntityStore>) store);
                }
            }

            // Notify free pet timer + show remaining time
            IllegalRings ir = IllegalRings.getInstance();
            boolean isFreePet = ir != null && hasFreePetItem(player);
            if (isFreePet) {
                // Ensure the Loot_Fox_Free item has a serial (stamp if shop-bought)
                ensureFreePetSerial(player, ownerUuid, ir);
                ir.onFreePetSpawned(ownerUuid);
                FreePetStorage.FreePetData fpData = ir.getFreePetStorage().load(ownerUuid);
                if (fpData.remainingMs > 0) {
                    player.sendMessage(Message.raw("Pet summoned! Remaining: " + formatDuration(fpData.remainingMs)));
                } else {
                    player.sendMessage(Message.raw("Pet summoned!"));
                }
            } else {
                player.sendMessage(Message.raw("Pet summoned!"));
            }
        } catch (Exception e) {
            LOG.severe("[VisPet] Spawn error: " + e.getMessage());
        }
    }

    private static String formatDuration(long ms) {
        long totalSec = ms / 1000;
        long days = totalSec / 86400;
        long hours = (totalSec % 86400) / 3600;
        long mins = (totalSec % 3600) / 60;
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + mins + "m";
        return mins + "m " + (totalSec % 60) + "s";
    }

    private boolean hasFreePetItem(Player player) {
        ItemContainer[] containers = { player.getInventory().getHotbar(), player.getInventory().getStorage() };
        for (ItemContainer container : containers) {
            if (container == null) continue;
            for (short s = 0; s < container.getCapacity(); s++) {
                ItemStack stack = container.getItemStack(s);
                if (!ItemStack.isEmpty(stack) && FREE_PET_ITEM_ID.equals(stack.getItemId())) return true;
            }
        }
        return false;
    }

    /**
     * Ensures the Loot_Fox_Free item in any inventory container has a serial.
     * Shop-bought items won't have one, so we stamp + create storage entry on first use.
     */
    private void ensureFreePetSerial(Player player, UUID ownerUuid, IllegalRings ir) {
        ItemContainer[] containers = { player.getInventory().getHotbar(), player.getInventory().getStorage() };
        for (ItemContainer container : containers) {
            if (container == null) continue;
            for (short s = 0; s < container.getCapacity(); s++) {
                ItemStack stack = container.getItemStack(s);
                if (ItemStack.isEmpty(stack) || !FREE_PET_ITEM_ID.equals(stack.getItemId())) continue;

                String existingSerial = stack.getFromMetadataOrNull(FreePetStorage.META_SERIAL,
                        com.hypixel.hytale.codec.Codec.STRING);
                if (existingSerial != null) {
                    // Already has a serial - make sure storage matches
                    FreePetStorage.FreePetData fpData = ir.getFreePetStorage().load(ownerUuid);
                    if (!existingSerial.equals(fpData.serial)) {
                        fpData.received = true;
                        if (fpData.serial == null || !fpData.serial.equals(existingSerial)) {
                            fpData.serial = existingSerial;
                            fpData.remainingMs = FreePetStorage.DURATION_MS;
                            ir.getFreePetStorage().save(ownerUuid, fpData);
                        }
                    }
                    return;
                }

                // No serial - stamp one (shop-bought item)
                String serial = UUID.randomUUID().toString();
                ItemStack stamped = stack.withMetadata(FreePetStorage.META_SERIAL,
                        com.hypixel.hytale.codec.Codec.STRING, serial);
                container.setItemStackForSlot(s, stamped);

                FreePetStorage.FreePetData fpData = ir.getFreePetStorage().load(ownerUuid);
                fpData.received = true;
                fpData.serial = serial;
                fpData.remainingMs = FreePetStorage.DURATION_MS;
                ir.getFreePetStorage().save(ownerUuid, fpData);
                LOG.info("[VisPet] Stamped serial " + serial + " on shop-bought Loot_Fox_Free for " + ownerUuid);
                return;
            }
        }
    }

    void despawnBuddy(UUID ownerUuid, Store<EntityStore> store, CommandBuffer<EntityStore> buffer) {
        Ref<EntityStore> npcRef = npcRefs.get(ownerUuid);
        if (npcRef != null) {
            NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
            if (npc != null) {
                storage.saveInventory(ownerUuid, npc.getInventory().getHotbar());
            }
            buffer.removeEntity(npcRef, RemoveReason.REMOVE);
            npcRefs.remove(ownerUuid);
        }

        // Remove active pet effects
        Ref<EntityStore> playerRef = ownerRefs.get(ownerUuid);
        if (playerRef != null) {
            Player p = store.getComponent(playerRef, Player.getComponentType());
            World world = (p != null) ? p.getWorld() : petWorlds.getOrDefault(ownerUuid, worldRef.get());
            if (world != null) {
                world.execute(() -> {
                    try {
                        EffectControllerComponent effectCtrl = store.getComponent(playerRef,
                                EffectControllerComponent.getComponentType());
                        if (effectCtrl != null) {
                            for (PetType type : PET_TYPES) {
                                if (type.activeEffectIndex != -1) {
                                    effectCtrl.removeEffect(playerRef, type.activeEffectIndex,
                                            (ComponentAccessor<EntityStore>) store);
                                }
                            }
                        }
                    } catch (Exception e) {
                        LOG.severe("[ERR-1011] despawnBuddy effect removal: " + e.getMessage());
                    }
                });
            }
        }

        activeBuddies.remove(ownerUuid);
        ownerRefs.remove(ownerUuid);
        petWorlds.remove(ownerUuid);
        activePetTypes.remove(ownerUuid);
        worldChangeGrace.remove(ownerUuid);
        freePetTickCounter.remove(ownerUuid);

        // Notify free pet timer
        IllegalRings ir = IllegalRings.getInstance();
        if (ir != null && ir.hasFreePetActive(ownerUuid)) {
            ir.onFreePetDespawned(ownerUuid);
        }
    }

    private void handleWorldChange(UUID uuid, Player player, World newWorld, World oldWorld,
            ArchetypeChunk<EntityStore> chunk, int index, Store<EntityStore> newStore) {
        if (pendingSpawn.contains(uuid)) return;
        pendingSpawn.add(uuid);

        Ref<EntityStore> oldNpcRef = npcRefs.remove(uuid);
        PetType type = activePetTypes.get(uuid);

        if (type == null || oldNpcRef == null) {
            pendingSpawn.remove(uuid);
            return;
        }

        TransformComponent transform = chunk.getComponent(index, TransformComponent.getComponentType());
        Vector3d pos = (transform != null) ? new Vector3d(transform.getPosition()) : new Vector3d(0, 100, 0);
        Ref<EntityStore> playerRef = chunk.getReferenceTo(index);

        LOG.info("[VisPet] World change for " + uuid + ", moving pet from "
                + oldWorld.getName() + " to " + newWorld.getName());

        worldChangeGrace.put(uuid, WORLD_CHANGE_GRACE_TICKS);

        try {
            oldWorld.execute(() -> {
                try {
                    Store<EntityStore> oldStore = oldWorld.getEntityStore().getStore();
                    NPCEntity npc = oldStore.getComponent(oldNpcRef, NPCEntity.getComponentType());
                    if (npc != null) {
                        storage.saveInventory(uuid, npc.getInventory().getHotbar());
                    }
                    oldStore.removeEntity(oldNpcRef, RemoveReason.REMOVE);
                } catch (Exception e) {
                    LOG.warning("[VisPet] World change - old cleanup error: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            LOG.warning("[VisPet] Could not schedule old world cleanup: " + e.getMessage());
        }

        newWorld.execute(() -> {
            try {
                Store<EntityStore> worldStore = newWorld.getEntityStore().getStore();
                doSpawnBuddy(uuid, pos, playerRef, worldStore, player, type);
                petWorlds.put(uuid, newWorld);
                player.sendMessage(Message.raw("[VisPet] Pet followed you to the new world!"));
            } catch (Exception e) {
                LOG.severe("[VisPet] World change - spawn error: " + e.getMessage());
                activeBuddies.remove(uuid);
                petWorlds.remove(uuid);
                activePetTypes.remove(uuid);
            } finally {
                pendingSpawn.remove(uuid);
            }
        });
    }
}
