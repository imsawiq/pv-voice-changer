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
        this.delegate.drawString(this.font, text, x, y, argb);
    }

    public void centeredText(Component text, int centerX, int y, int argb) {
        this.delegate.drawString(this.font, text, centerX - textWidth(text) / 2, y, argb);
    }

    public int textWidth(Component text) {
        return this.font.width(text);
    }

    public int lineHeight() {
        return this.font.lineHeight;
    }
}
