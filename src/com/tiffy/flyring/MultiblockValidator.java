package com.tiffy.flyring;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;


/**
 * Validates the multiblock structure for the Ring Pedestal altar.
 * Supports TWO altar types: Light (Marble) and Dark (Shale).
 *
 * LIGHT ALTAR Structure (5x4 base, 2 layers):
 *
 * Layer 2 (y = pedestal level):
 * x: -2 -1 0 +1 +2
 * z=-2: [E] [S] [G] [S] [E] <- S=Stairs, G=Light Statue
 * z=-1: [E] [E] [E] [E] [E]
 * z= 0: [E] [E] [P] [E] [E] <- P=Pedestal (reference point)
 * z=+1: [E] [E] [E] [E] [E]
 *
 * Layer 1 (y-1 = floor):
 * All 20 blocks = Rock_Marble_Brick
 *
 * DARK ALTAR Structure (5x4 base, 2 layers):
 *
 * Layer 2 (y = pedestal level):
 * x: -2 -1 0 +1 +2
 * z=-2: [E] [W] [G] [W] [E] <- W=Shale Wall, G=Dark Statue
 * z=-1: [E] [E] [E] [E] [E]
 * z= 0: [E] [E] [P] [E] [E] <- P=Pedestal (reference point)
 * z=+1: [E] [E] [E] [E] [E]
 *
 * Layer 1 (y-1 = floor):
 * All 20 blocks = Rock_Shale_Cobble
 *
 * Both altars require:
 * - FLEXIBLE: 1x Furniture_Temple_Dark_Candle anywhere on layer 2
 * - (Azure Fruit removed - not placeable)
 */
public class MultiblockValidator {

    // === LIGHT ALTAR Block IDs ===
    private static final String MARBLE_BRICK = "Rock_Marble_Brick";
    private static final String MARBLE_STAIRS = "Rock_Marble_Brick_Stairs";
    private static final String LIGHT_STATUE = "Furniture_Temple_Light_Statue";

    // === DARK ALTAR Block IDs ===
    private static final String SHALE_COBBLE = "Rock_Shale_Cobble";
    private static final String SHALE_WALL = "Rock_Shale_Cobble_Wall";
    private static final String DARK_STATUE = "Furniture_Temple_Dark_Statue_Gaia";

    // === SHARED (both altars) ===
    private static final String TEMPLE_CANDLE = "Furniture_Temple_Dark_Candle";


    /**
     * Rotation definitions for the 4 cardinal directions.
     * Each row: {floorDxMin, floorDxMax, floorDzMin, floorDzMax,
     *            statueDx, statueDz, stairLeftDx, stairLeftDz, stairRightDx, stairRightDz}
     *
     * The altar is 5 blocks wide × 4 blocks deep with the decorated edge (statue + stairs)
     * on one side and the pedestal 2 blocks away from the decorated edge.
     */
    private static final int[][] ROTATIONS = {
        // NORTH: decorated edge at z-2, floor extends z-2..+1, x-2..+2
        {-2, 2, -2, 1,   0, -2,  -1, -2,   1, -2},
        // SOUTH: decorated edge at z+2, floor extends z-1..+2, x-2..+2
        {-2, 2, -1, 2,   0,  2,   1,  2,  -1,  2},
        // EAST: decorated edge at x+2, floor extends x-1..+2, z-2..+2
        {-1, 2, -2, 2,   2,  0,   2, -1,   2,  1},
        // WEST: decorated edge at x-2, floor extends x-2..+1, z-2..+2
        {-2, 1, -2, 2,  -2,  0,  -2,  1,  -2, -1},
    };

    private static final String[] ROTATION_NAMES = {"NORTH", "SOUTH", "EAST", "WEST"};

    /**
     * Altar type enum for tracking which altar was validated.
     */
    public enum AltarType {
        NONE, LIGHT, DARK
    }

    /**
     * Result of multiblock validation.
     */
    public static class ValidationResult {
        public boolean valid;
        public String failureReason;
        public AltarType altarType = AltarType.NONE;
        public int floorCount;
        public boolean hasLeftWall; // Stairs (Light) or Wall (Dark)
        public boolean hasRightWall; // Stairs (Light) or Wall (Dark)
        public boolean hasStatue;
        public boolean hasCandle;

        public ValidationResult() {
            this.valid = false;
            this.failureReason = "Not validated";
        }
    }

    /**
     * Validates the multiblock structure around the pedestal.
     * Supports both LIGHT (Marble) and DARK (Shale) altar variants.
     * Tries all 4 cardinal rotations and succeeds if any rotation matches.
     *
     * @param player       The player (used to get world)
     * @param px           Pedestal X coordinate
     * @param py           Pedestal Y coordinate
     * @param pz           Pedestal Z coordinate
     * @param requiredType The altar type required by the placed block (LIGHT or DARK)
     * @return ValidationResult with details
     */
    public static ValidationResult validate(Player player, int px, int py, int pz, AltarType requiredType) {
        ValidationResult result = new ValidationResult();

        try {
            World world = player.getWorld();
            if (world == null) {
                result.failureReason = "World not available";
                return result;
            }

            if (requiredType == AltarType.NONE) {
                result.failureReason = "Unknown altar block type";
                return result;
            }

            // Try all 4 rotations, return the first valid one
            ValidationResult bestResult = null;

            for (int r = 0; r < ROTATIONS.length; r++) {
                ValidationResult rotResult = validateRotation(world, px, py, pz, requiredType, ROTATIONS[r]);
                if (rotResult.valid) {
                    return rotResult;
                }
                // Keep track of the best (closest to valid) result for error reporting
                if (bestResult == null || rotResult.floorCount > bestResult.floorCount) {
                    bestResult = rotResult;
                }
            }

            return bestResult;

        } catch (Exception e) {
            result.failureReason = "Validation error: " + e.getMessage();
            return result;
        }
    }

