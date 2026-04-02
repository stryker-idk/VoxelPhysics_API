package net.Stryker.VoxelPhysicsAPI.debug;

import net.Stryker.VoxelPhysicsAPI.BlockPropertyType.BlockPropertyRegistry;
import net.Stryker.VoxelPhysicsAPI.BlockPropertyType.BlockPropertyType;
import net.Stryker.VoxelPhysicsAPI.PhysicsType.MergeBehavior;
import net.Stryker.VoxelPhysicsAPI.VoxelPhysicsAPI;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;

public class DebugBlockPropertyTypes {

    public static final BlockPropertyType DEBUG_PROPERTY = BlockPropertyRegistry.registerType(
            ResourceLocation.fromNamespaceAndPath(VoxelPhysicsAPI.MOD_ID, "debug_property"),
            50, // default
            MergeBehavior.PUT_MAX
    );

    public static void register() {
        // Register some debug values
        BlockPropertyRegistry.register(Blocks.STONE, DEBUG_PROPERTY, 100);
        BlockPropertyRegistry.register(Blocks.DIRT, DEBUG_PROPERTY, 10);
        BlockPropertyRegistry.register(Blocks.OAK_LOG, DEBUG_PROPERTY, 30);
        BlockPropertyRegistry.register(BlockTags.LOGS, DEBUG_PROPERTY, 25); // Other logs get 25
    }
}