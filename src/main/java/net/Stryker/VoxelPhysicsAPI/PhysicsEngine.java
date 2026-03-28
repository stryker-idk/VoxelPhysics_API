package net.Stryker.VoxelPhysicsAPI;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

public class PhysicsEngine {

    private static final long X_OFFSET = 33_000_000L;
    private static final long Y_OFFSET = 64L;
    private static final long Z_OFFSET = 33_000_000L;

    public static long pack(int x, int y, int z) {
        return (x + X_OFFSET) | ((y + Y_OFFSET) << 26) | ((z + Z_OFFSET) << 36);
    }

    public static int unpackX(long key) { return (int)((key & 0x3FFFFFFL) - X_OFFSET); }
    public static int unpackY(long key) { return (int)(((key >> 26) & 0x3FFL) - Y_OFFSET); }
    public static int unpackZ(long key) { return (int)(((key >> 36) & 0x3FFFFFFL) - Z_OFFSET); }

    private static final int INITIAL_CAPACITY = 1 << 19;

    // Now 2D arrays: [typeIndex][valueIndex]
    private final LongIntMap[][] current;
    private final LongIntMap[][] next;
    private final LongIntMap[][] snapshotMaps;

    private final int[] tickCounters = new int[PhysicsType.COUNT];

    // Snapshot now holds 2D array
    private final AtomicReference<LongIntMap[][]> snapshot;

    // Seed queue now includes valueIndex: [packedKey, typeOrdinal, valueIndex, value]
    private final ConcurrentLinkedQueue<long[]> seedQueue = new ConcurrentLinkedQueue<>();

    private boolean active = false;
    private int quietTicks = 0;
    private int globalTick = 0;
    public static volatile boolean snapshotEnabled = false;

    private static final int QUIET_THRESHOLD = 20;
    private static final int SNAPSHOT_INTERVAL = 20;

    public PhysicsEngine() {
        // Allocate 2D arrays based on valuesPerCell for each type
        current = new LongIntMap[PhysicsType.COUNT][];
        next = new LongIntMap[PhysicsType.COUNT][];
        snapshotMaps = new LongIntMap[PhysicsType.COUNT][];

        for (int i = 0; i < PhysicsType.COUNT; i++) {
            PhysicsType type = PhysicsType.values()[i];
            int values = type.valuesPerCell;

            current[i] = new LongIntMap[values];
            next[i] = new LongIntMap[values];
            snapshotMaps[i] = new LongIntMap[values];

            for (int v = 0; v < values; v++) {
                current[i][v] = new LongIntMap(INITIAL_CAPACITY);
                next[i][v] = new LongIntMap(INITIAL_CAPACITY);
                snapshotMaps[i][v] = new LongIntMap(INITIAL_CAPACITY);
            }
        }

        snapshot = new AtomicReference<>(snapshotMaps);
    }


    public void seed(int x, int y, int z, PhysicsType type, int... values) {
        if (values.length != type.valuesPerCell) {
            throw new IllegalArgumentException(
                    type.name() + " requires exactly " + type.valuesPerCell +
                            " value(s), but got " + values.length
            );
        }

        // Only mark active if at least one value is non-zero
        boolean hasValue = false;
        for (int i = 0; i < values.length; i++) {
            if (values[i] > 0) {
                seedQueue.add(new long[]{ pack(x, y, z), type.ordinal(), i, values[i] });
                hasValue = true;
            }
        }

        if (hasValue) {
            active = true;
            quietTicks = 0;
        }
    }

    /**
     * Get the current value at a specific coordinate.
     * Reads from snapshot (thread-safe, may be up to 1 tick behind).
     *
     * @return The value, or 0 if no physics data exists there
     */
    public int getValue(int x, int y, int z, PhysicsType type, int valueIndex) {
        if (valueIndex < 0 || valueIndex >= type.valuesPerCell) return 0;
        long key = pack(x, y, z);
        return getSnapshot(type, valueIndex).get(key);
    }

    public void clear() {
        for (int i = 0; i < PhysicsType.COUNT; i++) {
            for (int v = 0; v < current[i].length; v++) {
                current[i][v].clear();
                next[i][v].clear();
                snapshotMaps[i][v].clear();
            }
        }
        seedQueue.clear();
        active = false;
        quietTicks = 0;
    }

    // Get specific value map from snapshot
    public LongIntMap getSnapshot(PhysicsType type, int valueIndex) {
        return snapshot.get()[type.ordinal()][valueIndex];
    }

    // Convenience: get first value (for single-value types)
    public LongIntMap getSnapshot(PhysicsType type) {
        return getSnapshot(type, 0);
    }

    public int getActiveBlockCount(PhysicsType type, int valueIndex) {
        return current[type.ordinal()][valueIndex].size();
    }

    public int getTotalActiveBlocks(PhysicsType type) {
        int total = 0;
        for (LongIntMap map : current[type.ordinal()]) {
            total += map.size();
        }
        return total;
    }

    public void tick() {
        // Drain seeds
        long[] seed;
        while ((seed = seedQueue.poll()) != null) {
            int typeIdx = (int) seed[1];
            int valueIdx = (int) seed[2];
            current[typeIdx][valueIdx].putMax(seed[0], (int) seed[3]);
            active = true;
            quietTicks = 0;
        }

        if (!active) return;

        globalTick++;
        boolean anyActive = false;

        for (PhysicsType type : PhysicsType.values()) {
            int i = type.ordinal();

            // Skip if all value maps are empty
            boolean allEmpty = true;
            for (LongIntMap map : current[i]) {
                if (!map.isEmpty()) {
                    allEmpty = false;
                    break;
                }
            }
            if (allEmpty) continue;

            tickCounters[i]++;
            if (tickCounters[i] < type.tickInterval) {
                anyActive = true;
                continue;
            }
            tickCounters[i] = 0;

            // Clear all next maps for this type
            for (LongIntMap map : next[i]) {
                map.clear();
            }

            boolean stillActive = type.ruleset.tick(current[i], next[i]);

            // Swap all maps for this type
            for (int v = 0; v < current[i].length; v++) {
                LongIntMap tmp = current[i][v];
                current[i][v] = next[i][v];
                next[i][v] = tmp;
            }

            if (stillActive) anyActive = true;
        }

        if (!anyActive) {
            quietTicks++;
            if (quietTicks >= QUIET_THRESHOLD) {
                active = false;
                quietTicks = 0;
            }
        } else {
            quietTicks = 0;
        }

        // Snapshot update
        if (snapshotEnabled && globalTick % SNAPSHOT_INTERVAL == 0) {
            for (int i = 0; i < PhysicsType.COUNT; i++) {
                for (int v = 0; v < current[i].length; v++) {
                    snapshotMaps[i][v].clear();
                    final LongIntMap snap = snapshotMaps[i][v];
                    final LongIntMap src = current[i][v];
                    src.forEach((key, value) -> snap.put(key, value));
                }
            }
        }
    }
}
