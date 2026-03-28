package net.Stryker.VoxelPhysicsAPI;

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

    PRESSURE(PressureRuleset.INSTANCE, 1, 1),
    //NEUTRON_RADIATION(NeutronRadiationRuleset.INSTANCE, 1, 1);
    // Future examples:
    // TEMPERATURE(TemperatureRuleset.INSTANCE, 2, 1),
    // RADIATION(RadiationRuleset.INSTANCE, 3, 2);  // 2 values: density + MeV
    ;

    public final IRuleset ruleset;
    public final int      tickInterval;  // run every N ticks
    public final int      valuesPerCell; // how many ints per block

    PhysicsType(IRuleset ruleset, int tickInterval, int valuesPerCell) {
        this.ruleset       = ruleset;
        this.tickInterval  = tickInterval;
        this.valuesPerCell = valuesPerCell;
    }

    public static final int COUNT = values().length;
}
