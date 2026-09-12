package timewaster.publicteleport.commands;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionCheck;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import timewaster.publicteleport.Messages;
import timewaster.publicteleport.PublicTeleport;
import timewaster.publicteleport.Registrar;
import timewaster.publicteleport.TeleportSafety;
import timewaster.publicteleport.records.Portal;
import timewaster.publicteleport.records.Teleport;

/**
 * Defines all Portal commands, registered by {@link Registrar}.
 */
public class Portals {
    private static final Integer MIN = Integer.MIN_VALUE;
    private static Map<String, Portal> tempPortalData = new HashMap<String, Portal>();

    @Nullable
    private static Vec3 getBlockPositionPlayerLooksAt(ServerPlayer player, String action) {
        if (!action.equals("from") && !action.equals("to")) {
            return new Vec3(0.0, 0.0, 0.0);
        }
        Vec3 start = player.getEyePosition(1.0f);
        Vec3 dir = player.getViewVector(1.0f);
        Vec3 end = start.add(dir.scale(player.blockInteractionRange()));
        BlockHitResult blockHitResult = player.level().clip(new ClipContext(
            start,
            end,
            ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE,
            player));

        if (blockHitResult.getType() != BlockHitResult.Type.BLOCK) {
            Messages.sendMessage(player, "portal_set_no_block", Messages.MessageType.ERROR);
            return null;
        }

        return blockHitResult.getLocation();
    }

    @Nullable
    private static Teleport createPortalTarget(CommandContext<CommandSourceStack> context, ServerPlayer player,
        String action, String name) {
        Teleport target = Teleport.create(player, name);

        if (action.equals("targetPosition") && !TeleportSafety.isBlockTeleportable(player, target)) {
            Messages.sendMessage(player, "teleport_unsafe_set", Messages.MessageType.ERROR, "Portal");
            return null;
        }

        if (action.equals("targetUrl")) {
            String url = StringArgumentType.getString(context, "url");
            // TODO: string checks

            target = new Teleport(name, 0, 0, 0, 0.0f, 0.0f, "url:" + url);
        }

        return target;
    }

    @Nullable
    private static Portal mutateTempPortalData(CommandContext<CommandSourceStack> context, ServerPlayer player,
        String action) {
        String name = StringArgumentType.getString(context, "name");
        Vec3 blockPosition = getBlockPositionPlayerLooksAt(player, action);
        Teleport teleportTarget = createPortalTarget(context, player, action, name);
        if (blockPosition == null || teleportTarget == null) {
            return null;
        }
        String dimensionIdentifier = player.level().dimension().identifier().toString();
        Portal existingPortalData = tempPortalData.getOrDefault(name,
            new Portal("", MIN, MIN, MIN, MIN, MIN, MIN, null));

        Portal newPortalData = new Portal(
            (action.equals("from") || action.equals("to")) ? dimensionIdentifier : existingPortalData.dimension(),
            (action.equals("from")) ? (int) Math.floor(blockPosition.x()) : existingPortalData.aX(),
            (action.equals("from")) ? (int) Math.floor(blockPosition.y()) : existingPortalData.aY(),
            (action.equals("from")) ? (int) Math.floor(blockPosition.z()) : existingPortalData.aZ(),
            (action.equals("to")) ? (int) Math.floor(blockPosition.x()) : existingPortalData.bX(),
            (action.equals("to")) ? (int) Math.floor(blockPosition.y()) : existingPortalData.bY(),
            (action.equals("to")) ? (int) Math.floor(blockPosition.z()) : existingPortalData.bZ(),
            (action.startsWith("target")) ? teleportTarget : existingPortalData.target());

        tempPortalData.put(name, newPortalData);

        return newPortalData;
    }

    private static Portal normalisePortal(Portal portal) {
        return new Portal(
            portal.dimension(),
            Math.min(portal.aX(), portal.bX()),
            Math.min(portal.aY(), portal.bY()),
            Math.min(portal.aZ(), portal.bZ()),
            Math.max(portal.aX(), portal.bX()),
            Math.max(portal.aY(), portal.bY()),
            Math.max(portal.aZ(), portal.bZ()),
            portal.target());
    }

