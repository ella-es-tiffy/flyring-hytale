package com.tiffy.flyring;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerWindow;
import com.hypixel.hytale.server.core.entity.entities.player.windows.Window;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nonnull;

/**
 * AdminCommands - Collection of admin commands under /ir
 * Usage: /ir config - Opens the config UI panel
 */
public class AdminCommands extends AbstractCommandCollection {

    public AdminCommands() {
        super("ir", "Illegal Rings admin commands");
        this.setPermissionGroup(GameMode.Creative);

        this.addSubCommand(new ConfigCommand());
        this.addSubCommand(new TestCommand());

        // VisPet commands (public - no permission group)
        this.addSubCommand(new PetOpenCommand());
        this.addSubCommand(new PetCloseCommand());

        // FreePet admin commands
        this.addSubCommand(new GiveFreePetCommand());
        this.addSubCommand(new ClearFreePetsCommand());
    }

    private static class ConfigCommand extends AbstractPlayerCommand {

        public ConfigCommand() {
            super("config", "Open the Illegal Rings config panel");
            this.setPermissionGroup(GameMode.Creative);
        }

        @Override
        protected void execute(
                @Nonnull CommandContext context,
                @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref,
                @Nonnull PlayerRef playerRef,
                @Nonnull World world) {

            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                context.sendMessage(Message.raw("&c[IR] Could not access player."));
                return;
            }

