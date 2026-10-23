package it.reozoz.streamclean;

import org.json.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import java.net.URI;
import java.util.*;

public final class TitleParser {
    public record Season(int number, String name, int count) {
        public String toString() { return (number==0?"Speciali":"Stagione "+number)+(count>0?" · "+count+" episodi":""); }
    }
    public record Episode(long id, int number, String name, String plot, boolean available,String thumbnail) {}
    public record Detail(long id,String slug,String name,String plot,String type,List<Season> seasons,int selectedSeason,List<Episode> episodes,boolean movieAvailable,String backdrop) {}
    public record Player(URI page,URI embed) {}

    static JSONObject props(String html) throws Exception {
        Element el=Jsoup.parse(html).selectFirst("[data-page]");
        if(el==null) throw new IllegalStateException("Dati della pagina assenti. Possibile challenge o sito modificato.");
        return new JSONObject(el.attr("data-page")).getJSONObject("props");
    }
    public static Detail parse(String html) throws Exception {
        JSONObject p=props(html),t=p.getJSONObject("title");
        List<Season> seasons=new ArrayList<>();JSONArray ss=t.optJSONArray("seasons");
        if(ss!=null)for(int i=0;i<Math.min(ss.length(),300);i++){
            JSONObject s=ss.getJSONObject(i);seasons.add(new Season(s.optInt("number"),s.optString("name",""),s.optInt("episodes_count")));
        }
        seasons.sort(Comparator.comparingInt(Season::number));
        JSONObject loaded=p.optJSONObject("loadedSeason");List<Episode> episodes=new ArrayList<>();
        JSONArray es=loaded==null?null:loaded.optJSONArray("episodes");
        if(es!=null)for(int i=0;i<Math.min(es.length(),1000);i++){
            JSONObject e=es.getJSONObject(i);
            episodes.add(new Episode(e.optLong("id"),e.optInt("number"),e.optString("name","Episodio "+e.optInt("number")),e.optString("plot",""),e.optLong("scws_id",0)>0,artwork(e,"cover","still","thumbnail","background")));
        }
        episodes.sort(Comparator.comparingInt(Episode::number));
        return new Detail(t.getLong("id"),t.optString("slug"),t.optString("name"),t.optString("plot"),t.optString("type"),seasons,loaded==null?-1:loaded.optInt("number"),episodes,t.optLong("scws_id",0)>0 && !t.optBoolean("coming_soon"),backdrop(t));
    }
    private static String backdrop(JSONObject title) {return artwork(title,"background","cover","cover_mobile","poster");}
    private static String artwork(JSONObject title,String... types) {
        JSONArray images=title.optJSONArray("images");if(images==null)return "";
        for(String type:types) {
            String fallback="";
            for(int i=0;i<images.length();i++){
                JSONObject image=images.optJSONObject(i);if(image==null||!type.equals(image.optString("type")))continue;
                String filename=image.optString("filename");
                if(!filename.matches("[a-zA-Z0-9_-]+\\.(webp|png|jpg|jpeg)"))continue;
                if("it".equals(image.optString("lang")))return filename;
                if(fallback.isEmpty())fallback=filename;
            }
            if(!fallback.isEmpty())return fallback;
        }
        return "";
    }
    public static URI watchFrame(String html,URI origin,long titleId,long episodeId) throws Exception {
        JSONObject p=props(html);
        if(p.optBoolean("redirectToLogin"))throw new IllegalStateException("Il sito richiede un account per questo video.");
        if(p.getJSONObject("title").optLong("id")!=titleId)throw new IllegalStateException("Titolo video inatteso.");
        JSONObject episode=p.optJSONObject("episode");
        if(episodeId>0 && (episode==null || episode.optLong("id")!=episodeId))throw new IllegalStateException("Episodio video inatteso.");
        String value=p.optString("embedUrl","");
        if(value.isEmpty())throw new IllegalStateException("Video non disponibile.");
        URI u=origin.resolve(value);
        if(!"https".equals(u.getScheme()) || !origin.getHost().equals(u.getHost()) || u.getUserInfo()!=null || (u.getPort()!=-1&&u.getPort()!=443)
                || !u.getPath().equals("/it/iframe/"+titleId))throw new IllegalStateException("Pagina player non riconosciuta.");
        return u;
    }
    public static Player player(String html,URI page) {
        for(Element el:Jsoup.parse(html).select("iframe[src]")) {
            try {
                URI u=page.resolve(el.attr("src"));
                if("https".equals(u.getScheme()) && "vixcloud.cc".equals(u.getHost()) && u.getUserInfo()==null && (u.getPort()==-1||u.getPort()==443) && u.getPath().matches("/embed/[0-9]+"))return new Player(page,u);
            }catch(IllegalArgumentException ignored){}
        }
        throw new IllegalStateException("Il sito non ha restituito un player riconosciuto o il video richiede accesso.");
    }
}
