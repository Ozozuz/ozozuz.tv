package it.reozoz.streamclean;
import android.content.*;
import java.net.URI;
import java.util.*;

final class Favorites {
    private final SharedPreferences prefs;
    private String cached;
    private final Set<Long> ids=new HashSet<>();
    Favorites(Context c){prefs=c.getSharedPreferences("favorites_v1",Context.MODE_PRIVATE);}
    List<HomeParser.Title> all(URI origin){return FavoriteCodec.decode(prefs.getString("titles","[]"),origin);}
    boolean contains(long id){String value=prefs.getString("titles","[]");if(!value.equals(cached)){cached=value;ids.clear();for(HomeParser.Title t:FavoriteCodec.decode(value,URI.create("https://catalog.invalid/")))ids.add(t.id());}return ids.contains(id);}
    void toggle(HomeParser.Title title,URI origin){
        List<HomeParser.Title> items=all(origin);boolean removed=items.removeIf(t->t.id()==title.id());if(!removed)items.add(0,title);
        try{prefs.edit().putString("titles",FavoriteCodec.encode(items)).apply();}catch(org.json.JSONException e){throw new IllegalStateException(e);}
    }
}
