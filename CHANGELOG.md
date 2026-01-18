# Changelog

## [0.2.6] - 2026-01-18

### Added
- **Peaceful Ring Implementation**:
  - Added `PeacefulAttitudeProvider` to change NPC attitude to `IGNORE` for ring wearers.
  - Added `PeacefulTargetClearSystem` to actively clear ring wearers from NPC target memory.
  - Added `PeacefulAttitudeSystem` to register the attitude provider with the Blackboard.
  - Registered `Jewelry_Peacefull_Ring` item in `IllegalRings.java`.

### Fixed
- Fixed typo in Heal Ring icon filename (`Jewelry_Heal_ring.png` -> `Jewelry_Heal_Ring.png`).
- Fixed `Jewelry_Peacefull_Ring.json` to correctly reference the icon and use standard item properties.

### Changed
- **Peaceful Ring Behavior**:
  - Mobs now completely ignore the player (Attitude.IGNORE).
  - If attacked, mobs take damage but do not retaliate (TargetMemory cleared).
  - Player takes 0 damage from mobs if forced interaction occurs.
- Temporarily using Heal Ring icon for Peaceful Ring until a custom asset is generated.

## [0.2.5] - Previous
- Initial support for Heal Ring (Lifesteal mechanic).
- Initial support for RingDamageSystem base.
