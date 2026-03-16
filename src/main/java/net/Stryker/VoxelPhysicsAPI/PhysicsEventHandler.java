package net.Stryker.VoxelPhysicsAPI;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.TickEvent;

/**
 * Hooks into Forge's event system to:
 *   - Tick the physics simulation every server tick
 *   - Register/unregister ChunkPhysicsManagers as chunks load/unload
 *   - Clean up WorldPhysicsManagers when a dimension unloads
 *
 * We only run on the SERVER side (isClientSide() == false).
 * Physics is purely server-side data — rendering effects are separate.
 */
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PhysicsEventHandler {

    /**
     * Tick the physics simulation.
     * We use Phase.END so that all block updates from this tick have settled first.
     */
    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.level.isClientSide()) return;

        WorldPhysicsManager.get(event.level.dimension()).tick();
    }

    /**
     * Register a new ChunkPhysicsManager when a chunk loads.
     */
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof Level level)) return;
        if (level.isClientSide()) return;

        ChunkAccess chunk = event.getChunk();
        WorldPhysicsManager.get(level.dimension())
                .registerChunk(chunk.getPos().x, chunk.getPos().z);
    }

    /**
     * Unregister the ChunkPhysicsManager when a chunk unloads.
     * Any pending overflow targeting this chunk will be discarded until it reloads.
     */
    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof Level level)) return;
        if (level.isClientSide()) return;

        ChunkAccess chunk = event.getChunk();
        WorldPhysicsManager.get(level.dimension())
                .unregisterChunk(chunk.getPos().x, chunk.getPos().z);
    }


// ... (existing imports unchanged)

    /**
     * DEBUG: When you break any block, seed pressure 32 at that position.
     * Remove this once you're done testing.
     */
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof Level level)) return;
        if (level.isClientSide()) return;

        WorldPhysicsManager.get(level.dimension())
                .setBlockValue(event.getPos(), PhysicsType.PRESSURE, 32);
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof Level level)) return;
        if (level.isClientSide()) return;

        WorldPhysicsManager.remove(level.dimension());
    }
}
