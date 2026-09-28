package org.sawiq.client.ui.compat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
     * hand back the end x, and now returns nothing. The call looks identical
     * in source, but the return type is part of the signature the JVM matches
     * on, so a build compiled against either half of 1.21 fails on the other
     * with a NoSuchMethodError. This build covers 1.21 through 1.21.8, so the
     * method is looked up by name and argument types, which finds it either
     * way.
     *
     * <p>Resolved once when the class loads; drawing a handful of labels a
     * frame through a cached Method costs nothing that shows up.</p>
     */
    private static final Method DRAW_STRING = resolveDrawString();

    private static Method resolveDrawString() {
        try {
            return GuiGraphics.class.getMethod(
                    "drawString", Font.class, Component.class, int.class, int.class, int.class);
        } catch (NoSuchMethodException exception) {
            throw new IllegalStateException("GuiGraphics has no drawString(Font, Component, int, int, int)", exception);
        }
    }

    private void drawString(Component text, int x, int y, int argb) {
        try {
            DRAW_STRING.invoke(this.delegate, this.font, text, x, y, argb);
        } catch (IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("Could not draw text", exception);
        }
    }
}
