package com.tiffy.flyring;

import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.assetstore.AssetStore;
import com.hypixel.hytale.assetstore.AssetUpdateQuery;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * RecipeManager - Handles runtime recipe overrides for the IllegalRings mod.
 * Uses Reflection and AssetStore synchronization.
 */
public class RecipeManager {

    public static void applyOverrides(List<ModConfig.RecipeOverride> overrides) {
        if (overrides == null || overrides.isEmpty()) return;

        DefaultAssetMap<String, CraftingRecipe> assetMap = CraftingRecipe.getAssetMap();
        
        for (ModConfig.RecipeOverride override : overrides) {
            String target = override.targetItemId;
            List<CraftingRecipe> targetRecipes = new ArrayList<>();

            // 1. Literal Match
            CraftingRecipe exact = assetMap.getAsset(target);
            if (exact != null) targetRecipes.add(exact);

            // 2. Pattern Match for generated recipes
            for (String key : assetMap.getAssetMap().keySet()) {
                if (key.startsWith(target) && key.contains("_Recipe_Generated_")) {
                    CraftingRecipe r = assetMap.getAsset(key);
                    if (r != null && !targetRecipes.contains(r)) {
                        targetRecipes.add(r);
                    }
                }
            }

            for (CraftingRecipe recipe : targetRecipes) {
                applyRecipeChange(recipe, override);
            }
        }
    }

    private static void applyRecipeChange(CraftingRecipe recipe, ModConfig.RecipeOverride override) {
        try {
            Field inputField = CraftingRecipe.class.getDeclaredField("input");
            inputField.setAccessible(true);

            List<MaterialQuantity> newInputs = new ArrayList<>();
            for (ModConfig.Ingredient ing : override.ingredients) {
                newInputs.add(new MaterialQuantity(ing.id, null, null, ing.amount, null));
            }

            inputField.set(recipe, newInputs.toArray(new MaterialQuantity[0]));

            // Sync with AssetStore 1:1
            AssetStore<String, CraftingRecipe, DefaultAssetMap<String, CraftingRecipe>> store = CraftingRecipe.getAssetStore();
            String packId = CraftingRecipe.getAssetMap().getAssetPack(recipe.getId());
            if (packId == null) packId = "Hytale:Hytale";

            store.loadAssets(packId, Collections.singletonList(recipe), AssetUpdateQuery.DEFAULT, true);
            
            System.out.println("[IllegalRings] Configured recipe: " + recipe.getId());

        } catch (Exception e) {
            System.err.println("[IllegalRings] Failed to override " + recipe.getId() + ": " + e.getMessage());
        }
    }
}
