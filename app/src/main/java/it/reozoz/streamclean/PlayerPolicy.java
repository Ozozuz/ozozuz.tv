package it.reozoz.streamclean;

import java.net.URI;
import java.util.*;

/** Resource policy for the embedded player, independent from the native catalogue. */
public final class PlayerPolicy {
    private static final Set<String> STATIC= Collections.singleton("vixcloud.cc");
    public static boolean resource(String value) {
        try {
            URI u=URI.create(value);String scheme=u.getScheme(),host=u.getHost();
            if("data".equals(scheme))return value.startsWith("data:image/") || value.startsWith("data:font/");
            if(!"https".equals(scheme) || host==null || u.getUserInfo()!=null || (u.getPort()!=-1&&u.getPort()!=443))return false;
            host=host.toLowerCase(Locale.ROOT);
            if(host.equals("analytics.vixcloud.cc"))return false;
            if(STATIC.contains(host))return true;
            String path=Optional.ofNullable(u.getPath()).orElse("").toLowerCase(Locale.ROOT);
            if(!host.equals("vix-content.net") && !host.endsWith(".vix-content.net"))return false;
            // Only observed media CDN hosts and media resource paths are accepted.
            return path.matches(".*\\.(m3u8|mpd|ts|m4s|mp4|webm|aac|m4a|vtt|srt|key|webp|jpg|jpeg|png)$");
        }catch(IllegalArgumentException e){return false;}
    }
    public static boolean frame(String value) {
        try {URI u=URI.create(value);return "https".equals(u.getScheme()) && "vixcloud.cc".equals(u.getHost()) && u.getUserInfo()==null && (u.getPort()==-1||u.getPort()==443) && u.getPath().matches("/embed/[0-9]+");}
        catch(IllegalArgumentException e){return false;}
    }
}
