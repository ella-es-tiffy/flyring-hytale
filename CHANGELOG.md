# Changelog



## v0.3.96 - 2026-01-25

### Color Codes Fixed (Proper Hytale API)
- **Fixed**: All chat messages now use proper Hytale Message API
  - Changed from invalid `&c`/`§c` codes to `.color(Color.RED)`, etc.
  - Now matches IllegalPipe's working implementation
  - Red: `Color.RED`
  - Orange: `Color.ORANGE`
  - Green: `Color.GREEN`
- **All affected files updated**: FireRing, WaterRing, HealRing, PeacefulRing, FlyRing

## v0.3.95 - 2026-01-25

### Jewelry Icons & Formatting Fixes
- **Fixed**: Item preview icons now display in crafting UI
  - Moved PNGs from `ItemsGenerated/` to `Items/` directory
  - Updated icon paths in all item JSONs
  - Applied proper rotation and scale (0.8)
- **Fixed**: Color codes changed from `§` to `&` for proper formatting
  - All ring and medallion chat messages now properly colored
  - Red (`&c`), Gold (`&6`), Green (`&a`), White (`&f`)

## v0.3.94 - 2026-01-25
- Removed duplicate ItemsGenerated icon folder to fix JAR bloat

## v0.3.93 - 2026-01-25
- Fixed icon visibility in crafting recipes

## v0.3.92 - 2026-01-25
- Fixed icon properties (removed Translation offset)

## v0.3.91 - 2026-01-25

### Gaia Medallion - Fixed Recipe
- **New Item**: `Jewelry_Gaia_Medallion` - Ultimate crafting material combining all 5 rings
- **Fixed Recipe**: Gaia Medallion recipe is now **hardcoded and not configurable**
- **Recipe Requirements**:
  - 1x Jewelry_Fly_Ring
  - 1x Jewelry_Fire_Ring
  - 1x Jewelry_Water_Ring
  - 1x Jewelry_Heal_Ring
  - 1x Jewelry_Peacefull_Ring
- **Workbench**: Workbench_Tinkering required

---

## v0.3.86 - 2026-01-25

### FlyRing - Flight System Overhaul
- **Fixed**: Flight now properly restores after dismounting/sitting/sleeping
- **Fixed**: Client-side prediction no longer ignores server flight state
- **Changed**: `movement.update()` now always sent when ring equipped (forces client sync)
- **Changed**: Heartbeat interval reduced from 2s to 1s for faster recovery

### Backpack Settings - Per-Player Toggle
- **New Command**: `/frbackpack` - toggles backpack ring detection per player
- **New File**: `PlayerSettings.java` - persistent per-player settings (CSV format)
- **Changed**: Backpack setting is now per-player instead of global
- **Default**: Backpack enabled (rings in backpack count as equipped)
- **Applies to**: All rings (Fly, Fire, Water, Heal, Peaceful)

### Config Migration
- **Changed**: Config folder moved from `mods/tiffy` to `mods/tiffy-illegalrings`
- **Added**: Automatic migration of existing configs (preserves custom recipes!)
- **Files migrated**:
  - `config.json` (recipes, gameplay values, ring toggles)
  - `player_settings.csv` (per-player backpack settings)

### Technical
- Heartbeat uses `ScheduledExecutorService` at 1000ms interval
- Thread-safe player tracking with `ConcurrentHashMap.newKeySet()`
- `world.execute()` wrapper for safe component access from scheduler thread
- Snapshot-based iteration to prevent race conditions with PlayerDisconnect

---

## v0.3.20 - 2026-01-24
- **Peaceful Ring Polish**:
  - **Interaction Fix**: Changed AI attitude from `IGNORE` to `FRIENDLY`. This restores interaction prompts (like 'F' to talk), enabling trading and questing while equipped.
  - **Seamless Immunity**: Hostile mobs now view the wearer as a friend and will not attempt to attack, removing the visual clutter of blocked hits.
- **Flight Stability**:
  - **Gamemode Conflict**: Fixed an issue where switching to `/gm adventure` would revoke flight. The mod now correctly enforces flight privileges regardless of gamemode defaults.

