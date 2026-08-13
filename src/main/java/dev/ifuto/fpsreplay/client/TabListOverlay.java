package dev.ifuto.fpsreplay.client;

import dev.ifuto.fpsreplay.replay.HudState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.List;

/**
 * Draws the recorded tab (player list) overlay during preview/export.
 *
 * <p>This is rendered from the recorded {@link HudState} directly (instead of
 * mutating {@code PlayerListS2CPacket.Entry}, which is fragile across MC
 * versions), so it reproduces the player names and latency exactly as they
 * appeared while recording — and only when the player had the list open.</p>
 */
public final class TabListOverlay {
    private static final int MAX_ROWS = 20;
    private static final int ROW_HEIGHT = 9;
    private static final int HEADER_HEIGHT = 13;

    private TabListOverlay() {
    }

    public static void render(DrawContext context, int scaledWidth, HudState hud) {
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer tr = client.textRenderer;
        if (hud == null || tr == null) {
            return;
        }

        List<HudState.PlayerEntry> entries = hud.playerList;
        if (entries.isEmpty()) {
            return;
        }

        String header = null;
        for (HudState.Objective o : hud.objectives) {
            if (o.slot == 0) { // LIST slot
                header = o.displayName;
                break;
            }
        }

        int columns = Math.max(1, (entries.size() + MAX_ROWS - 1) / MAX_ROWS);
        int columnWidth = scaledWidth / Math.max(1, columns);
        int rows = Math.min(MAX_ROWS, (int) Math.ceil(entries.size() / (double) columns));
        int bodyY = header != null && !header.isEmpty() ? HEADER_HEIGHT : 0;
        int totalHeight = bodyY + rows * ROW_HEIGHT + 1;

        // Translucent backdrop, like vanilla.
        context.fill(0, bodyY, scaledWidth, bodyY + rows * ROW_HEIGHT + 1, 0x80000000);

        if (header != null && !header.isEmpty()) {
            context.fill(0, 0, scaledWidth, HEADER_HEIGHT, 0x66000000);
            int hw = tr.getWidth(header);
            context.drawTextWithShadow(tr, header, scaledWidth / 2 - hw / 2, 2, 0xFFFFFFFF);
        }

        int idx = 0;
        for (int col = 0; col < columns && idx < entries.size(); col++) {
            for (int row = 0; row < MAX_ROWS && idx < entries.size(); row++, idx++) {
                HudState.PlayerEntry e = entries.get(idx);
                int px = col * columnWidth;
                int py = bodyY + row * ROW_HEIGHT;
                String name = e.name == null ? "" : e.name;
                context.drawTextWithShadow(tr, name, px + 4, py, 0xFFFFFFFF);

                int bars = pingBars(e.latency);
                int bx = px + columnWidth - 4 - bars * 2;
                for (int b = 0; b < bars; b++) {
                    context.fill(bx + b * 2, py + ROW_HEIGHT - 2, bx + b * 2 + 1, py + ROW_HEIGHT, 0xFF00E000);
                }
            }
        }
    }

    private static int pingBars(int latency) {
        if (latency < 0) {
            return 5;
        } else if (latency < 150) {
            return 5;
        } else if (latency < 300) {
            return 4;
        } else if (latency < 600) {
            return 3;
        } else if (latency < 1000) {
            return 2;
        } else {
            return 1;
        }
    }
}
