package net.Stryker.VoxelPhysicsAPI;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Owns and manages the dedicated physics simulation thread.
 *
 * Architecture:
 *   - One singleton for the entire mod.
 *   - Physics thread runs at PhysicsConfig.PHYSICS_TPS independently of Minecraft's 20 TPS.
 *   - Main thread communicates ONLY through lock-free ConcurrentLinkedQueues.
 *   - Renderer reads a periodic snapshot via AtomicReference — never live data.
 *
 * Main thread never touches hashmaps. Physics thread never calls Minecraft APIs.
 */
public class PhysicsThreadManager {

    private static final Logger LOGGER = LogManager.getLogger("VoxelPhysics");

    // -------------------------------------------------------------------------
    // Singleton
    // -------------------------------------------------------------------------

    private static final PhysicsThreadManager INSTANCE = new PhysicsThreadManager();
    public static PhysicsThreadManager get() { return INSTANCE; }

    // -------------------------------------------------------------------------
    // Inbound queues — main thread writes, physics thread drains
    // -------------------------------------------------------------------------

    private final ConcurrentLinkedQueue<PhysicsEvents.SeedEvent>            seedQueue        = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<PhysicsEvents.ChunkLoadEvent>       chunkLoadQueue   = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<PhysicsEvents.ChunkUnloadEvent>     chunkUnloadQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<PhysicsEvents.DimensionUnloadEvent> dimUnloadQueue   = new ConcurrentLinkedQueue<>();

    /**
     * Clear queue — signals the physics thread to wipe all hashmaps for a dimension
     * WITHOUT unregistering chunks. Chunks keep simulating; data is just zeroed.
     * Used by /vpdebug clear — avoids needing to access the protected ChunkMap.
     */
    private final ConcurrentLinkedQueue<ResourceKey<Level>> clearQueue = new ConcurrentLinkedQueue<>();

    // -------------------------------------------------------------------------
    // Render snapshot — physics thread writes, renderer reads
    // -------------------------------------------------------------------------

    public record RenderSnapshot(Map<ResourceKey<Level>, Map<BlockPos, int[]>> data) {}

    private final AtomicReference<RenderSnapshot> renderSnapshot =
            new AtomicReference<>(new RenderSnapshot(Map.of()));

    public RenderSnapshot getRenderSnapshot() { return renderSnapshot.get(); }

    // -------------------------------------------------------------------------
    // Thread lifecycle
    // -------------------------------------------------------------------------

    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread physicsThread;

    public void start() {
        if (running.getAndSet(true)) return;
        physicsThread = new Thread(this::loop, "VoxelPhysics-Thread");
        physicsThread.setDaemon(true);
        physicsThread.setPriority(Thread.NORM_PRIORITY);
        physicsThread.start();
        LOGGER.info("Physics thread started at {} TPS", PhysicsConfig.PHYSICS_TPS);
    }

    public void stop() {
        running.set(false);
        if (physicsThread != null) {
            physicsThread.interrupt();
            physicsThread = null;
        }
        LOGGER.info("Physics thread stopped.");
    }

    // -------------------------------------------------------------------------
    // Tick loop — runs entirely on the physics thread
    // -------------------------------------------------------------------------

    private final Map<ResourceKey<Level>, WorldPhysicsManager> worlds = new HashMap<>();

