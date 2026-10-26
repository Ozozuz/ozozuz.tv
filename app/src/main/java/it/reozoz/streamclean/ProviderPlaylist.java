package it.reozoz.streamclean;

import java.net.*;
import java.util.*;
import org.json.*;

/** Reads the provider's literal playlist configuration without executing its scripts. */
public final class ProviderPlaylist {
    public static URI parse(String html,URI embed)throws Exception {
        HlsDownloadPlan.uri(embed,embed.toString());
        if(!"vixcloud.cc".equals(embed.getHost())||!"https".equals(embed.getScheme())||!embed.getPath().matches("/embed/[0-9]+"))throw new IllegalArgumentException("Player non riconosciuto.");
        int start=html.indexOf("window.masterPlaylist");if(start<0)throw new IllegalArgumentException("Configurazione video assente.");
        start=html.indexOf('{',start);if(start<0)throw new IllegalArgumentException("Configurazione video non valida.");
        JSONObject master=(JSONObject)new JSONTokener(html.substring(start)).nextValue();URI root=URI.create(master.getString("url"));
        if(!"vixcloud.cc".equals(root.getHost())||root.getRawFragment()!=null||!new MediaAllowlist().playlist(root.toString())||!root.getPath().equals(embed.getPath().replace("/embed/","/playlist/")))throw new IllegalArgumentException("Playlist inattesa.");
        StringBuilder result=new StringBuilder(root.toString());JSONObject params=master.getJSONObject("params");
        for(Iterator<String> it=params.keys();it.hasNext();){String key=it.next();Object value=params.get(key);
            if(!key.matches("[A-Za-z0-9_-]{1,50}")||!(value instanceof String||value instanceof Number||value instanceof Boolean))throw new IllegalArgumentException("Parametro video non valido.");
            if(!value.toString().isEmpty()&&!Boolean.FALSE.equals(value)&&!(value instanceof Number&&((Number)value).doubleValue()==0))append(result,key,value.toString());
        }
        Map<String,String> query=new HashMap<>();if(embed.getRawQuery()!=null)for(String pair:embed.getRawQuery().split("&")){String[] p=pair.split("=",2);query.put(URLDecoder.decode(p[0],"UTF-8"),p.length>1?URLDecoder.decode(p[1],"UTF-8"):"");}
        if(!query.getOrDefault("canPlayFHD","").isEmpty())append(result,"h","1");
        if(!query.getOrDefault("scz","").isEmpty())append(result,"scz","1");
        append(result,"lang",query.getOrDefault("lang","en"));return URI.create(result.toString());
    }
    private static void append(StringBuilder target,String key,String value)throws Exception {
        target.append(target.indexOf("?")<0?'?':'&').append(URLEncoder.encode(key,"UTF-8")).append('=').append(URLEncoder.encode(value,"UTF-8").replace("+","%20"));
    }
}
