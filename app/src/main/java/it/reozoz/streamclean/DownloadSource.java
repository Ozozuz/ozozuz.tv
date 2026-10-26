package it.reozoz.streamclean;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class DownloadSource {
    static PlaybackSnapshot resolve(URI origin,long title,long episode)throws Exception {
        TitleParser.Player player=SafeHttp.player(origin,title,episode);
        Map<String,String> headers=new HashMap<>();
        headers.put("User-Agent","Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36");
        headers.put("Referer",player.embed().toString());headers.put("Origin","https://vixcloud.cc");
        String html=new String(SafeHttp.get(player.embed(),Collections.singleton("vixcloud.cc"),1024*1024,false,headers),StandardCharsets.UTF_8);
        URI initial=ProviderPlaylist.parse(html,player.embed());MediaAllowlist scope=new MediaAllowlist();
        PlaylistHttp.Response r=PlaylistHttp.get(initial,headers,scope);
        if(r.status()!=200)throw new java.io.IOException("Playlist HTTP "+r.status()+". Riprova più tardi.");
        String manifest=new String(r.body(),StandardCharsets.UTF_8);HlsDownloadPlan.select(r.source(),manifest);
        return new PlaybackSnapshot(initial,r.source(),manifest,headers);
    }
}
