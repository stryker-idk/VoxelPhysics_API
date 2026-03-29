package net.Stryker.VoxelPhysicsAPI;

import net.minecraft.resources.ResourceLocation;

/**
 * A registered physics type. Use PhysicsTypeRegistry to create and register.
 */
public final class PhysicsType {

    private final ResourceLocation id;
    private final IRuleset ruleset;
    private final int tickInterval;
    private final int valuesPerCell;
    private final String[] valueNames;
    private int ordinal = -1; // Assigned by registry

    public PhysicsType(ResourceLocation id, IRuleset ruleset, int tickInterval,
                       int valuesPerCell, String... valueNames) {
        if (valueNames.length != valuesPerCell) {
            throw new IllegalArgumentException(id + ": valueNames length must match valuesPerCell");
        }
        this.id = id;
        this.ruleset = ruleset;
        this.tickInterval = tickInterval;
        this.valuesPerCell = valuesPerCell;
        this.valueNames = valueNames;
    }

    // Internal use only - called by registry
    void setOrdinal(int ordinal) { this.ordinal = ordinal; }

    public int ordinal() {
        if (ordinal == -1) throw new IllegalStateException("Type not registered: " + id);
        return ordinal;
    }

    public ResourceLocation getId() { return id; }
    public IRuleset getRuleset() { return ruleset; }
    public int getTickInterval() { return tickInterval; }
    public int getValuesPerCell() { return valuesPerCell; }
    public String[] getValueNames() { return valueNames; }

    @Override
    public String toString() { return id.toString(); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PhysicsType other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() { return id.hashCode(); }
}