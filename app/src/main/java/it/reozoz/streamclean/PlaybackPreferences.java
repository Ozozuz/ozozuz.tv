package it.reozoz.streamclean;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.json.JSONObject;

/** Global online-player defaults; independent of title, domain, and watched history. */
final class PlaybackPreferences {
    record Language(String code,String label) {}
    static final String DEFAULT="default",OFF="off";
    static final List<Language> LANGUAGES=List.of(
        new Language("en","Inglese"),new Language("it","Italiano"),new Language("fr","Francese"),
        new Language("de","Tedesco"),new Language("es","Spagnolo"),new Language("pt","Portoghese"),
        new Language("ja","Giapponese"),new Language("ko","Coreano"),new Language("zh","Cinese"),
        new Language("ru","Russo"),new Language("ar","Arabo"));
    private final SharedPreferences prefs;
    PlaybackPreferences(Context c){prefs=c.getSharedPreferences("playback_preferences_v1",Context.MODE_PRIVATE);}
    private static boolean language(String code){return LANGUAGES.stream().anyMatch(item->item.code().equals(code));}
    String audio(){String value=prefs.getString("audio","en");return DEFAULT.equals(value)||language(value)?value:"en";}
    String subtitles(){String value=prefs.getString("subtitles","it");return DEFAULT.equals(value)||OFF.equals(value)||language(value)?value:"it";}
    void audio(String code){if(DEFAULT.equals(code)||language(code))prefs.edit().putString("audio",code).apply();}
    void subtitles(String code){if(DEFAULT.equals(code)||OFF.equals(code)||language(code))prefs.edit().putString("subtitles",code).apply();}
    static String label(String code){
        if(DEFAULT.equals(code))return "Predefinito del video";if(OFF.equals(code))return "Disattivati";
        return LANGUAGES.stream().filter(item->item.code().equals(code)).map(Language::label).findFirst().orElse("Predefinito del video");
    }
    String script(Context context)throws IOException{
        String json="{\"audio\":"+JSONObject.quote(audio())+",\"subtitles\":"+JSONObject.quote(subtitles())+"}";
        try(var in=context.getAssets().open("player-preferences.js")){
            java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[4096];int count;
            while((count=in.read(buffer))!=-1)out.write(buffer,0,count);
            return new String(out.toByteArray(),StandardCharsets.UTF_8).replace("__OZOZUZ_PREFERENCES__",json);
        }
    }
}
