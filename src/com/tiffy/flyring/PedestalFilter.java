package com.tiffy.flyring;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerBlockWindow;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ItemContainerWindow;
import com.hypixel.hytale.server.core.entity.entities.player.windows.Window;
import com.hypixel.hytale.server.core.entity.entities.player.windows.WindowManager;
import com.hypixel.hytale.server.core.event.events.entity.LivingEntityInventoryChangeEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.protocol.packets.interface_.ShowEventTitle;
import com.hypixel.hytale.protocol.FormattedMessage;

import java.awt.Color;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Filters the Ring Pedestal container to only allow rings, the Gaia Medallion,
 * and display items. Also manages display entity spawning above pedestals.
 */
public class PedestalFilter {

    private static final Set<String> ALLOWED_ITEMS = Set.of(
        "Jewelry_Fly_Ring",
        "Jewelry_Fire_Ring",
        "Jewelry_Water_Ring",
        "Jewelry_Heal_Ring",
        "Jewelry_Peacefull_Ring",
        "Jewelry_Gaia_Medallion",
        "Ring_Display_Fly"
    );

    private static final Set<String> DISPLAY_ITEMS = Set.of(
        "Jewelry_Fly_Ring",
        "Jewelry_Fire_Ring",
        "Jewelry_Water_Ring",
        "Jewelry_Heal_Ring",
        "Jewelry_Peacefull_Ring",
        "Jewelry_Gaia_Medallion",
        "Ring_Display_Fly"
    );

    private static final Map<String, String> RING_NAMES = Map.of(
        "Jewelry_Fly_Ring", "Fly Ring",
        "Jewelry_Fire_Ring", "Fire Ring",
        "Jewelry_Water_Ring", "Water Ring",
        "Jewelry_Heal_Ring", "Heal Ring",
        "Jewelry_Peacefull_Ring", "Peaceful Ring",
        "Jewelry_Gaia_Medallion", "Gaia Medallion"
    );

    private final JavaPlugin plugin;

