package net.Stryker.VoxelPhysicsAPI.ruleset.misc;

import net.Stryker.VoxelPhysicsAPI.PhysicsType;
import net.Stryker.VoxelPhysicsAPI.ruleset.PressureRuleset;

import java.util.EnumMap;
import java.util.Map;

/**
 * Central registry mapping each PhysicsType to its ruleset implementation.
 * When you add a new PhysicsType, register its ruleset here.
 */
public class RulesetRegistry {

    private static final Map<PhysicsType, IRuleset> REGISTRY = new EnumMap<>(PhysicsType.class);

    static {
        REGISTRY.put(PhysicsType.PRESSURE, PressureRuleset.INSTANCE);
        // Future: REGISTRY.put(PhysicsType.TEMPERATURE, TemperatureRuleset.INSTANCE);
    }

    /**
     * Returns the ruleset for a given physics type.
     * Throws if no ruleset is registered (shouldn't happen if you keep this in sync with the enum).
     */
    public static IRuleset get(PhysicsType type) {
        IRuleset ruleset = REGISTRY.get(type);
        if (ruleset == null) {
            throw new IllegalStateException("No ruleset registered for PhysicsType: " + type);
        }
        return ruleset;
    }
}
