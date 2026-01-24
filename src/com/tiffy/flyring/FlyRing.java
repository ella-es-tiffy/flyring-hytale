package com.tiffy.flyring;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.event.events.entity.LivingEntityInventoryChangeEvent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.protocol.MovementSettings;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.universe.world.World;

import java.util.Set;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * FlyRing - Handler for the Fly Ring (Creative flight + fall damage immunity).
 */
public class FlyRing {

    private static final String FLY_RING_ITEM_ID = "Jewelry_Fly_Ring";

    private final JavaPlugin plugin;
    private final Set<UUID> trackedFlightPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Boolean> wasSitting = new ConcurrentHashMap<>();
    private ScheduledExecutorService scheduler;

    public FlyRing(JavaPlugin plugin, boolean registerEvents) {
        this.plugin = plugin;
        if (registerEvents) {
            setup();
        } else {
            // Initialize scheduler only
            scheduler = Executors.newScheduledThreadPool(1);
            scheduler.scheduleAtFixedRate(this::tickEffects, 0, 500, TimeUnit.MILLISECONDS);
            Log.setup(plugin, "FlyRing handler initialized!");
        }
    }

    private void setup() {
        // Event Listeners
        plugin.getEventRegistry().registerGlobal(LivingEntityInventoryChangeEvent.class, this::onInventoryChange);
        plugin.getEventRegistry().registerGlobal(PlayerConnectEvent.class, this::onPlayerConnect);
        plugin.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, this::onPlayerDisconnect);
        plugin.getEventRegistry().registerGlobal(PlayerReadyEvent.class, this::onPlayerReady);

        // Fall damage protection (500ms interval to minimize spam)
        scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(this::tickEffects, 0, 500, TimeUnit.MILLISECONDS);

