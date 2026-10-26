package it.reozoz.streamclean;

import java.net.URI;
import java.util.*;
import org.json.*;

/** Persist title identity and image filenames, never an obsolete catalogue host. */
public final class FavoriteCodec {
    static String posterName(String value) {
        try {String path=URI.create(value).getPath();String name=path.substring(path.lastIndexOf('/')+1);
            return name.matches("[a-zA-Z0-9_-]+\\.(webp|png|jpg|jpeg)")?name:"";
        } catch(Exception e){return "";}
    }
    public static String encode(Collection<HomeParser.Title> titles)throws JSONException {
        JSONArray array=new JSONArray();
        for(HomeParser.Title t:titles)if(t.id()>0)array.put(new JSONObject().put("id",t.id()).put("name",t.name()).put("type",t.type())
            .put("slug",t.slug()).put("plot",t.plot()).put("score",t.score()).put("poster",posterName(t.poster())));
        return array.toString();
    }
    public static List<HomeParser.Title> decode(String value,URI origin) {
        LinkedHashMap<Long,HomeParser.Title> items=new LinkedHashMap<>();
        try {JSONArray a=new JSONArray(value);for(int i=0;i<a.length();i++){
            JSONObject o=a.optJSONObject(i);if(o==null||o.optLong("id")<=0)continue;
            String poster=posterName(o.optString("poster")),type=o.optString("type");if(!Arrays.asList("movie","tv").contains(type))continue;
            items.put(o.optLong("id"),new HomeParser.Title(o.optLong("id"),o.optString("name"),type,o.optString("score"),o.optString("plot"),
                poster.isEmpty()?"":"https://cdn."+origin.getHost()+"/images/"+poster,o.optString("slug","title")));
        }}catch(JSONException ignored){}
        return new ArrayList<>(items.values());
    }
}