    private static boolean setPortal(ServerPlayer player, Portal portal) {
        portal = normalisePortal(portal);

        if (PublicTeleport.storage.setPortal(player, portal)) {
            Level level = TeleportSafety.getLevelFromDimension(player, portal.dimension());
            Block purplePane = BuiltInRegistries.BLOCK
                .getValue(Identifier.fromNamespaceAndPath("minecraft", "purple_stained_glass_pane"));

            for (int x = portal.aX(); x <= portal.bX(); x++) {
                for (int y = portal.aY(); y <= portal.bY(); y++) {
                    for (int z = portal.aZ(); z <= portal.bZ(); z++) {
                        BlockPos blockPos = new BlockPos(x, y, z);
                        BlockState state = Block.updateFromNeighbourShapes(purplePane.defaultBlockState(),
                            Objects.requireNonNull(level), blockPos);

                        level.setBlock(blockPos, state, Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                    }
                }
            }

            tempPortalData.remove(portal.target().name());
            Messages.sendMessage(player, "portal_set", Messages.MessageType.SUCCESS, portal.target().name());

            return true;
        }

        return false;
    }

    private static void sendSuccess(CommandContext<CommandSourceStack> context, ServerPlayer player, String action) {
        String name = StringArgumentType.getString(context, "name");
        String successIdentifier = switch (action) {
            case "from" -> "portal_set_from";
            case "to" -> "portal_set_to";
            case "targetPosition" -> "portal_set_pos";
            case "targetUrl" -> "portal_set_url";
            default -> "unknown_error";
        };

        Messages.sendMessage(player, successIdentifier, Messages.MessageType.SUCCESS, name);
    }

    private static int setPortalData(CommandContext<CommandSourceStack> context, String action) {
        ServerPlayer player = context.getSource().getPlayer();

        Portal newPortalData = mutateTempPortalData(context, player, action);

        if (newPortalData == null) {
            return 0;
        }

        if (newPortalData.aX() > MIN && newPortalData.bX() > MIN && newPortalData.target() != null) {
            return setPortal(player, newPortalData) ? 1 : 0;
        }

        sendSuccess(context, player, action);

        return 1;
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        boolean portalCommandsOnlyOp = PublicTeleport.storage.getConfig().portalCommandsOnlyOp();
        PermissionCheck hasPermission = portalCommandsOnlyOp ? Commands.LEVEL_OWNERS : Commands.LEVEL_ALL;

        dispatcher.register(Commands.literal("setportal").requires(Commands.hasPermission(hasPermission))
            .then(Commands.argument("name", Objects.requireNonNull(StringArgumentType.word()))
                .then(Commands.literal("from").executes((context) -> setPortalData(context, "from")))
                .then(Commands.literal("to").executes((context) -> setPortalData(context, "to")))
                .then(Commands.literal("target").executes((context) -> setPortalData(context, "targetPosition"))
                    .then(Commands.argument("url", Objects.requireNonNull(StringArgumentType.string()))
                        .requires(Commands.hasPermission(Commands.LEVEL_OWNERS))
                        .executes((context) -> setPortalData(context, "targetUrl"))))));

        dispatcher.register(Commands.literal("delportal").requires(Commands.hasPermission(hasPermission))
            .then(Registrar.buildArgumentString("name", Registrar.SuggestionType.PORTALS,
                (ServerPlayer player, String name) -> {
                    Boolean success = PublicTeleport.storage.deletePortal(player, name);

                    if (success == null) {
                        return false;
                    }

                    if (success) {
                        Messages.sendMessage(player, "portal_deleted", Messages.MessageType.SUCCESS, name);
                    } else {
                        Messages.sendMessage(player, "portal_no_exist", Messages.MessageType.ERROR, name);
                        return false;
                    }

                    return true;
                })));

        dispatcher.register(Commands.literal("portals").requires(Commands.hasPermission(hasPermission))
            .executes(context -> Registrar.contextWrapper(context, (ServerPlayer player) -> {
                List<Portal> portals = PublicTeleport.storage.getPortals();

                if (portals.size() == 0) {
                    Messages.sendMessage(player, "portal_none", Messages.MessageType.WARNING);
                } else {
                    Messages.MessageBuilder builder = new Messages.MessageBuilder().append("headline_portals",
                        Messages.MessageType.HEADLINE);

                    portals.sort(Comparator.comparing(portal -> portal.target().name()));

                    for (Portal portal : portals) {
                        builder.appendRaw("\n  ")
                            .appendRawColored(Objects.requireNonNull(portal.target().name()),
                                Messages.MessageType.COMMAND)
                            .appendRawColored("  " + portal.dimension().substring(10) + "  "
                                + portal.aX() + " " + portal.aY() + " " + portal.aZ(), null);
                    }

                    builder.send(player);
                }

                return true;
            })));
    }
}
