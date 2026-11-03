package net.minecraft.world.entity.ai.attributes;

import java.util.function.Consumer;
import net.minecraft.core.Holder;

/**
 * Kiln's stand-in for {@code AttributeInstance}: an entity's base value of an attribute and its value with the
 * entity's modifiers, as the game said. A new base moves the value by as much (the modifiers Kiln can't see stay).
 */
public class AttributeInstance {
    private final Holder<Attribute> attribute;
    private final Consumer<AttributeInstance> onChange;
    private double base;
    private double value;

    public AttributeInstance(Holder<Attribute> attribute, double base, double value, Consumer<AttributeInstance> onChange) {
        this.attribute = attribute;
        this.base = base;
        this.value = value;
        this.onChange = onChange;
    }

    public Holder<Attribute> getAttribute() {
        return attribute;
    }

    public double getBaseValue() {
        return base;
    }

    public void setBaseValue(double base) {
        if (base == this.base) {
            return;
        }
        value += base - this.base;
        this.base = base;
        onChange.accept(this);
    }

    public double getValue() {
        return value;
    }
}
