package lms;

import java.util.*;

/** Minimal JSON reader/writer (JDK has none). Objects -> LinkedHashMap, arrays -> ArrayList. */
final class Json {
    private final String s;
    private int i;

    private Json(String s) { this.s = s; }

    static Object parse(String text) {
        Json j = new Json(text);
        j.ws();
        Object v = j.value();
        j.ws();
        if (j.i != j.s.length()) throw new IllegalArgumentException("Trailing data in JSON");
        return v;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> parseObject(String text) {
        if (text == null || text.trim().isEmpty()) return new LinkedHashMap<>();
        Object o = parse(text);
        if (!(o instanceof Map)) throw new IllegalArgumentException("JSON object expected");
        return (Map<String, Object>) o;
    }

    static Map<String, Object> obj(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int k = 0; k + 1 < kv.length; k += 2) m.put(String.valueOf(kv[k]), kv[k + 1]);
        return m;
    }

    // ---------- parsing ----------
    private void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }

    private Object value() {
        if (i >= s.length()) throw new IllegalArgumentException("Unexpected end of JSON");
        char c = s.charAt(i);
        if (c == '{') return object();
        if (c == '[') return array();
        if (c == '"') return string();
        if (s.startsWith("true", i))  { i += 4; return Boolean.TRUE; }
        if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
        if (s.startsWith("null", i))  { i += 4; return null; }
        return number();
    }

    private Map<String, Object> object() {
        Map<String, Object> m = new LinkedHashMap<>();
        i++; ws();
        if (s.charAt(i) == '}') { i++; return m; }
        while (true) {
            ws();
            String k = string();
            ws();
            expect(':');
            ws();
            m.put(k, value());
            ws();
            if (s.charAt(i) == ',') { i++; continue; }
            expect('}');
            return m;
        }
    }

    private List<Object> array() {
        List<Object> l = new ArrayList<>();
        i++; ws();
        if (s.charAt(i) == ']') { i++; return l; }
        while (true) {
            ws();
            l.add(value());
            ws();
            if (s.charAt(i) == ',') { i++; continue; }
            expect(']');
            return l;
        }
    }

    private void expect(char c) {
        if (i >= s.length() || s.charAt(i) != c) throw new IllegalArgumentException("Expected '" + c + "' at " + i);
        i++;
    }

    private String string() {
        expect('"');
        StringBuilder b = new StringBuilder();
        while (true) {
            if (i >= s.length()) throw new IllegalArgumentException("Unterminated string");
            char c = s.charAt(i++);
            if (c == '"') return b.toString();
            if (c != '\\') { b.append(c); continue; }
            char e = s.charAt(i++);
            switch (e) {
                case 'n': b.append('\n'); break;
                case 't': b.append('\t'); break;
                case 'r': b.append('\r'); break;
                case 'b': b.append('\b'); break;
                case 'f': b.append('\f'); break;
                case 'u': b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; break;
                default: b.append(e);
            }
        }
    }

    private Object number() {
        int st = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
        String t = s.substring(st, i);
        if (t.isEmpty()) throw new IllegalArgumentException("Bad JSON at " + st);
        if (t.indexOf('.') < 0 && t.indexOf('e') < 0 && t.indexOf('E') < 0) return Long.parseLong(t);
        return Double.parseDouble(t);
    }

    // ---------- writing ----------
    static String write(Object o) {
        StringBuilder b = new StringBuilder();
        w(b, o);
        return b.toString();
    }

    private static void w(StringBuilder b, Object o) {
        if (o == null) { b.append("null"); return; }
        if (o instanceof String) { str(b, (String) o); return; }
        if (o instanceof Boolean) { b.append(o); return; }
        if (o instanceof Double || o instanceof Float) {
            double d = ((Number) o).doubleValue();
            b.append(Double.isNaN(d) || Double.isInfinite(d) ? "null" : o.toString());
            return;
        }
        if (o instanceof Number) { b.append(o); return; }
        if (o instanceof Map) {
            b.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : ((Map<?, ?>) o).entrySet()) {
                if (!first) b.append(',');
                first = false;
                str(b, String.valueOf(e.getKey()));
                b.append(':');
                w(b, e.getValue());
            }
            b.append('}');
            return;
        }
        if (o instanceof Iterable) {
            b.append('[');
            boolean first = true;
            for (Object x : (Iterable<?>) o) {
                if (!first) b.append(',');
                first = false;
                w(b, x);
            }
            b.append(']');
            return;
        }
        str(b, o.toString());
    }

    private static void str(StringBuilder b, String s) {
        b.append('"');
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            switch (c) {
                case '"':  b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default:
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c)); else b.append(c);
            }
        }
        b.append('"');
    }
}
