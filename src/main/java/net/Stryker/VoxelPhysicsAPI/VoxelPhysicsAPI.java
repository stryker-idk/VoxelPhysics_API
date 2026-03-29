package net.Stryker.VoxelPhysicsAPI;

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
        LOGGER.info("VoxelPhysics API loaded. Registered types: " + PhysicsTypeRegistry.count());

        PhysicsTypeRegistry.freeze();
        VoxelPhysicsAPI.LOGGER.info("Registry frozen. Count: " + PhysicsTypeRegistry.count());

        // Don't start thread here - let it start naturally on server start
        // But we can verify the engine would work:
        PhysicsEngine testEngine = new PhysicsEngine();
        VoxelPhysicsAPI.LOGGER.info("Engine created with " + testEngine.getTypeCount() + " type slots");
        // Start physics thread (even if empty, addons will populate it)
        PhysicsThread.get().start();
    }
}