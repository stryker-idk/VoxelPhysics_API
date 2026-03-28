package net.Stryker.VoxelPhysicsAPI;

/**
 * Defines how one PhysicsType propagates each tick.
 *
 * current[0] = first value (e.g., flux)
 * current[1] = second value (e.g., energy)
 * etc.
 *
 * Rules:
 * - Only write non-zero values into next[]
 * - Use next[0].putMax() etc. for max-merge
 * - Return true if any next[] has entries (still active)
 * - Return false if all next[] are empty (fully dissipated)
 */
public interface IRuleset {
    boolean tick(LongIntMap[] current, LongIntMap[] next);
}
