package it.reozoz.streamclean;

import org.json.*;
import java.net.URI;
import java.util.*;

/** In-memory copy of a manifest already accepted by the current embedded player. */
record PlaybackSnapshot(URI initial,URI source,String manifest,Map<String,String> headers) {
    String encode()throws JSONException{
        return new JSONObject().put("initial",initial.toString()).put("source",source.toString()).put("manifest",manifest).put("headers",new JSONObject(headers)).toString();
    }
    static PlaybackSnapshot decode(String text)throws Exception{
        JSONObject o=new JSONObject(text);URI initial=URI.create(o.getString("initial")),source=URI.create(o.getString("source"));String manifest=o.getString("manifest");
        if(!"vixcloud.cc".equals(initial.getHost())||!new MediaAllowlist().playlist(initial.toString())||manifest.length()>256*1024)throw new IllegalArgumentException("Manifest non riconosciuto.");
        HlsDownloadPlan.uri(initial,source.toString());Map<String,String> headers=new HashMap<>();JSONObject h=o.optJSONObject("headers");
        if(h!=null)for(Iterator<String> i=h.keys();i.hasNext();){String k=i.next();if(Arrays.asList("user-agent","referer","origin","cookie").contains(k.toLowerCase(Locale.ROOT)))headers.put(k,h.getString(k));}
        return new PlaybackSnapshot(initial,source,manifest,Collections.unmodifiableMap(new HashMap<>(headers)));
    }
}
