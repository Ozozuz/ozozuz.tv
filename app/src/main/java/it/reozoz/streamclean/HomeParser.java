package it.reozoz.streamclean;

import org.json.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import java.net.URI;
import java.util.*;

/** Parses inert HTML only. Never interprets HTML banners, scripts or embed URLs. */
public final class HomeParser {
    public record Title(long id, String name, String type, String score, String plot, String poster, String slug) {}
    public record Section(String name, List<Title> titles) {}
    public record Home(Title hero, List<Section> sections, String cdnHost) {}

    public static URI origin(String value) {
        URI u = URI.create(value.trim());
        if (!"https".equalsIgnoreCase(u.getScheme()) || u.getHost() == null || u.getUserInfo() != null
                || (u.getPort() != -1 && u.getPort() != 443))
            throw new IllegalArgumentException("Inserisci un URL HTTPS valido, senza credenziali o porte personalizzate.");
        return URI.create("https://" + u.getHost().toLowerCase(Locale.ROOT) + "/");
    }

    public static Home parse(String html, URI base) throws Exception {
        Element el = Jsoup.parse(html).selectFirst("[data-page]");
        if (el == null) throw new IllegalStateException("Dati della home assenti: possibile challenge o struttura del sito cambiata.");
        JSONObject props = new JSONObject(el.attr("data-page")).getJSONObject("props");
        // Only the selected origin or its exact cdn subdomain may supply images.
        String cdnHost = "cdn." + base.getHost();
        URI cdn = URI.create("https://" + cdnHost + "/");
        List<Section> sections = new ArrayList<>();
        JSONArray sliders = props.optJSONArray("sliders");
        if (sliders == null) throw new IllegalStateException("Sezioni della home non riconosciute.");
        for (int i = 0; i < Math.min(sliders.length(), 30); i++) {
            JSONObject slider = sliders.getJSONObject(i);
            JSONArray list = slider.optJSONArray("titles");
            List<Title> titles = new ArrayList<>();
            if (list != null) for (int j = 0; j < Math.min(list.length(), 100); j++) titles.add(title(list.getJSONObject(j), cdn));
            sections.add(new Section(slider.optString("label", "Titoli"), titles));
        }
        JSONObject hero = props.optJSONObject("title");
        return new Home(hero == null ? null : title(hero, cdn), sections, cdnHost);
    }

    public static Home parseSearch(String html, URI base) throws Exception {
        Element el = Jsoup.parse(html).selectFirst("[data-page]");
        if (el == null) throw new IllegalStateException("Dati della ricerca assenti: possibile challenge o struttura cambiata.");
        JSONObject props = new JSONObject(el.attr("data-page")).getJSONObject("props");
        JSONArray list = props.optJSONArray("titles");
        if (list == null) throw new IllegalStateException("Risultati della ricerca non riconosciuti.");
        String cdnHost = "cdn." + base.getHost();
        URI cdn = URI.create("https://" + cdnHost + "/");
        List<Title> titles = new ArrayList<>();
        for (int i = 0; i < Math.min(list.length(), 100); i++) titles.add(title(list.getJSONObject(i), cdn));
        int total = props.optInt("totalCount", titles.size());
        String label = total > titles.size() ? "Risultati (" + titles.size() + " di " + total + ")" : "Risultati (" + titles.size() + ")";
        return new Home(null, Collections.singletonList(new Section(label, titles)), cdnHost);
    }

    private static Title title(JSONObject o, URI cdn) {
        String plot = o.optString("plot", ""), poster = "";
        JSONArray translations = o.optJSONArray("translations");
        if (translations != null) for (int i = 0; i < translations.length(); i++) {
            JSONObject t = translations.optJSONObject(i);
            if (t != null && "plot".equals(t.optString("key")) && "it".equals(t.optString("locale"))) plot = t.optString("value");
        }
        JSONArray images = o.optJSONArray("images");
        if (images != null) for (int i = 0; i < images.length(); i++) {
            JSONObject image = images.optJSONObject(i);
            if (image == null || !"poster".equals(image.optString("type"))) continue;
            String filename = image.optString("filename");
            if (filename.matches("[a-zA-Z0-9_-]+\\.(webp|png|jpg|jpeg)")) {
                poster = cdn.resolve("images/" + filename).toString();
                if ("it".equals(image.optString("lang"))) break;
            }
        }
        return new Title(o.optLong("id"), o.optString("name", "Senza titolo"), o.optString("type"),
                o.optString("score", ""), plot, poster, o.optString("slug", "title"));
    }
}
