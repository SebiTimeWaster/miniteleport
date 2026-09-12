package timewaster.publicteleport.records;

/**
 * Mod-wide configuration options.
 *
 * @param defaultlanguage      the default language to use
 * @param enableSpawn          whether the Spawn features are enabled
 * @param enableWarps          whether the Warps features are enabled
 * @param enableHomes          whether the Homes features are enabled
 * @param enableBack           whether the Back features are enabled
 * @param enablePortals        whether the Portals features are enabled
 * @param enableTpa            whether the TPA features are enabled
 * @param maxHomes             maximum number of Homes a single player may set
 * @param requestTimeout       how long a teleport request is active in seconds
 * @param portalCommandsOnlyOp whether only OPs can use the Portal commands
 */
public final record Config(
    String defaultLanguage,
    boolean enableSpawn,
    boolean enableWarps,
    boolean enableHomes,
    boolean enableBack,
    boolean enablePortals,
    boolean enableTpa,
    int maxHomes,
    int requestTimeout,
    boolean portalCommandsOnlyOp) {
}
