package net.Stryker.VoxelPhysicsAPI;

import net.Stryker.VoxelPhysicsAPI.ruleset.misc.RulesetOutput;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One WorldPhysicsManager per loaded dimension.
 * Owned and called exclusively by PhysicsThreadManager on the physics thread.
 * Never touched by the main thread directly.
 */
public class WorldPhysicsManager {

    private final Map<ChunkPos, ChunkPhysicsManager> chunkManagers = new HashMap<>();

    // Reused every tick — avoids one big ArrayList allocation per tick
    private final List<RulesetOutput> overflowAccumulator = new ArrayList<>();

    // -------------------------------------------------------------------------
    // Chunk registration
    // -------------------------------------------------------------------------

    public void registerChunk(int cx, int cz) {
        chunkManagers.put(new ChunkPos(cx, cz), new ChunkPhysicsManager(cx, cz));
    }

    public void unregisterChunk(int cx, int cz) {
        chunkManagers.remove(new ChunkPos(cx, cz));
    }

    // -------------------------------------------------------------------------
    // Tick — called by PhysicsThreadManager on the physics thread
    // -------------------------------------------------------------------------

    public void tick() {
        overflowAccumulator.clear();

        // Phase 1: compute all chunks, collect overflow
        for (ChunkPhysicsManager manager : chunkManagers.values()) {
            overflowAccumulator.addAll(manager.tick());
        }

        // Phase 2a: route overflow to the correct neighboring chunk inbox
        for (RulesetOutput output : overflowAccumulator) {
            ChunkPos targetPos = new ChunkPos(output.pos());
            ChunkPhysicsManager target = chunkManagers.get(targetPos);
            if (target != null) target.receiveInbox(output);
            // If target chunk is unloaded, output is discarded — intentional
        }

        // Phase 2b: all chunks apply their inboxes
        for (ChunkPhysicsManager manager : chunkManagers.values()) {
            manager.applyInbox();
        }
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public void setBlockValue(BlockPos pos, PhysicsType type, int value) {
        ChunkPos cp = new ChunkPos(pos);
        ChunkPhysicsManager manager = chunkManagers.get(cp);
        if (manager != null) manager.setValue(pos, type, value);
    }

    public int getBlockValue(BlockPos pos, PhysicsType type) {
        ChunkPos cp = new ChunkPos(pos);
        ChunkPhysicsManager manager = chunkManagers.get(cp);
        return manager != null ? manager.getValue(pos, type) : 0;
    }

    public Map<ChunkPos, ChunkPhysicsManager> getChunkManagers() {
        return chunkManagers;
    }

    /** Wipes all hashmap data. Chunk registrations stay intact. */
    public void clearAllData() {
        chunkManagers.values().forEach(ChunkPhysicsManager::clearAllData);
    }

    public int getTotalActiveBlocks() {
        int total = 0;
        for (ChunkPhysicsManager m : chunkManagers.values()) {
            total += m.getActiveBlockCount();
        }
        return total;
    }
}
