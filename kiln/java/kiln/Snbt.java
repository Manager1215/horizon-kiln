package kiln;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * NBT as the game writes it in text (SNBT, {@code {count:5,name:"x"}}), and as Kiln keeps it in Java: {@code Byte},
 * {@code Short}, {@code Integer}, {@code Long}, {@code Float}, {@code Double}, {@code String}, {@code byte[]},
 * {@code int[]}, {@code long[]}, a {@code List} of tags and a {@code Map} of named tags (a compound, kept sorted, as
 * Horizon keeps it). Horizon hands a block entity's saved data to Kiln, and takes it back, in this text.
 */
public final class Snbt {
    private static final Pattern INTEGER = Pattern.compile("[-+]?\\d+");
    private static final Pattern DECIMAL = Pattern.compile("[-+]?(\\d+\\.?\\d*|\\.\\d+)([eE][-+]?\\d+)?");
    private final String s;
    private int i;

    private Snbt(String s) {
        this.s = s;
    }

    /** The tag {@code text} writes; a compound that is not one, or is broken, reads as an empty compound. */
    public static Map<String, Object> compound(String text) {
        try {
            Snbt p = new Snbt(text);
            if (p.value() instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> compound = (Map<String, Object>) map;
                return compound;
            }
        } catch (RuntimeException e) {
            Host.send("log", "Kiln can't read the saved data " + text + ": " + e.getMessage());
        }
        return new TreeMap<>();
    }

    /** The tag {@code text} writes, of any kind. */
    public static Object value(String text) {
        return new Snbt(text).value();
    }

    /** {@code tag} as text: {@code Tag.toString}. */
    public static String write(Object tag) {
        StringBuilder out = new StringBuilder();
        write(tag, out);
        return out.toString();
    }

    private static void write(Object tag, StringBuilder out) {
        switch (tag) {
            case Byte b -> out.append(b).append('b');
            case Short v -> out.append(v).append('s');
            case Integer v -> out.append(v);
            case Long v -> out.append(v).append('L');
            case Float v -> out.append(v).append('f');
            case Double v -> out.append(v).append('d');
            case String v -> quote(v, out);
            case byte[] a -> {
                out.append("[B;");
                for (int k = 0; k < a.length; k++) {
                    out.append(k > 0 ? "," : "").append(a[k]).append('B');
                }
                out.append(']');
            }
            case int[] a -> {
                out.append("[I;");
                for (int k = 0; k < a.length; k++) {
                    out.append(k > 0 ? "," : "").append(a[k]);
                }
                out.append(']');
            }
            case long[] a -> {
                out.append("[L;");
                for (int k = 0; k < a.length; k++) {
                    out.append(k > 0 ? "," : "").append(a[k]).append('L');
                }
                out.append(']');
            }
            case List<?> list -> {
                out.append('[');
                for (int k = 0; k < list.size(); k++) {
                    if (k > 0) {
                        out.append(',');
                    }
                    write(list.get(k), out);
                }
                out.append(']');
            }
            case Map<?, ?> map -> {
                out.append('{');
                boolean first = true;
                for (Map.Entry<?, ?> e : new TreeMap<>(map).entrySet()) {
                    if (!first) {
                        out.append(',');
                    }
                    first = false;
                    String key = (String) e.getKey();
                    if (bare(key)) {
                        out.append(key);
                    } else {
                        quote(key, out);
                    }
                    out.append(':');
                    write(e.getValue(), out);
                }
                out.append('}');
            }
            default -> throw new IllegalArgumentException("not a tag: " + tag.getClass().getName());
        }
    }

    private static boolean bare(String key) {
        if (key.isEmpty() || key.equalsIgnoreCase("true") || key.equalsIgnoreCase("false")) {
            return false;
        }
        char first = key.charAt(0);
        if (!(Character.isLetter(first) && first < 128 || first == '.' || first == '_')) {
            return false;
        }
        for (char c : key.toCharArray()) {
            if (!(c < 128 && Character.isLetterOrDigit(c) || c == '.' || c == '_' || c == '+' || c == '-')) {
                return false;
            }
        }
        return true;
    }

