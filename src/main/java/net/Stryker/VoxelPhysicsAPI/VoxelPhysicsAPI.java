package net.Stryker.VoxelPhysicsAPI;

import net.Stryker.VoxelPhysicsAPI.BlockPropertyType.BlockPropertyRegistry;
import net.Stryker.VoxelPhysicsAPI.PhysicsType.PhysicsTypeRegistry;
import net.Stryker.VoxelPhysicsAPI.debug.DebugBlockPropertyTypes;
import net.Stryker.VoxelPhysicsAPI.debug.DebugPhysicsTypes;
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
        DebugPhysicsTypes.register();
        DebugBlockPropertyTypes.register();

        LOGGER.info("VoxelPhysics API loaded. Registered types: " + PhysicsTypeRegistry.count());

        PhysicsEngine testEngine = new PhysicsEngine();
        LOGGER.info("Engine created with " + testEngine.getTypeCount() + " type slots");

        // Create engine AFTER we have types
        PhysicsThread.get().start();
        BlockPropertyRegistry.freeze();
    }
}