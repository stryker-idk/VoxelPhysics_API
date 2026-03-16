package net.Stryker.VoxelPhysicsAPI;

/**
 * All simulated physical properties.
 * Each entry gets its own index into the int[] stored per block.
 * To add a new type: just add an enum entry and register a ruleset in RulesetRegistry.
 */
public enum PhysicsType {
    PRESSURE(0);
    // Future: TEMPERATURE(1), RADIOACTIVITY(2), HUMIDITY(3)

    public final int index;

    /** Total number of physics types — used to size the int[] per block. */
    public static final int COUNT = values().length;

    PhysicsType(int index) {
        this.index = index;
    }
}
