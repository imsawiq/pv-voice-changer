package org.sawiq.client.ui.compat;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/**
 * Screen base class that hides the version-specific render and key-event
 * signatures behind stable hooks.
 *
 * <p>This is the Minecraft 26.x variant: the render entry point is
 * {@code extractRenderState} and key events arrive as {@code KeyEvent}. The
 * 1.21 builds ship a copy built on {@code render(GuiGraphics, ...)} and raw key
 * codes. Subclasses see the same three hooks either way.</p>
 */
public abstract class CompatScreen extends Screen {
    protected CompatScreen(Component title) {
        super(title);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        StudioGraphics graphics = new StudioGraphics(context, this.font);
        paintBehindWidgets(graphics);
        super.extractRenderState(context, mouseX, mouseY, delta);
        paintAboveWidgets(graphics, mouseX, mouseY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return onKeyPressed(event.key()) || super.keyPressed(event);
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
