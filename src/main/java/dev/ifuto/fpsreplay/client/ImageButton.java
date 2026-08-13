package dev.ifuto.fpsreplay.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * A {@link ButtonWidget} that draws a 20x20 icon texture (the author-provided
 * START/STOP/Replay List images) followed by a translatable label.
 */
public final class ImageButton extends ButtonWidget {
    private Identifier texture;

    public ImageButton(int x, int y, int width, int height, Identifier texture, Text message, PressAction onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION_SUPPLIER);
        this.texture = texture;
    }

    public void setTexture(Identifier texture) {
        this.texture = texture;
    }

    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        MinecraftClient client = MinecraftClient.getInstance();
        context.drawTexture(texture, getX(), getY(), 0, 0, 20, 20, 20, 20);
        int tx = getX() + 24;
        int ty = getY() + (getHeight() - client.textRenderer.fontHeight) / 2;
        int color = isHovered() ? 0xFFFFFF00 : 0xFFFFFFFF;
        context.drawTextWithShadow(client.textRenderer, getMessage(), tx, ty, color);
        if (isHovered()) {
            context.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0x33FFFFFF);
        }
    }
}
