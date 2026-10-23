package it.reozoz.streamclean;

import android.content.*;

/** Stable catalogue IDs survive season switches and catalogue-domain changes. */
final class WatchedEpisodes {
    private final SharedPreferences prefs;
    WatchedEpisodes(Context c){prefs=c.getSharedPreferences("watched_episodes_v1",Context.MODE_PRIVATE);}
    static String key(long title,long episode){return title+":"+episode;}
    boolean contains(long title,long episode){return episode>0&&prefs.getBoolean(key(title,episode),false);}
    void set(long title,long episode,boolean value){if(title<=0||episode<=0)return;SharedPreferences.Editor e=prefs.edit();if(value)e.putBoolean(key(title,episode),true);else e.remove(key(title,episode));e.apply();}
}
