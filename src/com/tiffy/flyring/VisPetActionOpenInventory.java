package com.tiffy.flyring;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerWindow;
import com.hypixel.hytale.server.core.entity.entities.player.windows.Window;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.ActionBase;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderActionBase;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;
import javax.annotation.Nonnull;

public class VisPetActionOpenInventory extends ActionBase {

    public VisPetActionOpenInventory(@Nonnull VisPetBuilderActionOpenInventory builder,
            @Nonnull BuilderSupport support) {
        super((BuilderActionBase) builder);
    }

    @Override
    public boolean canExecute(@Nonnull Ref<EntityStore> ref, @Nonnull Role role, InfoProvider sensorInfo,
            double dt, @Nonnull Store<EntityStore> store) {
        return super.canExecute(ref, role, sensorInfo, dt, store)
                && role.getStateSupport().getInteractionIterationTarget() != null;
    }

    @Override
    public boolean execute(@Nonnull Ref<EntityStore> ref, @Nonnull Role role, InfoProvider sensorInfo,
            double dt, @Nonnull Store<EntityStore> store) {
        super.execute(ref, role, sensorInfo, dt, store);

        Ref<EntityStore> playerRef = role.getStateSupport().getInteractionIterationTarget();
        if (playerRef == null) return false;

        Player player = (Player) store.getComponent(playerRef, Player.getComponentType());
        if (player == null) return false;

        NPCEntity npc = (NPCEntity) store.getComponent(ref, NPCEntity.getComponentType());
        if (npc == null) return false;

        // SEC-001: Ownership check - only the pet owner can open inventory
        IllegalRings ir = IllegalRings.getInstance();
        if (ir != null) {
            VisPetComponent petComp = store.getComponent(ref, ir.getVisPetComponentType());
            if (petComp != null) {
                PlayerRef pRef = player.getPlayerRef();
                if (pRef == null || !petComp.getOwnerUuid().equals(pRef.getUuid())) {
                    player.sendMessage(Message.raw("&cThis is not your pet!"));
                    return false;
                }
            }
        }

        ItemContainer container = npc.getInventory().getHotbar();
        ContainerWindow window = new ContainerWindow(container);
        player.getPageManager().setPageWithWindows(playerRef, store, Page.Bench, true,
                new Window[] { window });

        return true;
    }
}
