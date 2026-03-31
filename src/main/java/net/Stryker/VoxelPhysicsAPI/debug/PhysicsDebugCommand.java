package net.Stryker.VoxelPhysicsAPI.debug;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
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
        var setSourceNode = Commands.literal("setsource");
        var constantSourceNode = Commands.literal("constantsource");

        for (PhysicsType type : PhysicsTypeRegistry.values()) {
            setSourceNode.then(buildTypeBranch(type, "setsource"));
            constantSourceNode.then(buildTypeBranch(type, "constantsource"));
        }

        dispatcher.register(
                Commands.literal("vpdebug")
                        .requires(source -> source.hasPermission(2))
                        .then(setSourceNode)
                        .then(constantSourceNode)
                        .then(Commands.literal("clearconstants")
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            for (PhysicsType t : PhysicsTypeRegistry.values())
                                                builder.suggest(t.getId().getPath());
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> clearConstantSources(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "type")
                                        ))
                                )
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
     * type -> flux -> energy -> executes
     */
    private static ArgumentBuilder<CommandSourceStack, ?> buildTypeBranch(PhysicsType type, String commandType) {
        // Build chain backwards: start with last value (which has the executor)
        String lastName = type.getValueNames()[type.getValuesPerCell() - 1];
        ArgumentBuilder<CommandSourceStack, ?> chain =
                Commands.argument(lastName, IntegerArgumentType.integer(0, 100000))
                        .executes(ctx -> executeCommand(ctx, type, commandType));

        // Chain backwards to first value
        for (int i = type.getValuesPerCell() - 2; i >= 0; i--) {
            chain = Commands.argument(type.getValueNames()[i], IntegerArgumentType.integer(0, 100000))
                    .then(chain);
        }

        return Commands.literal(type.getId().getPath()).then(chain);
    }

    private static int executeCommand(CommandContext<CommandSourceStack> ctx, PhysicsType type, String commandType) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            BlockPos pos = player.blockPosition();

            // Extract values
            int[] values = new int[type.getValuesPerCell()];
            for (int i = 0; i < type.getValuesPerCell(); i++) {
                values[i] = IntegerArgumentType.getInteger(ctx, type.getValueNames()[i]);
            }

            // Build message
            StringBuilder valueStr = new StringBuilder();
            for (int i = 0; i < values.length; i++) {
                valueStr.append(type.getValueNames()[i]).append("=").append(values[i]);
                if (i < values.length - 1) valueStr.append(", ");
            }

            final String msg;
            if (commandType.equals("constantsource")) {
                PhysicsThread.get().engine.setConstantSource(pos.getX(), pos.getY(), pos.getZ(), type, values);
                msg = "[VoxelPhysics] Constant source " + type.getId().getPath() + " (" + valueStr +
                        ") set at (" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
            } else {
                PhysicsThread.get().engine.setSource(pos.getX(), pos.getY(), pos.getZ(), type, values);
                msg = "[VoxelPhysics] Source " + type.getId().getPath() + " (" + valueStr +
                        ") set at (" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
            }

            ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
            return 1;

        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("[VoxelPhysics] Must be a player."));
            return 0;
        }
    }


    /**
     *
     * Clears all active blocks, does not remove sources (atleast i think)
     *
     * @param source
     * @return
     */
    private static int clearAll(CommandSourceStack source) {
        PhysicsThread.get().engine.clear();
        source.sendSuccess(
                () -> Component.literal("[VoxelPhysics] Cleared all physics data."),
                false
        );
        return 1;
    }

    /**
     *
     * Clears all constant sources
     *
     * @param source
     * @param typeName
     * @return
     */
    private static int clearConstantSources(CommandSourceStack source, String typeName) {
        // Find the type
        PhysicsType type = null;
        for (PhysicsType t : PhysicsTypeRegistry.values()) {
            if (t.getId().getPath().equalsIgnoreCase(typeName)) {
                type = t;
                break;
            }
        }

        if (type == null) {
            source.sendFailure(Component.literal("[VoxelPhysics] Unknown type: " + typeName));
            return 0;
        }

        final PhysicsType finalType = type; // Add this line
        PhysicsThread.get().engine.clearConstantSources(type);
        source.sendSuccess(
                () -> Component.literal("[VoxelPhysics] Cleared all constant sources for " + finalType.getId().getPath()),
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