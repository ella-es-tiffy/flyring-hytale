#!/usr/bin/env python3
"""
apply_config_to_recipes.py - Applies config values to recipe JSONs before build.
This ensures user-edited recipes from mods/tiffy/config.json are used in-game.
"""

import json
import os
import sys

def load_config(config_path):
    """Load and parse the config file."""
    try:
        with open(config_path, 'r') as f:
            return json.load(f)
    except FileNotFoundError:
        print(f"⚠️  Config not found at {config_path}, using defaults from JSONs")
        return None
    except json.JSONDecodeError as e:
        print(f"❌ Config JSON is invalid: {e}")
        print("   Using defaults from JSONs")
        return None

def apply_recipe(json_file, ingredients):
    """Apply recipe ingredients to a JSON file."""
    try:
        with open(json_file, 'r') as f:
            data = json.load(f)
        
        # Convert config format to Hytale JSON format
        recipe_inputs = []
        for ing in ingredients:
            recipe_inputs.append({
                "ItemId": ing["itemId"],
                "Quantity": ing["quantity"]
            })
        
        # Update the Recipe.Input field
        if "Recipe" in data:
            data["Recipe"]["Input"] = recipe_inputs
            
            with open(json_file, 'w') as f:
                json.dump(data, f, indent=4)
            
            return True
        else:
            print(f"⚠️  No Recipe field in {json_file}")
            return False
            
    except Exception as e:
        print(f"❌ Error applying recipe to {json_file}: {e}")
        return False

def main():
    # Paths
    config_path = "../../mods/tiffy/config.json"
    resources_dir = "resources/Server/Item/Items/Tool/Jewelry"
    
    # Mapping of config keys to JSON files
    recipe_map = {
        "flyRing": "Jewelry_Fly_Ring.json",
        "fireRing": "Jewelry_Fire_Ring.json",
        "waterRing": "Jewelry_Water_Ring.json",
        "healRing": "Jewelry_Heal_Ring.json",
        "peacefulRing": "Jewelry_Peacefull_Ring.json"
    }
    
    print("=" * 60)
    print("🔧 Applying Config to Recipes")
    print("=" * 60)
    
    # Load config
    config = load_config(config_path)
    
    if not config or "recipes" not in config:
        print("✓ No valid config found, keeping default recipes")
        return 0
    
    recipes = config["recipes"]
    applied_count = 0
    
    # Apply each recipe
    for config_key, json_file in recipe_map.items():
        if config_key not in recipes:
            print(f"⚠️  {config_key} not in config, skipping")
            continue
        
        json_path = os.path.join(resources_dir, json_file)
        if not os.path.exists(json_path):
            print(f"⚠️  {json_path} not found, skipping")
            continue
        
        ingredients = recipes[config_key]
        print(f"✓ Applying {config_key} ({len(ingredients)} ingredients) to {json_file}")
        
        if apply_recipe(json_path, ingredients):
            applied_count += 1
    
    print("=" * 60)
    print(f"✅ Applied {applied_count}/{len(recipe_map)} recipes from config")
    print("=" * 60)
    
    return 0

if __name__ == "__main__":
    sys.exit(main())
