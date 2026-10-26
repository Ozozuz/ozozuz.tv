package it.reozoz.streamclean;
import java.net.URI;
import java.util.function.Function;
import java.util.regex.*;

/** Rewrites every declared HLS URI, including keys/audio/init maps, to opaque relay IDs. */
public final class CastPlaylist {
    private static final Pattern ATTRIBUTE=Pattern.compile("URI=\"([^\"]+)\"");
    public static String rewrite(URI source,String manifest,Function<URI,String> local)throws Exception{
        if(manifest.startsWith("\uFEFF"))manifest=manifest.substring(1);
        if(!manifest.trim().startsWith("#EXTM3U"))throw new IllegalArgumentException("Invalid HLS");
        StringBuilder result=new StringBuilder();for(String raw:manifest.split("\\r?\\n")){
            String l=raw.trim();if(l.isEmpty()){result.append('\n');continue;}
            if(l.startsWith("#")){Matcher m=ATTRIBUTE.matcher(raw);StringBuffer rewritten=new StringBuffer();while(m.find())m.appendReplacement(rewritten,Matcher.quoteReplacement("URI=\""+local.apply(HlsDownloadPlan.uri(source,m.group(1)))+"\""));m.appendTail(rewritten);result.append(rewritten);}
            else result.append(local.apply(HlsDownloadPlan.uri(source,l)));result.append('\n');
        }return result.toString();
    }
}
