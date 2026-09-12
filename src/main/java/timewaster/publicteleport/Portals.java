package timewaster.publicteleport;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.common.ClientboundTransferPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import timewaster.publicteleport.records.Portal;
import timewaster.publicteleport.records.Teleport;

public class Portals {
    private static Map<ServerPlayer, Boolean> playerStates = new HashMap<ServerPlayer, Boolean>();
    private static Map<ServerPlayer, Vec3> playerPositions = new HashMap<ServerPlayer, Vec3>();

    private static void teleportOrRedirectPlayer(ServerPlayer player, Teleport target, Vec3 oldPosition) {
        if (target.dimension().startsWith("url:")) {
            String[] parts = target.dimension().split(":");
            ClientboundTransferPacket packet = new ClientboundTransferPacket(
                Objects.requireNonNull(parts[1]),
                Integer.parseInt(parts[2]));

            if (oldPosition != null) {
                player.setPos(oldPosition);
            }

            player.connection.send(packet);
        } else {
            Teleports.teleportPlayer(player, target);
        }
    }

    private static boolean playerIsNotInPortal(ServerPlayer player, Portal portal) {
        double playerWidth = 0.3; // player is 0.6 blocks wide, but player position is middle point
        double playerHeight = 1.8; // player is 1.8 blocks high
        double blockSize = 1.0; // width of a block to check far edges

        return !player.level().dimension().identifier().toString().equals(portal.dimension())
            || player.getX() < portal.aX() - playerWidth || player.getX() > portal.bX() + blockSize + playerWidth
            || player.getY() < portal.aY() - playerHeight || player.getY() > portal.bY() + blockSize
            || player.getZ() < portal.aZ() - playerWidth || player.getZ() > portal.bZ() + blockSize + playerWidth;
    }

    /**
     * Emits particles at all portal positions to show they are indeed portals.
     *
     * @param server the server object
     */
    public static void emitParticles(MinecraftServer server) {
        List<Portal> portals = PublicTeleport.storage.getPortals();

        for (Portal portal : portals) {
            for (ServerLevel level : server.getAllLevels()) {
                if (portal.dimension().equals(level.dimension().identifier().toString())) {
                    int portalW = portal.bX() - portal.aX() + 1;
                    int portalH = portal.bY() - portal.aY() + 1;
                    int portalD = portal.bZ() - portal.aZ() + 1;
                    int volume = portalW * portalH * portalD;

                    level.sendParticles(
                        ParticleTypes.PORTAL,
                        true,
                        true,
                        portalW / 2.0 + portal.aX(),
                        portalH / 2.0 + portal.aY() - 0.5,
                        portalD / 2.0 + portal.aZ(),
                        volume * 2,
                        portalW / 5.5,
                        portalH / 5.5,
                        portalD / 5.5,
                        0.25);

                }
            }
        }
    }

    /**
     * Checks if a player has walked into a portal and teleports the player to the
     * associated target.
     *
     * @param server the server object
     */
    public static void check(MinecraftServer server) {
        List<Portal> portals = PublicTeleport.storage.getPortals();

        if (portals.size() == 0) {
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Vec3 oldPosition = playerPositions.get(player);
            if (oldPosition != null && player.position().equals(oldPosition)) {
                continue;
            }
            Boolean oldState = playerStates.get(player);
            boolean newState = false;

            for (Portal portal : portals) {
                if (playerIsNotInPortal(player, portal)) {
                    continue;
                }

                newState = true;

                if (oldState != null && !oldState) {
                    teleportOrRedirectPlayer(player, portal.target(), oldPosition);

                    return;
                }
            }

            playerPositions.put(player, player.position());
            playerStates.put(player, newState);
        }
    }
}
