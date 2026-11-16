package net.minecraft.world.entity.ai.attributes;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

/**
 * Kiln's stand-in for {@code AttributeModifier}: an amount an item or an effect adds to an attribute, by id, and how
 * (D-177). Saved as the game saves it: {@code {"id": ..., "amount": ..., "operation": "add_value"}}.
 */
public record AttributeModifier(Identifier id, double amount, AttributeModifier.Operation operation) {
    public static final MapCodec<AttributeModifier> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Identifier.CODEC.fieldOf("id").forGetter(AttributeModifier::id),
            Codec.DOUBLE.fieldOf("amount").forGetter(AttributeModifier::amount),
            Operation.CODEC.fieldOf("operation").forGetter(AttributeModifier::operation))
            .apply(i, AttributeModifier::new));
    public static final Codec<AttributeModifier> CODEC = MAP_CODEC.codec();

    public boolean is(Identifier other) {
        return other.equals(id);
    }

    public static enum Operation implements net.minecraft.util.StringRepresentable {
        ADD_VALUE("add_value", 0),
        ADD_MULTIPLIED_BASE("add_multiplied_base", 1),
        ADD_MULTIPLIED_TOTAL("add_multiplied_total", 2);

        public static final Codec<Operation> CODEC = net.minecraft.util.StringRepresentable.fromEnum(Operation::values);

        private final String name;
        private final int id;

        Operation(String name, int id) {
            this.name = name;
            this.id = id;
        }

        public int id() {
            return id;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }
}
