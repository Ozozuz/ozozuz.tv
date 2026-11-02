package it.reozoz.streamclean;

import android.content.Context;
import android.content.SharedPreferences;

/** One last-opened episode per stable title ID, independent of host and watched flags. */
final class PlaybackHistory {
    record Entry(int season,long episode) {
        boolean hasSeason(TitleParser.Detail detail){
            return detail.seasons().stream().anyMatch(item->item.number()==season);
        }
        int indexIn(TitleParser.Detail detail){
            if(detail.selectedSeason()!=season)return -1;
            for(int i=0;i<detail.episodes().size();i++)if(detail.episodes().get(i).id()==episode)return i;
            return -1;
        }
    }
    private final SharedPreferences prefs;
    PlaybackHistory(Context context){prefs=context.getSharedPreferences("playback_history_v1",Context.MODE_PRIVATE);}
    Entry last(long title){
        String key=Long.toString(title);
        int season=prefs.getInt(key+":season",-1);long episode=prefs.getLong(key+":episode",0);
        return title>0&&season>=0&&episode>0?new Entry(season,episode):null;
    }
    void record(long title,int season,long episode){
        if(title<=0||season<0||episode<=0)return;
        String key=Long.toString(title);
        prefs.edit().putInt(key+":season",season).putLong(key+":episode",episode).apply();
    }
}
