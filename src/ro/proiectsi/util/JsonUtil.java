package ro.proiectsi.util;

import java.util.Map;

/**
 * Utilitar minimal pentru log JSON Lines (fara dependinte externe).
 */
public final class JsonUtil {
    private JsonUtil() {}

    public static String toJsonLine(Map<String, String> fields) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean first = true;
        for (Map.Entry<String, String> e : fields.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(escape(e.getKey())).append("\":");
            sb.append("\"").append(escape(e.getValue())).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    public static String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"': sb.append("\\\""); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 32) sb.append(" ");
                    else sb.append(c);
            }
        }
        return sb.toString();
    }

    public static String assistantRequestJson(String task, String context, String model) {
        return "{"
                + "\"task\":\"" + escape(task == null ? "" : task) + "\","
                + "\"context\":\"" + escape(context == null ? "" : context) + "\","
                + "\"model\":\"" + escape(model == null ? "" : model) + "\""
                + "}";
    }

    // Parse simplu pentru {"text":"..."} (suficient pentru serviciul nostru)
    public static String extractField(String json, String field) {
        if (json == null) return null;
        String key = "\"" + field + "\"";
        int k = json.indexOf(key);
        if (k < 0) return null;
        int colon = json.indexOf(':', k);
        if (colon < 0) return null;

        int q1 = json.indexOf('"', colon + 1);
        if (q1 < 0) return null;

        int q2 = findClosingQuote(json, q1 + 1);
        if (q2 < 0) return null;

        return unescapeBasic(json.substring(q1 + 1, q2));
    }

    private static int findClosingQuote(String s, int start) {
        boolean esc = false;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (esc) { esc = false; continue; }
            if (c == '\\') { esc = true; continue; }
            if (c == '"') return i;
        }
        return -1;
    }

    private static String unescapeBasic(String s) {
        if (s == null) return null;
        StringBuilder sb = new StringBuilder();
        boolean esc = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!esc) {
                if (c == '\\') esc = true;
                else sb.append(c);
            } else {
                esc = false;
                switch (c) {
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    default: sb.append(c);
                }
            }
        }
        return sb.toString();
    }
}
