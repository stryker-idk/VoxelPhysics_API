package net.Stryker.VoxelPhysicsAPI;

import net.Stryker.VoxelPhysicsAPI.ruleset.NeutronRadiationRuleset;
import net.Stryker.VoxelPhysicsAPI.ruleset.PressureRuleset;

/**
 * Every simulated physics value is a PhysicsType.
 *
 * HOW TO ADD A NEW TYPE:
 *   1. Add an enum entry here with its config
 *   2. Create a class implementing IRuleset
 *   3. That's it — the engine auto-registers everything
 *
 * Config per type:
 *   ruleset       — the IRuleset implementation
 *   tickInterval  — run every N physics ticks (1 = every tick, 3 = every 3rd tick)
 *   valuesPerCell — how many ints are stored per block (1 for simple, 2+ for complex types)
 *
 * Examples:
 *   PRESSURE      — 1 value (magnitude), runs every tick
 *   RADIATION     — 2 values (density, MeV), runs every 3 ticks (slower, cheaper)
 *   TEMPERATURE   — 1 value, runs every 2 ticks
 */
public enum PhysicsType {

    PRESSURE(PressureRuleset.INSTANCE, 1, 1, "pressure"),
    NEUTRON_RADIATION(NeutronRadiationRuleset.INSTANCE, 1, 2, "flux", "energy"),
    // Future: TEMPERATURE(TemperatureRuleset.INSTANCE, 2, 1, "temperature"),
    // Future: RADIATION(RadiationRuleset.INSTANCE, 3, 2, "density", "mev")
    ;

    public final IRuleset ruleset;
    public final int tickInterval;
    public final int valuesPerCell;
    public final String[] valueNames; // NEW: names for each value index

    // Constructor for single value (backward compatible)
    PhysicsType(IRuleset ruleset, int tickInterval, int valuesPerCell, String valueName) {
        this(ruleset, tickInterval, valuesPerCell, new String[]{valueName});
    }

    // Constructor for multiple values
    PhysicsType(IRuleset ruleset, int tickInterval, int valuesPerCell, String... valueNames) {
        if (valueNames.length != valuesPerCell) {
            throw new IllegalArgumentException(name() + ": valueNames count (" + valueNames.length +
                    ") doesn't match valuesPerCell (" + valuesPerCell + ")");
        }
        this.ruleset = ruleset;
        this.tickInterval = tickInterval;
        this.valuesPerCell = valuesPerCell;
        this.valueNames = valueNames;
    }

    public static final int COUNT = values().length;
}
