package net.Stryker.VoxelPhysicsAPI.ruleset.misc;

import net.Stryker.VoxelPhysicsAPI.PhysicsType;
import net.minecraft.core.BlockPos;

/**
 * A single output from a ruleset computation.
 * Represents: "block at [pos] should have [type] = [value] next generation."
 *
 * The ChunkPhysicsManager inspects [pos] to decide whether this
 * stays local or goes to the WorldPhysicsManager as overflow.
 */
public record RulesetOutput(BlockPos pos, PhysicsType type, int value) {}
