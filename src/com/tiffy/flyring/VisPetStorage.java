package com.tiffy.flyring;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.logging.Logger;

public class VisPetStorage {

    private static final Path DATA_DIR = Paths.get("data", "vispet");
    private final Logger logger;

    public VisPetStorage(Logger logger) {
        this.logger = logger;
        try {
            Files.createDirectories(DATA_DIR);
        } catch (IOException e) {
            logger.warning("[VisPet] Could not create data directory: " + e.getMessage());
        }
    }

    public void saveInventory(UUID ownerUuid, ItemContainer hotbar) {
        Path file = DATA_DIR.resolve(ownerUuid.toString() + ".csv");
        try (BufferedWriter writer = Files.newBufferedWriter(file)) {
            short capacity = hotbar.getCapacity();
            for (short s = 0; s < capacity; s++) {
                ItemStack stack = hotbar.getItemStack(s);
                if (!ItemStack.isEmpty(stack)) {
                    writer.write(s + "," + stack.getItemId() + "," + stack.getQuantity());
                    writer.newLine();
                }
            }
            logger.info("[VisPet] Saved inventory for " + ownerUuid);
        } catch (IOException e) {
            logger.warning("[VisPet] Failed to save inventory for " + ownerUuid + ": " + e.getMessage());
        }
    }

    public void loadInventory(UUID ownerUuid, ItemContainer hotbar) {
        Path file = DATA_DIR.resolve(ownerUuid.toString() + ".csv");
        if (!Files.exists(file)) return;

        try (BufferedReader reader = Files.newBufferedReader(file)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length != 3) continue;
                try {
                    short slot = Short.parseShort(parts[0]);
                    String itemId = parts[1];
                    int quantity = Integer.parseInt(parts[2]);
                    if (slot >= 0 && slot < hotbar.getCapacity() && quantity > 0) {
                        hotbar.setItemStackForSlot(slot, new ItemStack(itemId, quantity));
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            logger.info("[VisPet] Loaded inventory for " + ownerUuid);
        } catch (IOException e) {
            logger.warning("[VisPet] Failed to load inventory for " + ownerUuid + ": " + e.getMessage());
        }
    }

    public boolean hasSavedInventory(UUID ownerUuid) {
        return Files.exists(DATA_DIR.resolve(ownerUuid.toString() + ".csv"));
    }
}