    public PedestalFilter(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Called from the centralized inventory change handler.
     * Checks the player's open container windows for pedestal (1-slot)
     * and rejects non-ring items. Also manages display entities above pedestals.
     */
    public void onInventoryChange(LivingEntityInventoryChangeEvent event, Player player) {
        try {
            WindowManager wm = player.getWindowManager();
            if (wm == null) return;

            for (Window window : wm.getWindows()) {
                if (!(window instanceof ItemContainerWindow icw)) continue;

                ItemContainer container = icw.getItemContainer();
                if (container == null || container.getCapacity() != 1) continue;

                ItemStack stack = container.getItemStack((short) 0);

                // Validation: reject non-allowed items
                if (stack != null && !stack.isEmpty()) {
                    String itemId = stack.getItemId();
                    if (itemId != null && !ALLOWED_ITEMS.contains(itemId)) {
                        container.removeItemStackFromSlot((short) 0);
                        player.getInventory().getHotbar().addItemStack(stack);
                        Log.info(plugin, "[Pedestal] Rejected '" + itemId + "' - returned to " + RingUtils.getUsername(player));
                        continue;
                    }
                }

                // Update CSV and display entity (only if we know the block position)
                if (window instanceof ContainerBlockWindow cbw) {
                    int bx = cbw.getX(), by = cbw.getY(), bz = cbw.getZ();

                    // CSV Update - always
                    String itemId = (stack != null && !stack.isEmpty()) ? stack.getItemId() : "";
                    PedestalRegistry.setItem(bx, by, bz, itemId);

                    // Sofort Textur setzen basierend auf CSV Item-State (Trigger: on/off)
                    setPedestalTexture(player, bx, by, bz, !itemId.isEmpty());

                    // Real-time ring status update for pedestal owner
                    UUID ownerUuid = PedestalRegistry.getOwner(bx, by, bz);
                    if (ownerUuid != null && IllegalRings.getInstance() != null) {
                        IllegalRings.getInstance().refreshRingStatusForPlayer(ownerUuid);
                    }

                    // Queue texture update with delay (reads from CSV when applied)
                    // Delay of 20 ticks (1 second) ensures CloseWindow state doesn't override
                    LootDropTickSystem.queueTextureUpdate(bx, by, bz, 20);

                    // Multiblock validation when ring is placed
                    if (!itemId.isEmpty() && RING_NAMES.containsKey(itemId)) {
                        String ringName = RING_NAMES.get(itemId);

                        // Check if already verified (skip validation)
                        if (PedestalRegistry.isVerified(bx, by, bz)) {
                            // Already verified - just show success message (we don't know the altar type for old pedestals)
                            sendActivationMessage(player, ringName, "SACRED", bx, by, bz);
                            Log.info(plugin, "[Pedestal] " + RingUtils.getUsername(player) + " bound " + ringName + " to VERIFIED pedestal at " + bx + "," + by + "," + bz);
                        } else {
                            // Determine required altar type from the placed block
                            World world = player.getWorld();
                            MultiblockValidator.AltarType requiredType = MultiblockValidator.AltarType.NONE;
                            BlockType altarBlock = (world != null) ? world.getBlockType(bx, by, bz) : null;
                            if (altarBlock != null) {
                                String altarId = altarBlock.getId();
                                // Block ID may include state suffix like "*Ring_Altar_Dark_State_Definitions_Activated"
                                if (altarId.contains("Ring_Altar_Light")) {
                                    requiredType = MultiblockValidator.AltarType.LIGHT;
                                } else if (altarId.contains("Ring_Altar_Dark")) {
                                    requiredType = MultiblockValidator.AltarType.DARK;
                                }
                            }

                            // Validate multiblock structure with required type
                            MultiblockValidator.ValidationResult result = MultiblockValidator.validate(player, bx, by, bz, requiredType);

                            // Debug logging - scan and log all blocks in 5x4x3 area
                            boolean debug = ModConfig.getInstance() != null && ModConfig.getInstance().debugLogging;
                            if (debug) {
                                debugScanMultiblock(player, bx, by, bz);
                                MultiblockValidator.logValidation(plugin, result, bx, by, bz);
                            }

                            if (result.valid) {
                                // Mark as verified permanently
                                PedestalRegistry.setVerified(bx, by, bz, true);
                                String altarTypeName = result.altarType == MultiblockValidator.AltarType.DARK ? "DARK" : "LIGHT";
                                sendActivationMessage(player, ringName, altarTypeName, bx, by, bz);

                                // Texture already set above via setPedestalTexture

                                Log.info(plugin, "[Pedestal] " + RingUtils.getUsername(player) + " ACTIVATED " + altarTypeName + " altar with " + ringName + " at " + bx + "," + by + "," + bz);
                            } else {
                                // Structure incomplete - short feedback
                                player.sendMessage(Message.raw("[Rings] Altar incomplete").color(Color.RED));
                                Log.info(plugin, "[Pedestal] " + RingUtils.getUsername(player) + " tried " + ringName + " but altar incomplete: " + result.failureReason);
                            }
                        }
                    }

                    // Display entity - DISABLED
                    // if (stack != null && !stack.isEmpty() && DISPLAY_ITEMS.contains(stack.getItemId())) {
                    //     PedestalDisplaySystem.queueSpawn(bx, by, bz, stack.getItemId());
                    // } else {
                    //     PedestalDisplaySystem.queueDespawn(bx, by, bz);
                    // }
                }
            }
        } catch (Exception e) {
            // Player might not have windows open - ignore
        }
    }

    /**
     * Sends the epic activation message when a ring is successfully bound.
     * Shows screen banner and plays discovery sound.
     * @param altarType "LIGHT", "DARK", or "SACRED"
     */
    private void sendActivationMessage(Player player, String ringName, String altarType, int bx, int by, int bz) {
        // Send epic screen banner (like biome discoveries)
        sendAltarBanner(player, altarType);

        // Queue the altar activation sound to be played on world thread
        PlayerRef playerRef = player.getPlayerRef();
        if (playerRef != null) {
            LootDropTickSystem.queueAltarSound(playerRef, altarType, bx + 0.5, by + 1.0, bz + 0.5);
        }

        // Debug: chat message for testing (only when debugLogging enabled)
        boolean debug = ModConfig.getInstance() != null && ModConfig.getInstance().debugLogging;
        if (debug) {
            Color borderColor = altarType.equals("DARK") ? new Color(75, 0, 130) : new Color(138, 43, 226);
            Color textColor = altarType.equals("DARK") ? new Color(150, 120, 180) : new Color(200, 160, 255);

            player.sendMessage(Message.raw("").color(Color.DARK_GRAY));
            player.sendMessage(Message.raw("✦ ══════════════════════════════ ✦").color(borderColor));
            player.sendMessage(Message.raw("  " + ringName + " bound to altar").color(textColor));
            player.sendMessage(Message.raw("  ◆ SERVER-WIDE EFFECT ACTIVE ◆").color(new Color(0, 255, 127)));
            player.sendMessage(Message.raw("✦ ══════════════════════════════ ✦").color(borderColor));
        }
    }

    /**
     * Sends the epic screen banner notification for altar activation.
     * Uses Hytale's ShowEventTitle packet (same as biome discoveries).
     */
    private void sendAltarBanner(Player player, String altarType) {
        try {
            // Title and subtitle based on altar type
            String titleText;
            String subtitleText;
            String titleColor;

            if (altarType.equals("DARK")) {
                titleText = "SHADOW ALTAR AWAKENED";
                subtitleText = "Ancient darkness flows through you";
                titleColor = "#8B0000";  // Dark red
            } else if (altarType.equals("LIGHT")) {
                titleText = "SACRED ALTAR AWAKENED";
                subtitleText = "Divine light empowers your soul";
                titleColor = "#FFD700";  // Gold
            } else {
                titleText = "ALTAR BOND RESTORED";
                subtitleText = "Your power resonates once more";
                titleColor = "#9370DB";  // Medium purple
            }

            FormattedMessage title = new FormattedMessage();
            title.rawText = titleText;
            title.color = titleColor;

            FormattedMessage subtitle = new FormattedMessage();
            subtitle.rawText = subtitleText;
            subtitle.color = "#FFFFFF";

            ShowEventTitle packet = new ShowEventTitle(
                0.5f,    // fadeInDuration
                0.5f,    // fadeOutDuration
                3.0f,    // duration
                null,    // icon
                true,    // isMajor (big style)
                title,
                subtitle
            );

            PlayerRef playerRef = player.getPlayerRef();
            if (playerRef != null) {
                PacketUtil.sendPacket(playerRef, packet);
            }
        } catch (Exception e) {
            // Banner failed, chat message still shows
        }
    }

    /**
     * DEBUG: Scans the 5x4x3 multiblock area and logs ALL block IDs.
     * Only runs when debugLogging is enabled in config.
     */
    private void debugScanMultiblock(Player player, int px, int py, int pz) {
        try {
            World world = player.getWorld();
            if (world == null) return;

            Log.info(plugin, "[BLOCK SCAN] ========== 5x4x3 Area at Pedestal " + px + "," + py + "," + pz + " ==========");

            for (int layer = -1; layer <= 1; layer++) {
                int y = py + layer;
                String layerName = layer == -1 ? "FLOOR (y-1)" : layer == 0 ? "PEDESTAL (y)" : "ABOVE (y+1)";
                Log.info(plugin, "[BLOCK SCAN] --- " + layerName + " ---");

                for (int dz = -2; dz <= 1; dz++) {
                    StringBuilder row = new StringBuilder();
                    row.append("z").append(dz >= 0 ? "+" : "").append(dz).append(": ");

                    for (int dx = -2; dx <= 2; dx++) {
                        try {
                            var blockType = world.getBlockType(px + dx, y, pz + dz);
                            String blockId = blockType != null ? blockType.getId() : "Empty";
                            row.append("[").append(blockId).append("] ");
                        } catch (Exception e) {
                            row.append("[ERROR] ");
                        }
                    }
                    Log.info(plugin, "[BLOCK SCAN] " + row.toString());
                }
            }
            Log.info(plugin, "[BLOCK SCAN] ============================================================");
        } catch (Exception e) {
            Log.info(plugin, "[BLOCK SCAN] Error: " + e.getMessage());
        }
    }

    /**
     * Sets the pedestal block texture based on whether a ring is inside.
     * Uses chunk.setBlockInteractionState() with persist=true for permanent state change.
     * @param activated true = Ring inside (Top_On), false = Empty (Top_Off/default)
     */
    private void setPedestalTexture(Player player, int bx, int by, int bz, boolean activated) {
        try {
            World world = player.getWorld();
            if (world == null) return;

            // Get chunk for this block position
            WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(bx, bz));
            if (chunk == null) {
                Log.info(plugin, "[Pedestal] Chunk not in memory at " + bx + "," + bz);
                return;
            }

            // Resolve BASE block type (not a state variant) for state machine lookup
            BlockType blockType = resolveBaseAltarType(world, bx, by, bz);
            if (blockType != null) {
                // State name: "Activated" for on, "default" for off
                String stateName = activated ? "Activated" : "default";
                chunk.setBlockInteractionState(bx, by, bz, blockType, stateName, true);
                chunk.markNeedsSaving();

                boolean debug = ModConfig.getInstance() != null && ModConfig.getInstance().debugLogging;
                if (debug) {
                    Log.info(plugin, "[Pedestal] Texture set to '" + stateName + "' at " + bx + "," + by + "," + bz);
                }
            }
        } catch (Exception e) {
            Log.info(plugin, "[Pedestal] Failed to change texture: " + e.getMessage());
        }
    }

    /**
     * Resolves the BASE block type for a Ring_Altar, even if the block is currently
     * in a state variant (e.g. *Ring_Altar_Light_State_Definitions_Activated).
     * State variants don't have state machine data, so we need the base type
     * for setBlockInteractionState() to work.
     */
    static BlockType resolveBaseAltarType(World world, int x, int y, int z) {
        BlockType blockType = world.getBlockType(x, y, z);
        if (blockType == null) return null;
        String id = blockType.getId();
        if (!id.contains("Ring_Altar")) return null;

        // If already the base type, return as-is
        if (id.equals("Ring_Altar_Light") || id.equals("Ring_Altar_Dark")) {
            return blockType;
        }

        // Resolve base type from variant ID
        String baseId = id.contains("Ring_Altar_Light") ? "Ring_Altar_Light" : "Ring_Altar_Dark";
        return (BlockType) BlockType.getAssetMap().getAsset(baseId);
    }
}
