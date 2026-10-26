package it.reozoz.streamclean;
import java.net.URI;
import java.util.*;
import org.json.*;

/** Durable queue contains title/episode IDs only; each temporary stream is resolved just in time. */
public record SeasonDownloadPlan(URI origin,long titleId,String slug,String name,int season,List<Job> jobs) {
    public record Job(long id,int number,String name) {}
    public String key(){return "season:"+titleId+":"+season;}
    public String label(Job j){return name+" · S"+season+" E"+j.number();}
    public String episodeKey(Job j){return titleId+":"+j.id();}
    public static String filename(String value){String v=value.replaceAll("[^\\p{L}\\p{N} ._-]","_").trim();return (v.isEmpty()||v.matches("[. ]*")?"episodio":v.substring(0,Math.min(v.length(),150)))+".mp4";}
    public static SeasonDownloadPlan create(URI origin,TitleParser.Detail detail){
        if(!"tv".equals(detail.type())||detail.selectedSeason()<0)throw new IllegalArgumentException("Stagione non disponibile.");
        Map<Long,Job> jobs=new LinkedHashMap<>();for(TitleParser.Episode e:detail.episodes())if(e.available()&&e.id()>0)jobs.put(e.id(),new Job(e.id(),e.number(),e.name()));
        List<Job> sorted=new ArrayList<>(jobs.values());sorted.sort(Comparator.comparingInt(Job::number));
        return new SeasonDownloadPlan(HomeParser.origin(origin.toString()),detail.id(),detail.slug(),detail.name(),detail.selectedSeason(),sorted);
    }
    public String encode()throws JSONException{
        JSONArray a=new JSONArray();for(Job j:jobs)a.put(new JSONObject().put("id",j.id()).put("number",j.number()).put("name",j.name()));
        return new JSONObject().put("origin",origin.toString()).put("title",titleId).put("slug",slug).put("name",name).put("season",season).put("jobs",a).toString();
    }
    public static SeasonDownloadPlan decode(String value)throws Exception{
        JSONObject o=new JSONObject(value);long title=o.getLong("title");int season=o.getInt("season");JSONArray a=o.getJSONArray("jobs");
        if(title<=0||season<0||a.length()>1000)throw new IllegalArgumentException("Coda non valida.");
        List<Job> jobs=new ArrayList<>();Set<Long> ids=new HashSet<>();for(int i=0;i<a.length();i++){JSONObject j=a.getJSONObject(i);long id=j.getLong("id");if(id<=0||!ids.add(id))throw new IllegalArgumentException("Episodio non valido.");jobs.add(new Job(id,j.getInt("number"),j.getString("name")));}
        return new SeasonDownloadPlan(HomeParser.origin(o.getString("origin")),title,o.getString("slug"),o.getString("name"),season,jobs);
    }
}
