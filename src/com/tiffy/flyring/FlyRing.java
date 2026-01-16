package com.tiffy.flyring;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.event.events.entity.LivingEntityInventoryChangeEvent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.protocol.MovementSettings;
import com.hypixel.hytale.server.core.universe.PlayerRef;


/**
 * Tiffys FlyRing - Ermöglicht Fliegen, solange der Ring im Inventar ist.
 */
public class FlyRing extends JavaPlugin {

    private static final String RING_ITEM_ID = "Jewelry_Fly_Ring";


    public FlyRing(JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        // Registriere das Event für Inventar-Änderungen
        getEventRegistry().registerGlobal(LivingEntityInventoryChangeEvent.class, this::onInventoryChange);
        

        
        getLogger().atInfo().log("FlyRing Mod v0.1 initialisiert!");
    }

    private void onInventoryChange(LivingEntityInventoryChangeEvent event) {
        // Prüfen, ob die Entity ein Spieler ist
        if (event.getEntity() instanceof Player player) {
            updateFlightStatus(player);
        }
    }
    
    private void updateFlightStatus(Player player) {
        boolean hasRing = hasRingInInventory(player);
        
        try {
            // Zugriff auf den MovementManager über PlayerRef (funktioniert in dieser Version)
            MovementManager movement = player.getPlayerRef().getComponent(MovementManager.getComponentType());
            
            if (movement != null) {
                MovementSettings settings = movement.getSettings();
                
                // Null-Check für Settings (werden möglicherweise erst später initialisiert)
                if (settings != null && settings.canFly != hasRing) {
                    // Setze canFly direkt
                    settings.canFly = hasRing;
                    
                    // WICHTIG: Update zum Client senden (wie MovementCommands-Mod)
                    movement.update(player.getPlayerRef().getPacketHandler());
                    
                    // Simple text messages without color codes (Hytale doesn't support MC-style § codes here)
                    if (hasRing) {
                        player.sendMessage(com.hypixel.hytale.server.core.Message.raw("[FlyRing] The FlyRing pulsates... you feel as light as a feather!"));
                    } else {
                        player.sendMessage(com.hypixel.hytale.server.core.Message.raw("[FlyRing] The ring's magic fades. Be careful!"));
                    }
                }
            }
        } catch (Exception e) {
            getLogger().atSevere().log("Fehler beim Aktualisieren des Flugstatus: " + e.getMessage());
        }
    }

    private boolean hasRingInInventory(Player player) {
        Inventory inv = player.getInventory();
        if (inv == null) return false;
        
        return checkContainerForItem(inv.getHotbar(), RING_ITEM_ID) || 
               checkContainerForItem(inv.getStorage(), RING_ITEM_ID);
    }

    private boolean checkContainerForItem(com.hypixel.hytale.server.core.inventory.container.ItemContainer container, String itemId) {
        if (container == null) return false;
        
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
