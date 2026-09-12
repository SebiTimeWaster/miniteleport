package timewaster.publicteleport.records;

/**
 * A single portal definition.
 *
 * @param dimension identifier of the dimension/world this portal belongs to
 * @param aX        from position x-coordinate
 * @param aY        from position y-coordinate
 * @param aZ        from position z-coordinate
 * @param bX        to position x-coordinate
 * @param bY        to position y-coordinate
 * @param bZ        to position z-coordinate
 * @param target    the target to teleport a player walking into the portal to
 */
public final record Portal(
    String dimension,
    int aX,
    int aY,
    int aZ,
    int bX,
    int bY,
    int bZ,
    Teleport target) {
}
