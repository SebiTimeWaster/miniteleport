package dev.luxmiyu;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelResource;

public class MiniTeleport implements ModInitializer {

    static final String MOD_ID = "miniteleport";
    static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    static final Predicate<CommandSourceStack> PERMISSIONS_OWNER = source -> source.permissions().hasPermission(Permissions.COMMANDS_OWNER);

    static final long REQUEST_TIMEOUT_MS = 60_000; // 60 seconds

    record Warp(String name, int x, int y, int z, Float yaw, Float pitch, String dimension) {

    }

    record TeleportRequest(UUID sender, UUID receiver, boolean here, long expiry) {

    }

    final List<TeleportRequest> pendingRequests = new CopyOnWriteArrayList<>();

    // ------ WARPS ----------------------------------------------------------------------------------------------
    Path getDir(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(MOD_ID);
    }

    File getFile(MinecraftServer server, @Nullable UUID uuid) {
        Path worldDir = getDir(server);
        Path path = (uuid == null) ? worldDir.resolve("warps.json") : worldDir.resolve("homes/" + uuid + ".json");
        return path.toFile();
    }

    void createDir(MinecraftServer server) {
        try {
            Files.createDirectories(getDir(server).resolve("homes"));
        } catch (IOException e) {
            LOGGER.error("Failed to create data directory", e);
        }
    }

    Warp[] getWarps(File file) {
        if (!file.exists()) {
            return new Warp[0];
        }

        try (FileReader reader = new FileReader(file)) {
            return GSON.fromJson(reader, Warp[].class);
        } catch (IOException e) {
            LOGGER.error("Failed to load warps from {}", file, e);
            return new Warp[0];
        }
    }

    @Nullable
    Warp getWarp(MinecraftServer server, String name, @Nullable UUID uuid) {
        for (Warp warp : getWarps(getFile(server, uuid))) {
            if (warp.name().equals(name)) {
                return warp;
            }
        }
        return null;
    }

    void writeFile(File file, Object object) {
        try {
            Files.createDirectories(file.getParentFile().toPath());

            Path tempFile = Files.createTempFile(file.getParentFile().toPath(), "tmp-", ".json");
            try (FileWriter writer = new FileWriter(tempFile.toFile())) {
                GSON.toJson(object, writer);
            }

            Files.move(
                    tempFile,
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
            );
        } catch (IOException e) {
            LOGGER.error("Failed to save warps to {}", file, e);
        }
    }

    void setWarp(String name, ServerPlayer player, @Nullable UUID uuid) {
        MinecraftServer server = player.level().getServer();
        ArrayList<Warp> warps = new ArrayList<>(List.of(getWarps(getFile(server, uuid))));
        String dimension = player.level().dimension().identifier().toString();
        Warp warp = new Warp(
                name,
                (int) Math.floor(player.getX()),
                (int) Math.ceil(player.getY()),
                (int) Math.floor(player.getZ()),
                (Float) player.getYRot(),
                (Float) player.getXRot(),
                dimension
        );

        boolean warpExists = false;
        for (int i = 0; i < warps.size(); i++) {
            if (warps.get(i).name().equals(name)) {
                warps.set(i, warp);
                warpExists = true;
            }
        }

        if (!warpExists) {
            warps.add(warp);
        }

        CompletableFuture.runAsync(() -> writeFile(getFile(server, uuid), warps));
    }

    int delWarp(String name, ServerPlayer player, @Nullable UUID uuid) {
        MinecraftServer server = player.level().getServer();
        ArrayList<Warp> warps = new ArrayList<>(List.of(getWarps(getFile(server, uuid))));

        int delIndex = -1;
        for (int i = 0; i < warps.size(); i++) {
            if (warps.get(i).name().equals(name)) {
                delIndex = i;
                break;
            }
        }

        String start = uuid == null ? "Warp '" : "Home '";

        if (delIndex == -1) {
            player.sendSystemMessage(Component.literal(start + name + "' does not exist!").withStyle(ChatFormatting.RED));
            return 0;
        } else {
            warps.remove(delIndex);
            CompletableFuture.runAsync(() -> writeFile(getFile(server, uuid), warps));

            player.sendSystemMessage(Component.literal(start + name + "' deleted!").withStyle(ChatFormatting.AQUA));
            return 1;
        }
    }

