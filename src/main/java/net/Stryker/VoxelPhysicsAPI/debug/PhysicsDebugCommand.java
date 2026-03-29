package net.Stryker.VoxelPhysicsAPI.debug;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.Stryker.VoxelPhysicsAPI.PhysicsEngine;
import net.Stryker.VoxelPhysicsAPI.PhysicsThread;
import net.Stryker.VoxelPhysicsAPI.PhysicsType;
import net.Stryker.VoxelPhysicsAPI.PhysicsTypeRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PhysicsDebugCommand {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        // Build seed node with dynamic branches per type
        var seedNode = Commands.literal("seed");
        for (PhysicsType type : PhysicsTypeRegistry.values()) {
            seedNode.then(buildTypeBranch(type));
        }

        dispatcher.register(
                Commands.literal("vpdebug")
                        .requires(source -> source.hasPermission(2))
                        .then(seedNode)
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
     * type -> flux -> energy -> executes
     */
    private static ArgumentBuilder<CommandSourceStack, ?> buildTypeBranch(PhysicsType type) {
        // Build chain backwards: start with last value (which has the executor)
        String lastName = type.getValueNames()[type.getValuesPerCell() - 1];
        ArgumentBuilder<CommandSourceStack, ?> chain =
                Commands.argument(lastName, IntegerArgumentType.integer(0, 100000))
                        .executes(ctx -> executeSeed(ctx, type));

        // Chain backwards to first value
        for (int i = type.getValuesPerCell() - 2; i >= 0; i--) {
            chain = Commands.argument(type.getValueNames()[i], IntegerArgumentType.integer(0, 100000))
                    .then(chain);
        }

        return Commands.literal(type.getId().getPath()).then(chain);
    }

    private static int executeSeed(CommandContext<CommandSourceStack> ctx, PhysicsType type) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            BlockPos pos = player.blockPosition();

            // Extract values using the actual names
            int[] values = new int[type.getValuesPerCell()];
            for (int i = 0; i < type.getValuesPerCell(); i++) {
                values[i] = IntegerArgumentType.getInteger(ctx, type.getValueNames()[i]);
            }

            PhysicsThread.get().engine.seed(pos.getX(), pos.getY(), pos.getZ(), type, values);

            // Build message showing names
            StringBuilder valueStr = new StringBuilder();
            for (int i = 0; i < values.length; i++) {
                valueStr.append(type.getValueNames()[i]).append("=").append(values[i]);
                if (i < values.length - 1) valueStr.append(", ");
            }

            final String msg = "[VoxelPhysics] " + type.getId().getPath() + " (" + valueStr +
                    ") seeded at (" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";

            ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
            return 1;

        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("[VoxelPhysics] Must be a player."));
            return 0;
        }
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
        for (PhysicsType type : PhysicsTypeRegistry.values()) {
            for (int v = 0; v < type.getValuesPerCell(); v++) {
                int count = PhysicsThread.get().engine.getActiveBlockCount(type, v);
                String label = type.getValuesPerCell() == 1 ?
                        type.getId().getPath() :
                        type.getId().getPath() + "[" + v + "]";
                sb.append(" ").append(label).append(": ").append(count).append("\n");
            }
        }
        source.sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }
}