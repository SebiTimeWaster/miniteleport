package timewaster.publicteleport;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.server.level.ServerPlayer;
import timewaster.publicteleport.records.Config;
import timewaster.publicteleport.records.Portal;
import timewaster.publicteleport.records.Teleport;

/**
 * Loads and saves data in multiple files in the config directory
 */
public class Storage {
    private static final Config configDefault = new Config("en_us", true, true, true, true, true, true, 10, 60, true);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path pathConfig;
    private final Path pathConfigHomes;
    private final File fileConfig;
    private final File fileWarps;
    private final File filePortals;
    private final Config config;
    private final Map<String, String> translations;
    private List<Teleport> warps;
    private Map<UUID, List<Teleport>> homes = new HashMap<UUID, List<Teleport>>();
    private List<Portal> portals;

    public Storage() {
        this.pathConfig = FabricLoader.getInstance().getConfigDir().resolve(PublicTeleport.MOD_ID);
        this.pathConfigHomes = pathConfig.resolve("homes");
        this.fileConfig = pathConfig.resolve("config.json").toFile();
        this.fileWarps = pathConfig.resolve("warps.json").toFile();
        this.filePortals = pathConfig.resolve("portals.json").toFile();
        createDirectories();
        this.config = loadConfig();
        this.translations = loadTranslations();
        this.warps = loadFile(fileWarps, Teleport.class, true);
        this.portals = loadFile(filePortals, Portal.class, true);
    }

    private void createDirectories() {
        try {
            Files.createDirectories(pathConfigHomes);
        } catch (IOException e) {
            PublicTeleport.LOGGER.error(PublicTeleport.prefix("Failed to create config directories!"));
            throw new UncheckedIOException(e);
        }
    }

    private Config loadConfig() {
        if (!fileConfig.exists()) {
            saveFile(fileConfig, configDefault, true);
            return configDefault;
        }

        try (BufferedReader reader = Files.newBufferedReader(fileConfig.toPath(), StandardCharsets.UTF_8)) {
            JsonObject defaultObj = GSON.toJsonTree(configDefault).getAsJsonObject();
            JsonObject fileObj = JsonParser.parseReader(reader).getAsJsonObject();
            boolean changed = false;

            for (String key : defaultObj.keySet()) {
                if (!fileObj.has(key)) {
                    fileObj.add(key, defaultObj.get(key));
                    changed = true;
                }
            }

            TypeToken<Config> configType = new TypeToken<Config>() {
            };

            Config mergedConfig = GSON.fromJson(fileObj, configType);

            if (changed) {
                saveFile(fileConfig, mergedConfig, true);
            }

            return mergedConfig;
        } catch (IOException | JsonParseException e) {
            PublicTeleport.LOGGER.error(PublicTeleport.prefix("Failed to load config from: {}"),
                fileConfig.toPath().toString());

            throw new RuntimeException(e);
        }
    }

    private Map<String, String> loadTranslations() {
        String languageFile = "assets/" + PublicTeleport.MOD_ID + "/lang/" + config.defaultLanguage() + ".json";
        FabricLoader fabricLoader = FabricLoader.getInstance();
        ModContainer modContainer;
        Path languagePath;

        try {
            modContainer = fabricLoader.getModContainer(PublicTeleport.MOD_ID).get();
        } catch (NoSuchElementException e) {
            PublicTeleport.LOGGER.error(PublicTeleport.prefix("Could not find mod container!"));
            throw new NoSuchElementException(e);
        }

        try {
            languagePath = modContainer.findPath(languageFile).get();
        } catch (NoSuchElementException e) {
            PublicTeleport.LOGGER.error(PublicTeleport.prefix("Could not find language file: {}"), languageFile);
            throw new NoSuchElementException(e);
        }

        try (BufferedReader reader = Files.newBufferedReader(languagePath, StandardCharsets.UTF_8)) {
            TypeToken<Map<String, String>> mapType = new TypeToken<Map<String, String>>() {
            };

            return GSON.fromJson(reader, mapType);
        } catch (IOException | JsonParseException e) {
            PublicTeleport.LOGGER.error(PublicTeleport.prefix("Failed to load language from: {}"),
                languagePath.toString());

            throw new RuntimeException(e);
        }
    }

