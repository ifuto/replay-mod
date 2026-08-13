package dev.ifuto.fpsreplay.client;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.ifuto.fpsreplay.replay.ReplayFile;
import dev.ifuto.fpsreplay.replay.ReplayReader;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * Client-side commands:
 * <pre>
 *   /record start [name]        begin recording
 *   /record stop                stop and save
 *   /record status              show recording state
 *
 *   /replay render &lt;name&gt; [WxH] [fps]   render a replay to PNG frames
 *   /replay stop                abort rendering
 *   /replay list                list saved replays
 * </pre>
 */
public final class ReplayCommands {
    private ReplayCommands() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(literal("record")
                    .then(literal("start")
                            .then(argument("name", StringArgumentType.word())
                                    .executes(ctx -> start(ctx, StringArgumentType.getString(ctx, "name"))))
                            .executes(ctx -> start(ctx, null)))
                    .then(literal("stop").executes(ReplayCommands::stopRecording))
                    .then(literal("status").executes(ReplayCommands::status)));

            dispatcher.register(literal("replay")
                    .then(literal("render")
                            .then(argument("name", StringArgumentType.word())
                                    .executes(ctx -> render(ctx, null, null))
                                    .then(argument("res", StringArgumentType.word())
                                            .executes(ctx -> render(ctx, StringArgumentType.getString(ctx, "res"), null))
                                            .then(argument("fps", IntegerArgumentType.integer(1, 10000))
                                                    .executes(ctx -> render(ctx,
                                                            StringArgumentType.getString(ctx, "res"),
                                                            IntegerArgumentType.getInteger(ctx, "fps")))))))
                    .then(literal("stop").executes(ReplayCommands::stopRender))
                    .then(literal("list").executes(ReplayCommands::list)));
        });
    }

    private static int start(CommandContext<FabricClientCommandSource> ctx, String name) {
        FabricClientCommandSource src = ctx.getSource();
        String n = name == null || name.isBlank() ? defaultName() : name;
        File file = Recorder.start(src.getClient(), n);
        if (file == null) {
            src.sendError(Text.literal("Cannot record: not in a world."));
            return 0;
        }
        src.sendFeedback(Text.literal("Recording -> " + file.getName()));
        return Command.SINGLE_SUCCESS;
    }

    private static int stopRecording(CommandContext<FabricClientCommandSource> ctx) {
        if (!Recorder.isRecording()) {
            ctx.getSource().sendError(Text.literal("Not recording."));
            return 0;
        }
        Recorder.stop();
        ctx.getSource().sendFeedback(Text.literal("Recording saved."));
        return Command.SINGLE_SUCCESS;
    }

    private static int status(CommandContext<FabricClientCommandSource> ctx) {
        if (Recorder.isRecording()) {
            ctx.getSource().sendFeedback(Text.literal("Recording: " + Recorder.currentFile().getName()));
        } else if (Renderer.isRendering()) {
            ctx.getSource().sendFeedback(Text.literal("Rendering in progress..."));
        } else {
            ctx.getSource().sendFeedback(Text.literal("Idle."));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int render(CommandContext<FabricClientCommandSource> ctx, String res, Integer fps) {
        FabricClientCommandSource src = ctx.getSource();
        MinecraftClient client = src.getClient();
        String name = StringArgumentType.getString(ctx, "name");

        File dir = new File(FabricLoader.getInstance().getGameDir().toFile(), "replays");
        File file = new File(dir, name.endsWith(".fpr") ? name : name + ".fpr");
        if (!file.exists()) {
            src.sendError(Text.literal("Replay not found: " + file.getName()));
            return 0;
        }

        int width = ReplayConfig.renderWidth;
        int height = ReplayConfig.renderHeight;
        if (res != null) {
            int[] wh = parseRes(res);
            if (wh == null) {
                src.sendError(Text.literal("Invalid resolution '" + res + "', expected WxH (e.g. 3840x2160)"));
                return 0;
            }
            width = wh[0];
            height = wh[1];
        }
        int f = fps != null ? fps : ReplayConfig.renderFps;

        Renderer.start(client, file, width, height, f);
        if (Renderer.isRendering()) {
            src.sendFeedback(Text.literal(String.format(Locale.ROOT,
                    "Rendering %s @ %dx%d @ %dfps...", file.getName(), width, height, f)));
        } else {
            src.sendError(Text.literal("Failed to start rendering (see log)."));
            return 0;
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int stopRender(CommandContext<FabricClientCommandSource> ctx) {
        if (!Renderer.isRendering()) {
            ctx.getSource().sendError(Text.literal("Not rendering."));
            return 0;
        }
        Renderer.stop(ctx.getSource().getClient());
        ctx.getSource().sendFeedback(Text.literal("Rendering stopped."));
        return Command.SINGLE_SUCCESS;
    }

    private static int list(CommandContext<FabricClientCommandSource> ctx) {
        FabricClientCommandSource src = ctx.getSource();
        File dir = new File(FabricLoader.getInstance().getGameDir().toFile(), "replays");
        File[] files = dir.listFiles((d, n) -> n.endsWith(".fpr"));
        if (files == null || files.length == 0) {
            src.sendFeedback(Text.literal("No replays yet."));
            return Command.SINGLE_SUCCESS;
        }
        for (File f : files) {
            String info = describe(f);
            src.sendFeedback(Text.literal(f.getName() + "  " + info));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static String describe(File f) {
        try (ReplayReader r = ReplayFile.open(f)) {
            var meta = r.metadata();
            return String.format(Locale.ROOT, "(MC %s, seed %d, %s)",
                    meta.minecraftVersion, meta.worldSeed, meta.worldName);
        } catch (IOException e) {
            return "(unreadable)";
        }
    }

    private static int[] parseRes(String res) {
        String[] parts = res.toLowerCase(Locale.ROOT).split("x");
        if (parts.length != 2) {
            return null;
        }
        try {
            int w = Integer.parseInt(parts[0]);
            int h = Integer.parseInt(parts[1]);
            if (w <= 0 || h <= 0) {
                return null;
            }
            return new int[]{w, h};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String defaultName() {
        return "replay-" + java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
    }
}
