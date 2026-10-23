package it.reozoz.streamclean;
import org.junit.Test;
import org.json.*;
import java.net.URI;
import static org.junit.Assert.*;

public class TitleParserTest {
    private final URI origin=URI.create("https://catalog.example/");
    private String html(JSONObject props)throws Exception {
        return "<div data-page=\""+new JSONObject().put("props",props).toString().replace("&","&amp;").replace("\"","&quot;")+"\"></div>";
    }
    @Test public void loadsSeasonsAndSortsEpisodes()throws Exception {
        JSONObject title=new JSONObject().put("id",1).put("type","tv").put("name","Serie").put("slug","serie").put("seasons",new JSONArray().put(new JSONObject().put("number",2)).put(new JSONObject().put("number",1)));
        JSONArray episodes=new JSONArray().put(new JSONObject().put("id",20).put("number",2).put("scws_id",8)).put(new JSONObject().put("id",10).put("number",1).put("scws_id",JSONObject.NULL));
        TitleParser.Detail d=TitleParser.parse(html(new JSONObject().put("title",title).put("loadedSeason",new JSONObject().put("number",2).put("episodes",episodes))));
        assertEquals(1,d.seasons().get(0).number());assertEquals(2,d.selectedSeason());assertEquals(10,d.episodes().get(0).id());assertFalse(d.episodes().get(0).available());assertTrue(d.episodes().get(1).available());
    }
    @Test public void extractsEpisodeArtworkAndRejectsUnsafeFilenames()throws Exception {
        JSONObject title=new JSONObject().put("id",1).put("type","tv").put("images",new JSONArray()
            .put(new JSONObject().put("type","poster").put("filename","poster.webp"))
            .put(new JSONObject().put("type","background").put("filename","background.webp")));
        JSONArray images=new JSONArray()
            .put(new JSONObject().put("type","cover").put("filename","../escape.webp").put("lang","it"))
            .put(new JSONObject().put("type","cover").put("filename","english.webp").put("lang","en"))
            .put(new JSONObject().put("type","cover").put("filename","italian.webp").put("lang","it"));
        JSONArray episodes=new JSONArray()
            .put(new JSONObject().put("number",1).put("images",images))
            .put(new JSONObject().put("number",2).put("images",new JSONArray().put(new JSONObject().put("type","cover").put("filename","https://ads.example/image.webp"))))
            .put(new JSONObject().put("number",3));
        TitleParser.Detail d=TitleParser.parse(html(new JSONObject().put("title",title).put("loadedSeason",new JSONObject().put("number",1).put("episodes",episodes))));
        assertEquals("background.webp",d.backdrop());assertEquals("italian.webp",d.episodes().get(0).thumbnail());
        assertEquals("",d.episodes().get(1).thumbnail());assertEquals("",d.episodes().get(2).thumbnail());
    }
    @Test public void movieNeedsNoSeasons()throws Exception {
        TitleParser.Detail d=TitleParser.parse(html(new JSONObject().put("title",new JSONObject().put("id",1).put("type","movie").put("scws_id",9))));
        assertTrue(d.movieAvailable());assertTrue(d.seasons().isEmpty());
    }
    @Test public void playerIgnoresAdvertisingScriptsAndIframes(){
        TitleParser.Player p=TitleParser.player("<script src='https://ads.example/a.js'></script><iframe src='https://ads.example/'></iframe><iframe src='https://vixcloud.cc/embed/42?token=a&amp;expires=9'></iframe>",origin);
        assertEquals("https://vixcloud.cc/embed/42?token=a&expires=9",p.embed().toString());
    }
    @Test(expected=IllegalStateException.class) public void untrustedPlayerIsRejected(){TitleParser.player("<iframe src='https://vixcloud.cc.evil.example/embed/42'></iframe>",origin);}
    @Test public void verifiesWatchEpisodeIdentity()throws Exception {
        JSONObject p=new JSONObject().put("title",new JSONObject().put("id",1)).put("episode",new JSONObject().put("id",10)).put("embedUrl","https://catalog.example/it/iframe/1?episode_id=10");
        assertEquals("/it/iframe/1",TitleParser.watchFrame(html(p),origin,1,10).getPath());
        try{TitleParser.watchFrame(html(p),origin,1,11);fail();}catch(IllegalStateException expected){}
        p.put("redirectToLogin",true);try{TitleParser.watchFrame(html(p),origin,1,10);fail();}catch(IllegalStateException expected){}
    }
    @Test public void blocksExternalScriptsButAllowsMedia(){
        assertTrue(PlayerPolicy.resource("https://vixcloud.cc/build/player.js"));
        assertTrue(PlayerPolicy.resource("https://sc-u19-01.vix-content.net/hls/video.m3u8?token=x"));
        assertFalse(PlayerPolicy.resource("https://spbgc.com/tag.min.js"));
        assertFalse(PlayerPolicy.resource("https://ads.example/video.mp4"));
        assertFalse(PlayerPolicy.resource("https://ads.example/fake.mp4.js"));
        assertFalse(PlayerPolicy.resource("https://analytics.vixcloud.cc/js/script.js"));
        assertFalse(PlayerPolicy.resource("file:///etc/passwd"));
        assertFalse(PlayerPolicy.frame("https://vixcloud.cc/download/42"));
        assertTrue(PlayerPolicy.frame("https://vixcloud.cc/embed/42?token=x"));
    }
}
