package net.Stryker.VoxelPhysicsAPI;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The physics simulation engine.
 *
 * Uses LongIntMap instead of HashMap<Long, Integer> — zero autoboxing,
 * zero GC pressure in the hot path.
 *
 * To add a new physics type: edit PhysicsType.java only.
 */
public class PhysicsEngine {

    // -------------------------------------------------------------------------
    // Coordinate packing — XYZ → long, no object allocation
    // -------------------------------------------------------------------------

    private static final long X_OFFSET = 33_000_000L;
    private static final long Y_OFFSET = 64L;
    private static final long Z_OFFSET = 33_000_000L;

    public static long pack(int x, int y, int z) {
        return (x + X_OFFSET) | ((y + Y_OFFSET) << 26) | ((z + Z_OFFSET) << 36);
    }

    public static int unpackX(long key) { return (int)((key & 0x3FFFFFFL)         - X_OFFSET); }
    public static int unpackY(long key) { return (int)(((key >> 26) & 0x3FFL)     - Y_OFFSET); }
    public static int unpackZ(long key) { return (int)(((key >> 36) & 0x3FFFFFFL) - Z_OFFSET); }

    // -------------------------------------------------------------------------
    // Per-type double-buffered primitive maps
    // Pre-sized for ~200k entries at 60% load = capacity 524288
    // -------------------------------------------------------------------------

    private static final int INITIAL_CAPACITY = 1 << 19; // 524288

    private final LongIntMap[] current      = new LongIntMap[PhysicsType.COUNT];
    private final LongIntMap[] next         = new LongIntMap[PhysicsType.COUNT];
    private final LongIntMap[] snapshotMaps = new LongIntMap[PhysicsType.COUNT];

    private final int[] tickCounters = new int[PhysicsType.COUNT];

    // Snapshot — updated periodically for the debug renderer
    private final AtomicReference<LongIntMap[]> snapshot =
            new AtomicReference<>(new LongIntMap[PhysicsType.COUNT]);

    // Seeds from other threads: [packedKey, typeOrdinal, value]
    private final ConcurrentLinkedQueue<long[]> seedQueue = new ConcurrentLinkedQueue<>();

    private boolean active     = false;
    private int     quietTicks = 0;
    private int     globalTick = 0;

    public static volatile boolean snapshotEnabled = false;

    private static final int QUIET_THRESHOLD   = 20;
    private static final int SNAPSHOT_INTERVAL = 20;

    // -------------------------------------------------------------------------
    // Init
    // -------------------------------------------------------------------------

    public PhysicsEngine() {
        for (int i = 0; i < PhysicsType.COUNT; i++) {
            current[i]      = new LongIntMap(INITIAL_CAPACITY);
            next[i]         = new LongIntMap(INITIAL_CAPACITY);
            snapshotMaps[i] = new LongIntMap(INITIAL_CAPACITY);
        }
        snapshot.set(snapshotMaps);
    }

    // -------------------------------------------------------------------------
    // Public API — thread-safe
    // -------------------------------------------------------------------------

    public void seed(int x, int y, int z, PhysicsType type, int value) {
        if (value <= 0) return;
        seedQueue.add(new long[]{ pack(x, y, z), type.ordinal(), value });
        active     = true;
        quietTicks = 0;
    }

    public void clear() {
        for (int i = 0; i < PhysicsType.COUNT; i++) {
            current[i].clear();
            next[i].clear();
            snapshotMaps[i].clear();
        }
        seedQueue.clear();
        active     = false;
        quietTicks = 0;
    }

    public LongIntMap getSnapshot(PhysicsType type) {
        return snapshot.get()[type.ordinal()];
    }

    public int getActiveBlockCount(PhysicsType type) {
        return current[type.ordinal()].size();
    }

    // -------------------------------------------------------------------------
    // Tick — called by PhysicsThread only
    // -------------------------------------------------------------------------

    public void tick() {
        // Drain seeds
        long[] seed;
        while ((seed = seedQueue.poll()) != null) {
            int typeIdx = (int) seed[1];
            current[typeIdx].putMax(seed[0], (int) seed[2]);
            active     = true;
            quietTicks = 0;
        }

        if (!active) return;

        globalTick++;
        boolean anyActive = false;

        for (PhysicsType type : PhysicsType.values()) {
            int i = type.ordinal();
            if (current[i].isEmpty()) continue;

            tickCounters[i]++;
            if (tickCounters[i] < type.tickInterval) {
                anyActive = true;
                continue;
            }
            tickCounters[i] = 0;

            next[i].clear();
            boolean stillActive = type.ruleset.tick(current[i], next[i]);

            // Swap — no allocation
            LongIntMap tmp = current[i];
            current[i]     = next[i];
            next[i]        = tmp;

            if (stillActive) anyActive = true;
        }

        if (!anyActive) {
            quietTicks++;
            if (quietTicks >= QUIET_THRESHOLD) {
                active     = false;
                quietTicks = 0;
            }
        } else {
            quietTicks = 0;
        }

        // Snapshot — only when renderer is watching, reuse existing maps
        if (snapshotEnabled && globalTick % SNAPSHOT_INTERVAL == 0) {
            for (int i = 0; i < PhysicsType.COUNT; i++) {
                snapshotMaps[i].clear();
                final int fi = i;
                current[i].forEach((key, value) -> snapshotMaps[fi].put(key, value));
            }
        }
    }
}
