package net.Stryker.VoxelPhysicsAPI.item;

import net.Stryker.VoxelPhysicsAPI.VoxelPhysicsAPI;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

public class ModItems
{
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, VoxelPhysicsAPI.MOD_ID);



    //mod items here ig






    public static void register(IEventBus eventBus)
    {
        ITEMS.register(eventBus);
    }
}
