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
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.builtin.npccombatactionevaluator.memory.TargetMemory;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.math.vector.Vector3d;

import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * RingDamageSystem - Handles BloodSuck (Lifesteal) and Elemental Immunities.
 */
public class RingDamageSystem extends DamageEventSystem {

    private final IllegalRings plugin;
    private final FlyRing flyHandler;
    private final FireRing fireHandler;
    private final WaterRing waterHandler;
    private final HealRing healHandler; // This is now our BloodSuck handler
    private final PeacefullRing peacefulHandler;
    private RingLootSystem lootSystem;

    public RingDamageSystem(IllegalRings plugin, FlyRing flyHandler, FireRing fireHandler, WaterRing waterHandler,
            HealRing healHandler, PeacefullRing peacefulHandler) {
        this.plugin = plugin;
        this.flyHandler = flyHandler;
        this.fireHandler = fireHandler;
        this.waterHandler = waterHandler;
        this.healHandler = healHandler;
        this.peacefulHandler = peacefulHandler;
    }

    public void setLootSystem(RingLootSystem lootSystem) {
        this.lootSystem = lootSystem;
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
            Ref<EntityStore> victimRef = chunk.getReferenceTo(index);
            UUIDComponent victimUuidComp = (UUIDComponent) store.getComponent(victimRef,
                    UUIDComponent.getComponentType());
            UUID victimUuid = (victimUuidComp != null) ? victimUuidComp.getUuid() : null;

            Damage.Source source = event.getSource();
            DamageCause cause = event.getCause();
            String causeId = (cause != null) ? cause.getId().toLowerCase() : "unknown";

            // --- 1. DEFENSIVE RING EFFECTS ---
            // Fire Ring Immunity (Fire, Lava, Burn, etc.)
            boolean fireEnabled = ModConfig.getInstance() != null &&
                    ModConfig.getInstance().enabled != null &&
                    ModConfig.getInstance().enabled.fireRing;
            if (fireEnabled && victimUuid != null && isFireRelated(causeId) && fireHandler != null
                    && fireHandler.getFireImmunePlayers().contains(victimUuid)) {
                cancelDamage(event, "Fire", victimUuid, causeId);
                return;
            }

            // Water Ring Immunity (Drowning)
            boolean waterEnabled = ModConfig.getInstance() != null &&
                    ModConfig.getInstance().enabled != null &&
                    ModConfig.getInstance().enabled.waterRing;
            if (waterEnabled && victimUuid != null && isWaterRelated(causeId) && waterHandler != null
                    && waterHandler.getWaterImmunePlayers().contains(victimUuid)) {
                cancelDamage(event, "Water", victimUuid, causeId);
                return;
            }

            // Fly Ring Immunity (Fall Damage)
            boolean flyEnabled = ModConfig.getInstance() != null &&
                    ModConfig.getInstance().enabled != null &&
                    ModConfig.getInstance().enabled.flyRing;
            if (flyEnabled && victimUuid != null && isFallRelated(causeId) && flyHandler != null
                    && flyHandler.getFalldamageImmunePlayers().contains(victimUuid)) {
                cancelDamage(event, "Fly", victimUuid, causeId);
                return;
            }

            // --- 2. OFFENSIVE RING EFFECTS (BloodSuck / Lifesteal) ---
            // Check if HealRing is enabled
            boolean healEnabled = ModConfig.getInstance() != null &&
                    ModConfig.getInstance().enabled != null &&
                    ModConfig.getInstance().enabled.healRing;

            // If the attacker has the ring, heal the attacker based on damage dealt
            if (healEnabled && source instanceof Damage.EntitySource entitySource) {
                Ref<EntityStore> attackerRef = entitySource.getRef();
                if (attackerRef != null && attackerRef.isValid()) {
                    UUIDComponent attackerUuidComp = (UUIDComponent) store.getComponent(attackerRef,
                            UUIDComponent.getComponentType());
                    if (attackerUuidComp != null) {
                        UUID attackerUuid = attackerUuidComp.getUuid();

                        // BloodSuck check
                        if (healHandler != null && healHandler.getHealRingPlayers().contains(attackerUuid)) {
                            applyBloodSuckEffect(attackerRef, store, event, attackerUuid);
                        }

                        // --- 3. PEACEFUL RING LOGIC (Target Clearing) ---
                        // If a player with the Peaceful Ring attacks an entity,
                        // clear that entity's target so it doesn't retaliate.
                        if (peacefulHandler != null && peacefulHandler.getPeacefulPlayers().contains(attackerUuid)) {
                            TargetMemory targetMemory = (TargetMemory) store.getComponent(victimRef,
                                    TargetMemory.getComponentType());
                            if (targetMemory != null) {
                                // Clear them from known hostiles
                                if (targetMemory.getKnownHostiles() != null) {
                                    targetMemory.getKnownHostiles().remove(attackerRef.getIndex());
                                }
                                // Clear as primary/closest target
                                if (attackerRef.equals(targetMemory.getClosestHostile())) {
                                    targetMemory.setClosestHostile(null);
                                }
                            }
                        }
                    }
                }
            }

            // --- 4. PEACEFUL RING LOGIC (Damage Prevention) ---
            // Check if PeacefulRing is enabled
            boolean peacefulEnabled = ModConfig.getInstance() != null &&
                    ModConfig.getInstance().enabled != null &&
                    ModConfig.getInstance().enabled.peacefulRing;

            // If the victim has the Peaceful Ring, cancel damage from NPC sources.
            if (peacefulEnabled && victimUuid != null && peacefulHandler != null
                    && peacefulHandler.getPeacefulPlayers().contains(victimUuid)) {
                if (source instanceof Damage.EntitySource entitySource) {
                    Ref<EntityStore> attackerRef = entitySource.getRef();
                    // Check if the attacker is NOT a player (approximate check by trying to get
                    // Player component)
                    if (attackerRef != null && attackerRef.isValid()) {
                        boolean isPlayerAttacker = store.getComponent(attackerRef,
                                com.hypixel.hytale.server.core.entity.entities.Player.getComponentType()) != null;
                        if (!isPlayerAttacker) {
                            cancelDamage(event, "Peaceful", victimUuid, "NPC_" + causeId);
                            return;
                        }
                    }
                }
            }

            // --- 5. NPC DEATH DETECTION FOR LOOT DROPS ---
            if (lootSystem != null && lootSystem.isEnabled()) {
                checkNpcDeath(victimRef, store, event, source);
            }

            // --- 6. PETPIECE DROP (ALWAYS, independent of loot system) ---
            checkPetpieceDrop(victimRef, store, event, source);

        } catch (Exception e) {
            Log.info(plugin, "[RingDebug] Error: " + e.getMessage());
        }
    }

    /**
     * Check if this damage will kill an NPC and spawn loot drops.
     */
    private void checkNpcDeath(Ref<EntityStore> victimRef, Store<EntityStore> store, Damage event, Damage.Source source) {
        try {
            // Only process if damage is not cancelled
            if (event.isCancelled() || event.getAmount() <= 0) {
                return;
            }

            // Check if victim is an NPC
            NPCEntity npcEntity = (NPCEntity) store.getComponent(victimRef, NPCEntity.getComponentType());
            if (npcEntity == null) {
                return;
            }

            // Get NPC's current health
            EntityStatMap victimStats = (EntityStatMap) store.getComponent(victimRef, EntityStatMap.getComponentType());
            if (victimStats == null) {
                return;
            }

            EntityStatValue healthStat = victimStats.get(DefaultEntityStatTypes.getHealth());
            if (healthStat == null) {
                return;
            }

            float currentHealth = healthStat.get();
            float damageAmount = event.getAmount();

            // Check if this damage will kill the NPC
            if (currentHealth - damageAmount <= 0) {
                // Get NPC position for drop
                TransformComponent transform = (TransformComponent) store.getComponent(victimRef, TransformComponent.getComponentType());
                if (transform == null) {
                    return;
                }
                Vector3d position = transform.getPosition();

                // Get the killer's reference (if it was an entity attack)
                Ref<EntityStore> killerRef = null;
                if (source instanceof Damage.EntitySource entitySource) {
                    killerRef = entitySource.getRef();
                }

                // Let loot system handle the drop
                lootSystem.onNpcDeath(npcEntity.getRoleName(), position, store, killerRef);

                boolean debug = ModConfig.getInstance() != null && ModConfig.getInstance().debugLogging;
                if (debug) {
                    Log.info(plugin, "[RingLoot] NPC death detected: " + npcEntity.getRoleName() + " at " + position);
                }
            }
        } catch (Exception e) {
            Log.info(plugin, "[RingLoot] Death check error: " + e.getMessage());
        }
    }

    private static final String PETPIECE_ITEM = "Scroll_DeWateil";
    private static final double PETPIECE_DROP_CHANCE = 0.05; // 5%

    /**
     * Petpiece drops from ALL NPC deaths at 5% chance, always active regardless of loot config.
     */
    private void checkPetpieceDrop(Ref<EntityStore> victimRef, Store<EntityStore> store, Damage event, Damage.Source source) {
        try {
            if (event.isCancelled() || event.getAmount() <= 0) return;

            NPCEntity npc = (NPCEntity) store.getComponent(victimRef, NPCEntity.getComponentType());
            if (npc == null) return;

            EntityStatMap victimStats = (EntityStatMap) store.getComponent(victimRef, EntityStatMap.getComponentType());
            if (victimStats == null) return;

            EntityStatValue healthStat = victimStats.get(DefaultEntityStatTypes.getHealth());
            if (healthStat == null) return;

            if (healthStat.get() - event.getAmount() > 0) return; // Not a kill

            TransformComponent transform = (TransformComponent) store.getComponent(victimRef, TransformComponent.getComponentType());
            if (transform == null) return;

            if (java.util.concurrent.ThreadLocalRandom.current().nextDouble() < PETPIECE_DROP_CHANCE) {
                Ref<EntityStore> killerRef = null;
                if (source instanceof Damage.EntitySource entitySource) {
                    killerRef = entitySource.getRef();
                }
                LootDropTickSystem.queueDrop(PETPIECE_ITEM, 1, transform.getPosition(), killerRef, false);
            }
        } catch (Exception e) {
            Log.info(plugin, "[ERR-1013] checkPetpieceDrop: " + e.getMessage());
        }
    }

    private void applyBloodSuckEffect(Ref<EntityStore> attackerRef, Store<EntityStore> store, Damage event,
            UUID attackerUuid) {
        try {
            // Get Attacker's Stat Map
            EntityStatMap attackerStats = (EntityStatMap) store.getComponent(attackerRef,
                    EntityStatMap.getComponentType());
            if (attackerStats == null)
                return;

            EntityStatValue healthStat = attackerStats.get(DefaultEntityStatTypes.getHealth());
            if (healthStat == null)
                return;

            float damageDealt = event.getAmount();
            if (damageDealt <= 0)
                return;

            // Get lifesteal percentage from config (default 35%)
            float lifestealPercent = 0.35f;
            if (ModConfig.getInstance() != null && ModConfig.getInstance().gameplay != null) {
                lifestealPercent = (float) ModConfig.getInstance().gameplay.lifestealPercent;
            }

            float healAmount = damageDealt * lifestealPercent;
            float currentHealth = healthStat.get();
            float maxHealth = healthStat.getMax();
            float newHealth = Math.min(maxHealth, currentHealth + healAmount);

            // Apply healing to attacker
            attackerStats.setStatValue(DefaultEntityStatTypes.getHealth(), newHealth);

            Log.info(plugin,
                    "[BloodSuck] " + attackerUuid + " dealt " + damageDealt + " dmg and sucked " + healAmount + " HP");
        } catch (Exception e) {
            Log.info(plugin, "[BloodSuck] Error: " + e.getMessage());
        }
    }

    private boolean isFallRelated(String causeId) {
        return causeId.contains("fall") || causeId.contains("impact");
    }

    private boolean isFireRelated(String causeId) {
        return causeId.contains("fire") || causeId.contains("lava") || causeId.contains("magma")
                || causeId.contains("burn") || causeId.contains("hot");
    }

    private boolean isWaterRelated(String causeId) {
        return causeId.contains("drown") || causeId.contains("water");
    }

    private void cancelDamage(Damage event, String ringType, UUID uuid, String causeId) {
        Log.info(plugin, "[RingDebug] " + ringType + " Ring blocked " + causeId + " for " + uuid);
        event.setAmount(0.0f);
        event.setCancelled(true);
    }
}
