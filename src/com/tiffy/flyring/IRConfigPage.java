package com.tiffy.flyring;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.core.ui.DropdownEntryInfo;
import com.hypixel.hytale.server.core.ui.LocalizableString;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;
import java.util.logging.Logger;

public class IRConfigPage extends InteractiveCustomUIPage<IRConfigPage.ConfigEventData> {

    private static final Logger LOG = Logger.getLogger("IRConfig");
    private final PlayerRef playerRef;
    private boolean craftTabLoaded = false;
    private boolean lootTabLoaded = false;

    // All dropdown IDs for craft tab
    private static final String[] CRAFT_DD_IDS = {
        "#FlyDD1", "#FlyDD2", "#FlyDD3", "#FlyDD4",
        "#FireDD1", "#FireDD2", "#FireDD3", "#FireDD4",
        "#WaterDD1", "#WaterDD2", "#WaterDD3", "#WaterDD4",
        "#HealDD1", "#HealDD2", "#HealDD3", "#HealDD4",
        "#PeaceDD1", "#PeaceDD2", "#PeaceDD3", "#PeaceDD4",
        "#GaiaDD1", "#GaiaDD2", "#GaiaDD3", "#GaiaDD4"
    };

    // Number of NPC loot slots in UI
    private static final int LOOT_SLOT_COUNT = 24;

    // Static cache - built once, reused on every page open
    private static List<DropdownEntryInfo> cachedItemList = null;
    private static List<DropdownEntryInfo> cachedNpcList = null;
    private static List<DropdownEntryInfo> cachedRingList = null;