    void doTeleportEffect(ServerLevel world, ServerPlayer player) {
        world.playSound(
                null,
                player.getBlockX() + 0.5,
                player.getBlockY() + 0.5,
                player.getBlockZ() + 0.5,
                SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS,
                1.0f,
                1.0f
        );

        world.sendParticles(
                ParticleTypes.PORTAL,
                player.getBlockX() + 0.5,
                player.getBlockY() + 0.5,
                player.getBlockZ() + 0.5,
                25,
                0.25, 0.25, 0.25,
                0.0
        );
    }

    int warpPlayer(ServerPlayer player, @Nullable Warp warp) {
        if (warp == null) {
            player.sendSystemMessage(Component.literal("That warp doesn't exist!").withStyle(ChatFormatting.RED));
            return 0;
        }

        ServerLevel world = player.level().getServer()
                .getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(warp.dimension())));
        if (world == null) {
            player.sendSystemMessage(Component.literal("That dimension doesn't exist!").withStyle(ChatFormatting.RED));
            return 0;
        }

        setWarp("back", player, player.getUUID());

        player.teleportTo(
                world,
                warp.x() + 0.5,
                warp.y() + 0.05,
                warp.z() + 0.5,
                EnumSet.noneOf(Relative.class),
                warp.yaw() != null ? (float) warp.yaw() : player.getYRot(),
                warp.pitch() != null ? (float) warp.pitch() : player.getXRot(),
                true
        );

        doTeleportEffect(world, player);

        if (List.of("home", "back").contains(warp.name())) {
            player.sendSystemMessage(Component.literal(String.format("Teleported %s!", warp.name())).withStyle(ChatFormatting.AQUA));
        } else {
            player.sendSystemMessage(Component.literal(String.format("Teleported to %s!", warp.name())).withStyle(ChatFormatting.AQUA));
        }

        return 1;
    }

    Component listWarps(MinecraftServer server, @Nullable UUID uuid) {
        Warp[] warps = getWarps(getFile(server, uuid));
        Arrays.sort(warps, Comparator.comparing(Warp::name));

        if (warps.length == 0) {
            return Component.literal(uuid == null ? "There are no warps." : "You have no homes.").withStyle(ChatFormatting.RED);
        }

        MutableComponent text = Component.literal(uuid == null ? "Warps:" : "Homes:");
        for (Warp warp : warps) {
            text
                    .append(Component.literal(" "))
                    .append(Component.literal(warp.name()).withStyle(ChatFormatting.GOLD).withStyle(style -> style
                    .withClickEvent(new ClickEvent.RunCommand((uuid == null ? "/warp " : "/home ") + warp.name()))
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("Teleport to " + warp.name())))
                    )
                    );
        }
        return text;
    }

    // ------ REQUESTS -------------------------------------------------------------------------------------------
    void addRequest(TeleportRequest request) {
        // remove duplicate pairs
        pendingRequests.removeIf(r -> r.sender().equals(request.sender()) && r.receiver().equals(request.receiver()));
        pendingRequests.add(request);
    }

    void removeRequest(TeleportRequest request) {
        pendingRequests.remove(request);
    }

    TeleportRequest getMostRecentRequest(UUID receiver) {
        return pendingRequests.stream().filter(r -> r.receiver().equals(receiver))
                .max(Comparator.comparingLong(TeleportRequest::expiry)).orElse(null);
    }

    TeleportRequest getRequest(UUID receiver, UUID sender) {
        return pendingRequests.stream().filter(r -> r.receiver().equals(receiver) && r.sender().equals(sender))
                .findFirst().orElse(null);
    }

    void cleanupExpiredRequests() {
        long now = System.currentTimeMillis();
        pendingRequests.removeIf(r -> r.expiry() < now);
    }

    void sendTeleportRequest(ServerPlayer sender, ServerPlayer receiver, boolean here) {
        cleanupExpiredRequests();

        long expiry = System.currentTimeMillis() + REQUEST_TIMEOUT_MS;
        TeleportRequest request = new TeleportRequest(sender.getUUID(), receiver.getUUID(), here, expiry);
        addRequest(request);

        Component message = Component.literal(
                String.format("%s wants to teleport %s. ", sender.getName().getString(), here ? "you to them" : "to you")
        )
                .withStyle(ChatFormatting.YELLOW).append(Component.literal("[Accept]").withStyle(ChatFormatting.GREEN).withStyle(
                style -> style.withClickEvent(new ClickEvent.RunCommand("/tpaccept " + sender.getName().getString()))
                        .withHoverEvent(new HoverEvent.ShowText(
                                Component.literal("Accept teleport request from " + sender.getName().getString()))))
        )
                .append(Component.literal(" "))
                .append(Component.literal("[Deny]").withStyle(ChatFormatting.RED).withStyle(
                        style -> style.withClickEvent(new ClickEvent.RunCommand("/tpdeny " + sender.getName().getString()))
                                .withHoverEvent(new HoverEvent.ShowText(
                                        Component.literal("Deny teleport request from " + sender.getName().getString())))));

        receiver.sendSystemMessage(message);
        sender.sendSystemMessage(Component.literal("Teleport request sent to " + receiver.getName().getString()).withStyle(ChatFormatting.AQUA));
    }

    void cancelTeleportRequest(ServerPlayer sender) {
        cleanupExpiredRequests();

        List<TeleportRequest> requests
                = pendingRequests.stream().filter(r -> r.sender().equals(sender.getUUID())).toList();

        if (requests.isEmpty()) {
            sender.sendSystemMessage(Component.literal("You have no pending teleport requests.").withStyle(ChatFormatting.RED));
            return;
        }

        for (TeleportRequest request : requests) {
            ServerPlayer receiver
                    = sender.level().getServer().getPlayerList().getPlayer(request.receiver());

            if (receiver != null) {
                receiver.sendSystemMessage(Component.literal(sender.getName().getString() + " cancelled their teleport request.").withStyle(ChatFormatting.YELLOW));
            }

            removeRequest(request);
        }

        sender.sendSystemMessage(Component.literal("Teleport request cancelled.").withStyle(ChatFormatting.YELLOW));
    }

    void acceptTeleportRequest(ServerPlayer receiver, @Nullable ServerPlayer sender) {
        cleanupExpiredRequests();

        TeleportRequest request;

        if (sender != null) {
            request = getRequest(receiver.getUUID(), sender.getUUID());
        } else {
            request = getMostRecentRequest(receiver.getUUID());
        }

        if (request == null) {
            receiver.sendSystemMessage(Component.literal("Teleport request expired or doesn't exist.").withStyle(ChatFormatting.RED));
            return;
        }

        ServerPlayer actualSender
                = receiver.level().getServer().getPlayerList().getPlayer(request.sender());
        if (actualSender == null) {
            receiver.sendSystemMessage(Component.literal("Request sender is no longer online.").withStyle(ChatFormatting.RED));
            removeRequest(request);
            return;
        }

        if (request.here()) {
            warpPlayer(receiver, new Warp(
                    actualSender.getName().getString(),
                    (int) actualSender.getX(),
                    (int) actualSender.getY(),
                    (int) actualSender.getZ(),
                    (Float) actualSender.getYRot(),
                    (Float) actualSender.getXRot(),
                    actualSender.level().dimension().identifier().toString()
            ));
            actualSender.sendSystemMessage(Component.literal("Teleport request accepted!").withStyle(ChatFormatting.AQUA));
        } else {
            warpPlayer(actualSender, new Warp(
                    receiver.getName().getString(),
                    (int) receiver.getX(),
                    (int) receiver.getY(),
                    (int) receiver.getZ(),
                    (Float) receiver.getYRot(),
                    (Float) receiver.getXRot(),
                    receiver.level().dimension().identifier().toString()
            ));
            receiver.sendSystemMessage(Component.literal("Teleport request accepted!").withStyle(ChatFormatting.AQUA));
        }

        removeRequest(request);
    }

    void denyTeleportRequest(ServerPlayer receiver, @Nullable ServerPlayer sender) {
        cleanupExpiredRequests();

        TeleportRequest request;

        if (sender != null) {
            request = getRequest(receiver.getUUID(), sender.getUUID());
        } else {
            request = getMostRecentRequest(receiver.getUUID());
        }

        if (request == null) {
            receiver.sendSystemMessage(Component.literal("Teleport request expired or doesn't exist.").withStyle(ChatFormatting.RED));
            return;
        }

        ServerPlayer actualSender
                = receiver.level().getServer().getPlayerList().getPlayer(request.sender());
        if (actualSender == null) {
            receiver.sendSystemMessage(Component.literal("Request sender is no longer online.").withStyle(ChatFormatting.RED));
            removeRequest(request);
            return;
        }

        removeRequest(request);
    }

    // ------ COMMANDS ----------------------------------------------------------------------------------------
    MinecraftServer getServer(CommandContext<CommandSourceStack> context) {
        return context.getSource().getServer();
    }

    ServerPlayer getPlayer(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("You must be a player to use this command."));
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().create();
        }
        return player;
    }

    SuggestionProvider<CommandSourceStack> suggestWarps(boolean player) {
        return (context, builder) -> {
            MinecraftServer server = getPlayer(context.getSource()).level().getServer();
            UUID uuid = null;

            if (player) {
                uuid = getPlayer(context.getSource()).getUUID();
            }

            for (Warp warp : getWarps(getFile(server, uuid))) {
                builder.suggest(warp.name());
            }
            return builder.buildFuture();
        };
    }

    SuggestionProvider<CommandSourceStack> suggestPlayers() {
        return (context, builder) -> {
            ServerPlayer sender = getPlayer(context.getSource());

            List<ServerPlayer> players = sender.level().getServer().getPlayerList().getPlayers();

            for (ServerPlayer player : players) {
                if (!sender.getUUID().equals(player.getUUID())) {
                    builder.suggest(player.getName().getString());
                }
            }

            return builder.buildFuture();
        };
    }

    void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sethome")
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(context -> {
                            ServerPlayer player = getPlayer(context.getSource());

                            String homeName = StringArgumentType.getString(context, "name");
                            setWarp(homeName, player, player.getUUID());

                            player.sendSystemMessage(Component.literal(String.format("Home %s set!", homeName)).withStyle(ChatFormatting.AQUA));
                            return 1;
                        })
                )
                .executes(context -> {
                    ServerPlayer player = getPlayer(context.getSource());
                    setWarp("home", player, player.getUUID());
                    player.sendSystemMessage(Component.literal("Home set!").withStyle(ChatFormatting.AQUA));
                    return 1;
                })
        );

        dispatcher.register(Commands.literal("delhome")
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(suggestWarps(true))
                        .executes(context -> {
                            ServerPlayer player = getPlayer(context.getSource());

                            String homeName = StringArgumentType.getString(context, "name");
                            return delWarp(homeName, player, player.getUUID());
                        })
                )
                .executes(context -> {
                    ServerPlayer player = getPlayer(context.getSource());
                    return delWarp("home", player, player.getUUID());
                })
        );

        dispatcher.register(Commands.literal("home")
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(suggestWarps(true))
                        .executes(context -> {
                            ServerPlayer player = getPlayer(context.getSource());
                            String homeName = StringArgumentType.getString(context, "name");
                            return warpPlayer(player, getWarp(getServer(context), homeName, player.getUUID()));
                        })
                ).executes(context -> {
                    ServerPlayer player = getPlayer(context.getSource());

                    return warpPlayer(player, getWarp(getServer(context), "home", player.getUUID()));
                })
        );

        dispatcher.register(Commands.literal("homes")
                .executes(context -> {
                    ServerPlayer player = getPlayer(context.getSource());
                    player.sendSystemMessage(listWarps(getServer(context), player.getUUID()));
                    return 1;
                })
        );

        dispatcher.register(Commands.literal("back")
                .executes(context -> {
                    ServerPlayer player = getPlayer(context.getSource());
                    return warpPlayer(player, getWarp(getServer(context), "back", player.getUUID()));
                })
        );

        dispatcher.register(Commands.literal("setwarp")
                .requires(PERMISSIONS_OWNER)
                .then(Commands.argument("name", StringArgumentType.word()).executes(context -> {
                    ServerPlayer player = getPlayer(context.getSource());

                    String warpName = StringArgumentType.getString(context, "name");
                    setWarp(warpName, player, null);

                    player.sendSystemMessage(Component.literal(String.format("Warp %s set!", warpName)).withStyle(ChatFormatting.AQUA));
                    return 1;
                }))
        );

        dispatcher.register(Commands.literal("delwarp")
                .requires(PERMISSIONS_OWNER)
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(suggestWarps(false))
                        .executes(context -> {
                            ServerPlayer player = getPlayer(context.getSource());

                            String warpName = StringArgumentType.getString(context, "name");
                            return delWarp(warpName, player, null);
                        })
                )
        );

        dispatcher.register(Commands.literal("warp")
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(suggestWarps(false))
                        .executes(context -> {
                            ServerPlayer player = getPlayer(context.getSource());
                            String warpName = StringArgumentType.getString(context, "name");
                            return warpPlayer(player, getWarp(getServer(context), warpName, null));
                        })
                )
        );

        dispatcher.register(Commands.literal("warps")
                .executes(context -> {
                    ServerPlayer player = getPlayer(context.getSource());
                    player.sendSystemMessage(listWarps(getServer(context), null));
                    return 1;
                }));

        dispatcher.register(Commands.literal("setspawn")
                .requires(PERMISSIONS_OWNER)
                .executes(context -> {
                    ServerPlayer player = getPlayer(context.getSource());
                    setWarp("spawn", player, null);

                    ServerLevel world = player.level();
                    world.setRespawnData(LevelData.RespawnData.of(
                            player.level().dimension(),
                            player.blockPosition(),
                            0,
                            0
                    ));
                    world.getServer().getGameRules().set(GameRules.RESPAWN_RADIUS, 0, world.getServer());

                    player.sendSystemMessage(Component.literal("Spawn set!").withStyle(ChatFormatting.AQUA));
                    return 1;
                })
        );

        dispatcher.register(Commands.literal("spawn")
                .executes(context -> {
                    ServerPlayer player = getPlayer(context.getSource());
                    return warpPlayer(player, getWarp(getServer(context), "spawn", null));
                })
        );

        dispatcher.register(Commands.literal("tpa")
                .then(Commands.argument("target", EntityArgument.player())
                        .suggests(suggestPlayers())
                        .executes(context -> {
                            ServerPlayer sender = getPlayer(context.getSource());
                            ServerPlayer target = EntityArgument.getPlayer(context, "target");

                            if (sender.equals(target)) {
                                sender.sendSystemMessage(Component.literal("You cannot teleport to yourself!").withStyle(ChatFormatting.RED));
                                return 0;
                            }

                            sendTeleportRequest(sender, target, false);
                            return 1;
                        })
                )
        );

        dispatcher.register(Commands.literal("tpahere")
                .then(Commands.argument("target", EntityArgument.player())
                        .suggests(suggestPlayers())
                        .executes(context -> {
                            ServerPlayer sender = getPlayer(context.getSource());
                            ServerPlayer target = EntityArgument.getPlayer(context, "target");

                            if (sender.equals(target)) {
                                sender.sendSystemMessage(Component.literal("You cannot teleport to yourself!").withStyle(ChatFormatting.RED));
                                return 0;
                            }

                            sendTeleportRequest(sender, target, true);
                            return 1;
                        })
                )
        );

        dispatcher.register(Commands.literal("tpcancel")
                .executes(context -> {
                    ServerPlayer sender = getPlayer(context.getSource());
                    cancelTeleportRequest(sender);
                    return 1;
                })
        );

        dispatcher.register(Commands.literal("tpaccept")
                .executes(context -> {
                    ServerPlayer receiver = getPlayer(context.getSource());
                    acceptTeleportRequest(receiver, null);
                    return 1;
                })
                .then(Commands.argument("sender", EntityArgument.player())
                        .suggests(suggestPlayers())
                        .executes(context -> {
                            ServerPlayer receiver = getPlayer(context.getSource());
                            ServerPlayer sender = EntityArgument.getPlayer(context, "sender");
                            acceptTeleportRequest(receiver, sender);
                            return 1;
                        })
                )
        );

        dispatcher.register(Commands.literal("tpdeny")
                .executes(context -> {
                    ServerPlayer receiver = getPlayer(context.getSource());
                    denyTeleportRequest(receiver, null);
                    return 1;
                })
                .then(Commands.argument("sender", EntityArgument.player())
                        .suggests(suggestPlayers())
                        .executes(context -> {
                            ServerPlayer receiver = getPlayer(context.getSource());
                            ServerPlayer sender = EntityArgument.getPlayer(context, "sender");
                            denyTeleportRequest(receiver, sender);
                            return 1;
                        })
                )
        );
    }

    // ------ INITIALIZE ----------------------------------------------------------------------------------
    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> registerCommands(dispatcher)
        );

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, cause) -> {
            if (entity instanceof ServerPlayer player) {
                setWarp("back", player, player.getUUID());
            }
        });

        ServerLevelEvents.LOAD.register((server, world) -> createDir(server));

        LOGGER.info("Initialized!");
    }

    // -----------------------------------------------------------------------------------------------------------
}
