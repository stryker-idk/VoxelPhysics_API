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
                        .then(Commands.argument("value", IntegerArgumentType.integer(0, 100000))
                            .executes(ctx -> seed(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "type"),
                                IntegerArgumentType.getInteger(ctx, "value")
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

    private static int seed(CommandSourceStack source, String typeName, int value) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            BlockPos pos = player.blockPosition();

            // Find the matching PhysicsType by name (case-insensitive)
            PhysicsType type = null;
            for (PhysicsType t : PhysicsType.values()) {
                if (t.name().equalsIgnoreCase(typeName)) {
                    type = t;
                    break;
                }
            }

            if (type == null) {
                StringBuilder names = new StringBuilder();
                for (PhysicsType t : PhysicsType.values()) names.append(t.name().toLowerCase()).append(" ");
                source.sendFailure(Component.literal(
                    "[VoxelPhysics] Unknown type '" + typeName + "'. Available: " + names));
                return 0;
            }

            PhysicsThread.get().engine.seed(pos.getX(), pos.getY(), pos.getZ(), type, value);

            final PhysicsType finalType = type;
            source.sendSuccess(
                () -> Component.literal("[VoxelPhysics] " + finalType.name().toLowerCase() +
                    "=" + value + " seeded at (" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")"),
                false
            );
            return 1;

        } catch (Exception e) {
            source.sendFailure(Component.literal("[VoxelPhysics] Must be a player."));
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
            int count = PhysicsThread.get().engine.getActiveBlockCount(type);
            sb.append("  ").append(type.name().toLowerCase())
              .append(": ").append(count).append("\n");
        }
        String msg = sb.toString();
        source.sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }
}
