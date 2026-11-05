package net.fabricmc.fabric.api.transfer.v1.fluid;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import kiln.Variants;
import net.fabricmc.fabric.api.transfer.v1.storage.TransferVariant;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluid;

/** Kiln's stand-in for Fabric's {@code FluidVariant}: a fluid as a resource to store and move, by its registry id. */
public interface FluidVariant extends TransferVariant<Fluid> {
    /** As Fabric's: {@code {"fluid": "minecraft:water"}}; Kiln keeps no components on a variant yet. */
    Codec<FluidVariant> CODEC = RecordCodecBuilder.create(i -> i.group(
            BuiltInRegistries.FLUID.byNameCodec().fieldOf("fluid").forGetter(FluidVariant::getFluid))
            .apply(i, FluidVariant::of));
    StreamCodec<RegistryFriendlyByteBuf, FluidVariant> PACKET_CODEC = ByteBufCodecs.STRING_UTF8
            .<FluidVariant>map(id -> of(BuiltInRegistries.FLUID.getValue(Identifier.parse(id))),
                    v -> String.valueOf(BuiltInRegistries.FLUID.getKey(v.getFluid())))
            .cast();

    /** No fluid: its {@link #getFluid()} is {@code Fluids.EMPTY}. */
    static FluidVariant blank() {
        return Variants.fluid(null);
    }

    static FluidVariant of(Fluid fluid) {
        return Variants.fluid(fluid);
    }

    static FluidVariant of(Fluid fluid, DataComponentPatch components) {
        return of(fluid);
    }

    default Fluid getFluid() {
        return getObject();
    }

    @Override
    default Holder<Fluid> typeHolder() {
        return BuiltInRegistries.FLUID.get(BuiltInRegistries.FLUID.getKey(getFluid()))
                .<Holder<Fluid>>map(h -> h).orElseGet(() -> Holder.direct(getFluid()));
    }

    @Override
    FluidVariant withComponents(DataComponentPatch patch);
}
