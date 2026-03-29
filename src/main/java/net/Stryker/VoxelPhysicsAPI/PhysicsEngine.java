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

    // Initialized in constructor after registry is frozen
    private final LongIntMap[][] current;
    private final LongIntMap[][] next;
    private final LongIntMap[][] snapshotMaps;
    private final int[] tickCounters;

    private final AtomicReference<LongIntMap[][]> snapshot;
    private final ConcurrentLinkedQueue<long[]> seedQueue = new ConcurrentLinkedQueue<>();

    private boolean active = false;
    private int quietTicks = 0;
    private int globalTick = 0;
    public static volatile boolean snapshotEnabled = false;

    private static final int QUIET_THRESHOLD = 20;
    private static final int SNAPSHOT_INTERVAL = 20;

    public PhysicsEngine() {
        int typeCount = PhysicsTypeRegistry.count();

        current = new LongIntMap[typeCount][];
        next = new LongIntMap[typeCount][];
        snapshotMaps = new LongIntMap[typeCount][];
        tickCounters = new int[typeCount];

        for (int i = 0; i < typeCount; i++) {
            PhysicsType type = PhysicsTypeRegistry.values().get(i);
            int values = type.getValuesPerCell();

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
        if (values.length != type.getValuesPerCell()) {
            throw new IllegalArgumentException(
                    type.getId() + " requires exactly " + type.getValuesPerCell() +
                            " value(s), but got " + values.length
            );
        }

        System.out.println("[PHYSICS DEBUG] Seeding " + type.getId() + " at " + x + "," + y + "," + z);

        boolean hasValue = false;
        for (int i = 0; i < values.length; i++) {
            if (values[i] > 0) {
                seedQueue.add(new long[]{ pack(x, y, z), type.ordinal(), i, values[i] });
                System.out.println("[PHYSICS DEBUG] Added to queue: type=" + type.ordinal() +
                        ", value=" + values[i] + ", queue size now: " + seedQueue.size());
                hasValue = true;
            }
        }

        if (hasValue) {
            active = true;
            quietTicks = 0;
        }
    }

    public void clear() {
        for (int i = 0; i < current.length; i++) {
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

    public LongIntMap getSnapshot(PhysicsType type, int valueIndex) {
        return snapshot.get()[type.ordinal()][valueIndex];
    }

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

    public int getValue(int x, int y, int z, PhysicsType type, int valueIndex) {
        if (valueIndex < 0 || valueIndex >= type.getValuesPerCell()) return 0;
        long key = pack(x, y, z);
        return getSnapshot(type, valueIndex).get(key);
    }

    public int getValue(int x, int y, int z, PhysicsType type) {
        return getValue(x, y, z, type, 0);
    }

    public void tick() {

        //DEBUG
        //System.out.println("[PHYSICS DEBUG] Tick! Queue size: " + seedQueue.size() +
        //        ", Active: " + active + ", Types: " + current.length);

        // Drain seeds (ONCE!)
        long[] seed;
        int drained = 0;
        while ((seed = seedQueue.poll()) != null) {
            int typeIdx = (int) seed[1];
            int valueIdx = (int) seed[2];
            current[typeIdx][valueIdx].putMax(seed[0], (int) seed[3]);
            active = true;
            quietTicks = 0;
            drained++;
        }
        
        if (drained > 0) {
            System.out.println("[PHYSICS DEBUG] Drained " + drained + " seeds");
        }

        if (!active) return;

        globalTick++;
        boolean anyActive = false;

        for (PhysicsType type : PhysicsTypeRegistry.values()) {
            int i = type.ordinal();

            boolean allEmpty = true;
            for (LongIntMap map : current[i]) {
                if (!map.isEmpty()) {
                    allEmpty = false;
                    break;
                }
            }
            if (allEmpty) continue;

            tickCounters[i]++;
            if (tickCounters[i] < type.getTickInterval()) {
                anyActive = true;
                continue;
            }
            tickCounters[i] = 0;

            for (LongIntMap map : next[i]) {
                map.clear();
            }

            boolean stillActive = type.getRuleset().tick(current[i], next[i]);

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

        if (snapshotEnabled && globalTick % SNAPSHOT_INTERVAL == 0) {
            for (int i = 0; i < current.length; i++) {
                for (int v = 0; v < current[i].length; v++) {
                    snapshotMaps[i][v].clear();
                    final LongIntMap snap = snapshotMaps[i][v];
                    final LongIntMap src = current[i][v];
                    src.forEach((key, value) -> snap.put(key, value));
                }
            }
        }
    }

    public int getTypeCount() {
        return current != null ? current.length : 0;
    }
}