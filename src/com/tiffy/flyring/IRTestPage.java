package com.tiffy.flyring;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

public class IRTestPage extends InteractiveCustomUIPage<IRTestPage.TestEventData> {

    public IRTestPage(@Nonnull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismiss, TestEventData.CODEC);
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder,
                      @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {

        commandBuilder.append("Pages/IRTestPage.ui");

        // Load current values
        ModConfig.Config cfg = ModConfig.getInstance();
        if (cfg != null) {
            commandBuilder.set("#TestFlyRing.Value", cfg.enabled.flyRing);
            commandBuilder.set("#TestFireRing.Value", cfg.enabled.fireRing);
            commandBuilder.set("#TestWaterRing.Value", cfg.enabled.waterRing);
            commandBuilder.set("#TestHealRing.Value", cfg.enabled.healRing);
            commandBuilder.set("#TestPeacefulRing.Value", cfg.enabled.peacefulRing);
            commandBuilder.set("#TestGaiaMedallion.Value", cfg.enabled.gaiaMedallion);
        }

        // 6 ValueChanged bindings - NO @capture, just action identifier
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.ValueChanged, "#TestFlyRing",
            new EventData().append("Action", "fly"), false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.ValueChanged, "#TestFireRing",
            new EventData().append("Action", "fire"), false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.ValueChanged, "#TestWaterRing",
            new EventData().append("Action", "water"), false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.ValueChanged, "#TestHealRing",
            new EventData().append("Action", "heal"), false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.ValueChanged, "#TestPeacefulRing",
            new EventData().append("Action", "peaceful"), false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.ValueChanged, "#TestGaiaMedallion",
            new EventData().append("Action", "gaia"), false
        );

        // Save + Close buttons
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating, "#TestSave",
            new EventData().append("Action", "save"), false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating, "#TestClose",
            new EventData().append("Action", "close"), false
        );
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull TestEventData data) {
        ModConfig.Config cfg = ModConfig.getInstance();

        System.out.println("[IRTest] Action=" + data.action);

        if ("close".equals(data.action)) {
            ModConfig.save();
            close();
        } else if ("save".equals(data.action)) {
            ModConfig.save();
            System.out.println("[IRTest] Config saved!");
            UICommandBuilder cmd = new UICommandBuilder();
            cmd.set("#TestStatus.Text", "Saved!");
            sendUpdate(cmd, false);
        } else if (cfg != null) {
            // Toggle approach - flip the current config value
            switch (data.action) {
                case "fly" -> {
                    cfg.enabled.flyRing = !cfg.enabled.flyRing;
                    System.out.println("[IRTest] flyRing = " + cfg.enabled.flyRing);
                }
                case "fire" -> {
                    cfg.enabled.fireRing = !cfg.enabled.fireRing;
                    System.out.println("[IRTest] fireRing = " + cfg.enabled.fireRing);
                }
                case "water" -> {
                    cfg.enabled.waterRing = !cfg.enabled.waterRing;
                    System.out.println("[IRTest] waterRing = " + cfg.enabled.waterRing);
                }
                case "heal" -> {
                    cfg.enabled.healRing = !cfg.enabled.healRing;
                    System.out.println("[IRTest] healRing = " + cfg.enabled.healRing);
                }
                case "peaceful" -> {
                    cfg.enabled.peacefulRing = !cfg.enabled.peacefulRing;
                    System.out.println("[IRTest] peacefulRing = " + cfg.enabled.peacefulRing);
                }
                case "gaia" -> {
                    cfg.enabled.gaiaMedallion = !cfg.enabled.gaiaMedallion;
                    System.out.println("[IRTest] gaiaMedallion = " + cfg.enabled.gaiaMedallion);
                }
            }
            UICommandBuilder cmd = new UICommandBuilder();
            cmd.set("#TestStatus.Text", data.action + " toggled");
            sendUpdate(cmd, false);
        }
    }

    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        ModConfig.save();
        super.onDismiss(ref, store);
    }

    public static class TestEventData {
        public static final BuilderCodec<TestEventData> CODEC = BuilderCodec.builder(TestEventData.class, TestEventData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (e, v) -> e.action = v, e -> e.action)
            .add()
            .build();

        public String action = "";
    }
}
