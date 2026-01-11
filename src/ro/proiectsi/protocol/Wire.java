package ro.proiectsi.protocol;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

/**
 * Format content: CHAT/1;type=SEND;from=ana;to=maria;text=Salut%21
 * Valorile sunt URL-encoded (UTF-8), astfel incat sa evitam probleme cu ';' sau '='.
 */
public final class Wire {
    private Wire() {}

    public static String encode(MsgType type, Map<String, String> fields) {
        StringBuilder sb = new StringBuilder(ChatProtocol.WIRE_PREFIX);
        sb.append(";type=").append(enc(type == null ? "" : type.name()));

        if (fields != null) {
            for (Map.Entry<String, String> e : fields.entrySet()) {
                String k = e.getKey();
                if (k == null || k.trim().isEmpty()) continue;
                if ("type".equalsIgnoreCase(k)) continue;
                sb.append(";").append(k.trim()).append("=").append(enc(nvl(e.getValue())));
            }
        }
        return sb.toString();
    }

    public static Map<String, String> decode(String content) {
        Map<String, String> out = new HashMap<>();
        if (content == null) return out;

        String s = content.trim();
        if (s.startsWith(ChatProtocol.WIRE_PREFIX)) {
            int idx = s.indexOf(';');
            if (idx < 0) return out;
            s = s.substring(idx + 1);
        }
        if (s.isEmpty()) return out;

        String[] parts = s.split(";");
        for (String p : parts) {
            if (p == null || p.isEmpty()) continue;
            int eq = p.indexOf('=');
            if (eq <= 0) continue;
            String key = p.substring(0, eq).trim();
            String val = p.substring(eq + 1);
            out.put(key, dec(val));
        }
        return out;
    }

    public static MsgType typeOf(Map<String, String> decoded) {
        return MsgType.fromString(decoded.get("type"));
    }

    private static String nvl(String s) { return s == null ? "" : s; }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(nvl(s), "UTF-8");
        } catch (Exception e) {
            return nvl(s);
        }
    }

    private static String dec(String s) {
        try {
            return URLDecoder.decode(nvl(s), "UTF-8");
        } catch (Exception e) {
            return nvl(s);
        }
    }
}
