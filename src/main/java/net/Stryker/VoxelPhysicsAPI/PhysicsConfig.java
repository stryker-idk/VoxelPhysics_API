package net.Stryker.VoxelPhysicsAPI;

/**
 * Central configuration for the physics simulation.
 *
 * To change the tick rate, just change PHYSICS_TPS.
 * No other file needs to be touched.
 *
 * Examples:
 *   100  — default, good for pressure shockwaves
 *   500  — near-instant propagation, useful for radiation
 *   20   — matches Minecraft's tick rate (easy debugging)
 */
public final class PhysicsConfig {

    private PhysicsConfig() {}

    /** How many physics ticks run per real second. */
    public static final int PHYSICS_TPS = 100;

    /** Derived: how many nanoseconds each physics tick should take. */
    public static final long NANOS_PER_TICK = 1_000_000_000L / PHYSICS_TPS;

    /**
     * How many physics ticks between render snapshot updates.
     * Renderer reads from the snapshot, so this controls visual update frequency.
     * At 100 TPS, a value of 5 means the renderer updates 20 times/sec — plenty.
     */
    public static final int SNAPSHOT_INTERVAL = 5;
}
