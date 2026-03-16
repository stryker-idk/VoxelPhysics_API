package net.Stryker.VoxelPhysicsAPI.debug;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.Stryker.VoxelPhysicsAPI.PhysicsType;
import net.Stryker.VoxelPhysicsAPI.WorldPhysicsManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Registers the /vpdebug command for testing the physics simulation.
 *
 * Usage:
 *   /vpdebug pressure <value>   — seeds pressure at your current block position
 *   /vpdebug toggle             — toggles the particle debug visualizer on/off
 *   /vpdebug clear              — clears all physics data in loaded chunks
 *
 * Example workflow:
 *   /vpdebug toggle             — turn on particles first
 *   /vpdebug pressure 32        — seed a shockwave, watch it expand
 */
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PhysicsDebugCommand {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
            Commands.literal("vpdebug")
                .requires(source -> source.hasPermission(2)) // requires op level 2

                // /vpdebug pressure <0-255>
                .then(Commands.literal("pressure")
                    .then(Commands.argument("value", IntegerArgumentType.integer(0, 255))
                        .executes(ctx -> setPressure(
                            ctx.getSource(),
                            IntegerArgumentType.getInteger(ctx, "value")
                        ))
                    )
                )

                // /vpdebug toggle
                .then(Commands.literal("toggle")
                    .executes(ctx -> toggleVisualizer(ctx.getSource()))
                )

                // /vpdebug clear
                .then(Commands.literal("clear")
                    .executes(ctx -> clearAll(ctx.getSource()))
                )
        );
    }

    // -------------------------------------------------------------------------

    private static int setPressure(CommandSourceStack source, int value) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            ServerLevel level = player.serverLevel();
            BlockPos pos = player.blockPosition();

            WorldPhysicsManager.get(level.dimension())
                    .setBlockValue(pos, PhysicsType.PRESSURE, value);

            source.sendSuccess(
                () -> Component.literal(
                    "[VoxelPhysics] pressure=" + value + " set at " + formatPos(pos)
                ),
                false
            );
            return 1;

        } catch (Exception e) {
            source.sendFailure(Component.literal("[VoxelPhysics] Must be a player to use this command."));
            return 0;
        }
    }

    private static int toggleVisualizer(CommandSourceStack source) {
        PhysicsDebugRenderer.DEBUG_ENABLED = !PhysicsDebugRenderer.DEBUG_ENABLED;
        String state = PhysicsDebugRenderer.DEBUG_ENABLED ? "ON" : "OFF";
        source.sendSuccess(
            () -> Component.literal("[VoxelPhysics] Particle visualizer: " + state),
            false
        );
        return 1;
    }

    private static int clearAll(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            ServerLevel level = player.serverLevel();
            WorldPhysicsManager wpm = WorldPhysicsManager.get(level.dimension());

            // Re-register each chunk — replaces its hashmap with a fresh empty one
            wpm.getChunkManagers().forEach((chunkPos, manager) -> {
                wpm.unregisterChunk(chunkPos.x, chunkPos.z);
                wpm.registerChunk(chunkPos.x, chunkPos.z);
            });

            source.sendSuccess(
                () -> Component.literal("[VoxelPhysics] Cleared all physics data."),
                false
            );
            return 1;

        } catch (Exception e) {
            source.sendFailure(Component.literal("[VoxelPhysics] Must be a player to use this command."));
            return 0;
        }
    }

    private static String formatPos(BlockPos pos) {
        return "(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
    }
}