    /**
     * Validates one specific rotation of the altar structure.
     */
    private static ValidationResult validateRotation(World world, int px, int py, int pz,
            AltarType requiredType, int[] rot) {
        ValidationResult result = new ValidationResult();
        result.altarType = requiredType;

        int floorDxMin = rot[0], floorDxMax = rot[1], floorDzMin = rot[2], floorDzMax = rot[3];
        int statueDx = rot[4], statueDz = rot[5];
        int stairLDx = rot[6], stairLDz = rot[7];
        int stairRDx = rot[8], stairRDz = rot[9];

        String requiredFloor = (requiredType == AltarType.LIGHT) ? MARBLE_BRICK : SHALE_COBBLE;

        // Step 1: Count floor blocks
        int floorCount = 0;
        for (int dz = floorDzMin; dz <= floorDzMax; dz++) {
            for (int dx = floorDxMin; dx <= floorDxMax; dx++) {
                String blockId = getBlockId(world, px + dx, py - 1, pz + dz);
                if (requiredFloor.equals(blockId)) {
                    floorCount++;
                }
            }
        }
        result.floorCount = floorCount;

        if (floorCount == 0) {
            String floorName = (requiredType == AltarType.LIGHT) ? "Marble Brick" : "Shale Cobble";
            result.failureReason = "Wrong floor type for this altar (need " + floorName + ")";
            return result;
        }

        if (floorCount < 20) {
            result.failureReason = "Floor incomplete: " + floorCount + "/20 " + requiredFloor;
            return result;
        }

        // Step 2: Check fixed positions (stairs/walls + statue)
        String stairsOrWall = (requiredType == AltarType.LIGHT) ? MARBLE_STAIRS : SHALE_WALL;
        String statue = (requiredType == AltarType.LIGHT) ? LIGHT_STATUE : DARK_STATUE;

        String leftBlock = getBlockId(world, px + stairLDx, py, pz + stairLDz);
        String rightBlock = getBlockId(world, px + stairRDx, py, pz + stairRDz);
        result.hasLeftWall = stairsOrWall.equals(leftBlock);
        result.hasRightWall = stairsOrWall.equals(rightBlock);

        // Statue: check both y and y+1 for tall furniture
        String statuePos = getBlockId(world, px + statueDx, py, pz + statueDz);
        String statueAbove = getBlockId(world, px + statueDx, py + 1, pz + statueDz);
        result.hasStatue = statue.equals(statuePos) || statue.equals(statueAbove);

        String componentName = (requiredType == AltarType.LIGHT) ? "stairs" : "wall";
        if (!result.hasLeftWall) {
            result.failureReason = "Missing left " + componentName + " (" + stairsOrWall + ")";
            return result;
        }
        if (!result.hasRightWall) {
            result.failureReason = "Missing right " + componentName + " (" + stairsOrWall + ")";
            return result;
        }
        if (!result.hasStatue) {
            result.failureReason = "Missing statue (" + statue + ")";
            return result;
        }

        // Step 3: Check flexible items on altar level (same 5x4 area as floor)
        for (int dz = floorDzMin; dz <= floorDzMax; dz++) {
            for (int dx = floorDxMin; dx <= floorDxMax; dx++) {
                String blockId = getBlockId(world, px + dx, py, pz + dz);
                if (TEMPLE_CANDLE.equals(blockId)) {
                    result.hasCandle = true;
                }
            }
        }

        if (!result.hasCandle) {
            result.failureReason = "Missing Candle (Furniture_Temple_Dark_Candle) on altar level";
            return result;
        }
        // All checks passed!
        result.valid = true;
        result.failureReason = null;
        return result;
    }

    /**
     * Gets the block ID at the specified position, or "Empty" if air/null.
     */
    private static String getBlockId(World world, int x, int y, int z) {
        try {
            var blockType = world.getBlockType(x, y, z);
            return blockType != null ? blockType.getId() : "Empty";
        } catch (Exception e) {
            return "ERROR";
        }
    }

    /**
     * Logs validation details for debugging.
     */
    public static void logValidation(com.hypixel.hytale.server.core.plugin.JavaPlugin plugin,
            ValidationResult result, int px, int py, int pz) {
        Log.info(plugin, "[Multiblock] Validation at " + px + "," + py + "," + pz + ":");
        Log.info(plugin, "[Multiblock]   Altar Type: " + result.altarType);
        Log.info(plugin, "[Multiblock]   Floor: " + result.floorCount + "/20");
        Log.info(plugin, "[Multiblock]   Left Wall/Stairs: " + result.hasLeftWall);
        Log.info(plugin, "[Multiblock]   Right Wall/Stairs: " + result.hasRightWall);
        Log.info(plugin, "[Multiblock]   Statue: " + result.hasStatue);
        Log.info(plugin, "[Multiblock]   Candle: " + result.hasCandle);
        Log.info(plugin, "[Multiblock]   Result: "
                + (result.valid ? "VALID (" + result.altarType + " ALTAR)" : "INVALID - " + result.failureReason));
    }
}
