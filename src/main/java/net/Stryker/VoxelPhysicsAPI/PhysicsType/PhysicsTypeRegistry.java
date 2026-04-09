package net.Stryker.VoxelPhysicsAPI.PhysicsType;

import net.Stryker.VoxelPhysicsAPI.VoxelPhysicsAPI;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryBuilder;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PhysicsTypeRegistry {

    public static final ResourceLocation REGISTRY_KEY =
            ResourceLocation.fromNamespaceAndPath(VoxelPhysicsAPI.MOD_ID, "physics_types");

    private static DeferredRegister<PhysicsType> deferredRegister;

    private static List<PhysicsType> values = new ArrayList<>();
    private static int count = 0;
    private static boolean frozen = false;

    public static void register(IEventBus modEventBus) {
        deferredRegister = DeferredRegister.create(REGISTRY_KEY, VoxelPhysicsAPI.MOD_ID);
        deferredRegister.register(modEventBus);
        deferredRegister.makeRegistry(RegistryBuilder::new);
    }

    /** Call this in FMLCommonSetupEvent */
    public static void freeze() {
        if (frozen) return;

        // Build from DeferredRegister's entries
        for (RegistryObject<PhysicsType> ro : deferredRegister.getEntries()) {
            ro.ifPresent(values::add);
        }

        for (int i = 0; i < values.size(); i++) {
            values.get(i).setOrdinal(i);
        }
        count = values.size();
        frozen = true;

        VoxelPhysicsAPI.LOGGER.info("VoxelPhysics API: Registered " + count + " physics types");
    }

    // DEBUG: Add types after freeze for testing
    public static void registerType(PhysicsType type) {
        if (frozen) {
            throw new IllegalStateException("Cannot register type after freeze: " + type.getId());
        }
        // Add directly to values (like addDebugType but proper naming)
        type.setOrdinal(values.size());
        values.add(type);
        count = values.size();
    }

    public static PhysicsType byId(ResourceLocation id) {
        for (PhysicsType type : values) {
            if (type.getId().equals(id)) return type;
        }
        return null;
    }

    public static List<PhysicsType> values() {
        return Collections.unmodifiableList(values);
    }

    public static int count() {
        return count;
    }
}