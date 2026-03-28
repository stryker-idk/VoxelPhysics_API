package net.Stryker.VoxelPhysicsAPI;

/**
 * A minimal open-addressing hash map from primitive long → primitive int.
 *
 * WHY THIS EXISTS:
 *   HashMap<Long, Integer> autoboxes every key and value into objects.
 *   At 200k active blocks that's 400k object allocations per tick → GC spikes.
 *   This map stores everything in two plain arrays — zero object allocation
 *   during normal get/put operations.
 *
 * Design:
 *   - Open addressing with linear probing
 *   - Load factor 0.6 (resizes at 60% capacity)
 *   - EMPTY_KEY sentinel = Long.MIN_VALUE (reserved, can't be used as a key)
 *   - Values of 0 are valid; use remove() to delete entries
 *   - Not thread-safe — owned by the physics thread only
 */
public final class LongIntMap {

    private static final long EMPTY_KEY   = Long.MIN_VALUE;
    private static final float LOAD_FACTOR = 0.6f;

    private long[] keys;
    private int[]  values;
    private int    size;
    private int    threshold;
    private int    mask; // capacity - 1, for fast modulo

    public LongIntMap(int initialCapacity) {
        int cap = nextPowerOfTwo(Math.max(8, initialCapacity));
        keys      = new long[cap];
        values    = new int[cap];
        mask      = cap - 1;
        threshold = (int)(cap * LOAD_FACTOR);
        java.util.Arrays.fill(keys, EMPTY_KEY);
    }

    // -------------------------------------------------------------------------
    // Core operations
    // -------------------------------------------------------------------------

    public int get(long key) {
        int i = index(key);
        while (keys[i] != EMPTY_KEY) {
            if (keys[i] == key) return values[i];
            i = (i + 1) & mask;
        }
        return 0; // not found → treat as 0
    }

    public boolean containsKey(long key) {
        int i = index(key);
        while (keys[i] != EMPTY_KEY) {
            if (keys[i] == key) return true;
            i = (i + 1) & mask;
        }
        return false;
    }

    public void put(long key, int value) {
        if (size >= threshold) resize();
        int i = index(key);
        while (keys[i] != EMPTY_KEY && keys[i] != key) {
            i = (i + 1) & mask;
        }
        if (keys[i] == EMPTY_KEY) size++;
        keys[i]   = key;
        values[i] = value;
    }

    /** Put only if new value is greater than existing (max merge). */
    public void putMax(long key, int value) {
        if (size >= threshold) resize();
        int i = index(key);
        while (keys[i] != EMPTY_KEY && keys[i] != key) {
            i = (i + 1) & mask;
        }
        if (keys[i] == EMPTY_KEY) {
            size++;
            keys[i]   = key;
            values[i] = value;
        } else {
            if (value > values[i]) values[i] = value;
        }
    }

    public void remove(long key) {
        int i = index(key);
        while (keys[i] != EMPTY_KEY) {
            if (keys[i] == key) {
                keys[i] = EMPTY_KEY;
                size--;
                // Rehash subsequent entries to maintain probing invariant
                i = (i + 1) & mask;
                while (keys[i] != EMPTY_KEY) {
                    long k = keys[i];
                    int  v = values[i];
                    keys[i] = EMPTY_KEY;
                    size--;
                    put(k, v);
                    i = (i + 1) & mask;
                }
                return;
            }
            i = (i + 1) & mask;
        }
    }

    public void clear() {
        java.util.Arrays.fill(keys, EMPTY_KEY);
        size = 0;
    }

    public int size() { return size; }
    public boolean isEmpty() { return size == 0; }

    /**
     * Iterate all entries without allocation.
     * Usage:
     *   map.forEach((key, value) -> { ... });
     */
    public void forEach(LongIntConsumer consumer) {
        for (int i = 0; i < keys.length; i++) {
            if (keys[i] != EMPTY_KEY) {
                consumer.accept(keys[i], values[i]);
            }
        }
    }

    @FunctionalInterface
    public interface LongIntConsumer {
        void accept(long key, int value);
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private int index(long key) {
        // Fibonacci hashing — better distribution than key & mask alone
        return (int)((key * 0x9E3779B97F4A7C15L) >>> (64 - Integer.numberOfTrailingZeros(keys.length))) & mask;
    }

    private void resize() {
        long[] oldKeys   = keys;
        int[]  oldValues = values;
        int    newCap    = keys.length * 2;

        keys      = new long[newCap];
        values    = new int[newCap];
        mask      = newCap - 1;
        threshold = (int)(newCap * LOAD_FACTOR);
        size      = 0;
        java.util.Arrays.fill(keys, EMPTY_KEY);

        for (int i = 0; i < oldKeys.length; i++) {
            if (oldKeys[i] != EMPTY_KEY) {
                put(oldKeys[i], oldValues[i]);
            }
        }
    }

    private static int nextPowerOfTwo(int n) {
        n--;
        n |= n >> 1; n |= n >> 2; n |= n >> 4; n |= n >> 8; n |= n >> 16;
        return n + 1;
    }
}
