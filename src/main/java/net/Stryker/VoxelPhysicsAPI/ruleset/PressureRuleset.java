package net.Stryker.VoxelPhysicsAPI.ruleset;

import net.Stryker.VoxelPhysicsAPI.PhysicsType;
import net.Stryker.VoxelPhysicsAPI.ruleset.misc.IRuleset;
import net.Stryker.VoxelPhysicsAPI.ruleset.misc.RulesetOutput;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;

/**
 * Pressure spreads outward in all 6 directions (N/S/E/W/Up/Down),
 * each neighbor receiving (currentValue - 1).
 *
 * The source block is NOT included in the output, so it gets cleared.
 * This models an expanding shockwave — the wavefront moves outward
 * and doesn't linger at the origin.
 *
 * Example:
 *   Tick 0: one block at XYZ has pressure 4
 *   Tick 1: 6 neighbors have pressure 3, origin is 0
 *   Tick 2: their neighbors have pressure 2, etc.
 *   Tick 4: fully dissipated (value reaches 0, not stored)
 */
public class PressureRuleset implements IRuleset {

    public static final PressureRuleset INSTANCE = new PressureRuleset();

    @Override
    public List<RulesetOutput> compute(BlockPos pos, int currentValue) {
        List<RulesetOutput> outputs = new ArrayList<>(6);

        // Dissipate if value can't spread meaningfully
        if (currentValue <= 1) {
            return outputs; // empty = source gets cleared, nothing new
        }

        int nextValue = currentValue - 1;

        for (Direction dir : Direction.values()) {
            BlockPos neighbor = pos.relative(dir);
            outputs.add(new RulesetOutput(neighbor, PhysicsType.PRESSURE, nextValue));
        }

        // Source block is NOT added → it becomes 0 next generation
        return outputs;
    }
}