    private static void quote(String v, StringBuilder out) {
        out.append('"');
        for (char c : v.toCharArray()) {
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> out.append(c);
            }
        }
        out.append('"');
    }

    private Object value() {
        skip();
        char c = peek();
        if (c == '{') {
            return compound();
        }
        if (c == '[') {
            return list();
        }
        if (c == '"' || c == '\'') {
            return quoted();
        }
        return scalar(word());
    }

    private Map<String, Object> compound() {
        i++;
        Map<String, Object> map = new TreeMap<>();
        skip();
        if (peek() == '}') {
            i++;
            return map;
        }
        while (true) {
            skip();
            String key = peek() == '"' || peek() == '\'' ? quoted() : word();
            skip();
            expect(':');
            map.put(key, value());
            skip();
            if (peek() == ',') {
                i++;
                continue;
            }
            expect('}');
            return map;
        }
    }

    private Object list() {
        i++;
        if (i + 1 < s.length() && s.charAt(i + 1) == ';' && "BIL".indexOf(s.charAt(i)) >= 0) {
            char kind = s.charAt(i);
            i += 2;
            List<Number> values = new ArrayList<>();
            skip();
            if (peek() != ']') {
                while (true) {
                    values.add((Number) scalar(word()));
                    skip();
                    if (peek() == ',') {
                        i++;
                        continue;
                    }
                    break;
                }
            }
            expect(']');
            return switch (kind) {
                case 'B' -> {
                    byte[] a = new byte[values.size()];
                    for (int k = 0; k < a.length; k++) {
                        a[k] = values.get(k).byteValue();
                    }
                    yield a;
                }
                case 'I' -> values.stream().mapToInt(Number::intValue).toArray();
                default -> values.stream().mapToLong(Number::longValue).toArray();
            };
        }
        List<Object> list = new ArrayList<>();
        skip();
        if (peek() == ']') {
            i++;
            return list;
        }
        while (true) {
            list.add(value());
            skip();
            if (peek() == ',') {
                i++;
                continue;
            }
            expect(']');
            return list;
        }
    }

    private String quoted() {
        char quote = s.charAt(i++);
        StringBuilder out = new StringBuilder();
        while (true) {
            char c = s.charAt(i++);
            if (c == quote) {
                return out.toString();
            }
            if (c == '\\') {
                char e = s.charAt(i++);
                switch (e) {
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'u' -> {
                        out.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                    }
                    default -> out.append(e);
                }
            } else {
                out.append(c);
            }
        }
    }

    private String word() {
        int start = i;
        while (i < s.length() && ",:]}".indexOf(s.charAt(i)) < 0 && !Character.isWhitespace(s.charAt(i))) {
            i++;
        }
        return s.substring(start, i);
    }

    /** A bare word: a typed number ({@code 1b}, {@code 2L}, {@code 1.5f}), a boolean (a byte), else a string. */
    private static Object scalar(String w) {
        if (w.equals("true")) {
            return (byte) 1;
        }
        if (w.equals("false")) {
            return (byte) 0;
        }
        try {
            if (INTEGER.matcher(w).matches()) {
                return Integer.parseInt(w);
            }
            if (DECIMAL.matcher(w).matches()) {
                return Double.parseDouble(w);
            }
            String n = w.substring(0, w.length() - 1);
            boolean integer = INTEGER.matcher(n).matches();
            boolean decimal = integer || DECIMAL.matcher(n).matches();
            return switch (Character.toLowerCase(w.charAt(w.length() - 1))) {
                case 'b' -> integer ? Byte.parseByte(n) : w;
                case 's' -> integer ? Short.parseShort(n) : w;
                case 'l' -> integer ? Long.parseLong(n) : w;
                case 'f' -> decimal ? Float.parseFloat(n) : w;
                case 'd' -> decimal ? Double.parseDouble(n) : w;
                default -> w;
            };
        } catch (RuntimeException e) {
            return w;
        }
    }


    private void skip() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
    }

    private char peek() {
        if (i >= s.length()) {
            throw new IllegalArgumentException("the text ended early");
        }
        return s.charAt(i);
    }

    private void expect(char c) {
        if (peek() != c) {
            throw new IllegalArgumentException("expected " + c + " at " + i);
        }
        i++;
    }
}
