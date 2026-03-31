package net.Stryker.VoxelPhysicsAPI.BlockPropertyType;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.Stryker.VoxelPhysicsAPI.BlockPropertyType.BlockPropertyType;
import net.Stryker.VoxelPhysicsAPI.PhysicsType.MergeBehavior;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BlockPropertyRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger("BlockPropertyRegistry");

    // Block ID -> PropertyType Ordinal -> Value
    private static final Map<ResourceLocation, Object2IntOpenHashMap> BLOCK_PROPERTIES = new HashMap<>();

    // Tag-based registrations (resolved during freeze)
    private static final List<TagEntry> TAG_ENTRIES = new ArrayList<>();

    private record TagEntry(TagKey<Block> tag, BlockPropertyType type, int value, int priority) {}

    // Registered types (for ordinal assignment)
    private static final List<BlockPropertyType> TYPES = new ArrayList<>();
    private static boolean frozen = false;

    /**
     * Register a new property type (like FLAMMABILITY, THERMAL_CONDUCTIVITY).
     * Call this in your mod's constructor.
     */
    public static BlockPropertyType registerType(ResourceLocation id, int defaultValue, MergeBehavior behavior) {
        if (frozen) throw new IllegalStateException("Cannot register types after freeze!");

        BlockPropertyType type = new BlockPropertyType(id, defaultValue, behavior);
        type.setOrdinal(TYPES.size());
        TYPES.add(type);
        return type;
    }

    /**
     * Register a property value for a specific block.
     * Highest priority - overrides tag-based values.
     */
    public static void register(Block block, BlockPropertyType type, int value) {
        if (frozen) throw new IllegalStateException("Cannot register after freeze!");

        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(block);
        if (blockId == null) {
            LOGGER.warn("Cannot register property for unregistered block: {}", block);
            return;
        }

        BLOCK_PROPERTIES.computeIfAbsent(blockId, k -> new Object2IntOpenHashMap())
                .put((Integer) type.ordinal(), value);;

        LOGGER.debug("Registered {} = {} for block {}", type.getId(), value, blockId);
    }

    /**
     * Register by ResourceLocation for modded blocks.
     */
    public static void register(ResourceLocation blockId, BlockPropertyType type, int value) {
        if (frozen) throw new IllegalStateException("Cannot register after freeze!");

        BLOCK_PROPERTIES.computeIfAbsent(blockId, k -> new Object2IntOpenHashMap())
                .put((Integer) type.ordinal(), value);;
    }

    /**
     * Register by Forge tag.
     * Lower priority than specific block registration.
     * Higher priority number = wins over lower priority tags.
     */
    public static void register(TagKey<Block> tag, BlockPropertyType type, int value) {
        register(tag, type, value, 1);
    }

    /**
     * Register by tag with priority.
     * @param priority Higher number = higher priority (wins over conflicting tags)
     */
    public static void register(TagKey<Block> tag, BlockPropertyType type, int value, int priority) {
        if (frozen) throw new IllegalStateException("Cannot register after freeze!");

        TAG_ENTRIES.add(new TagEntry(tag, type, value, priority));
        LOGGER.debug("Registered tag {} -> {} = {} (priority {})", tag.location(), type.getId(), value, priority);
    }

    /**
     * Freeze the registry - resolves all tags to actual blocks.
     * Call this in FMLCommonSetupEvent.
     */
    public static void freeze() {
        if (frozen) return;
        frozen = true;

        LOGGER.info("Freezing BlockPropertyRegistry with {} types and {} tag entries...",
                TYPES.size(), TAG_ENTRIES.size());

        // Sort tag entries by priority (highest first)
        TAG_ENTRIES.sort((a, b) -> Integer.compare(b.priority(), a.priority()));

        // Resolve tags to blocks
        for (TagEntry entry : TAG_ENTRIES) {
            ForgeRegistries.BLOCKS.tags().getTag(entry.tag).forEach(block -> {
                ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(block);
                if (blockId == null) return;

                // Only apply if block doesn't have specific value already
                Object2IntOpenHashMap props = BLOCK_PROPERTIES.get(blockId);
                if (props == null || !props.containsKey(entry.type.ordinal())) {
                    BLOCK_PROPERTIES.computeIfAbsent(blockId, k -> new Object2IntOpenHashMap())
                            .put((Integer) entry.type.ordinal(), entry.value());;
                }
            });
        }

        LOGGER.info("BlockPropertyRegistry frozen. Properties registered for {} blocks.",
                BLOCK_PROPERTIES.size());
    }

    /**
     * Fast O(1) lookup for block properties.
     * Returns default value if property not set for this block.
     */
    public static int get(Block block, BlockPropertyType type) {
        if (!frozen) {
            LOGGER.warn("Getting property before freeze! Type: {}", type.getId());
        }

        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(block);
        if (blockId == null) return type.getDefaultValue();

        Object2IntOpenHashMap props = BLOCK_PROPERTIES.get(blockId);
        if (props == null) return type.getDefaultValue();

        int ordinal = type.ordinal();
        // Avoid ambiguous getOrDefault by checking containsKey first
        return props.containsKey(ordinal) ? props.get(ordinal) : type.getDefaultValue();
    }

    /**
     * Check if a specific property is defined for this block (not just default).
     */
    public static boolean has(Block block, BlockPropertyType type) {
        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(block);
        if (blockId == null) return false;

        Object2IntOpenHashMap props = BLOCK_PROPERTIES.get(blockId);
        if (props == null) return false;

        return props.containsKey(type.ordinal());
    }

    /**
     * Get all registered types.
     */
    public static List<BlockPropertyType> getTypes() {
        return List.copyOf(TYPES);
    }

    /**
     * Debug: Print all properties for a block.
     */
    public static void debugPrint(Block block) {
        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(block);
        LOGGER.info("Properties for {}:", blockId);

        for (BlockPropertyType type : TYPES) {
            int value = get(block, type);
            boolean isDefault = !has(block, type);
            LOGGER.info("  {} = {} {}", type.getId(), value, isDefault ? "(default)" : "");
        }
    }
}