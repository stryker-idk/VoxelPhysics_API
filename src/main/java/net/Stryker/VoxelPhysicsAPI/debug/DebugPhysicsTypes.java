package net.Stryker.VoxelPhysicsAPI.debug;

import net.Stryker.VoxelPhysicsAPI.*;
import net.minecraft.resources.ResourceLocation;

import static net.Stryker.VoxelPhysicsAPI.VoxelPhysicsAPI.MOD_ID;

public class DebugPhysicsTypes {

    public static PhysicsType DEBUG_TYPE;

    public static void register() {
        PhysicsType debugValue = new PhysicsType(
                ResourceLocation.fromNamespaceAndPath(MOD_ID, "debug_type"),
                new IRuleset() {
                    @Override
                    public boolean tick(LongIntMap[] current, LongIntMap[] next) {
                        // Simple pressure diffusion, spreads and decays
                        current[0].forEach((key, value) -> {
                            if (value <= 1) return; // Die if too low

                            int nextValue = value - 1;
                            int x = PhysicsEngine.unpackX(key);
                            int y = PhysicsEngine.unpackY(key);
                            int z = PhysicsEngine.unpackZ(key);

                            next[0].putMax(PhysicsEngine.pack(x+1, y, z), nextValue);
                            next[0].putMax(PhysicsEngine.pack(x-1, y, z), nextValue);
                            next[0].putMax(PhysicsEngine.pack(x, y+1, z), nextValue);
                            next[0].putMax(PhysicsEngine.pack(x, y-1, z), nextValue);
                            next[0].putMax(PhysicsEngine.pack(x, y, z+1), nextValue);
                            next[0].putMax(PhysicsEngine.pack(x, y, z-1), nextValue);
                        });
                        return !next[0].isEmpty(); // Return true if we have active blocks
                    }
                },
                1, //tick interval (no shit sherlock)
                new String[]{"test1", "test2"}, //names of the test values, single value would just be String valueName
                new MergeBehavior[]{MergeBehavior.ADD, MergeBehavior.PUT_MAX} //behavior of the test values, single value would just be MergeBehavior.behavior
        );

        PhysicsTypeRegistry.addDebugType(DEBUG_TYPE);
    }
}