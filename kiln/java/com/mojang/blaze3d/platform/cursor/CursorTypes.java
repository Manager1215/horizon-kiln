package com.mojang.blaze3d.platform.cursor;

/** Kiln's stand-in for {@code CursorTypes}: the game's pointer shapes. */
public final class CursorTypes {
    public static final CursorType ARROW = CursorType.kiln$named("arrow");
    public static final CursorType IBEAM = CursorType.kiln$named("ibeam");
    public static final CursorType CROSSHAIR = CursorType.kiln$named("crosshair");
    public static final CursorType POINTING_HAND = CursorType.kiln$named("pointing_hand");
    public static final CursorType RESIZE_NS = CursorType.kiln$named("resize_ns");
    public static final CursorType RESIZE_EW = CursorType.kiln$named("resize_ew");
    public static final CursorType RESIZE_ALL = CursorType.kiln$named("resize_all");
    public static final CursorType NOT_ALLOWED = CursorType.kiln$named("not_allowed");

    private CursorTypes() {}
}
