package com.tiffy.flyring;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.event.events.entity.LivingEntityInventoryChangeEvent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.protocol.MovementSettings;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.protocol.SavedMovementStates;
import com.hypixel.hytale.protocol.packets.player.SetMovementStates;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.universe.world.World;

import java.util.Set;
import java.util.UUID;
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
    private final Set<Player> onlinePlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> trackedFlightPlayers = ConcurrentHashMap.newKeySet();
    private ScheduledExecutorService scheduler;

    public FlyRing(JavaPlugin plugin, boolean registerEvents) {
        this.plugin = plugin;
        if (registerEvents) {
            setup();
        } else {
            // Initialize scheduler only
            scheduler = Executors.newScheduledThreadPool(1);
            scheduler.scheduleAtFixedRate(this::protectRingWearersFromFallDamage, 0, 500, TimeUnit.MILLISECONDS);
            plugin.getLogger().atInfo().log("FlyRing handler initialized!");
        }
    }

    private void setup() {
        // Event Listeners
        plugin.getEventRegistry().registerGlobal(LivingEntityInventoryChangeEvent.class, this::onInventoryChange);
        plugin.getEventRegistry().registerGlobal(PlayerConnectEvent.class, this::onPlayerConnect);
        plugin.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, this::onPlayerDisconnect);

        // Fall damage protection (500ms interval to minimize spam)
        scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(this::protectRingWearersFromFallDamage, 0, 500, TimeUnit.MILLISECONDS);

        plugin.getLogger().atInfo().log("FlyRing handler initialized!");
    }

    public void shutdown() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
        }
    }

    public void onPlayerConnect(PlayerConnectEvent event) {
        if (event.getPlayer() != null) {
            onlinePlayers.add(event.getPlayer());
        }
    }

    public void onPlayerDisconnect(PlayerDisconnectEvent event) {
        UUID uuid = event.getPlayerRef().getUuid();
        onlinePlayers.removeIf(p -> p.getPlayerRef().getUuid().equals(uuid));
        trackedFlightPlayers.remove(uuid);
    }

    public void onInventoryChange(LivingEntityInventoryChangeEvent event) {
        if (event.getEntity() instanceof Player player) {
            onlinePlayers.add(player);
            updateFlightStatus(player);
        }
    }

    private void protectRingWearersFromFallDamage() {
        for (Player player : onlinePlayers) {
            try {
                if (player == null || player.wasRemoved()) {
                    onlinePlayers.remove(player);
                    continue;
                }

                World world = player.getWorld();
                if (world == null || !world.isAlive())
                    continue;

                world.execute(() -> {
                    try {
                        if (!hasRingInInventory(player))
                            return;

                        Velocity velComp = player.getPlayerRef().getComponent(Velocity.getComponentType());
                        double yVelocity = (velComp != null) ? velComp.getY() : 0;

                        MovementStatesComponent statesComp = player.getPlayerRef()
                                .getComponent(MovementStatesComponent.getComponentType());
                        boolean onGround = false;
                        if (statesComp != null) {
                            MovementStates states = statesComp.getMovementStates();
                            if (states != null) {
                                onGround = states.onGround;
                            }
                        }

                        // Block fall damage
                        if (yVelocity < -0.5 && !onGround) {
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

                        double fallDistance = player.getCurrentFallDistance();
                        if (fallDistance > 0) {
                            player.setCurrentFallDistance(0);
                        }
                    } catch (Exception inner) {
                        // Silent skip
                    }
                });
            } catch (Exception e) {
                // Silently skip if world access fails
            }
        }
    }

    private void updateFlightStatus(Player player) {
        if (player == null)
            return;

        boolean hasRing = hasRingInInventory(player);
        UUID uuid = player.getPlayerRef().getUuid();
        String playerName = player.getPlayerRef().getUsername();

        onlinePlayers.add(player);

        try {
            MovementManager movement = player.getPlayerRef().getComponent(MovementManager.getComponentType());
            if (movement == null)
                return;

            MovementSettings settings = movement.getSettings();
            if (settings == null)
                return;

            boolean canFly = settings.canFly;

            if (hasRing) {
                if (!canFly) {
                    settings.canFly = true;
                    movement.update(player.getPlayerRef().getPacketHandler());
                    player.sendMessage(com.hypixel.hytale.server.core.Message
                            .raw("[FlyRing] The FlyRing pulsates... you feel as light as a feather!"));
                }
                trackedFlightPlayers.add(uuid);
            } else {
                if (canFly && trackedFlightPlayers.contains(uuid)) {
                    plugin.getLogger().atInfo().log("Revoking flight for " + playerName + " (Ring lost)");

                    movement.applyDefaultSettings();
                    settings = movement.getSettings();
                    if (settings != null)
                        settings.canFly = false;
                    movement.update(player.getPlayerRef().getPacketHandler());

                    try {
                        player.getPlayerRef().getPacketHandler()
                                .write(new SetMovementStates(new SavedMovementStates(false)));
                    } catch (Exception ex) {
                    }

                    try {
                        MovementStatesComponent statesComp = player.getPlayerRef()
                                .getComponent(MovementStatesComponent.getComponentType());
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
                    }

                    player.sendMessage(com.hypixel.hytale.server.core.Message
                            .raw("[FlyRing] The ring's magic fades. Be careful!"));
                }

                trackedFlightPlayers.remove(uuid);
            }
        } catch (Exception e) {
            plugin.getLogger().atSevere().log("Error in updateFlightStatus: " + e.getMessage());
        }
    }

    private boolean hasRingInInventory(Player player) {
        Inventory inv = player.getInventory();
        if (inv == null)
            return false;

        return checkContainerForItem(inv.getHotbar(), FLY_RING_ITEM_ID) ||
                checkContainerForItem(inv.getStorage(), FLY_RING_ITEM_ID);
    }

    private boolean checkContainerForItem(com.hypixel.hytale.server.core.inventory.container.ItemContainer container,
            String itemId) {
        if (container == null)
            return false;

        for (short i = 0; i < container.getCapacity(); i++) {
            ItemStack stack = container.getItemStack(i);
            if (stack != null && !stack.isEmpty()) {
                if (itemId.equals(stack.getItemId())) {
                    return true;
                }
            }
        }
        return false;
    }
}
