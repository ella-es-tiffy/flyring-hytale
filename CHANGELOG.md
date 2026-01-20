# Changelog

## v0.3.0 - 2026-01-20
- **RtChanger Integration**: Integrated the runtime recipe override system.
- **Dynamic Recipe Overrides**: Players can now customize the ingredients for all 5 rings directly in `mods/tiffy/config.json`.
- **"Insane" Default Balancing**:
  - Fly, Fire, Water, Heal, and Peaceful Rings now default to high-endgame crafting costs (Iron, Gold, Thorium, Essences).
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