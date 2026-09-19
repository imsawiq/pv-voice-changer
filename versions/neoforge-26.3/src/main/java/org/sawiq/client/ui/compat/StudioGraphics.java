package org.sawiq.client.ui.compat;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * The drawing operations the mod's screens need, expressed once so the screens
 * themselves do not have to know which Minecraft rendering API they are on.
 *
 * <p>This is the Minecraft 26.x variant, backed by
 * {@code GuiGraphicsExtractor}. The 1.21 builds ship a copy backed by
 * {@code GuiGraphics}; the method signatures here are identical in every
 * copy.</p>
 */
public final class StudioGraphics {
    private final GuiGraphicsExtractor delegate;
    private final Font font;

    public StudioGraphics(GuiGraphicsExtractor delegate, Font font) {
        this.delegate = delegate;
        this.font = font;
    }

    /** Fills a rectangle with a packed ARGB colour. */
    public void fill(int left, int top, int right, int bottom, int argb) {
        this.delegate.fill(left, top, right, bottom, argb);
    }

    /**
     * Left-aligned text, expressed through the centred call with the anchor
     * shifted by half the string. That is the one text entry point this
     * rendering API is known to expose across the whole 26.x line.
     */
    public void text(Component text, int x, int y, int argb) {
        this.delegate.centeredText(this.font, text, x + textWidth(text) / 2, y, argb);
    }

    public void centeredText(Component text, int centerX, int y, int argb) {
        this.delegate.centeredText(this.font, text, centerX, y, argb);
    }

    public int textWidth(Component text) {
        return this.font.width(text);
    }

    public int lineHeight() {
        return this.font.lineHeight;
    }
}
