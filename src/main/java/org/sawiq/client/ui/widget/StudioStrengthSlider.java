package org.sawiq.client.ui.widget;

import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

public final class StudioStrengthSlider extends SliderWidget {
    private final Supplier<Integer> valueSupplier;
    private final Consumer<Integer> applyConsumer;

    public StudioStrengthSlider(int x, int y, int width, Supplier<Integer> valueSupplier, Consumer<Integer> applyConsumer) {
        super(x, y, width, 20, Text.translatable("pvvoicechanger.slider.strength"), 0.0D);
        this.valueSupplier = valueSupplier;
        this.applyConsumer = applyConsumer;
        refresh();
    }

    public void refresh() {
        this.value = this.valueSupplier.get() / 100.0D;
        updateMessage();
    }

    @Override
    protected void updateMessage() {
        this.setMessage(Text.literal(Text.translatable("pvvoicechanger.slider.strength").getString() + ": " + (int) Math.round(this.value * 100.0D) + "%"));
    }

    @Override
    protected void applyValue() {
        this.applyConsumer.accept((int) Math.round(this.value * 100.0D));
    }
}