    public IRConfigPage(@Nonnull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismiss, ConfigEventData.CODEC);
        this.playerRef = playerRef;
    }

    private static List<DropdownEntryInfo> getItemList() {
        if (cachedItemList != null) return cachedItemList;

        List<DropdownEntryInfo> entries = new ArrayList<>();
        entries.add(new DropdownEntryInfo(LocalizableString.fromString("-- None --"), ""));

        try {
            TreeMap<String, Item> sorted = new TreeMap<>(Item.getAssetMap().getAssetMap());
            for (var entry : sorted.entrySet()) {
                String id = entry.getKey();
                entries.add(new DropdownEntryInfo(LocalizableString.fromString(id), id));
            }
        } catch (Exception e) {
            LOG.severe("[ERR-1014] getItemList: " + e.getMessage());
        }
        cachedItemList = entries;
        return entries;
    }

    private static List<DropdownEntryInfo> getNpcList() {
        if (cachedNpcList != null) return cachedNpcList;

        List<DropdownEntryInfo> entries = new ArrayList<>();
        entries.add(new DropdownEntryInfo(LocalizableString.fromString("-- None --"), ""));

        try {
            List<String> roles = NPCPlugin.get().getRoleTemplateNames(true);
            List<String> sorted = new ArrayList<>(roles);
            Collections.sort(sorted);
            for (String name : sorted) {
                entries.add(new DropdownEntryInfo(LocalizableString.fromString(name), name));
            }
        } catch (Exception e) {
            LOG.severe("[ERR-1015] getNpcList: " + e.getMessage());
        }
        cachedNpcList = entries;
        return entries;
    }

    private static List<DropdownEntryInfo> getRingList() {
        if (cachedRingList != null) return cachedRingList;

        List<DropdownEntryInfo> entries = new ArrayList<>();
        entries.add(new DropdownEntryInfo(LocalizableString.fromString("-- None --"), ""));
        entries.add(new DropdownEntryInfo(LocalizableString.fromString("Fly Ring"), "fly"));
        entries.add(new DropdownEntryInfo(LocalizableString.fromString("Fire Ring"), "fire"));
        entries.add(new DropdownEntryInfo(LocalizableString.fromString("Water Ring"), "water"));
        entries.add(new DropdownEntryInfo(LocalizableString.fromString("Heal Ring"), "heal"));
        entries.add(new DropdownEntryInfo(LocalizableString.fromString("Peaceful Ring"), "peaceful"));
        entries.add(new DropdownEntryInfo(LocalizableString.fromString("Gaia Medallion"), "gaia"));
        cachedRingList = entries;
        return entries;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder,
                      @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {

        commandBuilder.append("Pages/IRConfigPage.ui");
        commandBuilder.set("#ConfigTitle.Text", "Illegal Rings v" + BuildInfo.VERSION + " - Config");

        // Load current config values into UI
        ModConfig.Config cfg = ModConfig.getInstance();
        if (cfg != null) {
            // Rings Enabled
            commandBuilder.set("#EnabledFlyRing.Value", cfg.enabled.flyRing);
            commandBuilder.set("#EnabledFireRing.Value", cfg.enabled.fireRing);
            commandBuilder.set("#EnabledWaterRing.Value", cfg.enabled.waterRing);
            commandBuilder.set("#EnabledHealRing.Value", cfg.enabled.healRing);
            commandBuilder.set("#EnabledPeacefulRing.Value", cfg.enabled.peacefulRing);
            commandBuilder.set("#EnabledGaiaMedallion.Value", cfg.enabled.gaiaMedallion);

            // Rings Craftable
            commandBuilder.set("#CraftableFlyRing.Value", cfg.craftable.flyRing);
            commandBuilder.set("#CraftableFireRing.Value", cfg.craftable.fireRing);
            commandBuilder.set("#CraftableWaterRing.Value", cfg.craftable.waterRing);
            commandBuilder.set("#CraftableHealRing.Value", cfg.craftable.healRing);
            commandBuilder.set("#CraftablePeacefulRing.Value", cfg.craftable.peacefulRing);
            commandBuilder.set("#CraftableGaiaMedallion.Value", cfg.craftable.gaiaMedallion);

            // Gameplay
            commandBuilder.set("#LifestealValue.Text", Math.round(cfg.gameplay.lifestealPercent * 100) + "%");
            commandBuilder.set("#NightVisionEnabled.Value", cfg.gameplay.nightVisionEnabled);

            // General
            commandBuilder.set("#BackpackEnabled.Value", cfg.backpackEnabled);
            commandBuilder.set("#Notification.Value", cfg.notification);
            commandBuilder.set("#SpawnTiffyNpc.Value", cfg.spawnTiffyNpc);
            commandBuilder.set("#AnalyticsEnabled.Value", cfg.analyticsEnabled);

            // Free Pet (noFreePet is inverted: true = disabled)
            commandBuilder.set("#FreePetEnabled.Value", !cfg.noFreePet);
            if (cfg.freePetMessage != null) {
                commandBuilder.set("#FreePetMessage.Value", cfg.freePetMessage);
            }

            // Loot
            commandBuilder.set("#LootEnabled.Value", cfg.loot.enabled);
        }

        // Night Vision Trigger Item dropdown (General tab - load immediately)
        List<DropdownEntryInfo> items = getItemList();
        commandBuilder.set("#NightVisionTriggerItem.Entries", items);
        if (cfg != null && cfg.gameplay.nightVisionTriggerItem != null) {
            commandBuilder.set("#NightVisionTriggerItem.Value", cfg.gameplay.nightVisionTriggerItem);
        }

        // Craft + Loot tabs: loaded lazily on first tab switch (see switchTab)

        // === EVENT BINDINGS ===

        // Tab button events
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating, "#TabGeneral",
            new EventData().append("Action", "tab").append("Value", "general"), false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating, "#TabCraft",
            new EventData().append("Action", "tab").append("Value", "craft"), false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating, "#TabLoot",
            new EventData().append("Action", "tab").append("Value", "loot"), false
        );

        // FloatSlider ValueChanged - EventData.of() with @ key resolves property selector
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.ValueChanged, "#LifestealSlider",
            EventData.of("@Lifesteal", "#LifestealSlider.Value"), false
        );

        // CheckBox ValueChanged - NO @capture, server-side toggle
        // Rings Enabled
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#EnabledFlyRing",
            new EventData().append("Action", "en_fly"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#EnabledFireRing",
            new EventData().append("Action", "en_fire"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#EnabledWaterRing",
            new EventData().append("Action", "en_water"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#EnabledHealRing",
            new EventData().append("Action", "en_heal"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#EnabledPeacefulRing",
            new EventData().append("Action", "en_peaceful"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#EnabledGaiaMedallion",
            new EventData().append("Action", "en_gaia"), false);

        // Rings Craftable
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#CraftableFlyRing",
            new EventData().append("Action", "cr_fly"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#CraftableFireRing",
            new EventData().append("Action", "cr_fire"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#CraftableWaterRing",
            new EventData().append("Action", "cr_water"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#CraftableHealRing",
            new EventData().append("Action", "cr_heal"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#CraftablePeacefulRing",
            new EventData().append("Action", "cr_peaceful"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#CraftableGaiaMedallion",
            new EventData().append("Action", "cr_gaia"), false);

        // Gameplay + General checkboxes
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#NightVisionEnabled",
            new EventData().append("Action", "nightvision"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#BackpackEnabled",
            new EventData().append("Action", "backpack"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#Notification",
            new EventData().append("Action", "notification"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#SpawnTiffyNpc",
            new EventData().append("Action", "tiffynpc"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#AnalyticsEnabled",
            new EventData().append("Action", "analytics"), false);

        // Loot enabled checkbox
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#LootEnabled",
            new EventData().append("Action", "loot_en"), false);

        // NightVision trigger item dropdown - EventData.of() resolves property
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#NightVisionTriggerItem",
            EventData.of("@StrVal", "#NightVisionTriggerItem.Value").append("Action", "nv_item"), false);

        // Craft dropdown + qty ValueChanged bindings
        String[] craftPrefixes = {"Fly", "Fire", "Water", "Heal", "Peace", "Gaia"};
        for (String prefix : craftPrefixes) {
            for (int s = 1; s <= 4; s++) {
                eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#" + prefix + "DD" + s,
                    EventData.of("@StrVal", "#" + prefix + "DD" + s + ".Value")
                        .append("Action", "craft_dd").append("Value", prefix + "_" + s), false);
                eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#" + prefix + "Qty" + s,
                    EventData.of("@NumVal", "#" + prefix + "Qty" + s + ".Value")
                        .append("Action", "craft_qty").append("Value", prefix + "_" + s), false);
            }
        }

        // Loot slot ValueChanged bindings (NPC dropdown, Ring dropdown, Drop%, Frag%)
        for (int i = 1; i <= LOOT_SLOT_COUNT; i++) {
            String si = String.valueOf(i);
            eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#LootNpc" + i,
                EventData.of("@StrVal", "#LootNpc" + i + ".Value")
                    .append("Action", "loot_npc").append("Value", si), false);
            eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#LootRing" + i,
                EventData.of("@StrVal", "#LootRing" + i + ".Value")
                    .append("Action", "loot_ring").append("Value", si), false);
            eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#LootDrop" + i,
                EventData.of("@NumVal", "#LootDrop" + i + ".Value")
                    .append("Action", "loot_drop").append("Value", si), false);
            eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#LootFrag" + i,
                EventData.of("@NumVal", "#LootFrag" + i + ".Value")
                    .append("Action", "loot_frag").append("Value", si), false);
        }

        // Spawn + Delete buttons for loot slots
        for (int i = 1; i <= LOOT_SLOT_COUNT; i++) {
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating, "#LootSpawn" + i,
                new EventData().append("Action", "spawn").append("Value", String.valueOf(i)), false
            );
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating, "#LootDel" + i,
                new EventData().append("Action", "delete").append("Value", String.valueOf(i)), false
            );
        }

        // Add loot entry button
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating, "#LootAdd",
            new EventData().append("Action", "add"), false
        );

        // Free Pet
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#FreePetEnabled",
            new EventData().append("Action", "freepet_en"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#FreePetMessage",
            EventData.of("@StrVal", "#FreePetMessage.Value").append("Action", "freepet_msg"), false);
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating, "#ResetFreePets",
            new EventData().append("Action", "reset_freepets"), false
        );

        // Close button event
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating, "#CloseButton",
            new EventData().append("Action", "close"), false
        );
    }

    private static void loadCraftRing(UICommandBuilder cmd, ModConfig.RecipeOverride recipe, String prefix) {
        if (recipe == null) return;

        List<ModConfig.Ingredient> ings = recipe.ingredients;
        for (int i = 0; i < 4; i++) {
            if (ings != null && i < ings.size()) {
                cmd.set("#" + prefix + "DD" + (i + 1) + ".Value", ings.get(i).id);
                cmd.set("#" + prefix + "Qty" + (i + 1) + ".Value", ings.get(i).amount);
            } else {
                cmd.set("#" + prefix + "DD" + (i + 1) + ".Value", "");
                cmd.set("#" + prefix + "Qty" + (i + 1) + ".Value", 1);
            }
        }
    }

    private void switchTab(String tab) {
        if ("craft".equals(tab) && !craftTabLoaded) {
            loadCraftTabData();
            craftTabLoaded = true;
        } else if ("loot".equals(tab) && !lootTabLoaded) {
            loadLootTabData();
            lootTabLoaded = true;
        }
        UICommandBuilder cmd = new UICommandBuilder();
        cmd.set("#GeneralContent.Visible", "general".equals(tab));
        cmd.set("#CraftContent.Visible", "craft".equals(tab));
        cmd.set("#LootContent.Visible", "loot".equals(tab));
        sendUpdate(cmd, false);
    }

    private void loadCraftTabData() {
        UICommandBuilder cmd = new UICommandBuilder();
        List<DropdownEntryInfo> items = getItemList();
        for (String ddId : CRAFT_DD_IDS) {
            cmd.set(ddId + ".Entries", items);
        }
        ModConfig.Config cfg = ModConfig.getInstance();
        if (cfg != null && cfg.recipeOverrides != null) {
            for (ModConfig.RecipeOverride ro : cfg.recipeOverrides) {
                if (ro.targetItemId == null) continue;
                if (ro.targetItemId.contains("Fly_Ring")) loadCraftRing(cmd, ro, "Fly");
                else if (ro.targetItemId.contains("Fire_Ring")) loadCraftRing(cmd, ro, "Fire");
                else if (ro.targetItemId.contains("Water_Ring")) loadCraftRing(cmd, ro, "Water");
                else if (ro.targetItemId.contains("Heal_Ring")) loadCraftRing(cmd, ro, "Heal");
                else if (ro.targetItemId.contains("Peacefull_Ring")) loadCraftRing(cmd, ro, "Peace");
                else if (ro.targetItemId.contains("Gaia_Medallion")) loadCraftRing(cmd, ro, "Gaia");
            }
        }
        sendUpdate(cmd, false);
    }

    private void loadLootTabData() {
        UICommandBuilder cmd = new UICommandBuilder();
        List<DropdownEntryInfo> npcs = getNpcList();
        List<DropdownEntryInfo> rings = getRingList();
        for (int i = 1; i <= LOOT_SLOT_COUNT; i++) {
            cmd.set("#LootNpc" + i + ".Entries", npcs);
            cmd.set("#LootRing" + i + ".Entries", rings);
        }
        ModConfig.Config cfg = ModConfig.getInstance();
        int entryCount = (cfg != null && cfg.loot.npcs != null) ? cfg.loot.npcs.size() : 0;
        for (int i = 1; i <= LOOT_SLOT_COUNT; i++) {
            int idx = i - 1;
            cmd.set("#LootRow" + i + ".Visible", idx < entryCount);
            if (idx < entryCount) {
                ModConfig.NpcLootEntry npcLoot = cfg.loot.npcs.get(idx);
                cmd.set("#LootNpc" + i + ".Value", npcLoot.npc != null ? npcLoot.npc : "");
                cmd.set("#LootRing" + i + ".Value", npcLoot.ring != null ? npcLoot.ring : "");
                cmd.set("#LootDrop" + i + ".Value", npcLoot.directDropRate * 100);
                cmd.set("#LootFrag" + i + ".Value", npcLoot.fragmentDropRate * 100);
            }
        }
        sendUpdate(cmd, false);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull ConfigEventData data) {
        ModConfig.Config cfg = ModConfig.getInstance();
        String action = data.action != null ? data.action : "";
        String value = data.value != null ? data.value : "";
        System.out.println("[IRConfig] event: action=[" + action + "] value=[" + value + "] ls=[" + data.lifesteal + "] str=[" + data.strVal + "] num=[" + data.numVal + "]");

        if ("close".equals(action)) {
            ModConfig.save();
            if (IllegalRings.getInstance() != null) {
                IllegalRings.getInstance().reloadRecipes();
            }
            close();
        } else if ("tab".equals(action)) {
            switchTab(value);
        } else if ("spawn".equals(action)) {
            spawnNpcsForSlot(ref, store, value);
        } else if ("delete".equals(action)) {
            deleteLootEntry(value);
        } else if ("reset_freepets".equals(action)) {
            resetAllFreePets();
        } else if ("add".equals(action)) {
            addLootEntry();
        } else if ("freepet_msg".equals(action) && cfg != null && data.strVal != null) {
            cfg.freePetMessage = data.strVal;
        } else if ("nv_item".equals(action) && cfg != null && data.strVal != null) {
            cfg.gameplay.nightVisionTriggerItem = data.strVal;
        } else if ("craft_dd".equals(action) && cfg != null && data.strVal != null) {
            setCraftIngredientId(cfg, value, data.strVal);
        } else if ("craft_qty".equals(action) && cfg != null && data.numVal != null) {
            setCraftIngredientQty(cfg, value, Math.round(data.numVal));
        } else if ("loot_npc".equals(action) && cfg != null && data.strVal != null) {
            setLootField(cfg, value, "npc", data.strVal);
        } else if ("loot_ring".equals(action) && cfg != null && data.strVal != null) {
            setLootField(cfg, value, "ring", data.strVal);
        } else if ("loot_drop".equals(action) && cfg != null && data.numVal != null) {
            setLootField(cfg, value, "drop", String.valueOf(data.numVal));
        } else if ("loot_frag".equals(action) && cfg != null && data.numVal != null) {
            setLootField(cfg, value, "frag", String.valueOf(data.numVal));
        } else if (data.lifesteal != null && cfg != null) {
            cfg.gameplay.lifestealPercent = data.lifesteal;
            UICommandBuilder cmd = new UICommandBuilder();
            cmd.set("#LifestealValue.Text", Math.round(data.lifesteal * 100) + "%");
            sendUpdate(cmd, false);
        } else if (cfg != null) {
            // CheckBox toggle handlers - flip config boolean
            switch (action) {
                case "en_fly" -> cfg.enabled.flyRing = !cfg.enabled.flyRing;
                case "en_fire" -> cfg.enabled.fireRing = !cfg.enabled.fireRing;
                case "en_water" -> cfg.enabled.waterRing = !cfg.enabled.waterRing;
                case "en_heal" -> cfg.enabled.healRing = !cfg.enabled.healRing;
                case "en_peaceful" -> cfg.enabled.peacefulRing = !cfg.enabled.peacefulRing;
                case "en_gaia" -> cfg.enabled.gaiaMedallion = !cfg.enabled.gaiaMedallion;
                case "cr_fly" -> cfg.craftable.flyRing = !cfg.craftable.flyRing;
                case "cr_fire" -> cfg.craftable.fireRing = !cfg.craftable.fireRing;
                case "cr_water" -> cfg.craftable.waterRing = !cfg.craftable.waterRing;
                case "cr_heal" -> cfg.craftable.healRing = !cfg.craftable.healRing;
                case "cr_peaceful" -> cfg.craftable.peacefulRing = !cfg.craftable.peacefulRing;
                case "cr_gaia" -> cfg.craftable.gaiaMedallion = !cfg.craftable.gaiaMedallion;
                case "nightvision" -> cfg.gameplay.nightVisionEnabled = !cfg.gameplay.nightVisionEnabled;
                case "backpack" -> cfg.backpackEnabled = !cfg.backpackEnabled;
                case "notification" -> cfg.notification = !cfg.notification;
                case "tiffynpc" -> cfg.spawnTiffyNpc = !cfg.spawnTiffyNpc;
                case "analytics" -> cfg.analyticsEnabled = !cfg.analyticsEnabled;
                case "loot_en" -> cfg.loot.enabled = !cfg.loot.enabled;
                case "freepet_en" -> cfg.noFreePet = !cfg.noFreePet;
            }
        }
    }

    private void addLootEntry() {
        ModConfig.Config cfg = ModConfig.getInstance();
        if (cfg == null || cfg.loot.npcs == null) return;
        if (cfg.loot.npcs.size() >= LOOT_SLOT_COUNT) return;

        cfg.loot.npcs.add(new ModConfig.NpcLootEntry());
        refreshLootUI(cfg);
    }

    private void deleteLootEntry(String slotStr) {
        try {
            int slot = Integer.parseInt(slotStr) - 1;
            ModConfig.Config cfg = ModConfig.getInstance();
            if (cfg == null || cfg.loot.npcs == null || slot < 0 || slot >= cfg.loot.npcs.size()) return;

            cfg.loot.npcs.remove(slot);
            refreshLootUI(cfg);
        } catch (NumberFormatException e) {
            // Ignore
        }
    }

    private void refreshLootUI(ModConfig.Config cfg) {
        UICommandBuilder cmd = new UICommandBuilder();
        int entryCount = (cfg.loot.npcs != null) ? cfg.loot.npcs.size() : 0;
        for (int i = 1; i <= LOOT_SLOT_COUNT; i++) {
            int idx = i - 1;
            cmd.set("#LootRow" + i + ".Visible", idx < entryCount);
            if (idx < entryCount) {
                ModConfig.NpcLootEntry entry = cfg.loot.npcs.get(idx);
                cmd.set("#LootNpc" + i + ".Value", entry.npc != null ? entry.npc : "");
                cmd.set("#LootRing" + i + ".Value", entry.ring != null ? entry.ring : "");
                cmd.set("#LootDrop" + i + ".Value", entry.directDropRate * 100);
                cmd.set("#LootFrag" + i + ".Value", entry.fragmentDropRate * 100);
            }
        }
        sendUpdate(cmd, false);
    }

    private void spawnNpcsForSlot(Ref<EntityStore> ref, Store<EntityStore> store, String slotStr) {
        try {
            int slot = Integer.parseInt(slotStr) - 1;
            ModConfig.Config cfg = ModConfig.getInstance();
            if (cfg == null || cfg.loot.npcs == null || slot < 0 || slot >= cfg.loot.npcs.size()) return;

            String npcRole = cfg.loot.npcs.get(slot).npc;
            if (npcRole == null || npcRole.isEmpty()) return;

            TransformComponent transform = (TransformComponent) store.getComponent(ref, TransformComponent.getComponentType());
            if (transform == null) return;
            Vector3d pos = transform.getPosition();

            for (int i = 0; i < 10; i++) {
                double offsetX = (i % 5) * 2.0 - 4.0;
                double offsetZ = (i / 5) * 2.0 + 3.0;
                Vector3d spawnPos = new Vector3d(pos.x + offsetX, pos.y, pos.z + offsetZ);
                NPCPlugin.get().spawnNPC(store, npcRole, null, spawnPos, Vector3f.ZERO);
            }
        } catch (Exception e) {
            LOG.severe("[ERR-1016] spawnNpcsForSlot: " + e.getMessage());
        }
    }

    private void setCraftIngredientId(ModConfig.Config cfg, String slot, String itemId) {
        try {
            String[] parts = slot.split("_");
            if (parts.length != 2) return;
            int idx = Integer.parseInt(parts[1]) - 1;
            String targetItemId = switch (parts[0]) {
                case "Fly" -> "Jewelry_Fly_Ring";
                case "Fire" -> "Jewelry_Fire_Ring";
                case "Water" -> "Jewelry_Water_Ring";
                case "Heal" -> "Jewelry_Heal_Ring";
                case "Peace" -> "Jewelry_Peacefull_Ring";
                case "Gaia" -> "Jewelry_Gaia_Medallion";
                default -> null;
            };
            if (targetItemId == null || cfg.recipeOverrides == null) return;
            for (ModConfig.RecipeOverride ro : cfg.recipeOverrides) {
                if (targetItemId.equals(ro.targetItemId)) {
                    while (ro.ingredients.size() <= idx) ro.ingredients.add(new ModConfig.Ingredient("", 1));
                    ro.ingredients.get(idx).id = itemId;
                    return;
                }
            }
        } catch (Exception e) {
            LOG.severe("[ERR-1017] setCraftIngredientId: " + e.getMessage());
        }
    }

    private void setCraftIngredientQty(ModConfig.Config cfg, String slot, int qty) {
        try {
            String[] parts = slot.split("_");
            if (parts.length != 2) return;
            int idx = Integer.parseInt(parts[1]) - 1;
            String targetItemId = switch (parts[0]) {
                case "Fly" -> "Jewelry_Fly_Ring";
                case "Fire" -> "Jewelry_Fire_Ring";
                case "Water" -> "Jewelry_Water_Ring";
                case "Heal" -> "Jewelry_Heal_Ring";
                case "Peace" -> "Jewelry_Peacefull_Ring";
                case "Gaia" -> "Jewelry_Gaia_Medallion";
                default -> null;
            };
            if (targetItemId == null || cfg.recipeOverrides == null) return;
            for (ModConfig.RecipeOverride ro : cfg.recipeOverrides) {
                if (targetItemId.equals(ro.targetItemId)) {
                    while (ro.ingredients.size() <= idx) ro.ingredients.add(new ModConfig.Ingredient("", 1));
                    ro.ingredients.get(idx).amount = Math.max(1, qty);
                    return;
                }
            }
        } catch (Exception e) {
            LOG.severe("[ERR-1018] setCraftIngredientQty: " + e.getMessage());
        }
    }

    private void setLootField(ModConfig.Config cfg, String slotStr, String field, String value) {
        try {
            int slot = Integer.parseInt(slotStr) - 1;
            if (cfg.loot.npcs == null || slot < 0 || slot >= cfg.loot.npcs.size()) return;
            ModConfig.NpcLootEntry entry = cfg.loot.npcs.get(slot);
            switch (field) {
                case "npc" -> entry.npc = value;
                case "ring" -> entry.ring = value;
                case "drop" -> entry.directDropRate = Float.parseFloat(value) / 100.0;
                case "frag" -> entry.fragmentDropRate = Float.parseFloat(value) / 100.0;
            }
        } catch (Exception e) {
            LOG.severe("[ERR-1019] setLootField: " + e.getMessage());
        }
    }

    private void resetAllFreePets() {
        IllegalRings ir = IllegalRings.getInstance();
        if (ir == null) return;
        int count = ir.getFreePetStorage().resetAll();
        UICommandBuilder cmd = new UICommandBuilder();
        cmd.set("#FreePetStatus.Text", "Reset free pet for " + count + " player(s).");
        sendUpdate(cmd, false);
    }

    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        ModConfig.save();
        if (IllegalRings.getInstance() != null) {
            IllegalRings.getInstance().reloadRecipes();
        }
        super.onDismiss(ref, store);
    }

    public static class ConfigEventData {
        public static final BuilderCodec<ConfigEventData> CODEC = BuilderCodec.builder(ConfigEventData.class, ConfigEventData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (e, v) -> e.action = v, e -> e.action)
            .add()
            .append(new KeyedCodec<>("Value", Codec.STRING), (e, v) -> e.value = v != null ? v : "", e -> e.value)
            .add()
            .append(new KeyedCodec<>("@Lifesteal", Codec.FLOAT), (e, v) -> e.lifesteal = v, e -> e.lifesteal)
            .add()
            .append(new KeyedCodec<>("@StrVal", Codec.STRING), (e, v) -> e.strVal = v, e -> e.strVal)
            .add()
            .append(new KeyedCodec<>("@NumVal", Codec.FLOAT), (e, v) -> e.numVal = v, e -> e.numVal)
            .add()
            .build();

        public String action = "";
        public String value = "";
        public Float lifesteal = null;
        public String strVal = null;
        public Float numVal = null;
    }
}
