package org.sawiq.client.ui.compat;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/**
 * Screen base class that hides the version-specific render and key-event
 * signatures behind stable hooks.
 *
 * <p>This is the Minecraft 1.21.9 variant, and it sits on the seam: the render
 * entry point is still {@code render(GuiGraphics, ...)} as on the earlier 1.21
 * builds, but key events already arrive as {@code KeyEvent}, as they do on
 * 26.x. Mixing the two is exactly why this range needs a build of its own.
 * Subclasses see the same three hooks either way.</p>
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
