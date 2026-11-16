package net.fabricmc.fabric.api.attachment.v1;

import java.util.Objects;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import kiln.Attachments;
import net.fabricmc.fabric.api.event.Event;

/**
 * Kiln's stand-in for Fabric's {@code AttachmentTarget}: what carries the mods' attachments. Kiln's {@code
 * BlockEntity}, {@code Entity}, {@code Level} and {@code LevelChunk} are ones, as Fabric's interface injection makes the
 * game's. Kiln keeps the values ({@link Attachments}): a level's by its dimension and an entity's by its UUID, as
 * Kiln's objects of those are new each call, a block entity's with the object.
 */
public interface AttachmentTarget {
    /** Where a block entity's persistent attachments are in its saved compound, as in Fabric. */
    String NAME_IN_NBT = "fabric:attachments";

    default <T> T getAttached(AttachmentType<T> type) {
        return Attachments.get(this, type);
    }

    default <T> T getAttachedOrThrow(AttachmentType<T> type) {
        return Objects.requireNonNull(getAttached(type),
                () -> "No value attached for type " + type.identifier() + " on " + this);
    }

    default <T> T getAttachedOrSet(AttachmentType<T> type, T defaultValue) {
        T value = getAttached(type);
        if (value != null) {
            return value;
        }
        setAttached(type, defaultValue);
        return defaultValue;
    }

    default <T> T getAttachedOrCreate(AttachmentType<T> type, Supplier<T> initializer) {
        T value = getAttached(type);
        if (value != null) {
            return value;
        }
        T made = initializer.get();
        setAttached(type, made);
        return made;
    }

    default <T> T getAttachedOrCreate(AttachmentType<T> type) {
        Supplier<T> initializer = type.initializer();
        if (initializer == null) {
            throw new IllegalArgumentException("Attachment type " + type.identifier() + " has no initializer");
        }
        return getAttachedOrCreate(type, initializer);
    }

    default <T> T getAttachedOrElse(AttachmentType<T> type, T defaultValue) {
        T value = getAttached(type);
        return value != null ? value : defaultValue;
    }

    default <T> T getAttachedOrGet(AttachmentType<T> type, Supplier<T> defaultValue) {
        T value = getAttached(type);
        return value != null ? value : defaultValue.get();
    }

    /** Sets it (null removes it); the value it had before, or null. */
    default <T> T setAttached(AttachmentType<T> type, T value) {
        return Attachments.set(this, type, value);
    }

    default boolean hasAttached(AttachmentType<?> type) {
        return getAttached(type) != null;
    }

    default <T> T removeAttached(AttachmentType<T> type) {
        return setAttached(type, null);
    }

    /** The value {@code modifier} makes of the one there (null when none), set; null removes it. */
    default <T> T modifyAttached(AttachmentType<T> type, UnaryOperator<T> modifier) {
        T value = modifier.apply(getAttached(type));
        setAttached(type, value);
        return value;
    }

    /** Heard when this target's value of {@code type} is set or removed. */
    default <A> Event<OnAttachedSet<A>> onAttachedSet(AttachmentType<A> type) {
        return Attachments.onSet(this, type);
    }

    @FunctionalInterface
    interface OnAttachedSet<A> {
        void onAttachedSet(A oldValue, A newValue);
    }
}
