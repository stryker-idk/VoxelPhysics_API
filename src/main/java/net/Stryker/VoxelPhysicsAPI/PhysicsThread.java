package net.Stryker.VoxelPhysicsAPI;

import net.minecraft.core.BlockPos;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.concurrent.atomic.AtomicBoolean;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PhysicsThread {

    private static final PhysicsThread INSTANCE = new PhysicsThread();
    public static PhysicsThread get() { return INSTANCE; }

    public PhysicsEngine engine;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread thread;

    private static final long NANOS_PER_TICK = 1_000_000_000L / 100;

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        get().start();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        get().stop();
    }

    public void start() {
        if (running.getAndSet(true)) return;

        // Create engine HERE after registry is frozen
        if (PhysicsTypeRegistry.count() == 0) {
            VoxelPhysicsAPI.LOGGER.warn("No physics types registered! Engine will be idle.");
        }
        engine = new PhysicsEngine();

        thread = new Thread(this::loop, "VoxelPhysics-Thread");
        // ... rest
    }

    public void stop() {
        running.set(false);
        if (thread != null) { thread.interrupt(); thread = null; }
        VoxelPhysicsAPI.LOGGER.info("[VoxelPhysics] Physics thread stopped.");
    }

    private void loop() {
        while (running.get()) {
            long start = System.nanoTime();
            try {
                engine.tick();
            } catch (Exception e) {
                VoxelPhysicsAPI.LOGGER.error("[VoxelPhysics] Tick error: ", e);
            }
            long sleep = NANOS_PER_TICK - (System.nanoTime() - start);
            if (sleep > 0) {
                try {
                    Thread.sleep(sleep / 1_000_000, (int)(sleep % 1_000_000));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** Seed a single-value type (e.g. PRESSURE). */
    public void seed(BlockPos pos, PhysicsType type, int... values) {
        engine.seed(pos.getX(), pos.getY(), pos.getZ(), type, values);
    }

    /** Convenience for raw coordinates. */
    public void seed(int x, int y, int z, PhysicsType type, int... values) {
        engine.seed(x, y, z, type, values);
    }

    public void clear() { engine.clear(); }
}
