package net.Stryker.VoxelPhysicsAPI.debug;

import net.Stryker.VoxelPhysicsAPI.PhysicsThreadManager;
import net.Stryker.VoxelPhysicsAPI.PhysicsType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;

/**
 * DEBUG ONLY — spawns particles for every block with non-zero pressure.
 *
 * Reads from PhysicsThreadManager's render snapshot — a periodic copy of the
 * live data published by the physics thread. The renderer never touches live
 * hashmaps, so there's no thread contention here.
 *
 * Particle type scales with pressure:
 *   Low  (1–10)  → single smoke wisp
 *   Mid  (11–30) → small poof cloud
 *   High (31+)   → explosion particle
 *
 * Toggle: /vpdebug toggle
 */
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class PhysicsDebugRenderer {

    public static boolean DEBUG_ENABLED = false;

    // Render every N client ticks — the snapshot itself updates at SNAPSHOT_INTERVAL
    // physics ticks, so rendering faster than that is pointless
    private static final int RENDER_INTERVAL = 4;
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

        // Read from the snapshot — safe to access from any thread
        PhysicsThreadManager.RenderSnapshot snapshot = PhysicsThreadManager.get().getRenderSnapshot();
        Map<BlockPos, int[]> dimData = snapshot.data().get(level.dimension());
        if (dimData == null) return;

        dimData.forEach((pos, values) -> {
            int pressure = values[PhysicsType.PRESSURE.index];
            if (pressure > 0) spawnPressureParticle(level, pos, pressure);
        });
    }

    private static void spawnPressureParticle(ClientLevel level, BlockPos pos, int pressure) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.5;
        double z = pos.getZ() + 0.5;

        if (pressure >= 31) {
            level.addParticle(ParticleTypes.EXPLOSION, x, y, z, 0, 0, 0);
        } else if (pressure >= 11) {
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
            level.addParticle(ParticleTypes.SMOKE, x, y + 0.3, z, 0, 0.02, 0);
        }
    }
}
