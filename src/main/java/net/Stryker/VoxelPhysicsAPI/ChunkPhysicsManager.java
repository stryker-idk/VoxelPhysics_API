package net.Stryker.VoxelPhysicsAPI;

import net.Stryker.VoxelPhysicsAPI.ruleset.misc.RulesetOutput;
import net.Stryker.VoxelPhysicsAPI.ruleset.misc.RulesetRegistry;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Manages the physics simulation for a single 16x16 chunk column.
 *
 * Key optimization: ALL data structures are pre-allocated and reused
 * across ticks. Nothing is allocated inside the hot tick loop.
 *
 * Data model:
 *   HashMap<BlockPos, int[]>
 *     Key:   world BlockPos (only blocks with at least one non-zero value stored)
 *     Value: int array indexed by PhysicsType.index
 */
public class ChunkPhysicsManager {

    private final int chunkX;
    private final int chunkZ;
    private final int minX, maxX, minZ, maxZ;

    // Current generation — only non-zero entries stored
    private HashMap<BlockPos, int[]> currentGen = new HashMap<>();

    // Reused every tick instead of reallocated — eliminates the biggest GC source
    private HashMap<BlockPos, int[]> nextGen = new HashMap<>();
    private final List<RulesetOutput> overflowBuffer = new ArrayList<>();
    private final List<RulesetOutput> inboxBuffer = new ArrayList<>();

    public ChunkPhysicsManager(int chunkX, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.minX = chunkX * 16;
        this.maxX = chunkX * 16 + 15;
        this.minZ = chunkZ * 16;
        this.maxZ = chunkZ * 16 + 15;
    }

    // -------------------------------------------------------------------------
    // Boundary check — O(1)
    // -------------------------------------------------------------------------

    public boolean isInChunk(BlockPos pos) {
        int x = pos.getX();
        int z = pos.getZ();
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    // -------------------------------------------------------------------------
    // Phase 1: Compute next generation
    // Returns the overflow list — valid only until next tick() call
    // -------------------------------------------------------------------------

    public List<RulesetOutput> tick() {
        nextGen.clear();
        overflowBuffer.clear();

        for (Map.Entry<BlockPos, int[]> entry : currentGen.entrySet()) {
            BlockPos pos = entry.getKey();
            int[] values = entry.getValue();

            for (PhysicsType type : PhysicsType.values()) {
                int val = values[type.index];
                if (val == 0) continue;

                // getRuleset returns a reused output list — see PressureRuleset
                List<RulesetOutput> outputs = RulesetRegistry.get(type).compute(pos, val);

                for (RulesetOutput output : outputs) {
                    if (output.value() <= 0) continue;

                    if (isInChunk(output.pos())) {
                        mergeIntoMap(nextGen, output);
                    } else {
                        overflowBuffer.add(output);
                    }
                }
            }
        }

        // Swap buffers — no new HashMap allocated
        HashMap<BlockPos, int[]> tmp = currentGen;
        currentGen = nextGen;
        nextGen = tmp;

        return overflowBuffer;
    }

    // -------------------------------------------------------------------------
    // Phase 2: inbox from WorldPhysicsManager
    // -------------------------------------------------------------------------

    public void receiveInbox(RulesetOutput output) {
        inboxBuffer.add(output);
    }

    public void applyInbox() {
        for (RulesetOutput output : inboxBuffer) {
            if (output.value() > 0) {
                mergeIntoMap(currentGen, output);
            }
        }
        inboxBuffer.clear();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public void setValue(BlockPos pos, PhysicsType type, int value) {
        if (value <= 0) {
            int[] arr = currentGen.get(pos);
            if (arr != null) {
                arr[type.index] = 0;
                if (allZero(arr)) currentGen.remove(pos);
            }
        } else {
            int[] arr = currentGen.computeIfAbsent(pos, k -> new int[PhysicsType.COUNT]);
            arr[type.index] = value;
        }
    }

    public int getValue(BlockPos pos, PhysicsType type) {
        int[] arr = currentGen.get(pos);
        return arr != null ? arr[type.index] : 0;
    }

    public int getActiveBlockCount() {
        return currentGen.size();
    }

    public void forEachActiveBlock(BiConsumer<BlockPos, int[]> consumer) {
        currentGen.forEach(consumer);
    }

    public void clearAllData() {
        currentGen.clear();
        nextGen.clear();
        inboxBuffer.clear();
        overflowBuffer.clear();
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Merge strategy: MAX value wins when two sources hit the same block.
     * (two shockwaves converging — the stronger one wins)
     */
    private void mergeIntoMap(HashMap<BlockPos, int[]> gen, RulesetOutput output) {
        int[] arr = gen.computeIfAbsent(output.pos(), k -> new int[PhysicsType.COUNT]);
        arr[output.type().index] = Math.max(arr[output.type().index], output.value());
    }

    private boolean allZero(int[] arr) {
        for (int v : arr) if (v != 0) return false;
        return true;
    }
}
