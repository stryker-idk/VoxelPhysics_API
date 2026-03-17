package net.Stryker.VoxelPhysicsAPI;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;

/**
 * Hooks into Forge events and feeds the PhysicsThreadManager's queues.
 *
 * The main thread NEVER ticks the simulation directly anymore.
 * It only posts events into lock-free queues that the physics thread drains.
 *
 * TickEvent is gone — the physics thread manages its own clock.
 */
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PhysicsEventHandler {

    /** Start the physics thread when the server starts. */
    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        PhysicsThreadManager.get().start();
    }

    /** Stop the physics thread cleanly when the server shuts down. */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        PhysicsThreadManager.get().stop();
    }

    /** Tell the physics thread a chunk is available for simulation. */
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof Level level)) return;
        if (level.isClientSide()) return;

        ChunkAccess chunk = event.getChunk();
        PhysicsThreadManager.get().onChunkLoad(
            level.dimension(),
            chunk.getPos().x,
            chunk.getPos().z
        );
    }

    /** Tell the physics thread to drop a chunk — its data is lost until it reloads. */
    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof Level level)) return;
        if (level.isClientSide()) return;

        ChunkAccess chunk = event.getChunk();
        PhysicsThreadManager.get().onChunkUnload(
            level.dimension(),
            chunk.getPos().x,
            chunk.getPos().z
        );
    }

    /** Tell the physics thread a whole dimension is gone. */
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof Level level)) return;
        if (level.isClientSide()) return;

        PhysicsThreadManager.get().onDimensionUnload(level.dimension());
    }
}
