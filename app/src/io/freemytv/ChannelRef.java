package io.freemytv;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

/** Public channel addresses only; never accepts watch URLs, credentials or arbitrary hosts. */
final class ChannelRef {
    private ChannelRef() {}
    static boolean validChannelId(String id) { return id != null && id.matches("UC[A-Za-z0-9_-]{22}"); }
    static String normalize(String input) {
        if (input == null || input.length() > 600) throw invalid();
        String value = input.trim();
        if (value.startsWith("@")) value = "https://www.youtube.com/" + value;
        try {
            URI uri = new URI(value);
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null || uri.getUserInfo() != null
                    || uri.getPort() != -1 || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || !(host.equalsIgnoreCase("youtube.com") || host.equalsIgnoreCase("www.youtube.com")
                    || host.equalsIgnoreCase("m.youtube.com"))) throw invalid();
            String path = uri.getPath();
            if (path == null || !path.equals(uri.getRawPath())) throw invalid();
            if (path.endsWith("/")) path = path.substring(0, path.length() - 1);
            if (path.endsWith("/videos")) path = path.substring(0, path.length() - 7);
            if (path.startsWith("/channel/")) {
                if (!validChannelId(path.substring(9))) throw invalid();
            } else if (!path.matches("/@[\\p{L}\\p{N}_.-]{3,30}")) throw invalid();
            return "https://www.youtube.com" + path;
        } catch (java.net.URISyntaxException e) { throw invalid(); }
    }
    static String key(String value) {
        String normalized = normalize(value);
        return normalized.contains("/channel/") ? normalized : normalized.toLowerCase(Locale.ROOT);
    }
    static String channelId(String value) {
        String normalized = normalize(value);
        return normalized.contains("/channel/") ? normalized.substring(normalized.lastIndexOf('/') + 1) : null;
    }
    static List<String> decodeImport(String encoded) {
        if (encoded == null || encoded.length() > 16384) throw invalid();
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            if (bytes.length > 8192) throw invalid();
            String json = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            JSONTokener tokener = new JSONTokener(json);
            Object parsed = tokener.nextValue();
            if (!(parsed instanceof JSONObject) || tokener.nextClean() != 0) throw invalid();
            JSONObject object = (JSONObject) parsed;
            if (object.length() != 1 || !(object.opt("favorites") instanceof JSONArray)) throw invalid();
            JSONArray array = object.getJSONArray("favorites");
            if (array.length() > 12) throw invalid();
            List<String> list = new ArrayList<>(), keys = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                if (!(array.get(i) instanceof String)) throw invalid();
                String ref = normalize(array.getString(i)), key = key(ref);
                if (!keys.contains(key)) { keys.add(key); list.add(ref); }
            }
            return list;
        } catch (Exception e) { throw invalid(); }
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Use o link de um canal público do YouTube ou @nome"); }
}
