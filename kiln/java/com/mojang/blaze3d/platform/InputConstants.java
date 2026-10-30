package com.mojang.blaze3d.platform;

import java.util.HashMap;
import java.util.Map;

/**
 * Kiln's stand-in for {@code InputConstants}: key codes and their vanilla names. Since 26.1 Minecraft's key codes
 * are USB HID scancodes ({@code KEY_A = 4}), no longer GLFW's; mods compile these constants into their own code,
 * so Kiln must use the same numbers. Mouse buttons count from 1 (left).
 */
public class InputConstants {
    public static final int KEY_0 = 39;
    public static final int KEY_1 = 30;
    public static final int KEY_9 = 38;
    public static final int KEY_A = 4;
    public static final int KEY_H = 11;
    public static final int KEY_J = 13;
    public static final int KEY_K = 14;
    public static final int KEY_U = 24;
    public static final int KEY_Z = 29;
    public static final int KEY_F1 = 58;
    public static final int KEY_F12 = 69;
    public static final int KEY_F13 = 104;
    public static final int KEY_NUMPAD0 = 98;
    public static final int KEY_NUMPAD1 = 89;
    public static final int KEY_NUMPAD9 = 97;
    public static final int MOUSE_BUTTON_LEFT = 1;
    public static final int MOUSE_BUTTON_MIDDLE = 2;
    public static final int MOUSE_BUTTON_RIGHT = 3;

    public enum Type {
        KEYBOARD,
        MOUSE
    }

    /** Key code to its name after {@code key.keyboard.}, as options.txt and Horizon write it. */
    private static final Map<Integer, String> KEYS = new HashMap<>();

    static {
        for (int i = 0; i < 26; i++) {
            KEYS.put(KEY_A + i, String.valueOf((char) ('a' + i)));
        }
        for (int i = 1; i <= 9; i++) {
            KEYS.put(KEY_1 + i - 1, String.valueOf(i));
            KEYS.put(KEY_NUMPAD1 + i - 1, "keypad." + i);
        }
        KEYS.put(KEY_0, "0");
        KEYS.put(KEY_NUMPAD0, "keypad.0");
        for (int i = 1; i <= 12; i++) {
            KEYS.put(KEY_F1 + i - 1, "f" + i);
        }
        for (int i = 13; i <= 24; i++) {
            KEYS.put(KEY_F13 + i - 13, "f" + i);
        }
        Object[][] named = {
            {40, "enter"}, {41, "escape"}, {42, "backspace"}, {43, "tab"}, {44, "space"}, {45, "minus"},
            {46, "equal"}, {47, "left.bracket"}, {48, "right.bracket"}, {49, "backslash"}, {51, "semicolon"},
            {52, "apostrophe"}, {53, "grave.accent"}, {54, "comma"}, {55, "period"}, {56, "slash"},
            {57, "caps.lock"}, {70, "print.screen"}, {71, "scroll.lock"}, {72, "pause"}, {73, "insert"},
            {74, "home"}, {75, "page.up"}, {76, "delete"}, {77, "end"}, {78, "page.down"}, {79, "right"},
            {80, "left"}, {81, "down"}, {82, "up"}, {83, "num.lock"}, {84, "keypad.divide"},
            {85, "keypad.multiply"}, {86, "keypad.subtract"}, {87, "keypad.add"}, {88, "keypad.enter"},
            {99, "keypad.decimal"}, {101, "menu"}, {103, "keypad.equal"}, {224, "left.control"},
            {225, "left.shift"}, {226, "left.alt"}, {227, "left.win"}, {228, "right.control"},
            {229, "right.shift"}, {230, "right.alt"}, {231, "right.win"},
        };
        for (Object[] n : named) {
            KEYS.put((Integer) n[0], (String) n[1]);
        }
    }

    /** Kiln's own: a key code's name ({@code key.keyboard.j}, {@code key.mouse.left}); null for codes it doesn't know. */
    public static String kiln$name(Type type, int code) {
        if (type == Type.MOUSE) {
            return switch (code) {
                case MOUSE_BUTTON_LEFT -> "key.mouse.left";
                case MOUSE_BUTTON_MIDDLE -> "key.mouse.middle";
                case MOUSE_BUTTON_RIGHT -> "key.mouse.right";
                default -> "key.mouse." + code;
            };
        }
        String name = KEYS.get(code);
        return name == null ? null : "key.keyboard." + name;
    }
}
