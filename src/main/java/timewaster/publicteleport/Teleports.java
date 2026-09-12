package timewaster.publicteleport;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import timewaster.publicteleport.records.Teleport;

/**
 * Performs the mod's actual teleportation logic, and holds adjacent helpers.
 */
public class Teleports {
    @SuppressWarnings("null")
    private static void teleportEffect(ServerPlayer player) {
        player.level().playSound(
            null,
            player.getBlockX() + 0.5,
            player.getBlockY() + 1.0,
            player.getBlockZ() + 0.5,
            SoundEvents.ENDERMAN_TELEPORT,
            SoundSource.PLAYERS,
            1.0f,
            1.0f);

        player.level().sendParticles(
            ParticleTypes.PORTAL,
            true,
            true,
            player.getX(),
            player.getY() + 0.75,
            player.getZ(),
            100,
            0.5,
            0.75,
            0.5,
            0.25);
    }

    private static boolean teleport(ServerPlayer player, Teleport target, ServerLevel level) {
        if (target == null || level == null) {
            return false;
        }

        Teleport back = Teleport.create(player, "back");

        teleportEffect(player);

        boolean result = player.teleportTo(
            level,
            target.x() + 0.5,
            target.y() + 0.01,
            target.z() + 0.5,
            Objects.requireNonNull(Set.of()),
            target.yaw() != null ? (float) target.yaw() : player.getYRot(),
            target.pitch() != null ? (float) target.pitch() : player.getXRot(),
            true);

        if (result) {
            teleportEffect(player);

            if (!target.name().equals("back")) {
                PublicTeleport.storage.setTeleport(player, back, false);
            }

            Messages.sendMessage(player, "teleported_to", Messages.MessageType.SUCCESS, target.name());
        } else {
            Messages.sendMessage(player, "unknown_error", Messages.MessageType.ERROR);
        }

        return result;
    }

    @Nullable
    private static Teleport teleportPreflightCheck(ServerPlayer player, @Nullable ServerPlayer targetPlayer,
        Teleport target, ServerLevel level, boolean ignorePlayers) {
        if (level == null) {
            Messages.sendMessage(player, "level_no_exist", Messages.MessageType.ERROR);
            return null;
        }

        if (!TeleportSafety.doesPlayerClearTarget(player, target, 2.0, 3.0)) {
            Messages.sendMessage(player, "teleport_unnecessary", Messages.MessageType.WARNING, target.name());
            return null;
        }

        Teleport testedTarget = TeleportSafety.findTeleportablePosition(player, target, ignorePlayers);
        if (testedTarget == null) {
            if (targetPlayer == null) {
                Messages.sendMessage(player, "teleport_unsafe", Messages.MessageType.ERROR, target.name());
            } else {
                Messages.sendMessage(player, "teleport_unsafe_tpa", Messages.MessageType.ERROR,
                    targetPlayer.getName().getString());
                Messages.sendMessage(targetPlayer, "teleport_unsafe_target", Messages.MessageType.ERROR,
                    player.getName().getString());
            }
            return null;
        }

        return testedTarget;
    }

    /**
     * Registers a listener that records a player's {@code back} location
     * whenever they die.
     */
    public static void registerDeathEvent() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, cause) -> {
            if (entity instanceof ServerPlayer player) {
                PublicTeleport.storage.setTeleport(player, Teleport.create(player, "back"), false);
            }
        });
    }

    /**
     * Teleports the player to an already-resolved {@link Teleport} destination.
     *
     * @param player         the player to teleport
     * @param teleportTarget the destination to teleport the player to
     * @return {@code true} if the teleport succeeded
     */
    public static boolean teleportPlayer(ServerPlayer player, Teleport teleportTarget) {
        ServerLevel level = TeleportSafety.getLevelFromDimension(player, teleportTarget.dimension());
        Teleport testedTarget = teleportPreflightCheck(player, null, teleportTarget, level, false);

        return teleport(player, testedTarget, level);
    }

    /**
     * Teleports the player to another player (TPA destination).
     *
     * @param player       the player to teleport
     * @param targetPlayer the destination to teleport the player to
     * @param isTpaHereAll if it is a /tpahereall command usage
     * @return {@code true} if the teleport succeeded
     */
    public static boolean teleportPlayer(ServerPlayer player, ServerPlayer targetPlayer, boolean isTpaHereAll) {
        Teleport teleportTarget = Teleport.create(targetPlayer, targetPlayer.getName().getString());
        ServerLevel level = TeleportSafety.getLevelFromDimension(player, teleportTarget.dimension());
        Teleport testedTarget = teleportPreflightCheck(player, targetPlayer, teleportTarget, level, isTpaHereAll);

        return teleport(player, testedTarget, level);
    }

    /**
     * Looks up a named teleport destination (a home or a warp) and teleports the
     * player to it if found. Uses the fallback if it is specified otherwise.
     *
     * @param player     the player to teleport
     * @param targetName the name of the home or warp to teleport to
     * @param fallback   is used if not {@code null} and the target cannot be found
     * @param isWarp     {@code true} if the teleport is a Warp, not a Home
     * @return {@code true} if the destination was found and the teleport succeeded
     */
    public static boolean teleportPlayer(ServerPlayer player, String targetName, @Nullable Teleport fallback,
        boolean isWarp) {
        Teleport teleportTarget = PublicTeleport.storage.getTeleport(player, targetName, isWarp);
        if (teleportTarget == null) {
            return false;
        }
        if (teleportTarget.name().equals("public_teleport_not_found")) {
            if (fallback == null) {
                Messages.sendMessage(player, isWarp ? "warp_no_exist" : "home_no_exist", Messages.MessageType.ERROR,
                    targetName);
                return false;
            } else {
                teleportTarget = fallback;
            }
        }
        ServerLevel level = TeleportSafety.getLevelFromDimension(player, teleportTarget.dimension());
        Teleport testedTarget = teleportPreflightCheck(player, null, teleportTarget, level, false);

        return teleport(player, testedTarget, level);
    }

    /**
     * Looks up a named teleport destination (a home or a warp) and teleports
     * the player to it if found.
     *
     * @param player     the player to teleport
     * @param targetName the name of the home or warp to teleport to
     * @param isWarp     {@code true} if the teleport is a Warp, not a Home
     * @return {@code true} if the destination was found and the teleport succeeded
     */
    public static boolean teleportPlayer(ServerPlayer player, String targetName, boolean isWarp) {
        return teleportPlayer(player, targetName, null, isWarp);
    }

    /**
     * Sends a player a chat message listing a set of teleport names as
     * clickable buttons.
     *
     * @param player        the player to send the listing to
     * @param teleportNames a list of teleport names
     * @param isWarps       {@code true} if the list has Warps, not Homes
     */
    public static void listTeleportNames(ServerPlayer player, List<String> teleportNames, boolean isWarps) {
        if (teleportNames.size() == 0) {
            Messages.sendMessage(player, isWarps ? "warp_none" : "home_none", Messages.MessageType.WARNING);
        } else {
            Messages.MessageBuilder builder = new Messages.MessageBuilder().append(
                isWarps ? "headline_warps" : "headline_homes", Messages.MessageType.HEADLINE);

            for (String name : teleportNames) {
                MutableComponent buttonText = Messages.getMessage("button_named", Messages.MessageType.BUTTON, name);
                MutableComponent hoverText = Messages.getMessage("teleport_to", null, name);
                String command = isWarps ? "/warp " + name : "/home " + name;

                builder.appendRaw("\n  ").button(buttonText, hoverText, command);
            }

            builder.send(player);
        }
    }
}