## v0.3.18 - 2026-01-24
- **Fix: Flight-Reset when standing up (Beds/Chairs)**:
  - **State-Change-Detection**: The mod now actively detects the transition from "sitting/sleeping" to "standing". When standing up, a force-sync of the flight status is immediately sent to the client.
  - **Default-Settings Patch**: Hytale often resets movement settings to "Default" when standing up. The mod now also overrides these standard values (`defaultSettings`), so a reset automatically lands back in flight mode.
- **Fix: Crash on Login / World Change**:
  - Resolved `SkipSentryException` crash by ensuring all player component access is performed safely on the respective World thread.
- **Improved Fall Damage Immunity**:
  - Optimized fall distance reset for better reliability during rapid vertical movement.
- **Enhanced Debugging**:
  - Added `Heartbeat` logging to validate scheduler activity.
  - Added proactive sync packets every few seconds to prevent client-server desync.
  - Expanded `DebugEventListener` to track `Interact`, `MouseButton`, `Ready`, `WorldChange`, and `UseBlock` events.

## v0.3.5 - 2026-01-24
- **Expanded Inventory Support (Backpack & Equipment)**:
  - **Backpack Support**: Rings now function automatically while stored inside any equipped backpack.
  - **Equipment Slot Support**: Rings are now detected in Jewelry/Armor slots.
  - **Utility Slot Support**: Rings now work when placed in Off-hand or special Utility slots.
  - Applies to all 5 rings: **Fly, Fire, Water, Heal, and Peaceful**.
- **Bugfixes**:
  - **Sleeping/Sitting Bug**: Fixed an issue where the Fly Ring's flight ability was lost after sleeping in a bed or sitting on a bench/chair. The mod now proactively restores flight settings if the game engine resets them.
- **Technical Refactor**:
  - Implemented `RingUtils` to centralize inventory scanning.
  - Improved performance by reducing redundant inventory checks.
  - Support for custom storage components and modded inventory containers.

## v0.3.2 - 2026-01-24
- **Recipe Batch Synchronization**:
  - Refactored `RecipeManager` to collect all modified recipes into a batch.
  - Updates are now performed in a single `loadAssets` call instead of individual updates.
  - Mitigates "only 60 recipes" limit issues and improves performance during the first tick.

## v0.3.1 - 2026-01-20
- **Bench Requirement System**:
  - NEW: `benchRequirements` can now be customized for each ring in `config.json`.
  - Move rings to any crafting station (e.g., `Workbench_Tinkering`) or category.
  - Supports multiple workbenches and custom tier levels.
- **Config Auto-Sync & Repair**:
  - The mod now automatically repairs `config.json` at every start.
  - Missing rings or empty bench requirements are automatically restored with default templates.
  - User settings for ingredients and existing workbench configs are preserved.
- **Improved Transparency**:
  - Added a `_notice` field to `config.json` that shows the current mod version and regeneration instructions.
- **Crafting Toggle Refinement**:
  - Rings with `"craftable": false` are now completely hidden from the crafting UI.

## v0.3.0 - 2026-01-20
- **Dynamic Recipe Overrides**: Players can now customize the ingredients for all 5 rings directly in `mods/tiffy/config.json`.
- **Heal Ring Balancing**: Default lifesteal percentage reduced from 35% to 5% for better endgame balance.
- **Improved Lifecycle Management**: Recipe overrides are now applied in the `start()` phase with retries for maximum reliability.

## v0.2.6a - 2026-01-18
- **Debug Logging Toggle**: Added `debugLogging` to `config.json` to silence spammy server logs (Lifesteal, Damage cancellation, Flight revocation). Defaults to `false`.
- **Logging Refactor**: Centralized logging via new `Log` helper class.

## v0.2.6 - 2026-01-18

### Added - Configuration System
- **External Configuration File**: `config.json` now stored in `mods/tiffy/` for easy server-side customization
- **Ring Enable/Disable Switches**: Individual control for each ring:
  - `enabled.flyRing` - Toggle Fly Ring functionality
  - `enabled.fireRing` - Toggle Fire Ring immunity
  - `enabled.waterRing` - Toggle Water Ring immunity
  - `enabled.healRing` - Toggle Heal Ring lifesteal
  - `enabled.peacefulRing` - Toggle Peaceful Ring NPC immunity
