package net.Stryker.VoxelPhysicsAPI.debug;

import net.Stryker.VoxelPhysicsAPI.*;
import net.minecraft.resources.ResourceLocation;

import static net.Stryker.VoxelPhysicsAPI.VoxelPhysicsAPI.MOD_ID;

public class DebugPhysicsTypes {

    public static PhysicsType DEBUG_TYPE;

    public static void register() {
        DEBUG_TYPE = new PhysicsType(
                ResourceLocation.fromNamespaceAndPath(MOD_ID, "debug_type"),
                new IRuleset() {
                    @Override
                    public boolean tick(LongIntMap[] current, LongIntMap[] next) {
                        // Process value 0 (test1 with ADD behavior)
                        current[0].forEach((key, value) -> {
                            if (value <= 1) return;

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

                        // Process value 1 (test2 with PUT_MAX behavior)
                        current[1].forEach((key, value) -> {
                            if (value <= 1) return;

                            int nextValue = value - 1;
                            int x = PhysicsEngine.unpackX(key);
                            int y = PhysicsEngine.unpackY(key);
                            int z = PhysicsEngine.unpackZ(key);

                            next[1].putMax(PhysicsEngine.pack(x+1, y, z), nextValue);
                            next[1].putMax(PhysicsEngine.pack(x-1, y, z), nextValue);
                            next[1].putMax(PhysicsEngine.pack(x, y+1, z), nextValue);
                            next[1].putMax(PhysicsEngine.pack(x, y-1, z), nextValue);
                            next[1].putMax(PhysicsEngine.pack(x, y, z+1), nextValue);
                            next[1].putMax(PhysicsEngine.pack(x, y, z-1), nextValue);
                        });

                        // Return true if either value has active blocks
                        return !next[0].isEmpty() || !next[1].isEmpty();
                    }
                },
                1, // tick interval
                new String[]{"test1", "test2"},
                new MergeBehavior[]{MergeBehavior.ADD, MergeBehavior.PUT_MAX}
        );

        PhysicsTypeRegistry.addDebugType(DEBUG_TYPE);
    }
}