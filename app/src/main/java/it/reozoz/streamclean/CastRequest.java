package it.reozoz.streamclean;
import android.content.*;
import android.net.Uri;
import java.net.URI;
record CastRequest(String key,String title,URI origin,long titleId,long episodeId,Uri file,long position,PlaybackSnapshot source) {
 static CastRequest online(String title,URI origin,long id,long episode,PlaybackSnapshot source){return new CastRequest("online:"+id+":"+episode,title,origin,id,episode,null,0,source);}
 static CastRequest local(String key,String title,Uri file,long position){return new CastRequest("local:"+key,title,null,0,0,file,position,null);}
 Intent intent(Context c)throws Exception{
  Intent i=new Intent(c,CastRelayService.class).putExtra("key",key).putExtra("title",title).putExtra("position",position);
  if(file!=null)i.setData(file);else{i.putExtra("origin",origin.toString()).putExtra("titleId",titleId).putExtra("episodeId",episodeId);if(source!=null)i.putExtra("source",source.encode());}return i;
 }
}
