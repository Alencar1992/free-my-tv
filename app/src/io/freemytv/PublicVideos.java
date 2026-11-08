package io.freemytv;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.StringReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/** Pure validation/parsing of YouTube's public channel metadata and Atom uploads. No account access. */
final class PublicVideos {
    static final int FEED_LIMIT = 512 * 1024, HTML_LIMIT = 2 * 1024 * 1024;
    private static final String ATOM = "http://www.w3.org/2005/Atom";
    private static final String YT = "http://www.youtube.com/xml/schemas/2015";
    private static final Pattern META = Pattern.compile("<meta\\s+[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTRIBUTE = Pattern.compile("([\\w:-]+)\\s*=\\s*([\"'])(.*?)\\2", Pattern.DOTALL);
    private static final Pattern METADATA_ID = Pattern.compile("\"channelMetadataRenderer\"\\s*:\\s*\\{.{0,8192}?\"externalId\"\\s*:\\s*\"(UC[A-Za-z0-9_-]{22})\"", Pattern.DOTALL);

    static final class Source {
        final String ref, label, pkg;
        final boolean trailer, official;
        Source(String ref, String label, String pkg, boolean trailer, boolean official) {
            this.ref = ChannelRef.normalize(ref);
            this.label = clean(label, 120);
            this.pkg = pkg == null ? "" : pkg;
            this.trailer = trailer;
            this.official = official;
        }
    }

    static final class Video {
        final String id, channelId, channelTitle, title, thumbnail, url, serviceLabel;
        final long publishedAt;
        final boolean favorite, trailer;
        private Video(String id, String channelId, String channelTitle, String title, long at,
                      boolean favorite, boolean trailer, String serviceLabel) {
            this.id = id; this.channelId = channelId; this.channelTitle = channelTitle;
            this.title = title; publishedAt = at; this.favorite = favorite; this.trailer = trailer;
            this.serviceLabel = serviceLabel;
            url = "https://www.youtube.com/watch?v=" + id;
            thumbnail = "https://i.ytimg.com/vi/" + id + "/hqdefault.jpg";
        }
    }

    /** Parse only verified channel metadata, never links to related channels. */
    static String resolveChannel(String html) {
        if (html == null || html.length() > HTML_LIMIT) throw new IllegalArgumentException("Invalid metadata body");
        Matcher tags = META.matcher(html);
        while (tags.find()) {
            Map<String,String> attrs = attributes(tags.group());
            if (!"og:url".equalsIgnoreCase(attrs.get("property"))) continue;
            try {
                String id = ChannelRef.channelId(attrs.get("content"));
                if (id != null) return id;
            } catch (IllegalArgumentException ignored) { }
        }
        Matcher metadata = METADATA_ID.matcher(html);
        if (metadata.find()) return metadata.group(1);
        throw new IllegalArgumentException("Channel metadata unavailable");
    }

    private static Map<String,String> attributes(String tag) {
        Map<String,String> attrs = new LinkedHashMap<>();
        Matcher m = ATTRIBUTE.matcher(tag);
        while (m.find()) attrs.put(m.group(1).toLowerCase(Locale.ROOT), m.group(3));
        return attrs;
    }

    /** Both initial requests and redirects are restricted to official channel/feed paths. */
    static boolean safeUrl(String value) {
        try {
            URI u = new URI(value);
            if (!"https".equals(u.getScheme()) || u.getRawUserInfo() != null || u.getPort() != -1 || u.getRawFragment() != null) return false;
            String host = u.getHost();
            if (!"www.youtube.com".equalsIgnoreCase(host) && !"youtube.com".equalsIgnoreCase(host) && !"m.youtube.com".equalsIgnoreCase(host)) return false;
            if ("/feeds/videos.xml".equals(u.getRawPath())) {
                String query = u.getRawQuery();
                return query != null && query.startsWith("channel_id=") && ChannelRef.validChannelId(query.substring(11));
            }
            return u.getRawQuery() == null && ChannelRef.normalize(value) != null;
        } catch (Exception e) { return false; }
    }

    static String feedUrl(String channelId) {
        if (!ChannelRef.validChannelId(channelId)) throw new IllegalArgumentException("Invalid channel ID");
        return "https://www.youtube.com/feeds/videos.xml?channel_id=" + channelId;
    }

    static List<Video> parse(String xml, String expectedChannelId, Source source) {
        return select(parseRaw(xml, expectedChannelId), source);
    }

    /** Cache raw validated uploads so the same channel can back both favorites and filtered trailers. */
    static List<Video> parseRaw(String xml, String expectedChannelId) {
        if (!ChannelRef.validChannelId(expectedChannelId) || xml == null || xml.length() > FEED_LIMIT) throw new IllegalArgumentException("Invalid feed");
        String upper = xml.toUpperCase(Locale.ROOT);
        if (upper.contains("<!DOCTYPE") || upper.contains("<!ENTITY")) throw new IllegalArgumentException("DTD is not allowed");
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setExpandEntityReferences(false);
            // Android's built-in parser does not implement every optional JAXP feature. The lexical
            // DTD/entity rejection and rejecting resolver remain enforced on all supported runtimes.
            try { factory.setFeature("http://xml.org/sax/features/external-general-entities", false); } catch (Exception ignored) { }
            try { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false); } catch (Exception ignored) { }
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setEntityResolver((publicId, systemId) -> { throw new SAXException("External entities forbidden"); });
            builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler() {
                @Override public void error(org.xml.sax.SAXParseException e) throws SAXException { throw e; }
                @Override public void fatalError(org.xml.sax.SAXParseException e) throws SAXException { throw e; }
            });
            Document document = builder.parse(new InputSource(new StringReader(xml)));
            Element feed = document.getDocumentElement();
            if (!ATOM.equals(feed.getNamespaceURI()) || !"feed".equals(feed.getLocalName())
                    || !expectedChannelId.equals(child(feed, YT, "channelId"))) throw new IllegalArgumentException("Unexpected feed channel");
            String channelTitle = "";
            Element author = childElement(feed, ATOM, "author");
            if (author != null) channelTitle = clean(child(author, ATOM, "name"), 200);
            if (channelTitle.isEmpty()) channelTitle = clean(child(feed, ATOM, "title"), 200);
            Map<String,Video> unique = new LinkedHashMap<>();
            NodeList children = feed.getChildNodes();
            int entries = 0;
            for (int i = 0; i < children.getLength(); i++) {
                Node node = children.item(i);
                if (!(node instanceof Element) || !ATOM.equals(node.getNamespaceURI()) || !"entry".equals(node.getLocalName())) continue;
                if (++entries > 64) break;
                Element entry = (Element) node;
                String id = child(entry, YT, "videoId");
                if (!id.matches("[A-Za-z0-9_-]{11}") || !expectedChannelId.equals(child(entry, YT, "channelId"))) continue;
                String title = clean(child(entry, ATOM, "title"), 500);
                if (title.isEmpty()) continue;
                long at;
                try { at = Instant.parse(child(entry, ATOM, "published")).toEpochMilli(); } catch (Exception e) { continue; }
                if (at <= 0) continue;
                unique.put(id, new Video(id, expectedChannelId, channelTitle, title, at, true, false, ""));
            }
            List<Video> result = new ArrayList<>(unique.values());
            result.sort((a,b) -> { int d = Long.compare(b.publishedAt, a.publishedAt); return d != 0 ? d : a.id.compareTo(b.id); });
            return Collections.unmodifiableList(result);
        } catch (Exception e) { throw new IllegalArgumentException("Invalid public video feed", e); }
    }

    static List<Video> select(List<Video> raw, Source source) {
        List<Video> result = new ArrayList<>();
        for (Video video : raw) {
            if (source.trailer && !isTrailer(video.title)) continue;
            result.add(new Video(video.id, video.channelId, video.channelTitle, video.title, video.publishedAt,
                    !source.trailer, source.trailer, source.trailer ? source.label : ""));
            if (result.size() == 5) break;
        }
        return result;
    }

    static boolean isTrailer(String title) {
        String s = Normalizer.normalize(title == null ? "" : title, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
        return Pattern.compile("(?:^|[^a-z])(trailer|trailers|teaser|teasers|previa)(?:$|[^a-z])").matcher(s).find();
    }

    /** Global sort, bounded deduplication, and union of favorite/trailer category membership. */
    static List<Video> merge(List<List<Video>> groups) {
        Map<String,Video> unique = new LinkedHashMap<>();
        for (List<Video> group : groups) for (Video video : group) {
            Video old = unique.get(video.id);
            if (old == null) unique.put(video.id, video);
            else unique.put(video.id, new Video(old.id, old.channelId, old.channelTitle, old.title,
                    old.publishedAt, old.favorite || video.favorite, old.trailer || video.trailer,
                    old.serviceLabel.isEmpty() ? video.serviceLabel : old.serviceLabel));
        }
        List<Video> result = new ArrayList<>(unique.values());
        result.sort((a,b) -> { int d = Long.compare(b.publishedAt, a.publishedAt); return d != 0 ? d : a.id.compareTo(b.id); });
        if (result.size() > 100) result = new ArrayList<>(result.subList(0,100));
        return Collections.unmodifiableList(result);
    }

    static String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : bytes) out.append(String.format(Locale.ROOT,"%02x", b & 255));
            return out.toString();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    private static String clean(String s, int max) {
        if (s == null) return "";
        String value = s.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "").trim();
        return value.length() > max ? value.substring(0,max) : value;
    }
    private static String child(Element parent, String ns, String local) {
        Element element = childElement(parent,ns,local);
        return element == null ? "" : element.getTextContent().trim();
    }
    private static Element childElement(Element parent, String ns, String local) {
        NodeList children = parent.getChildNodes();
        for(int i=0;i<children.getLength();i++) {
            Node node=children.item(i);
            if(node instanceof Element && ns.equals(node.getNamespaceURI()) && local.equals(node.getLocalName())) return (Element) node;
        }
        return null;
    }
}