            IRConfigPage page = new IRConfigPage(playerRef);
            player.getPageManager().openCustomPage(ref, store, page);
        }
    }

    private static class TestCommand extends AbstractPlayerCommand {

        public TestCommand() {
            super("test", "Open the IR save test page");
            this.setPermissionGroup(GameMode.Creative);
        }

        @Override
        protected void execute(
                @Nonnull CommandContext context,
                @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref,
                @Nonnull PlayerRef playerRef,
                @Nonnull World world) {

            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                context.sendMessage(Message.raw("&c[IR] Could not access player."));
                return;
            }

            IRTestPage page = new IRTestPage(playerRef);
            player.getPageManager().openCustomPage(ref, store, page);
        }
    }

    // === VisPet Commands (public - no permission group) ===

    private static class PetOpenCommand extends AbstractPlayerCommand {

        public PetOpenCommand() {
            super("petopen", "Open your pet's inventory");
        }

        @Override
        protected void execute(
                @Nonnull CommandContext context,
                @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref,
                @Nonnull PlayerRef playerRef,
                @Nonnull World world) {

            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                context.sendMessage(Message.raw("&c[IR] Could not access player."));
                return;
            }

            IllegalRings ir = IllegalRings.getInstance();
            UUID uuid = playerRef.getUuid();

            if (!ir.getVisPetActiveBuddies().contains(uuid)) {
                player.sendMessage(Message.raw("No pet found! Use /ir pet to spawn one."));
                return;
            }

            AtomicReference<NPCEntity> foundNpc = new AtomicReference<>();
            store.forEachEntityParallel(
                    Query.and(NPCEntity.getComponentType(), ir.getVisPetComponentType()),
                    (index, chunk, buffer) -> {
                        if (foundNpc.get() != null) return;
                        VisPetComponent petComp = chunk.getComponent(index, ir.getVisPetComponentType());
                        if (petComp != null && uuid.equals(petComp.getOwnerUuid())) {
                            foundNpc.set(chunk.getComponent(index, NPCEntity.getComponentType()));
                        }
                    });

            if (foundNpc.get() == null) {
                player.sendMessage(Message.raw("Pet NPC not found!"));
                return;
            }

            NPCEntity npc = foundNpc.get();
            ItemContainer container = npc.getInventory().getHotbar();
            ContainerWindow window = new ContainerWindow(container);
            player.getPageManager().setPageWithWindows(ref, store, Page.Bench, true, new Window[] { window });
            player.sendMessage(Message.raw("Opened pet inventory."));
        }
    }

    private static class PetCloseCommand extends AbstractPlayerCommand {

        public PetCloseCommand() {
            super("petclose", "Save inventory and despawn your pet");
        }

        @Override
        protected void execute(
                @Nonnull CommandContext context,
                @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref,
                @Nonnull PlayerRef playerRef,
                @Nonnull World world) {

            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                context.sendMessage(Message.raw("&c[IR] Could not access player."));
                return;
            }

            IllegalRings ir = IllegalRings.getInstance();
            UUID uuid = playerRef.getUuid();

            if (!ir.getVisPetActiveBuddies().contains(uuid)) {
                player.sendMessage(Message.raw("No pet found!"));
                return;
            }

            AtomicBoolean found = new AtomicBoolean(false);
            store.forEachEntityParallel(
                    Query.and(NPCEntity.getComponentType(), ir.getVisPetComponentType()),
                    (index, chunk, buffer) -> {
                        VisPetComponent petComp = chunk.getComponent(index, ir.getVisPetComponentType());
                        if (petComp != null && uuid.equals(petComp.getOwnerUuid())) {
                            NPCEntity npc = chunk.getComponent(index, NPCEntity.getComponentType());
                            if (npc != null) {
                                ir.getVisPetStorage().saveInventory(uuid, npc.getInventory().getHotbar());
                            }
                            buffer.removeEntity(chunk.getReferenceTo(index), RemoveReason.REMOVE);
                            found.set(true);
                        }
                    });

            if (found.get()) {
                // Remove active pet effects
                EffectControllerComponent effectCtrl = store.getComponent(ref,
                        EffectControllerComponent.getComponentType());
                if (effectCtrl != null) {
                    for (VisPetToggleSystem.PetType type : VisPetToggleSystem.PET_TYPES) {
                        type.initIndices();
                        if (type.activeEffectIndex != -1) {
                            effectCtrl.removeEffect(ref, type.activeEffectIndex,
                                    (ComponentAccessor<EntityStore>) store);
                        }
                    }
                }

                ir.getVisPetOwnerRefs().remove(uuid);
                ir.getVisPetNpcRefs().remove(uuid);
                ir.getVisPetActiveBuddies().remove(uuid);
                ir.getVisPetWorlds().remove(uuid);
                ir.getVisPetActiveTypes().remove(uuid);
                player.sendMessage(Message.raw("Pet despawned. Inventory saved."));
            } else {
                player.sendMessage(Message.raw("Pet NPC not found!"));
            }
        }
    }

    private static class ClearFreePetsCommand extends AbstractPlayerCommand {

        public ClearFreePetsCommand() {
            super("clearfreepets", "Reset free pet for ALL players (everyone gets it again on next login)");
            this.setPermissionGroup(GameMode.Creative);
        }

        @Override
        protected void execute(
                @Nonnull CommandContext context,
                @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref,
                @Nonnull PlayerRef playerRef,
                @Nonnull World world) {

            IllegalRings ir = IllegalRings.getInstance();
            int count = ir.getFreePetStorage().resetAll();
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                player.sendMessage(Message.raw("[Pet] Cleared free pet data for " + count + " player(s). They will receive it again on next login."));
            }
        }
    }

    private static class GiveFreePetCommand extends AbstractPlayerCommand {

        public GiveFreePetCommand() {
            super("givefreepet", "Give the 3-day free Fox pet (resets timer)");
            this.setPermissionGroup(GameMode.Creative);
        }

        @Override
        protected void execute(
                @Nonnull CommandContext context,
                @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref,
                @Nonnull PlayerRef playerRef,
                @Nonnull World world) {

            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                context.sendMessage(Message.raw("&c[IR] Could not access player."));
                return;
            }

            IllegalRings ir = IllegalRings.getInstance();
            UUID uuid = playerRef.getUuid();

            // Reset free pet data so it can be granted again
            ir.getFreePetStorage().reset(uuid);

            // Grant the item with unique serial
            String serial = java.util.UUID.randomUUID().toString();
            ItemStack stamped = new ItemStack("Loot_Fox_Free", 1)
                    .withMetadata(FreePetStorage.META_SERIAL, com.hypixel.hytale.codec.Codec.STRING, serial);
            player.getInventory().getHotbar().addItemStack(stamped);

            FreePetStorage.FreePetData fpData = new FreePetStorage.FreePetData();
            fpData.received = true;
            fpData.remainingMs = FreePetStorage.DURATION_MS;
            fpData.serial = serial;
            ir.getFreePetStorage().save(uuid, fpData);

            player.sendMessage(Message.raw("[Pet] Free Fox granted! Timer starts when pet is summoned."));
        }
    }
}
