package net.Stryker.VoxelPhysicsAPI.debug;

import net.Stryker.VoxelPhysicsAPI.PhysicsType;
import net.Stryker.VoxelPhysicsAPI.WorldPhysicsManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * DEBUG ONLY — spawns particles at every block with non-zero pressure.
 *
 * Particle type scales with pressure value:
 *   Low  (1–10)  → single wisp of smoke
 *   Mid  (11–30) → small poof cloud
 *   High (31+)   → explosion particle
 *
 * Toggle on/off at runtime with:
 *   PhysicsDebugRenderer.DEBUG_ENABLED = true/false;
 * Or hook it up to a keybind / command.
 *
 * CLIENT SIDE ONLY.
 */
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class PhysicsDebugRenderer {

    public static boolean DEBUG_ENABLED = false;

    // Spawn particles every N ticks, not every tick — avoids particle spam
    private static final int RENDER_INTERVAL = 5;
    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!DEBUG_ENABLED) return;

        tickCounter++;
        if (tickCounter % RENDER_INTERVAL != 0) return;

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;

        // NOTE: This works in singleplayer because client and server share the JVM.
        // For multiplayer you'd need to sync active block data via packets instead.
        WorldPhysicsManager wpm = WorldPhysicsManager.get(level.dimension());

        wpm.getChunkManagers().forEach((chunkPos, chunkManager) -> {
            chunkManager.forEachActiveBlock((pos, values) -> {
                int pressure = values[PhysicsType.PRESSURE.index];
                if (pressure <= 0) return;
                spawnPressureParticle(level, pos, pressure);
            });
        });
    }

    private static void spawnPressureParticle(ClientLevel level, BlockPos pos, int pressure) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.5;
        double z = pos.getZ() + 0.5;

        if (pressure >= 31) {
            // High pressure — one big explosion poof
            level.addParticle(ParticleTypes.EXPLOSION, x, y, z, 0, 0, 0);

        } else if (pressure >= 11) {
            // Mid pressure — small smoke cloud, density scales with value
            int count = Math.max(1, pressure / 5);
            for (int i = 0; i < count; i++) {
                double jitter = 0.2;
                level.addParticle(ParticleTypes.POOF,
                        x + (Math.random() - 0.5) * jitter,
                        y + (Math.random() - 0.5) * jitter,
                        z + (Math.random() - 0.5) * jitter,
                        0, 0.05, 0);
            }
        } else {
            // Low pressure — a single wisp of smoke drifting upward
            level.addParticle(ParticleTypes.SMOKE, x, y + 0.3, z, 0, 0.02, 0);
        }
    }
}
