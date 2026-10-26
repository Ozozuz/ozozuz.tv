package it.reozoz.streamclean;
import org.json.*;
import java.util.regex.*;

/** Metadata migration is independent from Android and does not require the catalogue. */
public record DownloadMetadata(String title,int season,int number,String name) {
    public static DownloadMetadata parse(String saved,String display,long titleId,long episodeId){
        if(display==null)display="";JSONObject meta;try{meta=new JSONObject(saved);}catch(Exception e){meta=new JSONObject();}
        String title=meta.optString("titleName","Titolo "+titleId),name=meta.optString("name",display.isEmpty()?"Episodio "+episodeId:display);
        int season=meta.optInt("season",-1),number=meta.optInt("number",0);
        if(season<0){Matcher m=Pattern.compile("^(.+) - S([0-9]+)E([0-9]+) - (.+)\\.mp4$").matcher(display);if(m.matches())try{title=m.group(1);season=Integer.parseInt(m.group(2));number=Integer.parseInt(m.group(3));name=m.group(4);}catch(NumberFormatException ignored){season=-1;number=0;}}
        return new DownloadMetadata(title,season,number,name);
    }
}
