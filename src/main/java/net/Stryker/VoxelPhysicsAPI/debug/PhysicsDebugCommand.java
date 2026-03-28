package net.Stryker.VoxelPhysicsAPI.debug;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.Stryker.VoxelPhysicsAPI.PhysicsEngine;
import net.Stryker.VoxelPhysicsAPI.PhysicsThread;
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
 * /vpdebug seed <type> <value>  — seed a physics value at your feet
 * /vpdebug toggle               — toggle particle visualizer
 * /vpdebug clear                — wipe all active physics data
 * /vpdebug status               — print active block counts per type
 *
 * <type> is the PhysicsType name, lowercase: pressure, temperature, radiation, etc.
 */
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PhysicsDebugCommand {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        // Build seed node with dynamic branches per type
        LiteralArgumentBuilder<CommandSourceStack> seedNode = Commands.literal("seed");
        for (PhysicsType type : PhysicsType.values()) {
            seedNode.then(buildTypeBranch(type));
        }

        dispatcher.register(
                Commands.literal("vpdebug")
                        .requires(source -> source.hasPermission(2))
                        .then(seedNode)
                        .then(Commands.literal("toggle")
                                .executes(ctx -> toggleVisualizer(ctx.getSource()))
                        )
                        .then(Commands.literal("clear")
                                .executes(ctx -> clearAll(ctx.getSource()))
                        )
                        .then(Commands.literal("status")
                                .executes(ctx -> status(ctx.getSource()))
                        )
        );
    }

    /**
     * Builds the command branch for a specific PhysicsType:
     * type -> value0 -> value1 -> ... -> executes
     */
    private static ArgumentBuilder<CommandSourceStack, ?> buildTypeBranch(PhysicsType type) {
        // Build chain backwards: start with last value (which has the executor)
        ArgumentBuilder<CommandSourceStack, ?> chain =
                Commands.argument("value" + (type.valuesPerCell - 1), IntegerArgumentType.integer(0, 100000))
                        .executes(ctx -> executeSeed(ctx, type));

        // Chain backwards to first value
        for (int i = type.valuesPerCell - 2; i >= 0; i--) {
            final int index = i;
            chain = Commands.argument("value" + i, IntegerArgumentType.integer(0, 100000))
                    .then(chain);
        }

        return Commands.literal(type.name().toLowerCase()).then(chain);
    }

    private static int executeSeed(CommandContext<CommandSourceStack> ctx, PhysicsType type) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            BlockPos pos = player.blockPosition();

            // Extract values from context
            int[] values = new int[type.valuesPerCell];
            for (int i = 0; i < type.valuesPerCell; i++) {
                values[i] = IntegerArgumentType.getInteger(ctx, "value" + i);
            }

            PhysicsThread.get().engine.seed(pos.getX(), pos.getY(), pos.getZ(), type, values);

            // Build message
            StringBuilder valueStr = new StringBuilder();
            for (int i = 0; i < values.length; i++) {
                valueStr.append(values[i]);
                if (i < values.length - 1) valueStr.append(" ");
            }

            ctx.getSource().sendSuccess(
                    () -> Component.literal("[VoxelPhysics] " + type.name().toLowerCase() +
                            " " + valueStr + " seeded at (" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")"),
                    false
            );
            return 1;

        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("[VoxelPhysics] Must be a player."));
            return 0;
        }
    }

    private static int toggleVisualizer(CommandSourceStack source) {
        PhysicsDebugRenderer.DEBUG_ENABLED = !PhysicsDebugRenderer.DEBUG_ENABLED;
        PhysicsEngine.snapshotEnabled = PhysicsDebugRenderer.DEBUG_ENABLED;
        String state = PhysicsDebugRenderer.DEBUG_ENABLED ? "ON" : "OFF";
        source.sendSuccess(
                () -> Component.literal("[VoxelPhysics] Particle visualizer: " + state),
                false
        );
        return 1;
    }

    private static int clearAll(CommandSourceStack source) {
        PhysicsThread.get().clear();
        source.sendSuccess(
                () -> Component.literal("[VoxelPhysics] Cleared all physics data."),
                false
        );
        return 1;
    }

    private static int status(CommandSourceStack source) {
        StringBuilder sb = new StringBuilder("[VoxelPhysics] Active blocks:\n");
        for (PhysicsType type : PhysicsType.values()) {
            for (int v = 0; v < type.valuesPerCell; v++) {
                int count = PhysicsThread.get().engine.getActiveBlockCount(type, v);
                String label = type.valuesPerCell == 1 ?
                        type.name().toLowerCase() :
                        type.name().toLowerCase() + "[" + v + "]";
                sb.append(" ").append(label).append(": ").append(count).append("\n");
            }
        }
        source.sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }
}
