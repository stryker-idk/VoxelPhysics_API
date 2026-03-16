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
 * Data model:
 *   HashMap<BlockPos, int[]>
 *     Key:   world BlockPos (only blocks with at least one non-zero value are stored)
 *     Value: int array indexed by PhysicsType.index — sparse by design
 *
 * Tick is split into two phases (called by WorldPhysicsManager):
 *   1. tick()       — compute next generation, return overflow outside this chunk
 *   2. applyInbox() — merge in data that arrived from neighboring chunks
 */
public class ChunkPhysicsManager {

    // Chunk coordinates (chunk-space, not block-space)
    private final int chunkX;
    private final int chunkZ;

    // Pre-computed block-space bounds for O(1) boundary check
    private final int minX, maxX, minZ, maxZ;

    // Current generation: only non-zero entries are stored
    private HashMap<BlockPos, int[]> currentGen = new HashMap<>();

    // Pending updates from neighboring chunks, applied at end of tick
    private final List<RulesetOutput> inbox = new ArrayList<>();

    public ChunkPhysicsManager(int chunkX, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.minX = chunkX * 16;
        this.maxX = chunkX * 16 + 15;
        this.minZ = chunkZ * 16;
        this.maxZ = chunkZ * 16 + 15;
    }

    // -------------------------------------------------------------------------
    // Boundary check — O(1), no hashmap involved
    // -------------------------------------------------------------------------

    public boolean isInChunk(BlockPos pos) {
        int x = pos.getX();
        int z = pos.getZ();
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        // Y is unbounded per-chunk (full column), so no Y check needed
    }

    // -------------------------------------------------------------------------
    // Phase 1: Compute next generation
    // Returns overflow — outputs that belong to a different chunk
    // -------------------------------------------------------------------------

    public List<RulesetOutput> tick() {
        List<RulesetOutput> overflow = new ArrayList<>();
        HashMap<BlockPos, int[]> nextGen = new HashMap<>();

        for (Map.Entry<BlockPos, int[]> entry : currentGen.entrySet()) {
            BlockPos pos = entry.getKey();
            int[] values = entry.getValue();

            for (PhysicsType type : PhysicsType.values()) {
                int val = values[type.index];
                if (val == 0) continue; // skip zero values (sparse)

                List<RulesetOutput> outputs = RulesetRegistry.get(type).compute(pos, val);

                for (RulesetOutput output : outputs) {
                    if (output.value() <= 0) continue; // don't store zeros

                    if (isInChunk(output.pos())) {
                        mergeIntoMap(nextGen, output);
                    } else {
                        overflow.add(output);
                    }
                }
            }
        }

        currentGen = nextGen;
        return overflow;
    }

    // -------------------------------------------------------------------------
    // Phase 2: Receive and apply inbox from WorldPhysicsManager
    // -------------------------------------------------------------------------

    public void receiveInbox(RulesetOutput output) {
        inbox.add(output);
    }

    public void applyInbox() {
        for (RulesetOutput output : inbox) {
            if (output.value() > 0) {
                mergeIntoMap(currentGen, output);
            }
        }
        inbox.clear();
    }

    // -------------------------------------------------------------------------
    // Public API — set a value from outside (e.g. an explosion block event)
    // -------------------------------------------------------------------------

    /**
     * Directly set a physics value at a block position.
     * Use this to "seed" a simulation, e.g. trigger an explosion shockwave.
     * The pos must be inside this chunk.
     */
    public void setValue(BlockPos pos, PhysicsType type, int value) {
        if (value <= 0) {
            // Remove the value if it exists
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

    /** Exposed for PhysicsDebugRenderer — returns the live current generation map. */
    public Map<BlockPos, int[]> getActiveBlocks() {
        return currentGen;
    }

    public int getActiveBlockCount() {
        return currentGen.size();
    }

    /** Iterates all active blocks — used by the debug visualizer. */
    public void forEachActiveBlock(BiConsumer<BlockPos, int[]> consumer) {
        currentGen.forEach(consumer);
    }

    // -------------------------------------------------------------------------
    // Internal helpers    // -------------------------------------------------------------------------

    /**
     * Merges a RulesetOutput into a generation map.
     * Merge strategy: take the MAX value if two sources hit the same block.
     * (e.g. two shockwaves converging — the stronger one wins)
     */
    private void mergeIntoMap(HashMap<BlockPos, int[]> gen, RulesetOutput output) {
        int[] arr = gen.computeIfAbsent(output.pos(), k -> new int[PhysicsType.COUNT]);
        arr[output.type().index] = Math.max(arr[output.type().index], output.value());
    }

    private boolean allZero(int[] arr) {
        for (int v : arr) {
            if (v != 0) return false;
        }
        return true;
    }
}
