package com.tiffy.flyring;

import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import java.lang.reflect.Method;

/**
 * Compatibility helper for PacketHandler.write() across server versions.
 * Release: write(Packet), Pre-Release: write(ToClientPacket).
 */
public final class PacketUtil {

    private static Method writeMethod;

    static {
        try {
            // Try pre-release signature first: write(ToClientPacket)
            Class<?> toClientPacket = Class.forName("com.hypixel.hytale.protocol.ToClientPacket");
            writeMethod = PacketHandler.class.getMethod("write", toClientPacket);
        } catch (Exception e1) {
            try {
                // Fallback to release signature: write(Packet)
                writeMethod = PacketHandler.class.getMethod("write", Packet.class);
            } catch (Exception e2) {
                System.out.println("[PacketUtil] FATAL: No compatible write() method found!");
                e2.printStackTrace();
            }
        }
    }

    public static void sendPacket(PlayerRef playerRef, Packet packet) {
        if (playerRef == null || packet == null || writeMethod == null) return;
        try {
            writeMethod.invoke(playerRef.getPacketHandler(), packet);
        } catch (Exception e) {
            // Non-critical
        }
    }
}
