package dev.yml.core.json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tiny dependency-free JSON reader/writer.
 *
 * <p>Values map to {@code LinkedHashMap<String,Object>}, {@code ArrayList<Object>},
 * {@code String}, {@code Long}, {@code Double}, {@code Boolean} and {@code null}.</p>
 */
public final class Json {

    private Json() {
    }

    // ------------------------------------------------------------------ parse

    public static Object parse(String text) {
        Parser p = new Parser(text);
        Object v = p.value();
        p.skipWs();
        if (p.pos < p.text.length()) {
            throw new JsonException("Trailing content at offset " + p.pos);
        }
        return v;
    }

    public static Object parse(Path file) {
        try {
            return parse(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new JsonException("Cannot read " + file + ": " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------ write

    public static String write(Object value, boolean pretty) {
        StringBuilder sb = new StringBuilder(256);
        writeValue(sb, value, pretty, 0);
        return sb.toString();
    }

    public static void write(Path file, Object value) {
        try {
            Files.createDirectories(file.getParent() == null ? file.toAbsolutePath().getParent() : file.getParent());
            Files.write(file, write(value, true).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new JsonException("Cannot write " + file + ": " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private static void writeValue(StringBuilder sb, Object v, boolean pretty, int indent) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String s) {
            writeString(sb, s);
        } else if (v instanceof Boolean b) {
            sb.append(b);
        } else if (v instanceof Double d) {
            if (d.isNaN() || d.isInfinite()) {
                sb.append("0");
            } else if (d == Math.floor(d) && Math.abs(d) < 1e15) {
                sb.append((long) (double) d);
            } else {
                sb.append(d);
            }
        } else if (v instanceof Number n) {
            sb.append(n);
        } else if (v instanceof Map<?, ?> m) {
            if (m.isEmpty()) {
                sb.append("{}");
                return;
            }
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                newline(sb, pretty, indent + 1);
                writeString(sb, String.valueOf(e.getKey()));
                sb.append(pretty ? ": " : ":");
                writeValue(sb, e.getValue(), pretty, indent + 1);
            }
            newline(sb, pretty, indent);
            sb.append('}');
        } else if (v instanceof List<?> l) {
            if (l.isEmpty()) {
                sb.append("[]");
                return;
            }
            sb.append('[');
            for (int i = 0; i < l.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                newline(sb, pretty, indent + 1);
                writeValue(sb, l.get(i), pretty, indent + 1);
            }
            newline(sb, pretty, indent);
            sb.append(']');
        } else {
            writeString(sb, String.valueOf(v));
        }
    }

    private static void newline(StringBuilder sb, boolean pretty, int indent) {
        if (!pretty) {
            return;
        }
        sb.append('\n');
        for (int i = 0; i < indent; i++) {
            sb.append("  ");
        }
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    // -------------------------------------------------------------- accessors

    @SuppressWarnings("unchecked")
    public static Map<String, Object> obj(Object o) {
        if (o instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        throw new JsonException("Expected object, got " + typeName(o));
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> objOrEmpty(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : newObj();
    }

    @SuppressWarnings("unchecked")
    public static List<Object> arr(Object o) {
        if (o instanceof List<?> l) {
            return (List<Object>) l;
        }
        throw new JsonException("Expected array, got " + typeName(o));
    }

    @SuppressWarnings("unchecked")
    public static List<Object> arrOrEmpty(Object o) {
        return o instanceof List<?> l ? (List<Object>) l : new ArrayList<>();
    }

    public static String str(Object o) {
        return o instanceof String s ? s : null;
    }

    public static String str(Object o, String def) {
        return o instanceof String s ? s : def;
    }

    public static boolean bool(Object o, boolean def) {
        return o instanceof Boolean b ? b : def;
    }

    public static long lng(Object o, long def) {
        if (o instanceof Number n) {
            return n.longValue();
        }
        return def;
    }

    public static int integer(Object o, int def) {
        return (int) lng(o, def);
    }

    public static double dbl(Object o, double def) {
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        return def;
    }

    /** Walks nested objects, e.g. {@code get(root, "downloads", "client", "url")}. */
    public static Object get(Object root, String... path) {
        Object cur = root;
        for (String key : path) {
            if (!(cur instanceof Map<?, ?> m)) {
                return null;
            }
            cur = m.get(key);
        }
        return cur;
    }

    public static String getStr(String def, Object root, String... path) {
        Object v = get(root, path);
        return v instanceof String s ? s : def;
    }

    public static Map<String, Object> newObj() {
        return new LinkedHashMap<>();
    }

    public static List<Object> newArr() {
        return new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    public static Object deepCopy(Object o) {
        if (o instanceof Map<?, ?> m) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                copy.put(String.valueOf(e.getKey()), deepCopy(e.getValue()));
            }
            return copy;
        }
        if (o instanceof List<?> l) {
            List<Object> copy = new ArrayList<>(l.size());
            for (Object v : l) {
                copy.add(deepCopy(v));
            }
            return copy;
        }
        return o;
    }

    private static String typeName(Object o) {
        return o == null ? "null" : o.getClass().getSimpleName();
    }

    public static class JsonException extends RuntimeException {
        public JsonException(String message) {
            super(message);
        }

        public JsonException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    // ----------------------------------------------------------------- parser

    private static final class Parser {
        final String text;
        int pos;

        Parser(String text) {
            this.text = text;
        }

        void skipWs() {
            while (pos < text.length()) {
                char c = text.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\uFEFF') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        Object value() {
            skipWs();
            if (pos >= text.length()) {
                throw err("Unexpected end of input");
            }
            char c = text.charAt(pos);
            return switch (c) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        Map<String, Object> object() {
            expect('{');
            Map<String, Object> map = new LinkedHashMap<>();
            skipWs();
            if (peek() == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipWs();
                String key = string();
                skipWs();
                expect(':');
                Object v = value();
                map.put(key, v);
                skipWs();
                char c = next();
                if (c == '}') {
                    return map;
                }
                if (c != ',') {
                    throw err("Expected ',' or '}' but got '" + c + "'");
                }
            }
        }

        List<Object> array() {
            expect('[');
            List<Object> list = new ArrayList<>();
            skipWs();
            if (peek() == ']') {
                pos++;
                return list;
            }
            while (true) {
                list.add(value());
                skipWs();
                char c = next();
                if (c == ']') {
                    return list;
                }
                if (c != ',') {
                    throw err("Expected ',' or ']' but got '" + c + "'");
                }
            }
        }

        String string() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (pos < text.length()) {
                char c = text.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    char e = next();
                    switch (e) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            if (pos + 4 > text.length()) {
                                throw err("Invalid unicode escape");
                            }
                            sb.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                            pos += 4;
                        }
                        default -> throw err("Invalid escape '\\" + e + "'");
                    }
                } else {
                    sb.append(c);
                }
            }
            throw err("Unterminated string");
        }

        Object number() {
            int start = pos;
            if (peek() == '-') {
                pos++;
            }
            boolean decimal = false;
            while (pos < text.length()) {
                char c = text.charAt(pos);
                if (c >= '0' && c <= '9') {
                    pos++;
                } else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                    decimal = decimal || c == '.' || c == 'e' || c == 'E';
                    pos++;
                } else {
                    break;
                }
            }
            String raw = text.substring(start, pos);
            if (raw.isEmpty() || raw.equals("-")) {
                throw err("Invalid number");
            }
            try {
                if (decimal) {
                    return Double.parseDouble(raw);
                }
                long l = Long.parseLong(raw);
                return (double) l;
            } catch (NumberFormatException e) {
                throw err("Invalid number '" + raw + "'");
            }
        }

        Object literal(String word, Object value) {
            if (!text.startsWith(word, pos)) {
                throw err("Invalid literal");
            }
            pos += word.length();
            return value;
        }

        void expect(char c) {
            skipWs();
            if (pos >= text.length() || text.charAt(pos) != c) {
                throw err("Expected '" + c + "'");
            }
            pos++;
        }

        char peek() {
            return pos < text.length() ? text.charAt(pos) : '\0';
        }

        char next() {
            if (pos >= text.length()) {
                throw err("Unexpected end of input");
            }
            return text.charAt(pos++);
        }

        JsonException err(String msg) {
            int line = 1;
            int col = 1;
            for (int i = 0; i < Math.min(pos, text.length()); i++) {
                if (text.charAt(i) == '\n') {
                    line++;
                    col = 1;
                } else {
                    col++;
                }
            }
            return new JsonException(msg + " at line " + line + ", column " + col);
        }
    }
}