    private <T> List<T> loadFile(File file, Class<T> elementType, boolean failOnError) {
        List<T> defaultValue = new ArrayList<T>();

        if (!file.exists()) {
            saveFile(file, defaultValue, failOnError);
            return defaultValue;
        }

        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            Type listType = TypeToken.getParameterized(List.class, elementType).getType();

            return GSON.fromJson(reader, listType);
        } catch (IOException | JsonParseException e) {
            PublicTeleport.LOGGER.error(PublicTeleport.prefix("Failed to load data from: {}"),
                file.toPath().toString());

            if (failOnError) {
                throw new RuntimeException(e);
            }

            return null;
        }
    }

    private boolean saveFile(File file, Object data, boolean failOnError) {
        try {
            Path tempPath = Files.createTempFile(file.getParentFile().toPath(), "tmp-", ".json");

            try (BufferedWriter writer = Files.newBufferedWriter(tempPath, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE)) {
                GSON.toJson(data, writer);
                writer.flush();
            }

            Files.move(tempPath, file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | JsonIOException e) {
            PublicTeleport.LOGGER.error(PublicTeleport.prefix("Failed to save data to: {}"), file.toPath().toString());

            if (failOnError) {
                throw new RuntimeException(e);
            }

            return false;
        }

        return true;
    }

    private File getHomeFileByUuid(UUID uuid) {
        return pathConfigHomes.resolve(uuid + ".json").toFile();
    }

    private List<Teleport> loadTeleports(@Nullable UUID uuid) {
        List<Teleport> teleports;

        if (uuid != null) {
            teleports = homes.get(uuid);

            if (teleports == null) {
                teleports = loadFile(getHomeFileByUuid(uuid), Teleport.class, false);

                if (teleports != null) {
                    homes.put(uuid, teleports);
                }
            }
        } else {
            teleports = warps;
        }

        return teleports;
    }

    private boolean saveTeleports(@Nullable UUID uuid, List<Teleport> teleports) {
        if (uuid != null) {
            homes.put(uuid, teleports);

            return saveFile(getHomeFileByUuid(uuid), teleports, false);
        } else {
            warps = teleports;

            return saveFile(fileWarps, teleports, false);
        }
    }

    /**
     * Fetches and returns a teleport by name.
     *
     * @param player the player trying to load the data
     * @param name   the exact name of the teleport to find
     * @param isWarp if it is a Warp, not a Home
     * @return if found: the {@link Teleport}; if not found: a new {@link Teleport}
     *         with the name "public_teleport_not_found"; if a file error occured:
     *         {@code null}
     */
    @Nullable
    public Teleport getTeleport(ServerPlayer player, String name, boolean isWarp) {
        List<Teleport> teleports = loadTeleports(isWarp ? null : player.getUUID());

        if (teleports == null) {
            Messages.sendMessage(player, "data_not_loaded", Messages.MessageType.ERROR);
            return null;
        }

        for (Teleport teleport : teleports) {
            if (teleport.name().equals(name)) {
                return teleport;
            }
        }

        return Teleport.create(player, "public_teleport_not_found");
    }

    /**
     * Fetches and returns the names of all teleports belonging to Warps or Homes.
     *
     * @param player the player trying to load the data
     * @param isWarp if they are Warps, not Homes
     * @return an alphabetically sorted list of teleport names or {@code null} if a
     *         file error occured
     */
    @Nullable
    public List<String> getTeleportNames(ServerPlayer player, boolean isWarp) {
        List<String> names = new ArrayList<String>();
        UUID uuid = player.getUUID();
        List<Teleport> teleports = loadTeleports(isWarp ? null : uuid);

        if (teleports == null) {
            Messages.sendMessage(player, "data_not_loaded", Messages.MessageType.ERROR);
            return null;
        }

        for (Teleport teleport : teleports) {
            if ((!isWarp && !teleport.name().equals("back")) ||
                (isWarp && !teleport.name().equals("spawn"))) {
                names.add(teleport.name());
            }
        }

        Collections.sort(names);

        return names;
    }

    /**
     * Creates or updates a teleport with the given name and persists the change.
     *
     * @param player      the player trying to save the data
     * @param newTeleport the {@link Teleport} to add or update
     * @param isWarp      if it is a Warp, not a Home
     * @return if successful: {@code true}; if a new teleport would go over the
     *         {@link maxHomes} limit: {@code false}; on file error: {@code null}
     */
    @Nullable
    public Boolean setTeleport(ServerPlayer player, Teleport newTeleport, boolean isWarp) {
        UUID uuid = player.getUUID();
        List<Teleport> teleports = loadTeleports(isWarp ? null : uuid);
        boolean exists = false;
        int numTeleports = 0;

        if (teleports == null) {
            Messages.sendMessage(player, "data_not_loaded", Messages.MessageType.ERROR);
            return null;
        }

        for (int i = 0; i < teleports.size(); i++) {
            if (teleports.get(i).name().equals(newTeleport.name())) {
                teleports.set(i, newTeleport);
                exists = true;
            }

            if (!teleports.get(i).name().equals("back")) {
                numTeleports++;
            }
        }

        if (!exists) {
            if (!isWarp && config.maxHomes() > 0 && config.maxHomes() <= numTeleports
                && !newTeleport.name().equals("back")) {
                return false;
            }

            teleports.add(newTeleport);
        }

        if (!saveTeleports(isWarp ? null : uuid, teleports)) {
            Messages.sendMessage(player, "data_not_saved", Messages.MessageType.ERROR);
            return null;
        }

        return true;
    }

    /**
     * Deletes a teleport with the given name if it exists and persists the change.
     *
     * @param player the player trying to delete the data
     * @param name   the exact name of the teleport to delete
     * @param isWarp if it is a Warp, not a Home
     * @return if the teleport was found and deleted: {@code true}; if the teleport
     *         was not found: {@code false}; if a file error occured: {@code null}
     */
    @Nullable
    public Boolean deleteTeleport(ServerPlayer player, String name, boolean isWarp) {
        UUID uuid = player.getUUID();
        List<Teleport> teleports = loadTeleports(isWarp ? null : uuid);
        boolean exists = false;

        if (teleports == null) {
            Messages.sendMessage(player, "data_not_loaded", Messages.MessageType.ERROR);
            return null;
        }

        for (int i = 0; i < teleports.size(); i++) {
            if (teleports.get(i).name().equals(name)) {
                teleports.remove(i);
                exists = true;
            }
        }

        if (exists) {
            if (!saveTeleports(isWarp ? null : uuid, teleports)) {
                Messages.sendMessage(player, "data_not_saved", Messages.MessageType.ERROR);
                return null;
            }

            return true;
        }

        return false;
    }

    /**
     * Creates or updates a portal with the given name and persists the change.
     *
     * @param player    the player trying to save the data
     * @param newPortal the {@link Portal} to add or update
     * @return {@code true} if successful, {@code false} on file error
     */
    public boolean setPortal(ServerPlayer player, Portal newPortal) {
        boolean exists = false;

        for (int i = 0; i < portals.size(); i++) {
            if (portals.get(i).target().name().equals(newPortal.target().name())) {
                portals.set(i, newPortal);
                exists = true;
            }
        }

        if (!exists) {
            portals.add(newPortal);
        }

        if (!saveFile(filePortals, portals, false)) {
            Messages.sendMessage(player, "data_not_saved", Messages.MessageType.ERROR);
            return false;
        }

        return true;
    }

    /**
     * Deletes a portal with the given name if it exists and persists the change.
     *
     * @param player the player trying to delete the data
     * @param name   the exact name of the portal to delete
     * @return {@code true} if the teleport was found and deleted, {@code null} if a
     *         file error occured
     */
    @Nullable
    public Boolean deletePortal(ServerPlayer player, String name) {
        boolean exists = false;

        for (int i = 0; i < portals.size(); i++) {
            if (portals.get(i).target().name().equals(name)) {
                portals.remove(i);
                exists = true;
            }
        }

        if (exists) {
            if (!saveFile(filePortals, portals, false)) {
                Messages.sendMessage(player, "data_not_saved", Messages.MessageType.ERROR);
                return null;
            }

            return true;
        }

        return false;
    }

    public Config getConfig() {
        return config;
    }

    public Map<String, String> getTranslations() {
        return translations;
    }

    public List<Portal> getPortals() {
        return portals;
    }
}