    private void loop() {
        long tickCount = 0;
        VoxelPhysicsAPI.LOGGER.info("[VoxelPhysics] Thread loop started.");

        while (running.get()) {
            long tickStart = System.nanoTime();

            try {
                drainQueues();

                for (WorldPhysicsManager wpm : worlds.values()) {
                    wpm.tick();
                }

                tickCount++;
                if (tickCount % PhysicsConfig.SNAPSHOT_INTERVAL == 0) {
                    publishSnapshot();
                }

                // Log once per second (every 100 ticks) if there's active data
                if (tickCount % 100 == 0) {
                    int totalBlocks = worlds.values().stream()
                            .mapToInt(WorldPhysicsManager::getTotalActiveBlocks).sum();
                    if (totalBlocks > 0) {
                        VoxelPhysicsAPI.LOGGER.info("[VoxelPhysics] tick={} activeBlocks={} worlds={}",
                                tickCount, totalBlocks, worlds.size());
                    }
                }

            } catch (Exception e) {
                // Log and continue — don't let one bad tick kill the thread silently
                VoxelPhysicsAPI.LOGGER.error("[VoxelPhysics] Exception in physics tick {}: {}",
                        tickCount, e.getMessage(), e);
            }

            long sleepNanos = PhysicsConfig.NANOS_PER_TICK - (System.nanoTime() - tickStart);
            if (sleepNanos > 0) {
                try {
                    Thread.sleep(sleepNanos / 1_000_000, (int)(sleepNanos % 1_000_000));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Queue draining — called at the start of each physics tick
    // -------------------------------------------------------------------------

    private void drainQueues() {
        // Chunk loads
        PhysicsEvents.ChunkLoadEvent loadEvt;
        while ((loadEvt = chunkLoadQueue.poll()) != null) {
            worlds.computeIfAbsent(loadEvt.dimension(), k -> new WorldPhysicsManager())
                  .registerChunk(loadEvt.chunkX(), loadEvt.chunkZ());
        }

        // Chunk unloads
        PhysicsEvents.ChunkUnloadEvent unloadEvt;
        while ((unloadEvt = chunkUnloadQueue.poll()) != null) {
            WorldPhysicsManager wpm = worlds.get(unloadEvt.dimension());
            if (wpm != null) wpm.unregisterChunk(unloadEvt.chunkX(), unloadEvt.chunkZ());
        }

        // Dimension unloads
        PhysicsEvents.DimensionUnloadEvent dimEvt;
        while ((dimEvt = dimUnloadQueue.poll()) != null) {
            worlds.remove(dimEvt.dimension());
        }

        // Clear signals — wipe all hashmaps but keep chunk registrations intact
        ResourceKey<Level> clearDim;
        while ((clearDim = clearQueue.poll()) != null) {
            WorldPhysicsManager wpm = worlds.get(clearDim);
            if (wpm != null) wpm.clearAllData();
        }

        // Seed events
        PhysicsEvents.SeedEvent seedEvt;
        while ((seedEvt = seedQueue.poll()) != null) {
            WorldPhysicsManager wpm = worlds.get(seedEvt.dimension());
            if (wpm != null) {
                wpm.setBlockValue(seedEvt.pos(), seedEvt.type(), seedEvt.value());
                VoxelPhysicsAPI.LOGGER.info("[VoxelPhysics] Seeded {}={} at {} (chunks registered: {})",
                        seedEvt.type(), seedEvt.value(), seedEvt.pos(),
                        wpm.getChunkManagers().size());
            } else {
                VoxelPhysicsAPI.LOGGER.warn("[VoxelPhysics] Seed dropped — no WorldPhysicsManager for dimension: {}. Known dims: {}",
                        seedEvt.dimension().location(), worlds.keySet().stream()
                                .map(k -> k.location().toString()).toList());
            }
        }
    }

    // -------------------------------------------------------------------------
    // Snapshot publishing
    // -------------------------------------------------------------------------

    private void publishSnapshot() {
        Map<ResourceKey<Level>, Map<BlockPos, int[]>> snapshotData = new HashMap<>();

        for (var dimEntry : worlds.entrySet()) {
            Map<BlockPos, int[]> dimSnapshot = new HashMap<>();

            dimEntry.getValue().getChunkManagers().forEach((chunkPos, chunkManager) ->
                chunkManager.forEachActiveBlock((pos, values) ->
                    dimSnapshot.put(pos, values.clone())
                )
            );

            if (!dimSnapshot.isEmpty()) {
                snapshotData.put(dimEntry.getKey(), dimSnapshot);
            }
        }

        renderSnapshot.set(new RenderSnapshot(snapshotData));
    }

    // -------------------------------------------------------------------------
    // Public API — called from the main thread only
    // -------------------------------------------------------------------------

    public void onChunkLoad(ResourceKey<Level> dim, int cx, int cz) {
        chunkLoadQueue.add(new PhysicsEvents.ChunkLoadEvent(dim, cx, cz));
    }

    public void onChunkUnload(ResourceKey<Level> dim, int cx, int cz) {
        chunkUnloadQueue.add(new PhysicsEvents.ChunkUnloadEvent(dim, cx, cz));
    }

    public void onDimensionUnload(ResourceKey<Level> dim) {
        dimUnloadQueue.add(new PhysicsEvents.DimensionUnloadEvent(dim));
    }

    public void seed(ResourceKey<Level> dim, BlockPos pos, PhysicsType type, int value) {
        seedQueue.add(new PhysicsEvents.SeedEvent(dim, pos, type, value));
    }

    /**
     * Clears all physics data for a dimension without unregistering chunks.
     * Chunks continue simulating; all values are just zeroed.
     * Safe to call from the main thread — feeds the clearQueue.
     */
    public void clearDimension(ResourceKey<Level> dim) {
        clearQueue.add(dim);
    }
}
