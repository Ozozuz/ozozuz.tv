package it.reozoz.streamclean;

import android.app.Activity;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

/** Native playback of the user's locally saved MP4, with the same watched marker. */
public final class DownloadedVideoActivity extends Activity {
    private VideoView video;
    private int position;
    private boolean resume=true;
    static Intent open(Context c,String key,String label,Uri uri){
        String[] ids=key.split(":");return new Intent(c,DownloadedVideoActivity.class).setData(uri).putExtra("title",Long.parseLong(ids[0])).putExtra("episode",Long.parseLong(ids[1])).putExtra("label",label).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);Uri uri=getIntent().getData();long title=getIntent().getLongExtra("title",0),episode=getIntent().getLongExtra("episode",0);
        if(uri==null||!"content".equals(uri.getScheme())||title<=0||episode<=0){finish();return;}
        if(state!=null)position=state.getInt("position");WatchedEpisodes watched=new WatchedEpisodes(this);
        LinearLayout root=Ui.column(this);root.setBackgroundColor(Ui.BG);root.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(0,i.getSystemWindowInsetTop(),0,i.getSystemWindowInsetBottom());return i;});
        LinearLayout bar=new LinearLayout(this);bar.setPadding(Ui.dp(this,12),0,Ui.dp(this,12),0);bar.setGravity(Gravity.CENTER_VERTICAL);
        Button back=Ui.button(this,"< indietro",false);Ui.navigation(back,false);back.setOnClickListener(v->finish());bar.addView(back,new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));
        CheckBox seen=new CheckBox(this);seen.setText("Già vista");seen.setTextColor(Ui.MUTED);seen.setButtonTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));seen.setChecked(watched.contains(title,episode));seen.setOnCheckedChangeListener((b,value)->watched.set(title,episode,value));bar.addView(seen);root.addView(bar);
        TextView label=Ui.mono(this,getIntent().getStringExtra("label"),12,Ui.TEXT);label.setMaxLines(2);label.setPadding(Ui.dp(this,16),Ui.dp(this,8),Ui.dp(this,16),Ui.dp(this,8));root.addView(label);
        FrameLayout box=new FrameLayout(this);video=new VideoView(this);box.addView(video,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));root.addView(box,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);root.requestApplyInsets();
        MediaController controls=new MediaController(this);controls.setAnchorView(video);video.setMediaController(controls);video.setVideoURI(uri);
        video.setOnPreparedListener(p->{if(position>0)video.seekTo(position);if(resume)video.start();});
        video.setOnErrorListener((p,what,extra)->{Toast.makeText(this,"Impossibile leggere il file MP4. Verifica che non sia stato spostato o eliminato.",Toast.LENGTH_LONG).show();finish();return true;});
    }
    @Override protected void onPause(){if(video!=null){position=video.getCurrentPosition();resume=video.isPlaying();video.pause();}super.onPause();}
    @Override protected void onResume(){super.onResume();if(video!=null&&resume)video.start();}
    @Override protected void onSaveInstanceState(Bundle b){if(video!=null)b.putInt("position",video.getCurrentPosition());super.onSaveInstanceState(b);}
    @Override protected void onDestroy(){if(video!=null)video.stopPlayback();super.onDestroy();}
}
