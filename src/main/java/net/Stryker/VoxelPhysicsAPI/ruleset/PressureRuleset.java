package net.Stryker.VoxelPhysicsAPI.ruleset;

import net.Stryker.VoxelPhysicsAPI.PhysicsType;
import net.Stryker.VoxelPhysicsAPI.ruleset.misc.IRuleset;
import net.Stryker.VoxelPhysicsAPI.ruleset.misc.RulesetOutput;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;

/**
 * Pressure spreads outward in all 6 directions each tick.
 * Each neighbor receives (currentValue - 1). Source block is cleared.
 *
 * Optimization: uses a thread-local reused output list and immutable
 * BlockPos only when actually needed (when the output is kept).
 * This eliminates ~6 ArrayList + ~6 BlockPos allocations per block per tick.
 */
public class PressureRuleset implements IRuleset {

    public static final PressureRuleset INSTANCE = new PressureRuleset();

    // Reused output list per physics thread — never allocates during tick
    // ThreadLocal is safe here because only the physics thread calls compute()
    private static final ThreadLocal<List<RulesetOutput>> OUTPUT_BUFFER =
            ThreadLocal.withInitial(() -> new ArrayList<>(6));

    // Reused mutable pos for neighbor calculation — avoids 6 BlockPos allocations per block
    private static final ThreadLocal<BlockPos.MutableBlockPos> MUTABLE_POS =
            ThreadLocal.withInitial(BlockPos.MutableBlockPos::new);

    @Override
    public List<RulesetOutput> compute(BlockPos pos, int currentValue) {
        List<RulesetOutput> outputs = OUTPUT_BUFFER.get();
        outputs.clear();

        if (currentValue <= 1) {
            return outputs; // dissipate — empty list, source gets cleared
        }

        int nextValue = currentValue - 1;
        BlockPos.MutableBlockPos mutable = MUTABLE_POS.get();

        for (Direction dir : Direction.values()) {
            mutable.setWithOffset(pos, dir);

            // BlockPos.of(mutable) creates an immutable copy only when we actually
            // need to store it — avoids allocation for blocks that get filtered out
            outputs.add(new RulesetOutput(mutable.immutable(), PhysicsType.PRESSURE, nextValue));
        }

        return outputs;
    }
}
