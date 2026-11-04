package it.reozoz.streamclean;

import java.net.URI;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;

/** The provider uses the embed's lang parameter to choose the HLS default audio. */
final class PlayerLanguageUrl {
    static URI audio(URI embed,String language){
        if(!PlayerPolicy.frame(embed.toString()))throw new IllegalArgumentException("Player non riconosciuto.");
        if("default".equals(language))return embed;
        if(language==null||!language.matches("[a-z]{2}"))throw new IllegalArgumentException("Lingua non valida.");
        // Preserve every signed/encoded query component except the audio-language parameter.
        String raw=embed.toASCIIString();int fragmentIndex=raw.indexOf('#');
        String fragment=fragmentIndex<0?"":raw.substring(fragmentIndex),head=fragmentIndex<0?raw:raw.substring(0,fragmentIndex);
        int queryIndex=head.indexOf('?');String base=queryIndex<0?head:head.substring(0,queryIndex);
        List<String> query=new ArrayList<>();
        if(embed.getRawQuery()!=null)for(String part:embed.getRawQuery().split("&",-1)){
            String key=part.split("=",2)[0];
            if(!"lang".equals(decode(key)))query.add(part);
        }
        query.add("lang="+language);
        return URI.create(base+"?"+String.join("&",query)+fragment);
    }
    private static String decode(String value){
        try{return URLDecoder.decode(value,"UTF-8");}
        catch(java.io.UnsupportedEncodingException impossible){throw new IllegalStateException(impossible);}
    }
}
