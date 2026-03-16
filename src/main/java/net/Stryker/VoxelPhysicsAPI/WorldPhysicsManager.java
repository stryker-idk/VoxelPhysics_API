package net.Stryker.VoxelPhysicsAPI;

import net.Stryker.VoxelPhysicsAPI.ruleset.misc.RulesetOutput;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * One WorldPhysicsManager exists per loaded dimension.
 *
 * Responsibilities:
 *   1. Track all loaded ChunkPhysicsManagers
 *   2. Orchestrate the two-phase tick across all chunks
 *   3. Route overflow outputs to the correct neighboring chunk's inbox
 *   4. Discard overflow for unloaded chunks (they'll catch up when they load)
 *
 * Two-phase tick (called every game tick via PhysicsEventHandler):
 *   Phase 1 — All chunks compute their next generation independently
 *              and report their overflow to us.
 *   Phase 2 — We route the overflow, then all chunks apply their inboxes.
 *
 * This two-phase design prevents causality loops where chunk A updates B,
 * which updates C, all within the same tick.
 */
public class WorldPhysicsManager {

    // One instance per dimension, keyed by dimension ResourceKey
    private static final Map<ResourceKey<Level>, WorldPhysicsManager> INSTANCES = new HashMap<>();

    // All currently loaded chunk managers for this dimension
    private final Map<ChunkPos, ChunkPhysicsManager> chunkManagers = new HashMap<>();

    // -------------------------------------------------------------------------
    // Static lifecycle
    // -------------------------------------------------------------------------

    public static WorldPhysicsManager get(ResourceKey<Level> dimension) {
        return INSTANCES.computeIfAbsent(dimension, k -> new WorldPhysicsManager());
    }

    /** Called when a dimension unloads — cleans up the instance. */
    public static void remove(ResourceKey<Level> dimension) {
        INSTANCES.remove(dimension);
    }

    // -------------------------------------------------------------------------
    // Chunk registration (called by PhysicsEventHandler on chunk load/unload)
    // -------------------------------------------------------------------------

    public void registerChunk(int cx, int cz) {
        chunkManagers.put(new ChunkPos(cx, cz), new ChunkPhysicsManager(cx, cz));
    }

    public void unregisterChunk(int cx, int cz) {
        chunkManagers.remove(new ChunkPos(cx, cz));
    }

    // -------------------------------------------------------------------------
    // Main tick — called every server tick
    // -------------------------------------------------------------------------

    public void tick() {
        // Phase 1: Compute all chunks, collect overflow
        // Using a list of (overflow outputs) per chunk to avoid modifying while iterating
        List<RulesetOutput> allOverflow = new ArrayList<>();

        for (ChunkPhysicsManager manager : chunkManagers.values()) {
            List<RulesetOutput> overflow = manager.tick();
            allOverflow.addAll(overflow);
        }

        // Phase 2a: Route overflow to the correct chunk's inbox
        for (RulesetOutput output : allOverflow) {
            ChunkPos targetPos = new ChunkPos(output.pos());
            ChunkPhysicsManager target = chunkManagers.get(targetPos);

            if (target != null) {
                target.receiveInbox(output);
            }
            // If the target chunk is not loaded, the output is discarded.
            // This is intentional — unloaded chunks don't simulate.
        }

        // Phase 2b: All chunks apply their inboxes
        for (ChunkPhysicsManager manager : chunkManagers.values()) {
            manager.applyInbox();
        }
    }

    // -------------------------------------------------------------------------
    // Public API — seed a physics value from game events (e.g. explosions)
    // -------------------------------------------------------------------------

    /**
     * Sets a physics value at a world position.
     * Automatically routes to the correct ChunkPhysicsManager.
     *
     * Example usage:
     *   WorldPhysicsManager.get(level.dimension())
     *       .setBlockValue(explosionPos, PhysicsType.PRESSURE, 64);
     */
    public void setBlockValue(BlockPos pos, PhysicsType type, int value) {
        ChunkPos cp = new ChunkPos(pos);
        ChunkPhysicsManager manager = chunkManagers.get(cp);
        if (manager != null) {
            manager.setValue(pos, type, value);
        }
    }

    public int getBlockValue(BlockPos pos, PhysicsType type) {
        ChunkPos cp = new ChunkPos(pos);
        ChunkPhysicsManager manager = chunkManagers.get(cp);
        return manager != null ? manager.getValue(pos, type) : 0;
    }

    /**
     * Iterates every active block across all loaded chunks in this dimension.
     * Used by PhysicsDebugParticles to find blocks to visualize.
     * Callback receives (BlockPos, int[] values) — same int[] as in the hashmap.
     */
    public void forEachActiveBlock(ResourceKey<Level> dimension, BiConsumer<BlockPos, int[]> callback) {
        for (ChunkPhysicsManager manager : chunkManagers.values()) {
            manager.forEachActiveBlock(callback);
        }
    }

    /** Exposed for PhysicsDebugRenderer — returns the live chunk manager map. */
    public Map<ChunkPos, ChunkPhysicsManager> getChunkManagers() {
        return chunkManagers;
    }

    /** Iterate all loaded chunks — used by the debug renderer. */
    public void forEachChunk(BiConsumer<ChunkPos, ChunkPhysicsManager> consumer) {
        chunkManagers.forEach(consumer);
    }

    /** Exposed for PhysicsDebugRenderer — read-only view of all chunk managers. */

    /** Useful for debugging — total active blocks across all loaded chunks. */
    public int getTotalActiveBlocks() {
        int total = 0;
        for (ChunkPhysicsManager m : chunkManagers.values()) {
            total += m.getActiveBlockCount();
        }
        return total;
    }

    /** Returns the ChunkPhysicsManager for a given chunk, or null if not loaded. */
    public ChunkPhysicsManager getChunkManager(net.minecraft.world.level.ChunkPos cp) {
        return chunkManagers.get(cp);
    }

    /** Iterates all active chunk managers — used by the debug visualizer. */
    public void forEachChunk(java.util.function.Consumer<ChunkPhysicsManager> consumer) {
        chunkManagers.values().forEach(consumer);
    }
}
