package com.tiffy.flyring;

import com.google.gson.JsonElement;
import com.hypixel.hytale.server.npc.asset.builder.Builder;
import com.hypixel.hytale.server.npc.asset.builder.BuilderDescriptorState;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderActionBase;
import com.hypixel.hytale.server.npc.instructions.Action;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class VisPetBuilderActionOpenInventory extends BuilderActionBase {

    @Override
    @Nullable
    public BuilderDescriptorState getBuilderDescriptorState() {
        return null;
    }

    @Override
    @Nullable
    public String getShortDescription() {
        return "Opens pet inventory";
    }

    @Override
    @Nullable
    public String getLongDescription() {
        return "Opens the pet NPC inventory for the interacting player";
    }

    @Override
    @Nonnull
    public Builder<Action> readCommonConfig(@Nonnull JsonElement data) {
        super.readCommonConfig(data);
        return this;
    }

    @Override
    public Action build(@Nonnull BuilderSupport support) {
        return new VisPetActionOpenInventory(this, support);
    }
}
