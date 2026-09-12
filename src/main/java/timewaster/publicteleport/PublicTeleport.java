package timewaster.publicteleport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import timewaster.publicteleport.records.Config;

// TODO: Multi-modloader compatibility
// TODO: RTP random teleport functionality
// TODO: uppercase constants (final) + magic numbers
// TODO: reused helper functions to utils
// TODO: arrow functions always brackets
// TODO: Types Enums save to var if multiple occurences

/**
 * Entry point of the Public Teleport mod.
 */
public class PublicTeleport implements ModInitializer {
    public static final String MOD_ID = "public-teleport";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final Storage storage = new Storage();
    private static int tickCounter = 0;

    @Override
    public void onInitialize() {
        Config config = storage.getConfig();

        Registrar.registerCommands();
        Teleports.registerDeathEvent();

        if (config.enablePortals() || config.enableTpa()) {
            ServerTickEvents.END_SERVER_TICK.register(server -> {
                tickCounter++;

                // 5 ticks = 0.25 seconds
                if (config.enablePortals() && tickCounter % 5 == 1) {
                    Portals.check(server);
                }

                // 20 ticks = 1 second
                if (tickCounter >= 20) {
                    tickCounter = 0;

                    if (config.enablePortals()) {
                        Portals.emitParticles(server);
                    }

                    if (config.enableTpa()) {
                        Requests.cleanup(server);
                    }
                }

            });
        }

        LOGGER.info(prefix("Initialized!"));
    }

    /**
     * Workaround to show the {@link MOD_ID} in log messages. According to
     * https://docs.fabricmc.net/develop/debugging getting the logger with
     * {@code .getLogger(MOD_ID)} should do that automatically, but it is broken.
     *
     * @param text the message to prefix
     * @return the prefixed {@code text}
     */
    public static String prefix(String text) {
        return "[" + MOD_ID + "]: " + text;
    }
}
