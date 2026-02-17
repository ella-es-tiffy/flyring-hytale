package com.tiffy.flyring;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.*;
import com.hypixel.hytale.server.core.event.events.entity.LivingEntityUseBlockEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;

/**
 * DebugEventListener - Logs player-related events to identify what resets
 * flight state.
 */
public class DebugEventListener {

    private final JavaPlugin plugin;

    public DebugEventListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void setup() {
        plugin.getEventRegistry().registerGlobal(PlayerInteractEvent.class, this::onInteract);
        plugin.getEventRegistry().registerGlobal(PlayerMouseButtonEvent.class, this::onMouse);
        plugin.getEventRegistry().registerGlobal(PlayerReadyEvent.class, this::onReady);
        plugin.getEventRegistry().registerGlobal(AddPlayerToWorldEvent.class, this::onWorldChange);
        plugin.getEventRegistry().registerGlobal(LivingEntityUseBlockEvent.class, this::onUseBlock);
        plugin.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, this::onDisconnect);

        Log.info(plugin,
                "DebugEventListener expanded (v3). Catching: Interact, Mouse, Ready, WorldChange, UseBlock, Disconnect");
    }

    private void onInteract(PlayerInteractEvent event) {
        Log.info(plugin, "[DEBUG] PlayerInteractEvent: " + RingUtils.getUsername(event.getPlayer()) +
                " Action: " + event.getActionType());
    }

    private void onMouse(PlayerMouseButtonEvent event) {
        Log.info(plugin, "[DEBUG] PlayerMouseButtonEvent: " + RingUtils.getUsername(event.getPlayer()) +
                " Data: " + event.getMouseButton());
    }

    private void onReady(PlayerReadyEvent event) {
        Log.info(plugin, "[DEBUG] PlayerReadyEvent: " + RingUtils.getUsername(event.getPlayer()));
    }

    private void onWorldChange(AddPlayerToWorldEvent event) {
        Log.info(plugin, "[DEBUG] AddPlayerToWorldEvent in " + event.getWorld().getName());
    }

    private void onUseBlock(LivingEntityUseBlockEvent event) {
        Log.info(plugin, "[DEBUG] LivingEntityUseBlockEvent Type: " + event.getBlockType());
    }

    private void onDisconnect(PlayerDisconnectEvent event) {
        Log.info(plugin, "[DEBUG] PlayerDisconnectEvent: " + event.getPlayerRef().getUsername());
    }
}
