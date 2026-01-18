# FlyRing

**FlyRing** is a magical Hytale mod that grants you the power of creative flight simply by carrying a ring in your inventory. Perfect for builders and explorers who want to soar without limits!

## Features
- **Creative Flight:** Just carry the ring in your inventory (or hotbar) to fly freely like in creative mode.
- **Lightweight & Seamless:** No complex commands, just magic.
- **Immersive Feedback:** You feel "light as a feather" when the ring activates.

## Crafting Recipe
Craft the **Fly Ring** at a **Tinkering Bench**.

**Ingredients:**
- 100x **Iron Ingot**
- 100x **Life Essence**
- 10x **Thorium Bar**
- 1x **Light Feather**

## Installation
1. Download the latest `FlyRing-x.x.jar` from the releases.
2. Place the JAR file into your Hytale `mods` folder.
3. Start the game and craft your ring!

## Changelog

### v0.2.5
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

---
*Version 0.2.4*
