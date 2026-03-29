package net.Stryker.VoxelPhysicsAPI;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(VoxelPhysicsAPI.MOD_ID)
public class VoxelPhysicsAPI {
    public static final String MOD_ID = "voxelphysics_api";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public VoxelPhysicsAPI(FMLJavaModLoadingContext context) {
        LOGGER.info("VoxelPhysics API initializing...");

        IEventBus modBus = context.getModEventBus();

        // Register the physics type registry (empty by default)
        PhysicsTypeRegistry.register(modBus);

        modBus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        PhysicsTypeRegistry.freeze();

        // DEBUG: Register a test type directly if registry is empty
        if (PhysicsTypeRegistry.count() == 0) {
            LOGGER.warn("No physics types registered! Adding debug value type.");

            PhysicsType debugValue = new PhysicsType(
                    ResourceLocation.fromNamespaceAndPath(MOD_ID, "debug_value"),
                    new IRuleset() {
                        @Override
                        public boolean tick(LongIntMap[] current, LongIntMap[] next) {
                            // Simple pressure diffusion - spreads and decays
                            current[0].forEach((key, value) -> {
                                if (value <= 1) return; // Die if too low

                                int nextValue = value - 1;
                                int x = PhysicsEngine.unpackX(key);
                                int y = PhysicsEngine.unpackY(key);
                                int z = PhysicsEngine.unpackZ(key);

                                // Spread to 6 neighbors
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
                    1, 1, "value"
            );

            PhysicsTypeRegistry.addDebugType(debugValue);
        }

        LOGGER.info("VoxelPhysics API loaded. Registered types: " + PhysicsTypeRegistry.count());

        // Create engine AFTER we have types
        PhysicsThread.get().start();
    }
}