        Log.setup(plugin, "FlyRing handler initialized!");
    }

    public void shutdown() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
        }
    }

    public void onPlayerConnect(PlayerConnectEvent event) {
        // Handled via inventory change or periodic tick
    }

    public void onPlayerReady(PlayerReadyEvent event) {
        if (event.getPlayer() != null) {
            updateFlightStatus(event.getPlayer());
        }
    }

    public void onPlayerDisconnect(PlayerDisconnectEvent event) {
        UUID uuid = event.getPlayerRef().getUuid();
        trackedFlightPlayers.remove(uuid);
        wasSitting.remove(uuid);
    }

    public void onInventoryChange(LivingEntityInventoryChangeEvent event) {
        if (event.getEntity() instanceof Player player) {
            updateFlightStatus(player);
        }
    }

    private void tickEffects() {
        // Check if FlyRing is enabled
        if (ModConfig.getInstance() != null && ModConfig.getInstance().enabled != null) {
            if (!ModConfig.getInstance().enabled.flyRing) {
                // Ring is disabled - do nothing
                return;
            }
        }

        // Heartbeat log to ensure scheduler is alive
        Log.info(plugin, "[FlyRing] Heartbeat - Scanning players...");

        // Use global player manager from Universe for maximum reliability
        for (com.hypixel.hytale.server.core.universe.PlayerRef playerRef : com.hypixel.hytale.server.core.universe.Universe
                .get().getPlayers()) {
            java.util.UUID worldUuid = playerRef.getWorldUuid();
            if (worldUuid == null)
                continue;

            World world = com.hypixel.hytale.server.core.universe.Universe.get().getWorld(worldUuid);
            if (world == null || !world.isAlive())
                continue;

            world.execute(() -> {
                try {
                    Player player = playerRef.getComponent(Player.getComponentType());
                    if (player == null || player.wasRemoved())
                        return;
                    boolean hasRing = RingUtils.hasRing(player, FLY_RING_ITEM_ID);

                    if (hasRing && Math.random() < 0.1) { // Log occasionally to verify detection
                        Log.info(plugin,
                                "[FlyRing] Tracking " + player.getPlayerRef().getUsername() + " (Ring detected)");
                    }

                    // 1. Proactive Flight Refresh (Fix for Sleeping/Sitting/Teleporting Bug)
                    if (hasRing) {
                        refreshFlightSettings(player);
                    }

                    // 2. Fall Damage Protection
                    Velocity velComp = playerRef.getComponent(Velocity.getComponentType());
                    double yVelocity = (velComp != null) ? velComp.getY() : 0;

                    MovementStatesComponent statesComp = playerRef
                            .getComponent(MovementStatesComponent.getComponentType());
                    boolean onGround = false;
                    if (statesComp != null) {
                        MovementStates states = statesComp.getMovementStates();
                        if (states != null) {
                            onGround = states.onGround;
                        }
                    }

                    // Block fall damage
                    if (yVelocity < -0.5 && !onGround && hasRing) {
                        if (velComp != null) {
                            velComp.setZero();
                        }
                        if (statesComp != null) {
                            MovementStates states = statesComp.getMovementStates();
                            if (states != null) {
                                states.onGround = true;
                                states.falling = false;
                                statesComp.setMovementStates(states);
                            }
                        }
                    }

                    if (hasRing) {
                        double fallDistance = player.getCurrentFallDistance();
                        if (fallDistance > 0) {
                            player.setCurrentFallDistance(0);
                        }
                    }
                } catch (Exception inner) {
                    // Silent skip
                }
            });
        }
    }

    private void refreshFlightSettings(Player player) {
        try {
            MovementManager movement = player.getPlayerRef().getComponent(MovementManager.getComponentType());
            if (movement == null)
                return;

            MovementSettings settings = movement.getSettings();
            MovementSettings def = movement.getDefaultSettings();

            boolean updated = false;
            boolean isSitting = false;

            // 1. Determine if player is currently sitting/sleeping
            MovementStatesComponent statesComp = player.getPlayerRef()
                    .getComponent(MovementStatesComponent.getComponentType());
            if (statesComp != null) {
                MovementStates states = statesComp.getMovementStates();
                if (states != null) {
                    isSitting = states.sitting || states.sleeping || states.mounting;
                }
            }

            java.util.UUID uuid = player.getPlayerRef().getUuid();
            boolean previouslySitting = wasSitting.getOrDefault(uuid, false);

            // 2. Detect "Standing Up" event
            if (previouslySitting && !isSitting) {
                Log.info(plugin, "[FlyRing] PLAYER STOOD UP: FORCE RE-SYNC FOR " + player.getPlayerRef().getUsername());
                updated = true; // Force packet send later
            }
            wasSitting.put(uuid, isSitting);

            // 3. Ensure CanFly is true in both Current and Default settings
            if (def != null && !def.canFly) {
                def.canFly = true;
                updated = true;
                Log.info(plugin, "[FlyRing] Patching DEFAULT flight for " + player.getPlayerRef().getUsername());
            }

            if (settings != null && !settings.canFly) {
                settings.canFly = true;
                updated = true;
                Log.info(plugin, "[FlyRing] Patching CURRENT flight for " + player.getPlayerRef().getUsername());
            }

            // 4. Aggressively push updates to the client
            if (updated || (Math.random() < 0.1 && !isSitting)) {
                movement.update(player.getPlayerRef().getPacketHandler());

                if (statesComp != null && !isSitting) {
                    MovementStates states = statesComp.getMovementStates();
                    if (states != null && !states.onGround && !states.flying) {
                        states.flying = true;
                        statesComp.setMovementStates(states);
                        Log.info(plugin, "[FlyRing] Heartbeat Force-Sync: Flying state ON for "
                                + player.getPlayerRef().getUsername());
                    }
                }
            }
        } catch (Exception e) {
            Log.severe(plugin, "Error in flight refresh: " + e.getMessage());
        }
    }

    private void updateFlightStatus(Player player) {
        if (player == null)
            return;

        boolean hasRing = RingUtils.hasRing(player, FLY_RING_ITEM_ID);
        UUID uuid = player.getPlayerRef().getUuid();
        String playerName = player.getPlayerRef().getUsername();

        // Check if FlyRing is enabled in config
        if (ModConfig.getInstance() != null && ModConfig.getInstance().enabled != null) {
            if (!ModConfig.getInstance().enabled.flyRing) {
                return;
            }
        }

        try {
            MovementManager movement = player.getPlayerRef().getComponent(MovementManager.getComponentType());
            if (movement == null)
                return;

            MovementSettings settings = movement.getSettings();
            if (settings == null)
                return;

            if (hasRing) {
                if (!settings.canFly) {
                    settings.canFly = true;
                    movement.update(player.getPlayerRef().getPacketHandler());
                    player.sendMessage(com.hypixel.hytale.server.core.Message
                            .raw("[FlyRing] The FlyRing pulsates... you feel as light as a feather!"));
                }
                trackedFlightPlayers.add(uuid);
            } else {
                if (trackedFlightPlayers.contains(uuid)) {
                    Log.info(plugin, "Revoking flight for " + playerName + " (Ring lost)");
                    movement.applyDefaultSettings();
                    settings = movement.getSettings();
                    if (settings != null)
                        settings.canFly = false;
                    movement.update(player.getPlayerRef().getPacketHandler());
                    trackedFlightPlayers.remove(uuid);
                }
            }
        } catch (Exception e) {
            Log.severe(plugin, "Error in updateFlightStatus: " + e.getMessage());
        }
    }
}
