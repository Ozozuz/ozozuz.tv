package it.reozoz.streamclean;

import android.content.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.*;
import java.util.*;
import java.util.regex.*;
import org.json.*;

/** Indexes private app downloads and legacy SAF documents for offline navigation, including legacy 0.7 downloads. */
final class DownloadLibrary {
    record Entry(String key,long titleId,long episodeId,String title,int season,int number,String name,Uri file,long bytes,boolean available) {}
    static String metadata(long title,long episode,String titleName,int season,int number,String name){
        try{return new JSONObject().put("title",title).put("episode",episode).put("titleName",titleName).put("season",season).put("number",number).put("name",name).toString();}
        catch(JSONException e){throw new IllegalArgumentException(e);}
    }
    static List<Entry> list(Context c){
        List<Entry> result=new ArrayList<>();android.content.SharedPreferences prefs=EpisodeDownloadService.prefs(c);
        for(Map.Entry<String,?> item:prefs.getAll().entrySet()){
            if(!item.getKey().matches("file:[1-9][0-9]*:[1-9][0-9]*")||!(item.getValue() instanceof String))continue;
            String key=item.getKey().substring(5);String[] ids=key.split(":");
            try{
                long title=Long.parseLong(ids[0]),episode=Long.parseLong(ids[1]);Uri uri=Uri.parse((String)item.getValue());if(!"content".equals(uri.getScheme()))continue;
                long bytes=0;boolean available=false;String display="";
                try(ParcelFileDescriptor f=c.getContentResolver().openFileDescriptor(uri,"r")){if(f!=null){available=true;bytes=Math.max(0,f.getStatSize());}}catch(Exception ignored){}
                try(android.database.Cursor cursor=c.getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE},null,null,null)){
                    if(cursor!=null&&cursor.moveToFirst()){display=Objects.toString(cursor.getString(0),"");if(!cursor.isNull(1))bytes=Math.max(0,cursor.getLong(1));}
                }catch(Exception ignored){}
                DownloadMetadata meta=DownloadMetadata.parse(prefs.getString("meta:"+key,"{}"),display,title,episode);
                result.add(new Entry(key,title,episode,meta.title(),meta.season(),meta.number(),meta.name(),uri,bytes,available));
            }catch(Exception ignored){}
        }
        result.sort(Comparator.comparing(Entry::title,String.CASE_INSENSITIVE_ORDER).thenComparingLong(Entry::titleId).thenComparingInt(Entry::season).thenComparingInt(Entry::number).thenComparingLong(Entry::episodeId));return result;
    }
    static void delete(Context c,Entry entry)throws Exception{
        if(entry.available()&&!ManagedDownloads.delete(c,entry.file()))throw new java.io.IOException("Il file non può essere eliminato da questa posizione.");
        EpisodeDownloadService.prefs(c).edit().remove("file:"+entry.key()).remove("meta:"+entry.key()).remove("error:"+entry.key()).apply();
    }
}
