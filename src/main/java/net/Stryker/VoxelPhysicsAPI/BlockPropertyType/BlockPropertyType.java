package net.Stryker.VoxelPhysicsAPI.BlockPropertyType;

import net.Stryker.VoxelPhysicsAPI.PhysicsType.MergeBehavior;
import net.minecraft.resources.ResourceLocation;

/**
 * A property type for blocks (e.g., flammability, thermal conductivity).
 * Similar to PhysicsType but for static block properties.
 */
public final class BlockPropertyType {

    private final ResourceLocation id;
    private final int defaultValue;
    private final MergeBehavior behavior;
    private int ordinal = -1; // Assigned by registry

    public BlockPropertyType(ResourceLocation id, int defaultValue, MergeBehavior behavior) {
        this.id = id;
        this.defaultValue = defaultValue;
        this.behavior = behavior;
    }

    void setOrdinal(int ordinal) { this.ordinal = ordinal; }

    public int ordinal() {
        if (ordinal == -1) throw new IllegalStateException("Type not registered: " + id);
        return ordinal;
    }

    public ResourceLocation getId() { return id; }
    public int getDefaultValue() { return defaultValue; }
    public MergeBehavior getBehavior() { return behavior; }

    @Override
    public String toString() { return id.toString(); }
}