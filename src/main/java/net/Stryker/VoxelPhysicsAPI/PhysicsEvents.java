package net.Stryker.VoxelPhysicsAPI;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Immutable messages passed from the main thread into the physics thread's queues.
 * Using sealed interfaces so the physics thread can switch on type cleanly.
 */
public final class PhysicsEvents {

    private PhysicsEvents() {}

    /** Main thread → physics thread: inject a physics value at a position. */
    public record SeedEvent(
        ResourceKey<Level> dimension,
        BlockPos pos,
        PhysicsType type,
        int value
    ) {}

    /** Main thread → physics thread: a chunk was loaded, register it. */
    public record ChunkLoadEvent(
        ResourceKey<Level> dimension,
        int chunkX,
        int chunkZ
    ) {}

    /** Main thread → physics thread: a chunk was unloaded, drop it. */
    public record ChunkUnloadEvent(
        ResourceKey<Level> dimension,
        int chunkX,
        int chunkZ
    ) {}

    /** Main thread → physics thread: a dimension unloaded entirely. */
    public record DimensionUnloadEvent(
        ResourceKey<Level> dimension
    ) {}
}
