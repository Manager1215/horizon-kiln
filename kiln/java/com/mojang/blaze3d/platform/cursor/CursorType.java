package com.mojang.blaze3d.platform.cursor;

/** Kiln's stand-in for {@code CursorType}: a pointer shape by name. Horizon keeps its own pointer. */
public final class CursorType {
    public static final CursorType DEFAULT = new CursorType("default");

    private final String name;

    private CursorType(String name) {
        this.name = name;
    }

    static CursorType kiln$named(String name) {
        return new CursorType(name);
    }

    @Override
    public String toString() {
        return name;
    }
}
