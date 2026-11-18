package net.minecraft.client.gui.components;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Kiln's stand-in for {@code CycleButton}: a button that steps through its values when pressed (back with Shift, or
 * by the wheel), saying {@code <name>: <value>} unless it shows the value only.
 */
public class CycleButton<T> extends AbstractButton {
    public static final BooleanSupplier DEFAULT_ALT_LIST_SELECTOR = () -> false;
    private final Component name;
    private final Function<T, Component> valueText;
    private final ValueListSupplier<T> values;
    private final OnValueChange<T> onValueChange;
    private final boolean valueOnly;
    private final T initial;
    private T value;
    private int index;

    CycleButton(int x, int y, int width, int height, Component name, Function<T, Component> valueText,
            ValueListSupplier<T> values, T value, OnValueChange<T> onValueChange, boolean valueOnly) {
        super(x, y, width, height, name);
        this.name = name;
        this.valueText = valueText;
        this.values = values;
        this.onValueChange = onValueChange;
        this.valueOnly = valueOnly;
        this.initial = value;
        this.value = value;
        this.index = Math.max(0, values.getSelectedList().indexOf(value));
        updateMessage();
    }

    private void updateMessage() {
        Component shown = valueText.apply(value);
        setMessage(valueOnly ? shown : Component.literal(name.getString() + ": " + shown.getString()));
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        extractDefaultSprite(graphics);
        kiln$defaultLabel(graphics);
    }

    @Override
    public void onPress(InputWithModifiers input) {
        cycle(input.hasShiftDown() ? -1 : 1);
    }

    private void cycle(int by) {
        List<T> list = values.getSelectedList();
        if (list.isEmpty()) {
            return;
        }
        index = Math.floorMod(index + by, list.size());
        value = list.get(index);
        updateMessage();
        onValueChange.onValueChange(this, value);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (scrollY > 0.0) {
            cycle(-1);
        } else if (scrollY < 0.0) {
            cycle(1);
        }
        return true;
    }

    public void setValue(T value) {
        List<T> list = values.getSelectedList();
        int at = list.indexOf(value);
        if (at != -1) {
            index = at;
        }
        this.value = value;
        updateMessage();
    }

    public void resetValue() {
        setValue(initial);
    }

    public T getValue() {
        return value;
    }

    @Override
    protected MutableComponent createNarrationMessage() {
        return createDefaultNarrationMessage();
    }

    public MutableComponent createDefaultNarrationMessage() {
        return wrapDefaultNarrationMessage(getMessage());
    }

    public static <T> Builder<T> builder(Function<T, Component> valueText, Supplier<T> initial) {
        return new Builder<>(valueText, initial);
    }

    public static <T> Builder<T> builder(Function<T, Component> valueText, T initial) {
        return new Builder<>(valueText, () -> initial);
    }

    public static Builder<Boolean> booleanBuilder(Component on, Component off, boolean initial) {
        return new Builder<Boolean>(b -> b ? on : off, () -> initial).withValues(List.of(Boolean.TRUE, Boolean.FALSE));
    }

    public static Builder<Boolean> onOffBuilder(boolean initial) {
        return booleanBuilder(Component.translatable("options.on"), Component.translatable("options.off"), initial);
    }

    public static class Builder<T> {
        private final Function<T, Component> valueText;
        private final Supplier<T> initial;
        private ValueListSupplier<T> values = ValueListSupplier.create(List.of());
        private boolean valueOnly;

        public Builder(Function<T, Component> valueText, Supplier<T> initial) {
            this.valueText = valueText;
            this.initial = initial;
        }

        public Builder<T> withValues(Collection<T> values) {
            return withValues(ValueListSupplier.create(values));
        }

        @SafeVarargs
        public final Builder<T> withValues(T... values) {
            return withValues(List.of(values));
        }

        public Builder<T> withValues(List<T> values, List<T> altValues) {
            return withValues(ValueListSupplier.create(DEFAULT_ALT_LIST_SELECTOR, values, altValues));
        }

        public Builder<T> withValues(BooleanSupplier altSelector, List<T> values, List<T> altValues) {
            return withValues(ValueListSupplier.create(altSelector, values, altValues));
        }

        public Builder<T> withValues(ValueListSupplier<T> values) {
            this.values = values;
            return this;
        }

        public Builder<T> withCustomNarration(Function<CycleButton<T>, MutableComponent> narration) {
            return this;
        }

        public Builder<T> displayOnlyValue() {
            valueOnly = true;
            return this;
        }

        public CycleButton<T> create(Component name, OnValueChange<T> onValueChange) {
            return create(0, 0, Button.DEFAULT_WIDTH, Button.DEFAULT_HEIGHT, name, onValueChange);
        }

        public CycleButton<T> create(int x, int y, int width, int height, Component name) {
            return create(x, y, width, height, name, (button, value) -> {});
        }

        public CycleButton<T> create(int x, int y, int width, int height, Component name,
                OnValueChange<T> onValueChange) {
            T value = initial.get();
            if (values.getDefaultList().isEmpty() && value != null) {
                values = ValueListSupplier.create(List.of(value));
            }
            return new CycleButton<>(x, y, width, height, name, valueText, values, value, onValueChange, valueOnly);
        }
    }

    public interface OnValueChange<T> {
        void onValueChange(CycleButton<T> button, T value);
    }

    public interface ValueListSupplier<T> {
        List<T> getSelectedList();

        List<T> getDefaultList();

        static <T> ValueListSupplier<T> create(Collection<T> values) {
            List<T> list = List.copyOf(values);
            return new ValueListSupplier<>() {
                public List<T> getSelectedList() {
                    return list;
                }

                public List<T> getDefaultList() {
                    return list;
                }
            };
        }

        static <T> ValueListSupplier<T> create(BooleanSupplier altSelector, List<T> values, List<T> altValues) {
            List<T> list = new ArrayList<>(values);
            List<T> alt = new ArrayList<>(altValues);
            return new ValueListSupplier<>() {
                public List<T> getSelectedList() {
                    return altSelector.getAsBoolean() ? alt : list;
                }

                public List<T> getDefaultList() {
                    return list;
                }
            };
        }
    }
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    public net.minecraft.network.chat.Component createLabelForValue(T p0) { kiln.Missing.called("net.minecraft.client.gui.components.CycleButton.createLabelForValue"); return null; }
    // End of what tools/kiln-stubs.py generated.
}
