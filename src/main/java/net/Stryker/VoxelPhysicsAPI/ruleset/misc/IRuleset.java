package net.Stryker.VoxelPhysicsAPI.ruleset.misc;

import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * A ruleset defines how one physics value propagates each tick.
 * Think of it like one step of a cellular automaton.
 *
 * Input:  the position of a block and its current value
 * Output: a list of (position, type, value) pairs for the NEXT generation
 *
 * Rules:
 *   - The source block is implicitly cleared (value → 0) unless you explicitly
 *     include it in the output list.
 *   - Outputs can be in ANY chunk — the ChunkPhysicsManager handles routing.
 *   - Return an empty list to let the value dissipate entirely.
 */
public interface IRuleset {
    List<RulesetOutput> compute(BlockPos pos, int currentValue);
}
