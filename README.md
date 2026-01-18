# IllegalRings Mod

A Hytale server mod that adds five powerful rings with unique abilities.

## Features

### 🔵 Fly Ring
- Grants creative-mode flight
- Auto-revokes when ring is removed
- Fall damage immunity while equipped

### 🔥 Fire Ring
- Complete immunity to fire, lava, magma, and burn damage
- Heat-based damage cancellation via ECS system

### 💧 Water Ring
- Immunity to drowning and suffocation
- Safe underwater exploration

### ❤️ Heal Ring (Bloodsuck)
- Lifesteal mechanic (default 35%)
- Heal on dealing damage
- Configurable percentage via `config.json`

### 🕊️ Peaceful Ring
- Complete NPC immunity
- Mobs ignore you (Attitude.IGNORE)
- No damage from NPCs, no retaliation when you attack

## Configuration

The mod reads its configuration from `mods/tiffy/config.json`:

```json
{
  "enabled": {
    "flyRing": true,
    "fireRing": true,
    "waterRing": true,
    "healRing": true,
    "peacefulRing": true
  },
  "gameplay": {
    "lifestealPercent": 0.35
  }
}
```

### Configuration Options

- **`enabled.<ringName>`**: Toggle each ring's functionality on/off
  - When disabled, players see: `§c[RingName] DISABLED by server`
  - Ring has no effect when `enabled: false`
- **`gameplay.lifestealPercent`**: Lifesteal percentage for Heal Ring (0.35 = 35%)

Changes take effect after server restart.

## Installation

1. Place `FlyRing-0.2.6.jar` in your Hytale server's `mods/` folder
2. Start the server - `mods/tiffy/config.json` will be auto-generated
3. Customize config as needed and restart

## Upcoming Features

- **Recipe Modification via Config**: Allow server owners to customize crafting recipes for all rings through `config.json` without recompiling the mod

## Technical Details

- **ECS Damage System**: Fire/Water/Peaceful immunity via high-priority damage cancellation
- **Thread-Safe**: All component access properly synchronized with Hytale world thread


## Version

Current Version: **v0.2.6**

See [CHANGELOG.md](CHANGELOG.md) for full version history.
