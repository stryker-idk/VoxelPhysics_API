package net.Stryker.VoxelPhysicsAPI.debug;

import net.Stryker.VoxelPhysicsAPI.LongIntMap;
import net.Stryker.VoxelPhysicsAPI.PhysicsEngine;
import net.Stryker.VoxelPhysicsAPI.PhysicsThread;
import net.Stryker.VoxelPhysicsAPI.PhysicsType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class PhysicsDebugRenderer {

    public static boolean DEBUG_ENABLED = false;

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

        for (PhysicsType type : PhysicsType.values()) {
            LongIntMap snap = PhysicsThread.get().engine.getSnapshot(type);
            if (snap == null || snap.isEmpty()) continue;

            snap.forEach((key, value) -> {
                if (value <= 0) return;
                int x = PhysicsEngine.unpackX(key);
                int y = PhysicsEngine.unpackY(key);
                int z = PhysicsEngine.unpackZ(key);
                spawnParticle(level, type, x, y, z, value);
            });
        }
    }

    private static void spawnParticle(ClientLevel level, PhysicsType type,
                                       int x, int y, int z, int value) {
        double px = x + 0.5, py = y + 0.5, pz = z + 0.5;

        switch (type) {
            case PRESSURE -> {
                if (value >= 31) {
                    level.addParticle(ParticleTypes.EXPLOSION, px, py, pz, 0, 0, 0);
                } else if (value >= 11) {
                    int count = Math.max(1, value / 5);
                    for (int i = 0; i < count; i++) {
                        double j = 0.2;
                        level.addParticle(ParticleTypes.POOF,
                            px + (Math.random() - 0.5) * j,
                            py + (Math.random() - 0.5) * j,
                            pz + (Math.random() - 0.5) * j,
                            0, 0.05, 0);
                    }
                } else {
                    level.addParticle(ParticleTypes.SMOKE, px, py + 0.3, pz, 0, 0.02, 0);
                }
            }
            // Future: case TEMPERATURE -> { ... }
        }
    }
}
