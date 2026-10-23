package it.reozoz.streamclean;

import org.junit.Test;
import org.json.*;
import java.net.URI;
import static org.junit.Assert.*;

public class HomeParserTest {
    private final URI base=URI.create("https://catalog.example/");
    private String html(JSONObject props) throws Exception {
        String json=new JSONObject().put("props",props).toString();
        return "<div data-page=\""+json.replace("&","&amp;").replace("\"","&quot;")+"\"></div>";
    }
    private JSONObject item(String filename) throws Exception {
        return new JSONObject().put("id",1).put("name","Test & titolo").put("type","movie").put("score","8.1")
            .put("images",new JSONArray().put(new JSONObject().put("type","poster").put("lang","it").put("filename",filename)))
            .put("translations",new JSONArray().put(new JSONObject().put("locale","it").put("key","plot").put("value","Trama italiana")));
    }
    private JSONObject props(String filename) throws Exception {
        return new JSONObject().put("title",item(filename)).put("sliders",new JSONArray().put(new JSONObject().put("label","Recenti")
            .put("titles",new JSONArray().put(item(filename)))));
    }
    @Test public void readsEntitiesSectionsAndTranslations() throws Exception {
        HomeParser.Home h=HomeParser.parse(html(props("abc-123.webp")),base);
        assertEquals("Test & titolo",h.hero().name());
        assertEquals("Trama italiana",h.hero().plot());
        assertEquals("Recenti",h.sections().get(0).name());
        assertEquals("https://cdn.catalog.example/images/abc-123.webp",h.sections().get(0).titles().get(0).poster());
    }
    @Test public void ignoresUntrustedImageDestinationsAndBanners() throws Exception {
        JSONObject p=props("../../bad.svg").put("cdn_url","https://ads.example").put("banner",new JSONObject().put("html","<script>alert(1)</script>"));
        HomeParser.Home h=HomeParser.parse(html(p),base);
        assertEquals("",h.hero().poster());
        assertEquals("cdn.catalog.example",h.cdnHost());
    }
    @Test public void toleratesMissingHeroAndImages() throws Exception {
        JSONObject p=new JSONObject().put("sliders",new JSONArray().put(new JSONObject().put("titles",new JSONArray().put(new JSONObject().put("name","Solo nome")))));
        HomeParser.Home h=HomeParser.parse(html(p),base);
        assertNull(h.hero()); assertEquals("",h.sections().get(0).titles().get(0).poster());
    }
    @Test(expected=IllegalStateException.class) public void challengeIsNotEmptySuccess() throws Exception {HomeParser.parse("<html>Challenge</html>",base);}
    @Test(expected=IllegalStateException.class) public void missingSchemaFailsClearly() throws Exception {HomeParser.parse(html(new JSONObject()),base);}
    @Test public void normalizesOrigin() {assertEquals("https://catalog.example/",HomeParser.origin(" https://CATALOG.example/titles?q=1 ").toString());}
    @Test public void rejectsUnsafeOrigins() {
        for(String value:new String[]{"http://catalog.example/","javascript:alert(1)","https://user:pass@catalog.example/","https://catalog.example:8080/","not a url"}) {
            try {HomeParser.origin(value);fail(value);} catch(IllegalArgumentException expected){}
        }
    }
    @Test public void parsesCatalogueResultsWithoutHomeSliders() throws Exception {
        JSONObject p=new JSONObject().put("titles",new JSONArray().put(item("abc.webp").put("name","South Park"))).put("totalCount",62);
        HomeParser.Home h=HomeParser.parseSearch(html(p),base);
        assertNull(h.hero()); assertEquals("South Park",h.sections().get(0).titles().get(0).name());
        assertEquals("Risultati (1 di 62)",h.sections().get(0).name());
    }
    @Test public void acceptsEmptyCatalogueResults() throws Exception {
        HomeParser.Home h=HomeParser.parseSearch(html(new JSONObject().put("titles",new JSONArray()).put("totalCount",0)),base);
        assertTrue(h.sections().get(0).titles().isEmpty());
    }
    @Test(expected=IllegalStateException.class) public void missingSearchSchemaFailsClearly() throws Exception {HomeParser.parseSearch(html(new JSONObject()),base);}
}
