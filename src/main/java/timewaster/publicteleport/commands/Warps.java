package timewaster.publicteleport.commands;

import java.util.List;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import timewaster.publicteleport.Messages;
import timewaster.publicteleport.PublicTeleport;
import timewaster.publicteleport.Registrar;
import timewaster.publicteleport.TeleportSafety;
import timewaster.publicteleport.Teleports;
import timewaster.publicteleport.records.Teleport;

/**
 * Defines all Warp commands, registered by {@link Registrar}.
 */
public class Warps {
    private static final Registrar.SuggestionType typeNone = Registrar.SuggestionType.NONE;
    private static final Registrar.SuggestionType typeWarps = Registrar.SuggestionType.WARPS;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("setwarp").requires(Commands.hasPermission(Commands.LEVEL_OWNERS))
            .then(Registrar.buildArgumentString("name", typeNone, (ServerPlayer player, String argValue) -> {
                if (argValue.equals("spawn")) {
                    if (PublicTeleport.storage.getConfig().enableSpawn()) {
                        Messages.sendMessage(player, "warp_reserved_spawn_set", Messages.MessageType.WARNING,
                            "/setspawn");
                    } else {
                        Messages.sendMessage(player, "warp_reserved_name", Messages.MessageType.WARNING);
                    }
                    return false;
                }

                Teleport target = Teleport.create(player, argValue);

                if (!TeleportSafety.isBlockTeleportable(player, target)) {
                    Messages.sendMessage(player, "teleport_unsafe_set", Messages.MessageType.ERROR, "Warp");
                    return false;
                }

                Boolean isSaved = PublicTeleport.storage.setTeleport(player, target, true);

                if (isSaved == null) {
                    return false;
                }

                Messages.sendMessage(player, "warp_set", Messages.MessageType.SUCCESS, argValue);

                return true;
            })));

        dispatcher.register(Commands.literal("delwarp").requires(Commands.hasPermission(Commands.LEVEL_OWNERS))
            .then(Registrar.buildArgumentString("name", typeWarps, (ServerPlayer player, String argValue) -> {
                if (argValue.equals("spawn")) {
                    Messages.sendMessage(player, "warp_no_exist", Messages.MessageType.ERROR, "spawn");
                    return false;
                }

                Boolean success = PublicTeleport.storage.deleteTeleport(player, argValue, true);

                if (success == null) {
                    return false;
                }

                if (success) {
                    Messages.sendMessage(player, "warp_deleted", Messages.MessageType.SUCCESS, argValue);
                } else {
                    Messages.sendMessage(player, "warp_no_exist", Messages.MessageType.ERROR, argValue);
                }

                return true;
            })));

        dispatcher.register(Commands.literal("warp")
            .then(Registrar.buildArgumentString("name", typeWarps, (ServerPlayer player, String argValue) -> {
                if (argValue.equals("spawn")) {
                    if (PublicTeleport.storage.getConfig().enableSpawn()) {
                        Messages.sendMessage(player, "warp_reserved_spawn_get", Messages.MessageType.WARNING, "/spawn");
                    } else {
                        Messages.sendMessage(player, "warp_no_exist", Messages.MessageType.ERROR, "spawn");
                    }
                    return false;
                }

                return Teleports.teleportPlayer(player, argValue, true);
            })));

        dispatcher.register(Commands.literal("warps")
            .executes(context -> Registrar.contextWrapper(context, (ServerPlayer player) -> {
                List<String> names = PublicTeleport.storage.getTeleportNames(player, true);

                if (names == null) {
                    return false;
                }

                Teleports.listTeleportNames(player, names, true);

                return true;
            })));
    }
}
