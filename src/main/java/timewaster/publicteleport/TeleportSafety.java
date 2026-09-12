package timewaster.publicteleport;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ScaffoldingBlock;
import timewaster.publicteleport.records.Teleport;

/**
 * A collection of Utils to ensure safe teleportation.
 */
public class TeleportSafety {
    private static boolean blockHasCollision(Level level, @NotNull BlockPos blockPos) {
        return !level.getBlockState(blockPos).getCollisionShape(level, blockPos).isEmpty();
    }

    private static boolean isBlockEmpty(Level level, @NotNull BlockPos blockPos) {
        Block block = level.getBlockState(blockPos).getBlock();

        return block != Blocks.LAVA && (block instanceof ScaffoldingBlock || !blockHasCollision(level, blockPos));
    }

    private static boolean isBlockTeleportable(Level level, BlockPos blockPos) {
        return (blockHasCollision(level, blockPos.below())
            && isBlockEmpty(level, blockPos)
            && isBlockEmpty(level, blockPos.above()));
    }

    public static boolean isBlockTeleportableAndWithoutPlayers(ServerPlayer player, Level level, BlockPos blockPos) {
        boolean isBlockAvailable = isBlockTeleportable(level, blockPos);
        boolean isBlockedByPlayer = false;

        if (isBlockAvailable) {
            List<ServerPlayer> onlinePlayers = level.getServer().getPlayerList().getPlayers();

            for (ServerPlayer onlinePlayer : onlinePlayers) {
                if (onlinePlayer != player
                    && !doesPlayerClearTarget(onlinePlayer, blockPos, getDimensionName(level), 0.6, 1.8)) {
                    isBlockedByPlayer = true;
                }
            }
        }

        return isBlockAvailable && !isBlockedByPlayer;
    }

    public static boolean doesPlayerClearTarget(ServerPlayer player, BlockPos blockPos, String dimension,
        double clearanceXZ, double clearanceY) {

        return !getDimensionName(player.level()).equals(dimension)
            || Math.abs(player.getX() - (blockPos.getX() + 0.5)) > clearanceXZ
            || Math.abs(player.getY() - (blockPos.getY() + 0.01)) > clearanceY
            || Math.abs(player.getZ() - (blockPos.getZ() + 0.5)) > clearanceXZ;
    }

    /**
     * Get the dimension name from a given {@link Level}
     *
     * @param level the level to get the name from
     * @return the fetched name
     */
    public static String getDimensionName(Level level) {
        return level.dimension().identifier().toString();
    }

    /**
     * Gets the players int position and rounds up fractional Y positions.
     *
     * @param player the player whos position to get
     * @return the position with corrected Y position
     */
    public static BlockPos getPlayerBlockPos(ServerPlayer player) {
        double playerY = player.getY();
        double fractionY = playerY - Math.floor(playerY);
        // prevent weird scaffolding Y = x.00032, a carpet is 0.0625 high
        double realY = fractionY > 0.06 ? Math.ceil(playerY) : Math.floor(playerY);

        return new BlockPos((int) Math.floor(player.getX()), (int) realY, (int) Math.floor(player.getZ()));
    }

    /**
     * Gets a specific level from a {@link Teleport} target dimension identifier.
     *
     * @param player    the player to be teleported
     * @param dimension the dimension name
     * @return the level matching the dimension identifier
     */
    public static ServerLevel getLevelFromDimension(ServerPlayer player, String dimension) {
        Identifier dimId = Identifier.parse(Objects.requireNonNull(dimension));
        ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, dimId);

        return player.level().getServer().getLevel(dimKey);
    }

    /**
     * Checks if a specified {@link BlockPos} is a valid teleport target.
     *
     * @param player the player to be teleported
     * @param target the position to check
     * @return {@code true} is position is clear to use
     */
    public static boolean isBlockTeleportable(ServerPlayer player, Teleport target) {
        BlockPos blockPos = new BlockPos(target.x(), target.y(), target.z());
        Level level = getLevelFromDimension(player, target.dimension());

        return isBlockTeleportable(level, blockPos);
    }

    /**
     * Checks if any of the blocks in a certain radius around the given
     * {@link target} position is a valid teleport target and if no other player is
     * currently blocking it.
     *
     * @param player        the player to be teleported
     * @param target        the position to check
     * @param ignorePlayers if block checks around the target block should ignore
     *                          players blocking them to make sure /tpahereall works
     * @return {@link Teleport} the position that is teleportable to or {@code null}
     *         is none was found
     */
    @Nullable
    public static Teleport findTeleportablePosition(ServerPlayer player, Teleport target, boolean ignorePlayers) {
        Level level = getLevelFromDimension(player, target.dimension());

        if (!isBlockTeleportableAndWithoutPlayers(player, level, new BlockPos(target.x(), target.y(), target.z()))) {
            int[] orderY = { 0, 1, -1, 2, -2 };
            List<Integer> orderXZ = Arrays.asList(0, 1, 2, 3, 4, 16, 17, 18, 19, 20, 32, 33, 35, 36, 48, 49, 50, 51, 52,
                64, 65, 66, 67, 68);
            Collections.shuffle(orderXZ);

            for (int y : orderY) {
                for (int i : orderXZ) {
                    int x = ((i & 0x000000F0) >>> 4) - 2;
                    int z = (i & 0x0000000F) - 2;
                    BlockPos testPos = new BlockPos(target.x() + x, target.y() + y, target.z() + z);

                    if ((ignorePlayers && isBlockTeleportable(level, testPos))
                        || isBlockTeleportableAndWithoutPlayers(player, level, testPos)) {
                        return new Teleport(
                            target.name(),
                            target.x() + x,
                            target.y() + y,
                            target.z() + z,
                            target.yaw(),
                            target.pitch(),
                            target.dimension());
                    }
                }
            }

            return null;
        }

        return target;
    }

    /**
     * Checks if a player position and a {@link Teleport} target intersect within
     * specified clearances
     *
     * @param player      the player whos position to check
     * @param target      the position to check
     * @param clearanceXZ the minimum clearance in the X and Z directions needed
     * @param clearanceY  the minimum clearance in the Y direction needed
     * @return
     */
    public static boolean doesPlayerClearTarget(ServerPlayer player, Teleport target, double clearanceXZ,
        double clearanceY) {
        return doesPlayerClearTarget(player, new BlockPos(target.x(), target.y(), target.z()), target.dimension(),
            clearanceXZ, clearanceY);
    }
}
