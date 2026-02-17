package com.tiffy.flyring;

import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.protocol.BenchRequirement;
import com.hypixel.hytale.protocol.BenchType;
import com.hypixel.hytale.assetstore.AssetStore;
import com.hypixel.hytale.assetstore.AssetUpdateQuery;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class RecipeManager {
    private static Field inputField;
    private static Field benchField;
    private static Field memoriesField;

    static {
        try {
            inputField = CraftingRecipe.class.getDeclaredField("input");
            inputField.setAccessible(true);
            benchField = CraftingRecipe.class.getDeclaredField("benchRequirement");
            benchField.setAccessible(true);
            memoriesField = CraftingRecipe.class.getDeclaredField("requiredMemoriesLevel");
            memoriesField.setAccessible(true);
        } catch (Exception e) {
            System.err.println("[IllegalRings] Failed to initialize reflection fields: " + e.getMessage());
        }
    }

    public static boolean applyOverrides(List<ModConfig.RecipeOverride> overrides, ModConfig.RingEnabled craftable,
            boolean broadcast) {
        if (overrides == null || overrides.isEmpty())
            return false;
        DefaultAssetMap<String, CraftingRecipe> assetMap = CraftingRecipe.getAssetMap();
        List<CraftingRecipe> allModifiedRecipes = new ArrayList<>();

        for (ModConfig.RecipeOverride override : overrides) {
            String target = override.targetItemId;

            List<CraftingRecipe> targetRecipes = new ArrayList<>();
            CraftingRecipe exact = assetMap.getAsset(target);
            if (exact != null)
                targetRecipes.add(exact);
            for (String key : assetMap.getAssetMap().keySet()) {
                if (key.startsWith(target) && key.contains("_Recipe_Generated_")) {
                    CraftingRecipe r = assetMap.getAsset(key);
                    if (r != null && !targetRecipes.contains(r))
                        targetRecipes.add(r);
                }
            }
            boolean isCraftable = true;
            if (target.contains("Fly_Ring"))
                isCraftable = craftable.flyRing;
            else if (target.contains("Fire_Ring"))
                isCraftable = craftable.fireRing;
            else if (target.contains("Water_Ring"))
                isCraftable = craftable.waterRing;
            else if (target.contains("Heal_Ring"))
                isCraftable = craftable.healRing;
            else if (target.contains("Peacefull_Ring"))
                isCraftable = craftable.peacefulRing;
            else if (target.contains("Gaia_Medallion"))
                isCraftable = craftable.gaiaMedallion;

            for (CraftingRecipe recipe : targetRecipes) {
                if (modifyRecipe(recipe, override, isCraftable)) {
                    allModifiedRecipes.add(recipe);
                }
            }
        }

        if (!allModifiedRecipes.isEmpty()) {
            AssetStore<String, CraftingRecipe, DefaultAssetMap<String, CraftingRecipe>> store = CraftingRecipe
                    .getAssetStore();
            String packId = "Hytale:Hytale";
            String firstPack = CraftingRecipe.getAssetMap().getAssetPack(allModifiedRecipes.get(0).getId());
            if (firstPack != null)
                packId = firstPack;

            try {
                store.loadAssets(packId, allModifiedRecipes, AssetUpdateQuery.DEFAULT, broadcast);
                System.out.println("[IllegalRings] Batch synchronized " + allModifiedRecipes.size()
                        + " recipes (Broadcast: " + broadcast + ")");
                return true;
            } catch (Exception e) {
                System.err.println("[IllegalRings] Failed to synchronize recipes: " + e.getMessage());
                return false;
            }
        }
        return false;
    }

    private static boolean modifyRecipe(CraftingRecipe recipe, ModConfig.RecipeOverride override, boolean isCraftable) {
        try {
            if (inputField != null) {
                List<MaterialQuantity> newInputs = new ArrayList<>();
                for (ModConfig.Ingredient ing : override.ingredients) {
                    if (ing.id == null || ing.id.isEmpty()) continue;
                    newInputs.add(new MaterialQuantity(ing.id, null, null, ing.amount, null));
                }
                inputField.set(recipe, newInputs.toArray(new MaterialQuantity[0]));
            }

            if (benchField != null && override.benchRequirements != null && !override.benchRequirements.isEmpty()) {
                List<BenchRequirement> benchReqs = new ArrayList<>();
                for (ModConfig.BenchRequirementConfig brc : override.benchRequirements) {
                    BenchType type = BenchType.Crafting;
                    try {
                        type = BenchType.valueOf(brc.type);
                    } catch (Exception ignored) {
                    }
                    String[] categories = brc.categories != null ? brc.categories.toArray(new String[0]) : null;
                    benchReqs.add(new BenchRequirement(type, brc.id, categories, brc.requiredTierLevel));
                }
                benchField.set(recipe, benchReqs.toArray(new BenchRequirement[0]));
            }

            if (!isCraftable && benchField != null && memoriesField != null) {
                benchField.set(recipe, new BenchRequirement[] { new BenchRequirement(BenchType.Crafting,
                        "DISABLED_BY_SERVER", new String[] { "HIDDEN" }, 999) });
                memoriesField.set(recipe, 9999);
                System.out.println("[IllegalRings] HIDDEN recipe for: " + recipe.getId());
            }
            return true;
        } catch (Exception e) {
            System.err.println("[IllegalRings] Failed to modify " + recipe.getId() + ": " + e.getMessage());
            return false;
        }
    }
}
