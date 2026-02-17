package com.tiffy.flyring;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.event.events.entity.LivingEntityInventoryChangeEvent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.protocol.MovementSettings;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.protocol.SavedMovementStates;
import com.hypixel.hytale.protocol.packets.player.SetMovementStates;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.UUID;
import java.util.Set;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * FlyRing - Handler for the Fly Ring (Creative flight).
 * STEP 1: Pure inventory-based flight control
 */
public class FlyRing {

    private static final String FLY_RING_ITEM_ID = "Jewelry_Fly_Ring";

    private final JavaPlugin plugin;
    private final Set<UUID> trackedFlightPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> falldamageImmunePlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> gaiaPlayers = ConcurrentHashMap.newKeySet();

    private final Map<UUID, Player> onlinePlayers = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> lastFlyState = new ConcurrentHashMap<>();
    private ScheduledExecutorService scheduler;

    public FlyRing(JavaPlugin plugin) {
        this.plugin = plugin;

        // Heartbeat - jede Sekunde Flight sync (Fix für sitting/sleeping reset)
        scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(this::refreshFlightIfNeeded, 0, 1000, TimeUnit.MILLISECONDS);

        Log.setup(plugin, "FlyRing handler initialized!");
    }

    public void shutdown() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
        }
        falldamageImmunePlayers.clear();
    }

    public void onPlayerConnect(com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent event) {
        Player player = (Player) event.getHolder().getComponent(Player.getComponentType());
        if (player != null) {
            UUID uuid = event.getPlayerRef().getUuid();
            if (uuid != null) {
                onlinePlayers.put(uuid, player);
            }
        }
    }

    public void onPlayerReady(com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent event) {
        Player player = event.getPlayer();
        if (player != null) {

            // Test Feature: Give FlyRing to specific users if missing
            String playerName = RingUtils.getUsername(player);
            if ("WhyIamSoAngry".equals(playerName)
                    || "6859bbe8-1ff9-44f0-bdb0-fbc9766a7a9d".equals(RingUtils.getUUID(player).toString())) {
                if (!RingUtils.getRingSnapshot(player).hasFly) {
                    // Try to give item using CommandManager
                    try {
                        com.hypixel.hytale.server.core.command.system.CommandManager.get().handleCommand(
                                (com.hypixel.hytale.server.core.command.system.CommandSender) com.hypixel.hytale.server.core.console.ConsoleSender.INSTANCE,
                                "give " + playerName + " " + FLY_RING_ITEM_ID);

                        Log.info(plugin, "[FlyRing] Mod Author " + playerName
                                + " detected. Granting complimentary FlyRing (Auto-Give Feature). No action required.");
                        player.sendMessage(Message.raw("[FlyRing] Welcome! You received a free FlyRing."));

                    } catch (Exception e) {
                        Log.severe(plugin, "Failed to give test FlyRing via command: " + e.getMessage());
                    }
                }
            }
        }
    }

    public void onPlayerDisconnect(com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent event) {
        UUID uuid = event.getPlayerRef().getUuid();
        onlinePlayers.remove(uuid);
        trackedFlightPlayers.remove(uuid);
        falldamageImmunePlayers.remove(uuid);
        gaiaPlayers.remove(uuid);
        lastFlyState.remove(uuid);
    }

    public void onInventoryChange(LivingEntityInventoryChangeEvent event, RingUtils.RingSnapshot snapshot) {
        if (event.getEntity() instanceof Player player) {
            UUID uuid = RingUtils.getUUID(player);
            if (uuid != null) {
                onlinePlayers.put(uuid, player);
            }
            updateStatus(player, snapshot);
        }
    }

    public void updateStatus(Player player, RingUtils.RingSnapshot snapshot) {
        if (player == null || snapshot == null)
            return;

        UUID uuid = RingUtils.getUUID(player);
        if (uuid == null) {
            AnalyticsClient.reportPotatoState(ModConfig.VERSION);
            return;
        }

        boolean hasFlightRing = snapshot.hasFly || snapshot.hasGaia;
        if (hasFlightRing) {
            ModConfig.Config mcfg = ModConfig.getInstance();
            if (mcfg != null && mcfg.enabled != null && !mcfg.enabled.flyRing) {
                hasFlightRing = false;
            }
        }
        if (hasFlightRing) {
            falldamageImmunePlayers.add(uuid);
        } else {
            falldamageImmunePlayers.remove(uuid);
        }

        // Track Gaia separately for analytics duration tracking
        if (snapshot.hasGaia) {
            gaiaPlayers.add(uuid);
        } else {
            gaiaPlayers.remove(uuid);
        }

        setForceFlightState(player, hasFlightRing);
    }

    /**
     * Sanfter Heartbeat - Refreshes flight if a player has the ring but lost flight
     * state
     * (e.g., after dismounting from a mount that removed the flying state) or after
     * sitting/sleeping.
     */
    private void refreshFlightIfNeeded() {
        List<Player> playersSnapshot = new ArrayList<>(onlinePlayers.values());
        if (playersSnapshot.isEmpty())
            return;

        for (Player player : playersSnapshot) {
            try {
                if (player == null || player.wasRemoved()) {
                    continue;
                }

                World world = player.getWorld();
                if (world == null || !world.isAlive())
                    continue;

                world.execute(() -> {
                    try {
                        RingUtils.RingSnapshot snapshot = RingUtils.getRingSnapshot(player);
                        if (snapshot.hasFly || snapshot.hasGaia) {
                            ModConfig.Config mcfg = ModConfig.getInstance();
                            if (mcfg == null || mcfg.enabled == null || mcfg.enabled.flyRing) {
                                setForceFlightState(player, true);
                            }
                        }
                    } catch (Exception inner) {
                        // Skip
                    }
                });
            } catch (Exception e) {
                // Skip
            }
        }
    }

    public Set<UUID> getFalldamageImmunePlayers() {
        return falldamageImmunePlayers;
    }

    public Set<UUID> getGaiaPlayers() {
        return gaiaPlayers;
    }

    /**
     * Force set flight state for a player
     *
     * @param player  Target player
     * @param enabled True to enable flight, false to disable and ground the player
     */
    public void setForceFlightState(Player player, boolean enabled) {
        if (player == null)
            return;

        UUID uuid = RingUtils.getUUID(player);

        try {
            Ref<EntityStore> refRef = player.getReference();
            PlayerRef ref = refRef.getStore().getComponent(refRef, PlayerRef.getComponentType());
            MovementManager movement = refRef.getStore().getComponent(refRef, MovementManager.getComponentType());
            if (movement == null)
                return;

            // Report state change ONLY if it changed to avoid spamming the heartbeat
            Boolean previous = lastFlyState.get(uuid);
            if (previous == null || previous != enabled) {
                lastFlyState.put(uuid, enabled);
                try {
                    AnalyticsClient.reportFlyState(enabled);
                } catch (Exception e) {
                }
            }

            MovementSettings settings = movement.getSettings();
            if (settings == null)
                return;

            if (enabled) {
                // Enable flight - IMMER update senden, Client könnte out of sync sein
                settings.canFly = true;
                movement.update(player.getReference().getStore()
                        .getComponent(player.getReference(), PlayerRef.getComponentType()).getPacketHandler());

                // Nur Message wenn neu aktiviert
                if (!trackedFlightPlayers.contains(uuid)) {
                    trackedFlightPlayers.add(uuid);
                    player.sendMessage(
                            Message.raw("[FlyRing] The FlyRing pulsates... you feel as light as a feather!"));
                }
            } else {
                // Disable flight - force grounded
                if (trackedFlightPlayers.contains(uuid)) {
                    // 1. Apply default movement settings (resets to original state)
                    movement.applyDefaultSettings();

                    // 2. Ensure canFly is false in current settings
                    settings = movement.getSettings();
                    if (settings != null) {
                        settings.canFly = false;
                    }

                    // 3. Send UPDATED movement capability to client
                    movement.update(player.getReference().getStore()
                            .getComponent(player.getReference(), PlayerRef.getComponentType()).getPacketHandler());

                    // 4. Send SetMovementStates packet directly (critical for client sync)
                    try {
                        player.getReference().getStore()
                                .getComponent(player.getReference(), PlayerRef.getComponentType()).getPacketHandler()
                                .write(new SetMovementStates(new SavedMovementStates(false)));
                    } catch (Exception ex) {
                        // Ignore packet write errors
                    }

                    // 5. Reset movement states - player should be grounded
                    try {
                        MovementStatesComponent statesComp = player.getReference().getStore()
                                .getComponent(player.getReference(), MovementStatesComponent.getComponentType());
                        if (statesComp != null) {
                            MovementStates states = statesComp.getMovementStates();
                            if (states != null) {
                                states.flying = false;
                                states.onGround = true;
                                states.falling = false;
                                statesComp.setMovementStates(states);
                            }
                        }
                        player.setCurrentFallDistance(0);
                    } catch (Exception ex) {
                        // Ignore state update errors
                    }

                    trackedFlightPlayers.remove(uuid);
                    player.sendMessage(Message.raw("[FlyRing] Flight revoked."));
                }
            }
        } catch (Exception e) {
            Log.severe(plugin, "Error in setForceFlightState: " + e.getMessage());
        }
    }
}
