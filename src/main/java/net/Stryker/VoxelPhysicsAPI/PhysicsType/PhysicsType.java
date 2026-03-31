package net.Stryker.VoxelPhysicsAPI.PhysicsType;

import net.Stryker.VoxelPhysicsAPI.IRuleset;
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
    private final MergeBehavior[] behaviors; // NEW: How each value merges
    private int ordinal = -1;

    /**
     * Full constructor for multi-value types with custom merge behaviors.
     *
     * @param id            Unique identifier (e.g., "modid:pressure")
     * @param ruleset       The simulation rules
     * @param tickInterval  Run every N ticks (1 = every tick)
     * @param valueNames    Display names for each value ("pressure", "flux", etc.)
     * @param behaviors     How to merge values (PUT, PUT_MAX, or ADD)
     */
    public PhysicsType(ResourceLocation id, IRuleset ruleset, int tickInterval,
                       String[] valueNames, MergeBehavior[] behaviors) {
        if (valueNames.length != behaviors.length) {
            throw new IllegalArgumentException(id + ": valueNames and behaviors must have same length");
        }
        this.id = id;
        this.ruleset = ruleset;
        this.tickInterval = tickInterval;
        this.valuesPerCell = valueNames.length;
        this.valueNames = valueNames;
        this.behaviors = behaviors;
    }

    /**
     * Convenience constructor for single-value types.
     *
     * @param id           Unique identifier
     * @param ruleset      The simulation rules
     * @param tickInterval Run every N ticks
     * @param valueName    Display name (e.g., "pressure")
     * @param behavior     Merge behavior (PUT, PUT_MAX, or ADD)
     */
    public PhysicsType(ResourceLocation id, IRuleset ruleset, int tickInterval,
                       String valueName, MergeBehavior behavior) {
        this(id, ruleset, tickInterval,
                new String[]{valueName},
                new MergeBehavior[]{behavior});
    }

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
    public MergeBehavior[] getBehaviors() { return behaviors; } // NEW

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