- **Configurable Lifesteal**: `gameplay.lifestealPercent` allows dynamic adjustment of Heal Ring lifesteal (default: 0.35 = 35%)
- **Player Feedback**: When a disabled ring is equipped, players receive a clear message: `§c[RingName] DISABLED by server`
- **ModConfig Class**: Centralized configuration management with automatic JSON loading and default value generation

### Added - Peaceful Ring Implementation
- **Peaceful Ring**: Complete implementation with three-layer protection:
  - `PeacefulAttitudeProvider` - Changes NPC attitude to `IGNORE` for ring wearers
  - `PeacefulTargetClearSystem` - Actively clears ring wearers from NPC target memory
  - `PeacefulAttitudeSystem` - Registers attitude provider with AI Blackboard
- **Peaceful Ring Behavior**:
  - Mobs completely ignore the player (Attitude.IGNORE)
  - If attacked, mobs take damage but do not retaliate (TargetMemory cleared)
  - Player takes 0 damage from mobs if forced interaction occurs

### Changed
- **Configuration-Driven Functionality**: All ring functions now respect `enabled` flags
  - Disabled rings show server message instead of activating
  - Logic completely bypassed when `enabled: false`
- **Dynamic Lifesteal**: Heal Ring now reads lifesteal percentage from config at runtime
- **Config Loading**: Automatic config creation on first server start with sensible defaults

### Fixed
- Fixed typo in Heal Ring icon filename (`Jewelry_Heal_ring.png` → `Jewelry_Heal_Ring.png`)
- Fixed `Jewelry_Peacefull_Ring.json` to correctly reference icon and use standard item properties
- Fixed config path to use `mods/tiffy/` for better organization

### Technical
- Temporarily using Heal Ring icon for Peaceful Ring until custom asset is generated
- All rings now have consistent enabled-check patterns for maintainability
- Config system supports hot-reload via server restart

## v0.2.51 - Previous
- Initial support for Heal Ring (Lifesteal mechanic).
- Initial support for RingDamageSystem base.
- **New Item: Water Ring:** Grants immunity to drowning and suffocation.
- **New Item: Bloodsuck Ring:** Grants you 35% Lifesteal.

## v0.2.5
- **New Item: Fire Ring:** Grants immunity to fire, lava, magma, and burn damage.
- **ECS Damage Filtering:** Implemented a high-priority `DamageEventSystem` in the `FilterDamageGroup` for reliable damage cancellation.
- **Specific Immunity:** Designed to only block heat-based damage, keeping other hazards like physical attacks and fall damage (handled by Fly Ring) relevant.
- **Bugfixes:** Resolved JSON decoding errors in `EntityEffect` assets and improved item inventory detection.

### v0.2.4
- **Critical Fix: Main Thread Synchronization:** Resolved `java.lang.NoClassDefFoundError` and `PlayerRef.getComponent(Velocity) called async` errors by properly scheduling component access on the Hytale World thread.
- **Improved Game Loop Integration:** Logic now executes via `world.execute()` to ensure safe interactions with internal server components.
- **Resource Management:** Added proactive tracking cleanup and player validity checks using `wasRemoved()` to prevent memory leaks and redundant processing.
- **Code Quality:** Refactored package imports for better visibility and simplified code structure.

### v0.2.2
- **Fall Damage Immunity:** Ring wearers are now completely immune to fall damage
- **Velocity-Based Detection:** Monitors Y-velocity to detect falling state before damage is calculated
- **Real-Time Protection:** 50ms tick-based protection system that resets velocity and movement states
- **Improved Reliability:** Blocks fall damage at ECS component level (Velocity + MovementStates)
- **Refactor:** Migrated from Timer to ScheduledExecutorService for better performance

### v0.2.0
- **Exploit Fix:** Added a background scheduler that verifies flight status every 2 seconds.
- **Smart Tracking:** The mod now tracks if flight was granted by the ring, preventing it from revoking flight enabled by other commands (e.g., `/movement`).
- **Forced Landing:** Enhanced revocation logic that forces the client to land immediately using network packets and server-side state overrides.
- **Bugfixes:** Corrected issues where flight would persist after dropping the ring or during mid-air inventory changes.