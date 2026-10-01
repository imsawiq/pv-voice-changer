package org.sawiq.client.ui.compat;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The drawing operations the mod's screens need, expressed once so the screens
 * themselves do not have to know which Minecraft rendering API they are on.
 *
 * <p>This is the Minecraft 1.21 variant, backed by {@code GuiGraphics}. The
 * 26.x builds ship a copy backed by {@code GuiGraphicsExtractor}; the method
 * signatures here are identical in every copy.</p>
 */
public final class StudioGraphics {
    private final GuiGraphics delegate;
    private final Font font;

    public StudioGraphics(GuiGraphics delegate, Font font) {
        this.delegate = delegate;
        this.font = font;
    }

    /** Fills a rectangle with a packed ARGB colour. */
    public void fill(int left, int top, int right, int bottom, int argb) {
        this.delegate.fill(left, top, right, bottom, argb);
    }

    public void text(Component text, int x, int y, int argb) {
        drawString(text, x, y, argb);
    }

    public void centeredText(Component text, int centerX, int y, int argb) {
        drawString(text, centerX - textWidth(text) / 2, y, argb);
    }

    public int textWidth(Component text) {
        return this.font.width(text);
    }

    public int lineHeight() {
        return this.font.lineHeight;
    }

    /**
     * Minecraft 1.21.6 changed what {@code drawString} returns - it used to
     * hand back the end x, and now returns nothing. The return type is part of
     * the signature the JVM links against, so a direct call compiled against
     * either half of 1.21 fails on the other, and this build covers 1.21
     * through 1.21.8.
     *
     * <p>{@code drawCenteredString} has returned nothing in every 1.21 release,
     * and all it does is shift x left by half the text width before drawing.
     * Shifting right by the same half first lands on exactly the requested x.
     * A reflective lookup by name is not an option: Fabric runs the game under
     * intermediary names, so "drawString" only exists in the dev environment.</p>
     */
    private void drawString(Component text, int x, int y, int argb) {
        int halfWidth = this.font.width(text.getVisualOrderText()) / 2;
        this.delegate.drawCenteredString(this.font, text, x + halfWidth, y, argb);
    }
}
