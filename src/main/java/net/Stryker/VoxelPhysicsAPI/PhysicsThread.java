package net.Stryker.VoxelPhysicsAPI;

import net.minecraft.core.BlockPos;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.concurrent.atomic.AtomicBoolean;

import static net.Stryker.VoxelPhysicsAPI.VoxelPhysicsAPI.LOGGER;

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
        if (running.getAndSet(true)) {
            LOGGER.info("[VoxelPhysics] Physics thread already running.");
            return;
        }

        System.out.println("[PHYSICS DEBUG] Creating PhysicsEngine...");
        if (engine == null) {
            engine = new PhysicsEngine();
            System.out.println("[PHYSICS DEBUG] Engine created with " + engine.getTypeCount() + " types");
        }

        thread = new Thread(this::loop, "VoxelPhysics-Thread");
        thread.setDaemon(true);
        thread.start();
        LOGGER.info("[VoxelPhysics] Physics thread started at 100 TPS.");
    }

    public void stop() {
        running.set(false);
        if (thread != null) { thread.interrupt(); thread = null; }
        LOGGER.info("[VoxelPhysics] Physics thread stopped.");
    }

    private void loop() {

        //DEBUG
        //System.out.println("[PHYSICS DEBUG] Thread loop starting!");

        while (running.get()) {
            long start = System.nanoTime();


            try {
                //DEBUG
                //System.out.println("[PHYSICS DEBUG] About to call engine.tick()");
                engine.tick();
                //DEBUG
                //System.out.println("[PHYSICS DEBUG] engine.tick() completed");
            } catch (Exception e) {
                LOGGER.error("[VoxelPhysics] Tick error: ", e);
                e.printStackTrace(); // Print to console too
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

        System.out.println("[PHYSICS DEBUG] Thread loop ending!");
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** Seed a single-value type (e.g. PRESSURE). */
    public void seed(BlockPos pos, PhysicsType type, int... values) {
        engine.setSource(pos.getX(), pos.getY(), pos.getZ(), type, values);
    }

    /** Convenience for raw coordinates. */
    public void seed(int x, int y, int z, PhysicsType type, int... values) {
        engine.setSource(x, y, z, type, values);
    }

    public void clear() { engine.clear(); }
}
