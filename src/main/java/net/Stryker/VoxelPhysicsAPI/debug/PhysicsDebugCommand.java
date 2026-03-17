package net.Stryker.VoxelPhysicsAPI.debug;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.Stryker.VoxelPhysicsAPI.PhysicsThreadManager;
import net.Stryker.VoxelPhysicsAPI.PhysicsType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * /vpdebug command — seeds and inspects the physics simulation.
 *
 * Usage:
 *   /vpdebug pressure <0-255>   — seed pressure at your feet
 *   /vpdebug toggle             — toggle particle visualizer
 *   /vpdebug clear              — wipe all physics data in loaded chunks
 */
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PhysicsDebugCommand {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
            Commands.literal("vpdebug")
                .requires(source -> source.hasPermission(2))

                .then(Commands.literal("pressure")
                    .then(Commands.argument("value", IntegerArgumentType.integer(0, 255))
                        .executes(ctx -> setPressure(
                            ctx.getSource(),
                            IntegerArgumentType.getInteger(ctx, "value")
                        ))
                    )
                )

                .then(Commands.literal("toggle")
                    .executes(ctx -> toggleVisualizer(ctx.getSource()))
                )

                .then(Commands.literal("clear")
                    .executes(ctx -> clearAll(ctx.getSource()))
                )
        );
    }

    // -------------------------------------------------------------------------

    private static int setPressure(CommandSourceStack source, int value) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            BlockPos pos = player.blockPosition();

            // Feed into the queue — physics thread picks it up next tick
            PhysicsThreadManager.get().seed(
                player.serverLevel().dimension(),
                pos,
                PhysicsType.PRESSURE,
                value
            );

            source.sendSuccess(
                () -> Component.literal("[VoxelPhysics] pressure=" + value + " queued at " + formatPos(pos)),
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

            // Feeds the clearQueue — physics thread zeroes all hashmaps next tick.
            // Chunk registrations stay intact, so simulation resumes immediately.
            PhysicsThreadManager.get().clearDimension(player.serverLevel().dimension());

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
