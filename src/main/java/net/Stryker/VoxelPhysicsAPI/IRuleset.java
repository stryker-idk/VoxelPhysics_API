package net.Stryker.VoxelPhysicsAPI;

/**
 * Defines how one PhysicsType propagates each tick.
 *
 * Both maps use LongIntMap — zero boxing, zero GC pressure.
 * Keys are packed XYZ longs (see PhysicsEngine.pack/unpack).
 * Values are the physics magnitude (e.g. pressure level).
 *
 * For multi-value types (e.g. radiation with density + MeV),
 * encode both values into a single int:
 *   int packed = (density << 16) | (mev & 0xFFFF);
 * Max 65535 per sub-value. Decode with:
 *   int density = packed >> 16;
 *   int mev     = packed & 0xFFFF;
 *
 * Rules:
 *   - Only write non-zero values into next
 *   - Use next.putMax() for max-merge when multiple sources hit the same block
 *   - Return true if next has any entries (still active)
 *   - Return false if next is empty (fully dissipated)
 */
public interface IRuleset {
    boolean tick(LongIntMap current, LongIntMap next);
}
