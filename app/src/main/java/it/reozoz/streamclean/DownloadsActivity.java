package it.reozoz.streamclean;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

/** Offline library: no catalogue or provider requests are required to browse or play. */
public final class DownloadsActivity extends androidx.appcompat.app.AppCompatActivity {
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final List<Object> rows=new ArrayList<>();
    private TextView summary,progress;
    private Button cancel;
    private CastUi cast;
    private Adapter adapter;
    private int generation;
    private String previousJob="";
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener changes=(p,key)->{if(key!=null&&key.startsWith("file:"))handler.post(()->{if(!isDestroyed())reload();});};
    private final Runnable poll=new Runnable(){public void run(){String active=EpisodeDownloadService.activeKey();progress.setText(active.isEmpty()?"":EpisodeDownloadService.progressText());progress.setVisibility(active.isEmpty()?View.GONE:View.VISIBLE);cancel.setVisibility(active.isEmpty()?View.GONE:View.VISIBLE);if(!previousJob.isEmpty()&&active.isEmpty())reload();previousJob=active;handler.postDelayed(this,1000);}};
    @Override public void onCreate(Bundle state){
        super.onCreate(state);EpisodeDownloadService.recover(this);
        LinearLayout root=Ui.column(this);root.setBackgroundColor(Ui.BG);root.setPadding(dp(18),0,dp(18),0);root.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(dp(18),i.getSystemWindowInsetTop()+dp(12),dp(18),i.getSystemWindowInsetBottom());return i;});
        Button back=Ui.button(this,"< catalogo",false);Ui.navigation(back,false);back.setOnClickListener(v->finish());LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.addView(back,new LinearLayout.LayoutParams(0,dp(44),1));cast=new CastUi(this,null,null);cast.addTo(bar);root.addView(bar,Ui.margin(this,-1,44,0,14));
        root.addView(Ui.section(this,"download",23));summary=Ui.mono(this,"Caricamento…",11,Ui.MUTED);root.addView(summary,Ui.margin(this,-1,-2,10,12));
        LinearLayout current=new LinearLayout(this);current.setGravity(Gravity.CENTER_VERTICAL);progress=Ui.mono(this,"",11,Ui.ACCENT);progress.setMaxLines(2);current.addView(progress,new LinearLayout.LayoutParams(0,-2,1));
        cancel=Ui.button(this,"Annulla",false);cancel.setOnClickListener(v->startService(new Intent(this,EpisodeDownloadService.class).setAction("cancel")));current.addView(cancel,new LinearLayout.LayoutParams(-2,dp(44)));root.addView(current);
        ListView list=new ListView(this);list.setDivider(null);list.setBackgroundColor(Ui.BG);list.setVerticalScrollBarEnabled(false);adapter=new Adapter();list.setAdapter(adapter);root.addView(list,new LinearLayout.LayoutParams(-1,0,1));cast.mini(root);setContentView(root);root.requestApplyInsets();
    }
    private int dp(float n){return Ui.dp(this,n);}
    private void reload(){
        final int request=++generation;
        io.execute(()->{List<DownloadLibrary.Entry> entries=DownloadLibrary.list(this);runOnUiThread(()->{
            if(isDestroyed()||generation!=request)return;rows.clear();long bytes=0;int missing=0;String group="";
            for(DownloadLibrary.Entry e:entries){String key=e.titleId()+":"+e.season();if(!key.equals(group)){group=key;rows.add(e.title()+(e.season()<0?"":" · "+(e.season()==0?"Speciali":"Stagione "+e.season())));}rows.add(e);if(e.available())bytes+=e.bytes();else missing++;}
            summary.setText(entries.isEmpty()?"Nessun download. Gli episodi salvati appariranno qui.":entries.size()+" episodi · "+Formatter.formatFileSize(this,bytes)+(missing>0?" · "+missing+" file non disponibili":""));adapter.notifyDataSetChanged();
        });});
    }
    private void remove(DownloadLibrary.Entry e){
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(e.available()?"Eliminare il download?":"Rimuovere dalla lista?").setMessage(e.name()+"\n"+(e.available()?"Il file MP4 verrà eliminato. La spunta già vista viene conservata.":"Il file non è più accessibile dalla posizione salvata."))
            .setNegativeButton("Annulla",null).setPositiveButton(e.available()?"Elimina":"Rimuovi",(d,w)->io.execute(()->{
                try{DownloadLibrary.delete(this,e);runOnUiThread(()->{if(!isDestroyed())reload();});}
                catch(Exception failure){runOnUiThread(()->{if(!isDestroyed())Toast.makeText(this,"Eliminazione non riuscita: "+failure.getMessage(),Toast.LENGTH_LONG).show();});}
            })).create();dialog.show();dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Ui.ACCENT);
    }
    private final class Holder{TextView name,meta;Button play,remove;}
    private final class Adapter extends BaseAdapter {
        public int getCount(){return rows.size();}public Object getItem(int p){return rows.get(p);}public long getItemId(int p){return p;}
        public int getViewTypeCount(){return 2;}public int getItemViewType(int p){return rows.get(p) instanceof String?0:1;}
        public boolean isEnabled(int p){return false;}
        public View getView(int p,View view,ViewGroup parent){
            Object value=rows.get(p);if(value instanceof String){TextView label=view instanceof TextView?(TextView)view:Ui.mono(DownloadsActivity.this,"",13,Ui.ACCENT);label.setText((String)value);label.setPadding(0,dp(22),0,dp(12));return label;}
            Holder h;if(view==null||!(view.getTag() instanceof Holder)){
                h=new Holder();LinearLayout row=new LinearLayout(DownloadsActivity.this);row.setPadding(dp(12),dp(12),dp(8),dp(12));row.setGravity(Gravity.CENTER_VERTICAL);row.setBackground(Ui.surface(DownloadsActivity.this,Ui.SURFACE,Ui.BORDER,4));
                LinearLayout text=Ui.column(DownloadsActivity.this);h.name=Ui.text(DownloadsActivity.this,"",13,Ui.TEXT,true);h.name.setMaxLines(2);h.name.setEllipsize(TextUtils.TruncateAt.END);text.addView(h.name);h.meta=Ui.mono(DownloadsActivity.this,"",10,Ui.MUTED);text.addView(h.meta,Ui.margin(DownloadsActivity.this,-1,-2,7,0));row.addView(text,new LinearLayout.LayoutParams(0,-2,1));
                h.play=Ui.button(DownloadsActivity.this,"▶",false);row.addView(h.play,new LinearLayout.LayoutParams(dp(44),dp(44)));h.remove=Ui.button(DownloadsActivity.this,"×",false);h.remove.setTextSize(21);row.addView(h.remove,new LinearLayout.LayoutParams(dp(44),dp(44)));view=row;view.setTag(h);
            }else h=(Holder)view.getTag();
            DownloadLibrary.Entry e=(DownloadLibrary.Entry)value;boolean seen=new WatchedEpisodes(DownloadsActivity.this).contains(e.titleId(),e.episodeId());h.name.setText((e.number()>0?e.number()+" · ":"")+e.name());
            h.meta.setText((seen?"✓ già vista · ":"")+(e.available()?Formatter.formatFileSize(DownloadsActivity.this,e.bytes()):"File non disponibile"));h.play.setEnabled(e.available());h.play.setContentDescription("Riproduci offline: "+e.name());h.remove.setContentDescription("Elimina download: "+e.name());
            h.play.setOnClickListener(v->startActivity(DownloadedVideoActivity.open(DownloadsActivity.this,e.key(),e.title()+" · "+e.name(),e.file())));h.remove.setOnClickListener(v->remove(e));return view;
        }
    }
    @Override protected void onResume(){super.onResume();if(cast!=null)cast.resume();EpisodeDownloadService.prefs(this).registerOnSharedPreferenceChangeListener(changes);reload();handler.removeCallbacks(poll);handler.post(poll);}
    @Override protected void onPause(){if(cast!=null)cast.pause();EpisodeDownloadService.prefs(this).unregisterOnSharedPreferenceChangeListener(changes);handler.removeCallbacks(poll);super.onPause();}
    @Override protected void onDestroy(){generation++;handler.removeCallbacksAndMessages(null);io.shutdownNow();super.onDestroy();}
}
