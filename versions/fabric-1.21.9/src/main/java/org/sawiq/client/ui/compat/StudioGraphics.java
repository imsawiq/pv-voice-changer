package org.sawiq.client.ui.compat;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The drawing operations the mod's screens need, expressed once so the screens
 * themselves do not have to know which Minecraft rendering API they are on.
 *
 * <p>This is the Minecraft 1.21.9 variant, backed by {@code GuiGraphics}. It
 * calls {@code drawString} directly: the return type of that method changed in
 * 1.21.6, but this build covers only 1.21.9 and 1.21.10, so it is the same on
 * both and no lookup is needed. The 1.21 build, which spans the change, has to
 * resolve it reflectively instead.</p>
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
