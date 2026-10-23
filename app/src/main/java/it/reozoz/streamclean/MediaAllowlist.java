package it.reozoz.streamclean;

import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.*;

/** Per-playback resources declared by the provider's actual HLS playlists. */
public final class MediaAllowlist {
    private final Map<String,Boolean> resources=new ConcurrentHashMap<>();
    private static final Pattern ATTRIBUTE=Pattern.compile("(?:^|[, :])URI=\"([^\"]+)\"");
    private static String normalized(URI u) {
        if(!"https".equalsIgnoreCase(u.getScheme()) || u.getHost()==null || u.getUserInfo()!=null || (u.getPort()!=-1&&u.getPort()!=443))return null;
        try{return URI.create("https://"+u.getHost().toLowerCase(Locale.ROOT)+(u.getRawPath()==null||u.getRawPath().isEmpty()?"/":u.getRawPath())+(u.getRawQuery()==null?"":"?"+u.getRawQuery())).toASCIIString();}
        catch(Exception e){return null;}
    }
    public boolean allows(String value) {try{String key=normalized(URI.create(value));return key!=null&&resources.containsKey(key);}catch(Exception e){return false;}}
    public boolean playlist(String value) {
        try{
            URI u=URI.create(value);String key=normalized(u);if(key==null)return false;
            return ("vixcloud.cc".equalsIgnoreCase(u.getHost())&&u.getPath().matches("/playlist/[0-9]+")) || Boolean.TRUE.equals(resources.get(key));
        }catch(Exception e){return false;}
    }
    public void registerRedirect(URI from,URI to) {
        if(playlist(from.toString()))register(to,true);
    }
    private void register(URI u,boolean playlist) {
        String key=normalized(u);if(key==null)return;
        if(resources.size()<20000)resources.merge(key,playlist,(oldValue,newValue)->oldValue||newValue);
    }
    public void registerPlaylist(URI source,String text) {
        if(!playlist(source.toString()) || !text.replace("\uFEFF","").trim().startsWith("#EXTM3U"))return;
        boolean nextVariant=false;
        for(String raw:text.split("\\r?\\n")) {
            String line=raw.trim();if(line.isEmpty())continue;
            if(line.startsWith("#")) {
                boolean variant=line.startsWith("#EXT-X-MEDIA:")||line.startsWith("#EXT-X-I-FRAME-STREAM-INF:");
                Matcher m=ATTRIBUTE.matcher(line);
                while(m.find())try{register(source.resolve(m.group(1)),variant);}catch(IllegalArgumentException ignored){}
                if(line.startsWith("#EXT-X-STREAM-INF:"))nextVariant=true;
            }else{
                try{register(source.resolve(line),nextVariant || Optional.ofNullable(URI.create(line).getPath()).orElse("").endsWith(".m3u8"));}catch(IllegalArgumentException ignored){}
                nextVariant=false;
            }
        }
    }
}
