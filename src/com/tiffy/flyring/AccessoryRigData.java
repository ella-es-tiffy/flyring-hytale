package com.tiffy.flyring;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * AccessoryRigData - Persistence component for the custom ring slots.
 */
public class AccessoryRigData implements Component<EntityStore> {
    public static final String COMPONENT_ID = "IllegalRings_AccessoryData";
    public static final int SLOT_COUNT = 6; // 6 specialized slots for rings/accessories

    public static final Codec<ItemStack[]> ENTRIES_CODEC = new ArrayCodec<>(ItemStack.CODEC, ItemStack[]::new);
    public static final BuilderCodec<AccessoryRigData> CODEC = BuilderCodec
            .builder(AccessoryRigData.class, AccessoryRigData::new)
            .append(new KeyedCodec<>("Version", Codec.INTEGER), AccessoryRigData::setVersion,
                    AccessoryRigData::getVersion)
            .add()
            .append(new KeyedCodec<>("Entries", ENTRIES_CODEC), AccessoryRigData::setEntries,
                    AccessoryRigData::getEntries)
            .add()
            .build();

    private int version = 1;
    private ItemStack[] entries;

    public AccessoryRigData() {
        this.entries = new ItemStack[SLOT_COUNT];
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public ItemStack[] getEntries() {
        return entries;
    }

    public void setEntries(ItemStack[] entries) {
        if (entries != null && entries.length < SLOT_COUNT) {
            // Migration: Expand array to new slot count
            ItemStack[] newEntries = new ItemStack[SLOT_COUNT];
            System.arraycopy(entries, 0, newEntries, 0, entries.length);
            this.entries = newEntries;
        } else {
            this.entries = entries;
        }
    }

    public ItemStack getStack(int slot) {
        if (slot < 0 || entries == null || slot >= entries.length)
            return null;
        return entries[slot];
    }

    public void setStack(int slot, ItemStack stack) {
        if (entries != null && slot >= 0 && slot < entries.length) {
            entries[slot] = stack;
        }
    }

    @Override
    public AccessoryRigData clone() {
        AccessoryRigData clone = new AccessoryRigData();
        clone.version = this.version;
        clone.entries = this.entries.clone();
        return clone;
    }

    // Static helper to get component type (needs to be registered in IllegalRings)
    private static ComponentType<EntityStore, AccessoryRigData> type;

    public static void setComponentType(ComponentType<EntityStore, AccessoryRigData> type) {
        AccessoryRigData.type = type;
    }

    public static ComponentType<EntityStore, AccessoryRigData> getComponentType() {
        return type;
    }
}
