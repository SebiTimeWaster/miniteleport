package timewaster.publicteleport.commands;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelData.RespawnData;
import timewaster.publicteleport.Messages;
import timewaster.publicteleport.PublicTeleport;
import timewaster.publicteleport.Registrar;
import timewaster.publicteleport.TeleportSafety;
import timewaster.publicteleport.Teleports;
import timewaster.publicteleport.records.Teleport;

/**
 * Defines all Spawn commands, registered by {@link Registrar}.
 */
public class Spawn {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("setspawn").requires(Commands.hasPermission(Commands.LEVEL_OWNERS))
            .executes(context -> Registrar.contextWrapper(context, (ServerPlayer player) -> {
                Teleport target = Teleport.create(player, "spawn");

                if (!TeleportSafety.isBlockTeleportable(player, target)) {
                    Messages.sendMessage(player, "teleport_unsafe_set", Messages.MessageType.ERROR, "Spawn");
                    return false;
                }

                Boolean isSaved = PublicTeleport.storage.setTeleport(player, target, true);

                if (isSaved == null) {
                    return false;
                }

                Messages.sendMessage(player, "spawn_set", Messages.MessageType.SUCCESS);

                return true;
            })));

        dispatcher.register(Commands.literal("spawn")
            .executes(context -> Registrar.contextWrapper(context, (ServerPlayer player) -> {
                RespawnData spawnData = player.level().getServer().getLevel(Level.OVERWORLD).getRespawnData();
                Teleport fallback = new Teleport(
                    "spawn",
                    spawnData.pos().getX(),
                    spawnData.pos().getY(),
                    spawnData.pos().getZ(),
                    spawnData.yaw(),
                    spawnData.pitch(),
                    spawnData.dimension().identifier().toString());

                return Teleports.teleportPlayer(player, "spawn", fallback, true);
            })));
    }
}
