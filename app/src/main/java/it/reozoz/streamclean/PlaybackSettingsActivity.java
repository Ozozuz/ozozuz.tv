package it.reozoz.streamclean;

import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;
import java.util.ArrayList;
import java.util.List;

public final class PlaybackSettingsActivity extends androidx.appcompat.app.AppCompatActivity {
    private PlaybackPreferences preferences;
    private Button audio,subtitles;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);preferences=new PlaybackPreferences(this);
        LinearLayout root=Ui.column(this);root.setBackgroundColor(Ui.BG);
        root.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(Ui.dp(this,20),i.getSystemWindowInsetTop()+Ui.dp(this,12),Ui.dp(this,20),i.getSystemWindowInsetBottom());return i;});
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        ImageButton back=Ui.iconButton(this,R.drawable.ic_back,"Torna al catalogo");back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(Ui.dp(this,44),Ui.dp(this,44)));
        top.addView(Ui.text(this,"Impostazioni",22,Ui.TEXT,true),new LinearLayout.LayoutParams(0,-2,1));root.addView(top);
        root.addView(Ui.rule(this),Ui.margin(this,-1,1,16,24));root.addView(Ui.section(this,"lingue del player",18));
        TextView description=Ui.text(this,"Scegli come iniziare film ed episodi nel player online.",15,Ui.MUTED,false);root.addView(description,Ui.margin(this,-1,-2,12,24));
        root.addView(Ui.text(this,"Lingua audio preferita",14,Ui.TEXT,true));audio=Ui.button(this,"",false);audio.setOnClickListener(v->choose(false));root.addView(audio,Ui.margin(this,-1,52,10,24));
        root.addView(Ui.text(this,"Sottotitoli preferiti",14,Ui.TEXT,true));subtitles=Ui.button(this,"",false);subtitles.setOnClickListener(v->choose(true));root.addView(subtitles,Ui.margin(this,-1,52,10,24));
        TextView hint=Ui.text(this,"Se la lingua scelta non è disponibile, resta la selezione del video. Puoi sempre cambiare audio e sottotitoli dai controlli del player. Le modifiche valgono dal prossimo video aperto.",14,Ui.MUTED,false);hint.setLineSpacing(Ui.dp(this,4),1);root.addView(hint);
        if(!EmbeddedPlayer.supportsLanguageDefaults())root.addView(Ui.text(this,"Per la selezione automatica aggiorna Android System WebView sul dispositivo.",14,Ui.ACCENT,false),Ui.margin(this,-1,-2,20,0));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(root);setContentView(scroll);root.requestApplyInsets();update();
    }
    private void update(){
        audio.setText(PlaybackPreferences.label(preferences.audio())+"  ▾");audio.setContentDescription("Lingua audio preferita: "+PlaybackPreferences.label(preferences.audio()));
        subtitles.setText(PlaybackPreferences.label(preferences.subtitles())+"  ▾");subtitles.setContentDescription("Sottotitoli preferiti: "+PlaybackPreferences.label(preferences.subtitles()));
    }
    private void choose(boolean caption){
        List<PlaybackPreferences.Language> values=new ArrayList<>();values.add(new PlaybackPreferences.Language(PlaybackPreferences.DEFAULT,"Predefinito del video"));
        if(caption)values.add(new PlaybackPreferences.Language(PlaybackPreferences.OFF,"Disattivati"));values.addAll(PlaybackPreferences.LANGUAGES);
        String selected=caption?preferences.subtitles():preferences.audio();int current=0;
        for(int i=0;i<values.size();i++)if(values.get(i).code().equals(selected))current=i;
        String[] labels=values.stream().map(PlaybackPreferences.Language::label).toArray(String[]::new);
        new androidx.appcompat.app.AlertDialog.Builder(this).setTitle(caption?"Sottotitoli preferiti":"Lingua audio preferita")
            .setSingleChoiceItems(labels,current,(dialog,index)->{if(caption)preferences.subtitles(values.get(index).code());else preferences.audio(values.get(index).code());update();dialog.dismiss();})
            .setNegativeButton("Annulla",null).show();
    }
}
