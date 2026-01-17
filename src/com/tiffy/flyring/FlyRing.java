package com.tiffy.flyring;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
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
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * FlyRing - Ermöglicht Fliegen, solange der Ring im Inventar ist.
 */
public class FlyRing extends JavaPlugin {

    private static final String RING_ITEM_ID = "Jewelry_Fly_Ring";

    // Player tracking
    private final Set<Player> onlinePlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> trackedFlightPlayers = ConcurrentHashMap.newKeySet();
    private ScheduledExecutorService scheduler;

    public FlyRing(JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        // Event Listeners
        getEventRegistry().registerGlobal(LivingEntityInventoryChangeEvent.class, this::onInventoryChange);
        getEventRegistry().registerGlobal(PlayerConnectEvent.class, this::onPlayerConnect);
        getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, this::onPlayerDisconnect);

        // Tick-basierter Fallschaden-Schutz für Ring-Träger
        scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(this::protectRingWearersFromFallDamage, 0, 50, TimeUnit.MILLISECONDS);

        getLogger().atInfo().log("FlyRing Mod v0.2.1 initialisiert (Event-driven)!");
    }

    @Override
    protected void shutdown() {
        // Cleanup scheduler
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
        }
    }

    private void onPlayerConnect(PlayerConnectEvent event) {
        if (event.getPlayer() != null) {
            onlinePlayers.add(event.getPlayer());
        }
    }

    private void onPlayerDisconnect(PlayerDisconnectEvent event) {
        UUID uuid = event.getPlayerRef().getUuid();
        onlinePlayers.removeIf(p -> p.getPlayerRef().getUuid().equals(uuid));
        trackedFlightPlayers.remove(uuid);
    }

    private void onInventoryChange(LivingEntityInventoryChangeEvent event) {
        if (event.getEntity() instanceof Player player) {
            onlinePlayers.add(player); // Ensure they are tracked
            updateFlightStatus(player);
        }
    }

    private void protectRingWearersFromFallDamage() {
        try {
            for (Player player : onlinePlayers) {
                if (player != null && hasRingInInventory(player)) {
                    try {
                        // Get Velocity Component
                        Velocity velComp = player.getPlayerRef().getComponent(Velocity.getComponentType());
                        double yVelocity = 0;
                        if (velComp != null) {
                            yVelocity = velComp.getY();
                        }

                        // Get Movement States
                        MovementStatesComponent statesComp = player.getPlayerRef()
                                .getComponent(MovementStatesComponent.getComponentType());
                        boolean onGround = false;
                        if (statesComp != null) {
                            MovementStates states = statesComp.getMovementStates();
                            if (states != null) {
                                onGround = states.onGround;
                            }
                        }

                        // Wenn Y-Velocity negativ (fallend) und nicht on ground, setze auf 0
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

                        // Immer Fall Distance auf 0 setzen
                        double fallDistance = player.getCurrentFallDistance();
                        if (fallDistance > 0) {
                            player.setCurrentFallDistance(0);
                        }
                    } catch (Exception e) {
                        // Silently ignore errors
                    }
                }
            }
        } catch (Exception e) {
            // Silently ignore errors
        }
    }

    private void updateFlightStatus(Player player) {
        if (player == null)
            return;

        boolean hasRing = hasRingInInventory(player);
        UUID uuid = player.getPlayerRef().getUuid();
        String playerName = player.getPlayerRef().getUsername();

        // Auto-track for scheduler
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
                // Fall: Spieler hat den Ring
                if (!canFly) {
                    settings.canFly = true;
                    movement.update(player.getPlayerRef().getPacketHandler());
                    player.sendMessage(com.hypixel.hytale.server.core.Message
                            .raw("[FlyRing] The FlyRing pulsates... you feel as light as a feather!"));
                }
                // Wir merken uns, dass wir (der Ring) diesen Spieler fliegen lassen
                trackedFlightPlayers.add(uuid);
            } else {
                // Fall: Spieler hat KEINEN Ring
                if (canFly && trackedFlightPlayers.contains(uuid)) {
                    // Er fliegt, und WIR haben es ihm erlaubt -> Jetzt Ring weg, also abstürzen
                    getLogger().atInfo().log("Revoking flight for " + playerName + " (Ring lost)");

                    // 1. Reset Settings
                    movement.applyDefaultSettings();
                    settings = movement.getSettings();
                    if (settings != null)
                        settings.canFly = false;
                    movement.update(player.getPlayerRef().getPacketHandler());

                    // 2. Force Landing Packet
                    try {
                        player.getPlayerRef().getPacketHandler()
                                .write(new SetMovementStates(new SavedMovementStates(false)));
                    } catch (Exception ex) {
                    }

                    // 3. Kill Server State
                    try {
                        MovementStatesComponent statesComp = player.getPlayerRef()
                                .getComponent(MovementStatesComponent.getComponentType());
                        if (statesComp != null) {
                            MovementStates states = statesComp.getMovementStates();
                            if (states != null) {
                                states.flying = false;
                                // Nicht auf 'falling = true' setzen, um Fallschaden zu vermeiden
                                statesComp.setMovementStates(states);
                            }
                        }
                    } catch (Exception ex) {
                    }

                    player.sendMessage(com.hypixel.hytale.server.core.Message
                            .raw("[FlyRing] The ring's magic fades. Be careful!"));
                }

                // Wir tracken ihn nicht mehr (entweder gerade gelandet oder er fliegt
                // fremd-gesteuert)
                trackedFlightPlayers.remove(uuid);
            }
        } catch (Exception e) {
            getLogger().atSevere().log("Error in updateFlightStatus: " + e.getMessage());
        }
    }

    private boolean hasRingInInventory(Player player) {
        Inventory inv = player.getInventory();
        if (inv == null)
            return false;

        return checkContainerForItem(inv.getHotbar(), RING_ITEM_ID) ||
                checkContainerForItem(inv.getStorage(), RING_ITEM_ID);
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
