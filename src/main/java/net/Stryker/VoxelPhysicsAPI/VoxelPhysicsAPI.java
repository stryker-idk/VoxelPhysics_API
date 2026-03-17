package net.Stryker.VoxelPhysicsAPI;

import com.mojang.logging.LogUtils;
import net.Stryker.VoxelPhysicsAPI.block.ModBlocks;
import net.Stryker.VoxelPhysicsAPI.item.ModItems;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;



// The value here should match an entry in the META-INF/mods.toml file
@Mod(VoxelPhysicsAPI.MOD_ID)
public class VoxelPhysicsAPI
{
    public static final String MOD_ID = "voxelphysics_api";
    public static final Logger LOGGER = LogUtils.getLogger();





    public VoxelPhysicsAPI(FMLJavaModLoadingContext context)
    {
        LOGGER.info("VoxelPhysics initializing...");

        IEventBus modEventBus = context.getModEventBus();

        //ModCreativeModTabs.register(modEventBus);

        ModItems.register(modEventBus);
        ModBlocks.register(modEventBus);

        modEventBus.addListener(this::commonSetup);

        MinecraftForge.EVENT_BUS.register(this);

        MinecraftForge.EVENT_BUS.addListener(this::onServerTick);



        //modEventBus.addListener(this::addCreative);

    }

    @SubscribeEvent
    public void onServerTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.side != LogicalSide.SERVER) return;

    }

    private void commonSetup(final FMLCommonSetupEvent event)
    {

    }



    //private void addCreative(BuildCreativeModeTabContentsEvent event)
    //{
    //    if(event.getTabKey() == CreativeModeTabs.INGREDIENTS)
    //    {
    //       //creative tab shenanigans
    //    }
    //}



    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents
    {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event)
        {

        }
    }
}
