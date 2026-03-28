package net.Stryker.VoxelPhysicsAPI;

import net.minecraft.core.BlockPos;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.concurrent.atomic.AtomicBoolean;

@Mod.EventBusSubscriber(modid = VoxelPhysicsAPI.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PhysicsThread {

    private static final PhysicsThread INSTANCE = new PhysicsThread();
    public static PhysicsThread get() { return INSTANCE; }

    public final PhysicsEngine engine = new PhysicsEngine();

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
        thread = new Thread(this::loop, "VoxelPhysics-Thread");
        thread.setDaemon(true);
        thread.start();
        VoxelPhysicsAPI.LOGGER.info("[VoxelPhysics] Physics thread started at 100 TPS.");
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
    public void seed(BlockPos pos, PhysicsType type, int value) {
        engine.seed(pos.getX(), pos.getY(), pos.getZ(), type, value);
    }

    /** Seed a multi-value type (e.g. RADIATION with density + MeV). */
    //public void seedMulti(BlockPos pos, PhysicsType type, int value1, int value2) {
    //    engine.seedMulti(pos.getX(), pos.getY(), pos.getZ(), type, value1, value2);
    //}

    public void clear() { engine.clear(); }
}
