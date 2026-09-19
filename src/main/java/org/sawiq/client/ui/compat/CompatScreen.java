package org.sawiq.client.ui.compat;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Screen base class that hides the version-specific render and key-event
 * signatures behind stable hooks.
 *
 * <p>This is the Minecraft 1.21 variant: the render entry point is
 * {@code render(GuiGraphics, ...)} and key events arrive as raw key codes. The
 * 26.x builds ship a copy whose entry point is
 * {@code extractRenderState(GuiGraphicsExtractor, ...)} and which unpacks
 * {@code KeyEvent}. Subclasses see the same three hooks either way.</p>
 */
public abstract class CompatScreen extends Screen {
    protected CompatScreen(Component title) {
        super(title);
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        StudioGraphics graphics = new StudioGraphics(context, this.font);
        paintBehindWidgets(graphics);
        super.render(context, mouseX, mouseY, delta);
        paintAboveWidgets(graphics, mouseX, mouseY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return onKeyPressed(keyCode) || super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** Drawn before the widgets: backdrops, panels, section headings. */
    protected void paintBehindWidgets(StudioGraphics graphics) {
    }

    /** Drawn after the widgets: scrollbars and meters. */
    protected void paintAboveWidgets(StudioGraphics graphics, int mouseX, int mouseY) {
    }

    /** @return true when the key was handled and should not fall through */
    protected boolean onKeyPressed(int keyCode) {
        return false;
    }
}
