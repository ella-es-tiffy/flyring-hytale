package com.tiffy.flyring;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.asset.type.attitude.Attitude;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.blackboard.view.attitude.IAttitudeProvider;
import com.hypixel.hytale.server.npc.role.Role;
import java.util.UUID;

/**
 * PeacefulAttitudeProvider - Changes NPC attitude towards Peaceful Ring
 * wearers.
 */
public class PeacefulAttitudeProvider implements IAttitudeProvider {

    private final PeacefullRing handler;

    public PeacefulAttitudeProvider(PeacefullRing handler) {
        this.handler = handler;
    }

    @Override
    public Attitude getAttitude(Ref<EntityStore> observer, Role role, Ref<EntityStore> target,
            ComponentAccessor<EntityStore> accessor) {
        if (target == null)
            return null;

        // Try to get player component to see if target is a player
        PlayerRef playerComp = (PlayerRef) accessor.getComponent(target, PlayerRef.getComponentType());
        if (playerComp != null) {
            UUID uuid = playerComp.getUuid();
            if (handler.getPeacefulPlayers().contains(uuid)) {
                // Changing NEUTRAL to FRIENDLY to fully prevent hostile targeting
                // whilst maintaining interaction capability.
                return Attitude.FRIENDLY;
            }
        }

        return null; // Let other providers decide
    }
}
