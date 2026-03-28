package net.Stryker.VoxelPhysicsAPI.debug;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
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

        dispatcher.register(
            Commands.literal("vpdebug")
                .requires(source -> source.hasPermission(2))

                // /vpdebug seed <type> <value>
                .then(Commands.literal("seed")
                    .then(Commands.argument("type", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            for (PhysicsType t : PhysicsType.values())
                                builder.suggest(t.name().toLowerCase());
                            return builder.buildFuture();
                        })
                            .then(Commands.argument("values", StringArgumentType.greedyString())
                                    .executes(ctx -> seed(
                                            ctx.getSource(),
                                            StringArgumentType.getString(ctx, "type"),
                                            StringArgumentType.getString(ctx, "values")
                                    ))
                            )
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

                // /vpdebug status
                .then(Commands.literal("status")
                    .executes(ctx -> status(ctx.getSource()))
                )
        );
    }

    private static int seed(CommandSourceStack source, String typeName, String valuesStr) {
        // Get player first (separate try-catch for clarity)
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("[VoxelPhysics] Must be a player."));
            return 0;
        }

        BlockPos pos = player.blockPosition();

        // Find the matching PhysicsType
        PhysicsType type = null;
        for (PhysicsType t : PhysicsType.values()) {
            if (t.name().equalsIgnoreCase(typeName)) {
                type = t;
                break;
            }
        }

        if (type == null) {
            StringBuilder names = new StringBuilder();
            for (PhysicsType t : PhysicsType.values()) {
                names.append(t.name().toLowerCase()).append(" ");
            }
            source.sendFailure(Component.literal(
                    "[VoxelPhysics] Unknown type '" + typeName + "'. Available: " + names));
            return 0;
        }

        // Parse values (comma-separated: "100" or "1000,50")
        String[] parts = valuesStr.split(",");
        if (parts.length != type.valuesPerCell) {
            source.sendFailure(Component.literal(
                    "[VoxelPhysics] Type '" + typeName + "' requires " + type.valuesPerCell +
                            " value(s) (e.g., " + (type.valuesPerCell == 1 ? "100" : "1000,50") + ")"));
            return 0;
        }

        int[] values = new int[parts.length];
        try {
            for (int i = 0; i < parts.length; i++) {
                values[i] = Integer.parseInt(parts[i].trim());
                if (values[i] < 0) {
                    source.sendFailure(Component.literal("[VoxelPhysics] Values must be positive."));
                    return 0;
                }
            }
        } catch (NumberFormatException e) {
            source.sendFailure(Component.literal("[VoxelPhysics] Invalid number format."));
            return 0;
        }

        // Seed it
        PhysicsThread.get().engine.seed(pos.getX(), pos.getY(), pos.getZ(), type, values);

        // Build success message
        StringBuilder valueStr = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            valueStr.append(values[i]);
            if (i < values.length - 1) valueStr.append(", ");
        }

        final PhysicsType finalType = type;
        source.sendSuccess(
                () -> Component.literal("[VoxelPhysics] " + finalType.name().toLowerCase() +
                        "=" + valueStr + " seeded at (" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")"),
                false
        );
        return 1;
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
            // For multi-value types, show count for each value index
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
