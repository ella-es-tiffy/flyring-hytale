package com.tiffy.flyring;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;

public class VisPetComponent implements Component<EntityStore> {

    private UUID ownerUuid;

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public void setOwnerUuid(UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
    }

    @Override
    public VisPetComponent clone() {
        VisPetComponent copy = new VisPetComponent();
        copy.ownerUuid = this.ownerUuid;
        return copy;
    }
}
