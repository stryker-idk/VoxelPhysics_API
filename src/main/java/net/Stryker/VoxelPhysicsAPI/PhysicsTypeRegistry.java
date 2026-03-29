package net.Stryker.VoxelPhysicsAPI;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegistryBuilder;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

public class PhysicsTypeRegistry {

    public static final ResourceLocation REGISTRY_KEY =
            ResourceLocation.fromNamespaceAndPath(VoxelPhysicsAPI.MOD_ID, "physics_types");

    public static final DeferredRegister<PhysicsType> DEFERRED_REGISTER =
            DeferredRegister.create(REGISTRY_KEY, VoxelPhysicsAPI.MOD_ID);

    private static List<PhysicsType> values = new ArrayList<>();
    private static int count = 0;
    private static boolean frozen = false;

    public static void register(IEventBus modEventBus) {
        DEFERRED_REGISTER.register(modEventBus);
        // This creates the registry - no event listener needed
        DEFERRED_REGISTER.makeRegistry(RegistryBuilder::new);
    }

    /** Call this in FMLCommonSetupEvent */
    public static void freeze() {
        if (frozen) return;

        // Build from DeferredRegister's entries
        values = new ArrayList<>();
        for (RegistryObject<PhysicsType> ro : DEFERRED_REGISTER.getEntries()) {
            ro.ifPresent(values::add);
        }

        for (int i = 0; i < values.size(); i++) {
            values.get(i).setOrdinal(i);
        }
        count = values.size();
        frozen = true;

        VoxelPhysicsAPI.LOGGER.info("VoxelPhysics API: Registered " + count + " physics types");
    }

    public static RegistryObject<PhysicsType> register(String name, Supplier<PhysicsType> type) {
        return DEFERRED_REGISTER.register(name, type);
    }

    public static PhysicsType byId(ResourceLocation id) {
        // Try to get from RegistryObject if registry isn't fully accessible
        for (RegistryObject<PhysicsType> ro : DEFERRED_REGISTER.getEntries()) {
            if (ro.isPresent() && ro.get().getId().equals(id)) {
                return ro.get();
            }
        }
        return null;
    }

    public static List<PhysicsType> values() {
        return Collections.unmodifiableList(values);
    }

    public static int count() {
        return frozen ? count : (int) DEFERRED_REGISTER.getEntries().size();
    }
}