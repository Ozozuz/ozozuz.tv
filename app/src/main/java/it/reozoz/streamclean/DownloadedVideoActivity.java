package it.reozoz.streamclean;

import android.app.Activity;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

/** Native playback of the user's locally saved MP4, with the same watched marker. */
public final class DownloadedVideoActivity extends androidx.appcompat.app.AppCompatActivity {
    private VideoView video;
    private int position;
    private boolean resume=true;
    private PlaybackRotation rotation;
    private PlaybackWindow window;
    private LinearLayout root,bar;
    private TextView label;
    private ImageButton exitButton;
    private final android.os.Handler controlsHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable hideExit=()->{if(exitButton!=null)exitButton.setVisibility(View.GONE);};
    private CastUi cast;
    private ImageButton landscape;
    static Intent open(Context c,String key,String label,Uri uri){
        String[] ids=key.split(":");return new Intent(c,DownloadedVideoActivity.class).setData(uri).putExtra("title",Long.parseLong(ids[0])).putExtra("episode",Long.parseLong(ids[1])).putExtra("label",label).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);Uri uri=getIntent().getData();long title=getIntent().getLongExtra("title",0),episode=getIntent().getLongExtra("episode",0);
        if(uri==null||!"content".equals(uri.getScheme())||title<=0||episode<=0){finish();return;}
        if(state!=null){position=state.getInt("position");resume=state.getBoolean("resume",true);}
        rotation=new PlaybackRotation(this);window=new PlaybackWindow(this);window.awake(true);WatchedEpisodes watched=new WatchedEpisodes(this);
        root=Ui.column(this);root.setBackgroundColor(Ui.BG);androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root,(v,i)->{androidx.core.graphics.Insets inset=i.getInsets(rotation.forced()?androidx.core.view.WindowInsetsCompat.Type.displayCutout():androidx.core.view.WindowInsetsCompat.Type.systemBars());v.setPadding(inset.left,inset.top,inset.right,inset.bottom);return i;});
        bar=new LinearLayout(this);bar.setPadding(Ui.dp(this,12),0,Ui.dp(this,12),0);bar.setGravity(Gravity.CENTER_VERTICAL);
        Button back=Ui.button(this,"<",false);Ui.navigation(back,false);back.setContentDescription("Torna ai download");back.setOnClickListener(v->onBackPressed());bar.addView(back,new LinearLayout.LayoutParams(Ui.dp(this,44),Ui.dp(this,48)));
        CheckBox seen=new CheckBox(this);seen.setText("Già vista");seen.setTextColor(Ui.MUTED);seen.setButtonTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));seen.setChecked(watched.contains(title,episode));seen.setOnCheckedChangeListener((b,value)->watched.set(title,episode,value));bar.addView(seen,new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));
        landscape=Ui.iconButton(this,R.drawable.ic_landscape,"Attiva o disattiva orientamento orizzontale forzato");landscape.setOnClickListener(v->{setFullscreen(!rotation.forced());});bar.addView(landscape,new LinearLayout.LayoutParams(Ui.dp(this,44),Ui.dp(this,48)));
        cast=new CastUi(this,()->CastRequest.local(title+":"+episode,getIntent().getStringExtra("label"),uri,video==null?position:video.getCurrentPosition()),()->{if(video!=null){video.pause();resume=false;if(window!=null)window.awake(false);if(rotation.forced())setFullscreen(false);}});cast.addTo(bar);root.addView(bar);
        label=Ui.mono(this,getIntent().getStringExtra("label"),12,Ui.TEXT);label.setMaxLines(2);label.setPadding(Ui.dp(this,16),Ui.dp(this,8),Ui.dp(this,16),Ui.dp(this,8));root.addView(label);
        FrameLayout box=new FrameLayout(this);video=new VideoView(this);box.addView(video,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));root.addView(box,new LinearLayout.LayoutParams(-1,0,1));cast.mini(root);setContentView(root);root.requestApplyInsets();
        exitButton=Ui.iconButton(this,R.drawable.ic_back,"Esci dal fullscreen");exitButton.setBackground(Ui.ripple(this,0x99000000,0,4));exitButton.setVisibility(View.GONE);exitButton.setOnClickListener(v->setFullscreen(false));
        FrameLayout.LayoutParams exitParams=new FrameLayout.LayoutParams(Ui.dp(this,44),Ui.dp(this,44),Gravity.TOP|Gravity.START);exitParams.setMargins(Ui.dp(this,8),Ui.dp(this,8),0,0);box.addView(exitButton,exitParams);
        video.setOnTouchListener((v,event)->{if(rotation.forced()&&event.getActionMasked()==MotionEvent.ACTION_DOWN)showExit();return false;});
        MediaController controls=new MediaController(this);controls.setAnchorView(video);video.setMediaController(controls);video.setVideoURI(uri);
        video.setOnPreparedListener(p->{if(position>0)video.seekTo(position);if(resume)video.start();window.awake(true);p.setScreenOnWhilePlaying(true);});
        video.setOnCompletionListener(p->window.awake(false));
        video.setOnErrorListener((p,what,extra)->{Toast.makeText(this,"Impossibile leggere il file MP4. Verifica che non sia stato spostato o eliminato.",Toast.LENGTH_LONG).show();finish();return true;});
    }
    @Override protected void onPause(){if(window!=null)window.pause();if(cast!=null)cast.pause();if(video!=null){position=video.getCurrentPosition();resume=video.isPlaying();video.pause();}super.onPause();}
    @Override protected void onResume(){super.onResume();if(window!=null)window.resume();if(cast!=null)cast.resume();if(video!=null&&resume)video.start();}
    @Override protected void onSaveInstanceState(Bundle b){if(video!=null){b.putInt("position",video.getCurrentPosition());b.putBoolean("resume",video.isPlaying()||resume); }super.onSaveInstanceState(b);}
    private void showExit(){exitButton.setVisibility(View.VISIBLE);controlsHandler.removeCallbacks(hideExit);controlsHandler.postDelayed(hideExit,3000);}
    private void setFullscreen(boolean enabled){
        if(enabled)rotation.landscape();else rotation.restore();window.fullscreen(enabled);
        bar.setVisibility(enabled?View.GONE:View.VISIBLE);label.setVisibility(enabled?View.GONE:View.VISIBLE);cast.showMini(!enabled);
        if(enabled)showExit();else{controlsHandler.removeCallbacks(hideExit);exitButton.setVisibility(View.GONE);}
        Ui.selection(landscape,enabled);root.requestApplyInsets();
    }
    @Override public void onBackPressed(){if(rotation!=null&&rotation.forced()){setFullscreen(false);return;}super.onBackPressed();}
    @Override protected void onDestroy(){controlsHandler.removeCallbacksAndMessages(null);if(window!=null)window.close();if(rotation!=null)rotation.restore();if(video!=null)video.stopPlayback();super.onDestroy();}
}
