package timewaster.publicteleport.commands;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import timewaster.publicteleport.Registrar;
import timewaster.publicteleport.Requests;
import timewaster.publicteleport.Requests.RequestType;

/**
 * Defines all TPA commands, registered by {@link Registrar}.
 */
public class Tpa {
    private static final Registrar.SuggestionType typePlayers = Registrar.SuggestionType.PLAYERS;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("tpa")
            .then(Registrar.buildArgumentPlayer("player", typePlayers, (ServerPlayer player, ServerPlayer target) -> {
                return Requests.sendRequest(player, target, RequestType.NORMAL);
            })));

        dispatcher.register(Commands.literal("tpahere")
            .then(Registrar.buildArgumentPlayer("player", typePlayers, (ServerPlayer player, ServerPlayer target) -> {
                return Requests.sendRequest(player, target, RequestType.REVERSE);
            })));

        dispatcher.register(Commands.literal("tpahereall").requires(Commands.hasPermission(Commands.LEVEL_OWNERS))
            .executes(context -> Registrar.contextWrapper(context, (ServerPlayer player) -> {
                return Requests.sendRequest(player, null, RequestType.REVERSE_ALL);
            })));

        dispatcher.register(Commands.literal("tpcancel")
            .executes(context -> Registrar.contextWrapper(context, (ServerPlayer player) -> {
                return Requests.cancelRequest(player);
            })));

        dispatcher.register(Commands.literal("tpaccept")
            .then(Registrar.buildArgumentPlayer("player", typePlayers, (ServerPlayer player, ServerPlayer sender) -> {
                return Requests.acceptRequest(sender, player);
            }))
            .executes(context -> Registrar.contextWrapper(context, (ServerPlayer player) -> {
                return Requests.acceptRequest(null, player);
            })));

        dispatcher.register(Commands.literal("tpdeny")
            .then(Registrar.buildArgumentPlayer("player", typePlayers, (ServerPlayer player, ServerPlayer sender) -> {
                return Requests.denyRequest(sender, player);
            }))
            .executes(context -> Registrar.contextWrapper(context, (ServerPlayer player) -> {
                return Requests.denyRequest(null, player);
            })));
    }
}
