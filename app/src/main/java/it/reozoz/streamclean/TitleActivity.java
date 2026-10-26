package it.reozoz.streamclean;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import java.net.URI;
import java.util.*;
import java.util.concurrent.*;

public final class TitleActivity extends androidx.appcompat.app.AppCompatActivity {
    private URI origin;
    private long titleId;
    private String slug;
    private TitleParser.Detail detail;
    private int selectedEpisode=-1,detailVersion,playerVersion;
    private final ExecutorService pages=Executors.newFixedThreadPool(2);
    private LinearLayout root,controls,seasonBar,episodeActions;
    private WatchedEpisodes watched;
    private CheckBox watchedToggle;
    private Button download;
    private Button seasonDownload;
    private ImageButton favorite,landscape;
    private CastUi cast;
    private Favorites favorites;
    private Intent pendingDownload;
    private final Handler downloadHandler=new Handler(Looper.getMainLooper());
    private final Runnable downloadPoll=new Runnable(){public void run(){updateEpisodeActions();downloadHandler.postDelayed(this,1000);}};
    private TextView title,metadata,plot,status,nowPlaying,episodeHeading;
    private ListView episodes;
    private FrameLayout playerBox;
    private Button previous,next,retry,movie,seasonSelector;
    private EmbeddedPlayer player;
    private TitleBackdrop backdrop;
    private ThumbnailLoader thumbnails;
    private Dialog seasonDialog;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        try{origin=HomeParser.origin(getIntent().getStringExtra("origin"));titleId=getIntent().getLongExtra("id",0);slug=getIntent().getStringExtra("slug");if(titleId<=0||slug==null)throw new IllegalArgumentException();}
        catch(Exception e){finish();return;}
        watched=new WatchedEpisodes(this);favorites=new Favorites(this);EpisodeDownloadService.recover(this);
        if(state!=null)pendingDownload=state.getParcelable("pending_download");
        thumbnails=new ThumbnailLoader(this,origin);
        root=Ui.column(this);root.setPadding(dp(20),dp(12),dp(20),0);
        root.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(dp(20),i.getSystemWindowInsetTop()+dp(12),dp(20),i.getSystemWindowInsetBottom());return i;});
        FrameLayout scene=new FrameLayout(this);backdrop=new TitleBackdrop(this);scene.addView(backdrop,new FrameLayout.LayoutParams(-1,-1));scene.addView(root,new FrameLayout.LayoutParams(-1,-1));
        setContentView(scene);root.requestApplyInsets();
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        ImageButton back=Ui.iconButton(this,R.drawable.ic_back,"Torna al catalogo");back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(dp(44),dp(44)));
        top.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
        favorite=Ui.iconButton(this,R.drawable.ic_star_outline,"Aggiungi ai preferiti");favorite.setEnabled(false);top.addView(favorite,new LinearLayout.LayoutParams(dp(44),dp(44)));favorite.setOnClickListener(v->toggleFavorite());
        landscape=Ui.iconButton(this,R.drawable.ic_landscape,"Forza orientamento orizzontale");landscapeAvailable(false);landscape.setOnClickListener(v->{if(player!=null)player.forceLandscape();});top.addView(landscape,new LinearLayout.LayoutParams(dp(44),dp(44)));
        cast=new CastUi(this,this::castRequest,()->{playerVersion++;if(player!=null)player.clear();landscapeAvailable(false);setStatus("Riproduzione sulla TV · usa Cast per i controlli o Ricarica per tornare al telefono.");});cast.addTo(top);
        root.addView(top,Ui.margin(this,-1,44,0,14));
        title=Ui.text(this,"Caricamento…",25,Ui.TEXT,true);title.setMaxLines(2);root.addView(title);
        metadata=Ui.mono(this,"",11,Ui.MUTED);root.addView(metadata,Ui.margin(this,-1,-2,8,10));
        plot=Ui.text(this,"",13,Ui.MUTED,false);plot.setMaxLines(3);plot.setEllipsize(TextUtils.TruncateAt.END);plot.setLineSpacing(dp(2),1);root.addView(plot,Ui.margin(this,-1,-2,0,8));
        status=Ui.mono(this,"",11,Ui.MUTED);status.setMaxLines(2);status.setEllipsize(TextUtils.TruncateAt.END);status.setPadding(0,dp(4),0,dp(8));root.addView(status);
        playerBox=new FrameLayout(this);playerBox.setBackgroundColor(Color.BLACK);playerBox.setVisibility(View.GONE);root.addView(playerBox,new LinearLayout.LayoutParams(-1,dp(260)));
        root.addOnLayoutChangeListener((v,l,t,r,b,oldL,oldT,oldR,oldB)->{if(r-l!=oldR-oldL)resizePlayer();});
        nowPlaying=Ui.text(this,"",13,Ui.TEXT,true);nowPlaying.setMaxLines(2);nowPlaying.setEllipsize(TextUtils.TruncateAt.END);nowPlaying.setVisibility(View.GONE);root.addView(nowPlaying,Ui.margin(this,-1,-2,10,8));
        controls=new LinearLayout(this);previous=Ui.button(this,"< Prec.",false);retry=Ui.button(this,"Ricarica",false);next=Ui.button(this,"Succ. >",false);
        for(Button b:new Button[]{previous,retry,next}){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(42),1);if(b!=next)lp.rightMargin=dp(8);controls.addView(b,lp);}
        controls.setVisibility(View.GONE);root.addView(controls,Ui.margin(this,-1,42,0,10));
        previous.setOnClickListener(v->playEpisode(selectedEpisode-1));next.setOnClickListener(v->playEpisode(selectedEpisode+1));retry.setOnClickListener(v->playSelected());
        movie=Ui.button(this,"Guarda film >",true);movie.setVisibility(View.GONE);movie.setOnClickListener(v->playSelected());root.addView(movie,Ui.margin(this,-1,48,10,12));
        episodeActions=new LinearLayout(this);episodeActions.setGravity(Gravity.CENTER_VERTICAL);episodeActions.setVisibility(View.GONE);
        watchedToggle=new CheckBox(this);watchedToggle.setText("Già vista");watchedToggle.setTextSize(12);watchedToggle.setTextColor(Ui.MUTED);watchedToggle.setTypeface(android.graphics.Typeface.MONOSPACE);
        watchedToggle.setButtonTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));episodeActions.addView(watchedToggle,new LinearLayout.LayoutParams(0,dp(44),1));
        watchedToggle.setOnCheckedChangeListener((b,checked)->{if(detail!=null&&selectedEpisode>=0&&selectedEpisode<detail.episodes().size()){watched.set(titleId,detail.episodes().get(selectedEpisode).id(),checked);if(episodes.getAdapter() instanceof BaseAdapter)((BaseAdapter)episodes.getAdapter()).notifyDataSetChanged();}});
        download=Ui.button(this,"Scarica ↓",false);download.setOnClickListener(v->downloadEpisode());episodeActions.addView(download,new LinearLayout.LayoutParams(-2,dp(44)));root.addView(episodeActions,Ui.margin(this,-1,44,0,6));
        seasonBar=new LinearLayout(this);seasonBar.setGravity(Gravity.CENTER_VERTICAL);seasonBar.setVisibility(View.GONE);
        episodeHeading=Ui.section(this,"episodi",12);seasonBar.addView(episodeHeading,new LinearLayout.LayoutParams(0,-2,1));
        episodeHeading.setMaxLines(1);episodeHeading.setEllipsize(TextUtils.TruncateAt.END);
        seasonDownload=Ui.button(this,"↓ stagione",false);seasonDownload.setOnClickListener(v->downloadSeason());seasonBar.addView(seasonDownload,new LinearLayout.LayoutParams(-2,dp(44)));
        seasonSelector=Ui.button(this,"stagione  ▾",false);seasonSelector.setContentDescription("Scegli stagione");seasonSelector.setOnClickListener(v->chooseSeason());LinearLayout.LayoutParams seasonParams=new LinearLayout.LayoutParams(-2,dp(44));seasonParams.leftMargin=dp(8);seasonBar.addView(seasonSelector,seasonParams);
        root.addView(seasonBar,Ui.margin(this,-1,48,6,12));
        episodes=new ListView(this);episodes.setBackgroundColor(Color.TRANSPARENT);episodes.setCacheColorHint(Color.TRANSPARENT);
        episodes.setDivider(new ColorDrawable(Color.TRANSPARENT));episodes.setDividerHeight(dp(8));episodes.setSelector(new ColorDrawable(Color.TRANSPARENT));episodes.setVerticalScrollBarEnabled(false);
        episodes.setPadding(0,0,0,dp(20));episodes.setClipToPadding(false);root.addView(episodes,new LinearLayout.LayoutParams(-1,0,1));
        cast.mini(root);episodes.setOnItemClickListener((p,v,index,id)->playEpisode(index));fetch(getIntent().getIntExtra("season",-1));
    }
    private CastRequest castRequest(){
        if(detail==null||seasonSelector==null||!seasonSelector.isEnabled())return null;
        boolean tv="tv".equals(detail.type());if(tv&&(selectedEpisode<0||selectedEpisode>=detail.episodes().size()))return null;
        if(!tv&&!detail.movieAvailable())return null;
        long episode=tv?detail.episodes().get(selectedEpisode).id():0;
        String label=tv?detail.name()+" · S"+detail.selectedSeason()+" E"+detail.episodes().get(selectedEpisode).number():detail.name();
        return CastRequest.online(label,origin,titleId,episode,player==null?null:player.downloadSource());
    }
    private void landscapeAvailable(boolean available){landscape.setEnabled(available);landscape.setVisibility(available?View.VISIBLE:View.GONE);}
    private void setStatus(String value){status.setText(value);status.setVisibility(value.isEmpty()?View.GONE:View.VISIBLE);}
    private void fetch(int season){
        final int request=++detailVersion;playerVersion++;selectedEpisode=-1;landscapeAvailable(false);
        if(player!=null)player.clear();playerBox.setVisibility(View.GONE);controls.setVisibility(View.GONE);nowPlaying.setVisibility(View.GONE);nowPlaying.setText("");episodeActions.setVisibility(View.GONE);
        plot.setVisibility(View.VISIBLE);setStatus(season<0?"Caricamento del titolo…":"Caricamento episodi…");episodes.setAdapter(null);seasonSelector.setEnabled(false);seasonDownload.setEnabled(false);movie.setEnabled(false);
        pages.execute(()->{
            try{TitleParser.Detail result=SafeHttp.detail(origin,titleId,slug,season);runOnUiThread(()->{if(isDestroyed()||request!=detailVersion)return;detail=result;slug=result.slug();showDetail();});}
            catch(Exception e){runOnUiThread(()->{
                if(isDestroyed()||request!=detailVersion)return;setStatus("Caricamento non riuscito: "+e.getMessage());seasonSelector.setEnabled(true);
                Button b=Ui.button(this,"Riprova",false);b.setOnClickListener(v->{root.removeView(b);fetch(season);});root.addView(b,Ui.margin(this,-1,44,4,10));
            });}
        });
    }
    private void showDetail(){
        updateFavorite();favorite.setEnabled(true);
        backdrop.show(origin,detail.backdrop());title.setText(detail.name());plot.setText(detail.plot());plot.setVisibility(detail.plot().isEmpty()?View.GONE:View.VISIBLE);movie.setEnabled(true);seasonSelector.setEnabled(true);
        if(!"tv".equals(detail.type())){
            metadata.setText("film");seasonBar.setVisibility(View.GONE);episodes.setVisibility(View.GONE);movie.setVisibility(View.VISIBLE);movie.setEnabled(detail.movieAvailable());
            setStatus(detail.movieAvailable()?"":"Video non ancora disponibile sul sito.");return;
        }
        metadata.setText("serie tv · "+detail.seasons().size()+" stagioni");movie.setVisibility(View.GONE);seasonBar.setVisibility(View.VISIBLE);episodes.setVisibility(View.VISIBLE);
        seasonSelector.setText((detail.selectedSeason()==0?"Speciali":"stagione "+detail.selectedSeason())+"  ▾");
        seasonSelector.setContentDescription("Scegli stagione. Attuale: "+detail.selectedSeason());
        episodeHeading.setText(Ui.accentPrefix("// ","episodi · "+detail.episodes().size()));episodes.setAdapter(new EpisodeAdapter());
        setStatus(detail.episodes().isEmpty()?"Nessun episodio disponibile in questa stagione.":"");
        updateSeasonDownload();
    }
    private void updateFavorite(){if(favorite!=null){boolean saved=favorites.contains(titleId);favorite.setImageResource(saved?R.drawable.ic_star_filled:R.drawable.ic_star_outline);Ui.selection(favorite,saved);favorite.setContentDescription(saved?"Rimuovi dai preferiti":"Aggiungi ai preferiti");favorite.setTooltipText(favorite.getContentDescription());}}
    private void toggleFavorite(){
        if(detail==null)return;String poster=getIntent().getStringExtra("poster");if(poster==null||poster.isEmpty())poster="https://cdn."+origin.getHost()+"/images/"+detail.backdrop();
        favorites.toggle(new HomeParser.Title(titleId,detail.name(),detail.type(),getIntent().getStringExtra("score")==null?"":getIntent().getStringExtra("score"),detail.plot(),poster,slug),origin);updateFavorite();
        Toast.makeText(this,favorites.contains(titleId)?"Salvato nei preferiti":"Rimosso dai preferiti",Toast.LENGTH_SHORT).show();
    }
    private String seasonKey(){return detail==null?"":"season:"+titleId+":"+detail.selectedSeason();}
    private void updateSeasonDownload(){
        if(detail==null||!"tv".equals(detail.type())||!seasonSelector.isEnabled())return;
        String active=EpisodeDownloadService.activeKey(),key=seasonKey();boolean busy=!active.isEmpty(),same=active.equals(key);
        boolean resume=key.equals(EpisodeDownloadService.prefs(this).getString("queue_key",""));
        seasonDownload.setText(same?"Annulla":resume?"↓ riprendi":"↓ stagione");seasonDownload.setEnabled(same||!busy&&detail.episodes().stream().anyMatch(TitleParser.Episode::available));
        seasonDownload.setContentDescription(same?"Annulla download stagione. "+EpisodeDownloadService.progressText():resume?"Riprendi download stagione":"Scarica tutti gli episodi della stagione "+detail.selectedSeason());
        if(same)setStatus(EpisodeDownloadService.progressText());
        String result=EpisodeDownloadService.prefs(this).getString("error:"+key,"");if(!result.isEmpty()){EpisodeDownloadService.prefs(this).edit().remove("error:"+key).apply();setStatus(result);}
    }
    private void downloadSeason(){
        if(detail==null)return;String key=seasonKey(),active=EpisodeDownloadService.activeKey();
        if(!active.isEmpty()){if(active.equals(key))startService(new Intent(this,EpisodeDownloadService.class).setAction("cancel"));return;}
        boolean resume=key.equals(EpisodeDownloadService.prefs(this).getString("queue_key",""));
        pendingDownload=new Intent(this,EpisodeDownloadService.class).setAction("season").putExtra("key",key).putExtra("label",detail.name()+" · Stagione "+detail.selectedSeason())
            .putExtra("origin",origin.toString()).putExtra("title",titleId).putExtra("slug",slug).putExtra("season",detail.selectedSeason()).putExtra("resume",resume);
        startDownload();
    }
    private void startDownload(){
        try{
            if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},7002);
            startForegroundService(pendingDownload);setStatus("Download avviato · gestisci gli episodi dalla sezione Download.");
        }catch(Exception e){setStatus("Download non avviato: "+e.getMessage());}finally{pendingDownload=null;}
    }
    private void chooseSeason(){
        if(detail==null||detail.seasons().isEmpty()||!seasonSelector.isEnabled())return;
        if(seasonDialog!=null)seasonDialog.dismiss();
        final TitleParser.Detail selectedDetail=detail;
        Dialog dialog=new Dialog(this);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);seasonDialog=dialog;
        LinearLayout sheet=Ui.column(this);sheet.setPadding(dp(20),dp(20),dp(20),dp(24));
        android.graphics.drawable.GradientDrawable surface=Ui.surface(this,0xff111418,Ui.BORDER,0);
        surface.setCornerRadii(new float[]{dp(6),dp(6),dp(6),dp(6),0,0,0,0});sheet.setBackground(surface);
        LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(Ui.section(this,"stagioni",20),new LinearLayout.LayoutParams(0,-2,1));
        Button close=Ui.button(this,"×",false);Ui.navigation(close,false);close.setTextSize(22);close.setContentDescription("Chiudi selezione stagioni");close.setOnClickListener(v->dialog.dismiss());heading.addView(close,new LinearLayout.LayoutParams(dp(44),dp(44)));sheet.addView(heading);
        TextView caption=Ui.text(this,detail.name()+" · "+detail.seasons().size()+" stagioni",13,Ui.MUTED,false);caption.setMaxLines(1);caption.setEllipsize(TextUtils.TruncateAt.END);sheet.addView(caption,Ui.margin(this,-1,-2,6,18));
        int panelWidth=Math.min(getResources().getDisplayMetrics().widthPixels,dp(600));int columns=panelWidth>=dp(520)?6:4;
        ScrollView scroll=new ScrollView(this);scroll.setVerticalScrollBarEnabled(false);GridLayout grid=new GridLayout(this);grid.setColumnCount(columns);
        int selectedRow=0;
        for(int i=0;i<detail.seasons().size();i++){
            TitleParser.Season season=detail.seasons().get(i);boolean chosen=season.number()==detail.selectedSeason();
            if(chosen)selectedRow=i/columns;
            Button chip=Ui.button(this,season.number()==0?"Extra":String.format(Locale.ROOT,"%02d",season.number()),false);Ui.selection(chip,chosen);chip.setTextSize(16);
            chip.setContentDescription(season.toString()+(chosen?", selezionata":""));
            GridLayout.LayoutParams cell=new GridLayout.LayoutParams();cell.columnSpec=GridLayout.spec(i%columns,1f);cell.rowSpec=GridLayout.spec(i/columns);cell.width=0;cell.height=dp(52);cell.setMargins(dp(4),dp(4),dp(4),dp(4));grid.addView(chip,cell);
            chip.setOnClickListener(v->{dialog.dismiss();if(detail==selectedDetail&&season.number()!=detail.selectedSeason())fetch(season.number());});
        }
        int rows=(detail.seasons().size()+columns-1)/columns;int height=Math.min(dp(rows*60),(int)(getResources().getDisplayMetrics().heightPixels*.5f));
        scroll.addView(grid);sheet.addView(scroll,new LinearLayout.LayoutParams(-1,height));dialog.setContentView(sheet);dialog.setCanceledOnTouchOutside(true);
        Window window=dialog.getWindow();if(window!=null){window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.setDimAmount(.58f);window.setGravity(Gravity.BOTTOM);window.setNavigationBarColor(Ui.BG);}
        dialog.show();if(window!=null)window.setLayout(panelWidth,-2);
        final int row=selectedRow;scroll.post(()->scroll.scrollTo(0,Math.max(0,dp(row*60)-height/3)));
    }
    private final class EpisodeHolder {
        LinearLayout cell;
        ImageView image;
        TextView number,name,info,description,seen;
    }
    private final class EpisodeAdapter extends BaseAdapter {
        public int getCount(){return detail.episodes().size();}
        public TitleParser.Episode getItem(int p){return detail.episodes().get(p);}
        public long getItemId(int p){return getItem(p).id();}
        public boolean hasStableIds(){return true;}
        public View getView(int position,View convert,ViewGroup parent){
            EpisodeHolder h;
            if(convert==null){
                h=new EpisodeHolder();h.cell=new LinearLayout(TitleActivity.this);h.cell.setGravity(Gravity.CENTER_VERTICAL);h.cell.setPadding(dp(11),dp(12),dp(12),dp(12));h.cell.setMinimumHeight(dp(106));
                FrameLayout preview=new FrameLayout(TitleActivity.this);preview.setBackground(Ui.surface(TitleActivity.this,0xff171b1f,0,3));preview.setClipToOutline(true);
                int width=getResources().getDisplayMetrics().widthPixels<dp(360)?92:112;
                h.cell.addView(preview,new LinearLayout.LayoutParams(dp(width),dp(width*9f/16f)));
                h.image=new ImageView(TitleActivity.this);h.image.setScaleType(ImageView.ScaleType.CENTER_CROP);preview.addView(h.image,new FrameLayout.LayoutParams(-1,-1));
                TextView play=Ui.text(TitleActivity.this,"▶",11,Ui.TEXT,true);play.setGravity(Gravity.CENTER);play.setBackground(Ui.surface(TitleActivity.this,0xb80c0e10,0x3cffffff,3));FrameLayout.LayoutParams pp=new FrameLayout.LayoutParams(dp(28),dp(28),Gravity.CENTER);preview.addView(play,pp);
                h.number=Ui.text(TitleActivity.this,"",10,Color.WHITE,true);h.number.setPadding(dp(5),dp(3),dp(5),dp(3));h.number.setBackground(Ui.surface(TitleActivity.this,0xe00c0e10,0,2));FrameLayout.LayoutParams np=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.LEFT);np.setMargins(dp(5),dp(5),0,0);preview.addView(h.number,np);
                h.seen=Ui.text(TitleActivity.this,"✓",12,Ui.ACCENT,true);h.seen.setGravity(Gravity.CENTER);h.seen.setBackground(Ui.surface(TitleActivity.this,0xf00c0e10,Ui.ACCENT,3));FrameLayout.LayoutParams sp=new FrameLayout.LayoutParams(dp(22),dp(22),Gravity.RIGHT|Gravity.TOP);sp.setMargins(0,dp(5),dp(5),0);preview.addView(h.seen,sp);
                LinearLayout body=Ui.column(TitleActivity.this);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,-2,1);bp.leftMargin=dp(12);h.cell.addView(body,bp);
                h.info=Ui.mono(TitleActivity.this,"",10,Ui.MUTED);body.addView(h.info);
                h.name=Ui.text(TitleActivity.this,"",13,Ui.TEXT,true);h.name.setMaxLines(2);h.name.setEllipsize(TextUtils.TruncateAt.END);body.addView(h.name,Ui.margin(TitleActivity.this,-1,-2,5,4));
                h.description=Ui.text(TitleActivity.this,"",12,Ui.MUTED,false);h.description.setMaxLines(2);h.description.setEllipsize(TextUtils.TruncateAt.END);h.description.setLineSpacing(dp(1),1);body.addView(h.description);
                h.cell.setTag(h);convert=h.cell;
            }else h=(EpisodeHolder)convert.getTag();
            TitleParser.Episode ep=getItem(position);boolean chosen=position==selectedEpisode;
            h.cell.setBackground(Ui.ripple(TitleActivity.this,chosen?0xf0152019:Ui.SURFACE,chosen?0x9949f879:Ui.BORDER,4));h.cell.setAlpha(ep.available()?1f:.55f);
            h.number.setText(String.format(Locale.ROOT,"%02d",ep.number()));h.image.setContentDescription("Anteprima episodio "+ep.number());
            h.info.setText(chosen?"[in riproduzione]":"episodio "+String.format(Locale.ROOT,"%02d",ep.number()));h.info.setTextColor(chosen?Ui.ACCENT:Ui.MUTED);h.name.setText(ep.name());
            h.description.setText(ep.available()?ep.plot():"Non disponibile");h.description.setVisibility(h.description.getText().length()==0?View.GONE:View.VISIBLE);
            boolean seen=watched.contains(titleId,ep.id());h.seen.setVisibility(seen?View.VISIBLE:View.GONE);h.seen.setContentDescription("Episodio già visto");
            thumbnails.bind(h.image,ep.thumbnail());return convert;
        }
    }
    private void playEpisode(int index){
        if(detail==null||index<0||index>=detail.episodes().size())return;
        TitleParser.Episode ep=detail.episodes().get(index);if(!ep.available()){setStatus("Questo episodio non è disponibile sul sito.");return;}
        selectedEpisode=index;playSelected();
    }
    private void playSelected(){
        if(detail==null)return;boolean tv="tv".equals(detail.type());if(tv&&(selectedEpisode<0||selectedEpisode>=detail.episodes().size()))return;
        long episodeId=tv?detail.episodes().get(selectedEpisode).id():0;final int request=++playerVersion;
        if(player==null)player=new EmbeddedPlayer(this,playerBox,status);else player.clear();
        landscapeAvailable(true);plot.setVisibility(View.GONE);playerBox.setVisibility(View.VISIBLE);controls.setVisibility(View.VISIBLE);nowPlaying.setVisibility(View.VISIBLE);
        if(tv){resizePlayer();episodeActions.setVisibility(View.VISIBLE);updateEpisodeActions();}else episodeActions.setVisibility(View.GONE);
        if(!tv){episodes.setVisibility(View.GONE);movie.setVisibility(View.GONE);playerBox.setLayoutParams(new LinearLayout.LayoutParams(-1,0,1));}
        previous.setVisibility(tv?View.VISIBLE:View.GONE);next.setVisibility(tv?View.VISIBLE:View.GONE);previous.setEnabled(selectedEpisode>0);next.setEnabled(tv&&selectedEpisode<detail.episodes().size()-1);
        nowPlaying.setText(tv?"S"+detail.selectedSeason()+" · E"+detail.episodes().get(selectedEpisode).number()+"  "+detail.episodes().get(selectedEpisode).name():detail.name());
        if(episodes.getAdapter() instanceof BaseAdapter)((BaseAdapter)episodes.getAdapter()).notifyDataSetChanged();setStatus("Apertura player…");
        pages.execute(()->{try{TitleParser.Player source=SafeHttp.player(origin,titleId,episodeId);runOnUiThread(()->{if(!isDestroyed()&&request==playerVersion)player.load(source);});}
            catch(Exception e){runOnUiThread(()->{if(!isDestroyed()&&request==playerVersion)setStatus("Player non disponibile: "+e.getMessage()+". Premi Ricarica per riprovare.");});}});
    }
    private void updateEpisodeActions(){
        if(cast!=null)cast.refresh();updateSeasonDownload();
        if(download==null||detail==null||selectedEpisode<0||selectedEpisode>=detail.episodes().size())return;
        long episode=detail.episodes().get(selectedEpisode).id();boolean seen=watched.contains(titleId,episode);if(watchedToggle.isChecked()!=seen)watchedToggle.setChecked(seen);
        String key=WatchedEpisodes.key(titleId,episode),active=EpisodeDownloadService.activeKey();boolean busy=!active.isEmpty();
        download.setText(busy?(active.equals(key)?EpisodeDownloadService.progressText()+" · Annulla":"Download in corso"):EpisodeDownloadService.saved(this,key).isEmpty()?"Scarica ↓":"Apri MP4");download.setEnabled(!busy||active.equals(key));
        String error=EpisodeDownloadService.prefs(this).getString("error:"+key,"");if(!error.isEmpty()){EpisodeDownloadService.prefs(this).edit().remove("error:"+key).apply();setStatus(error);}
    }
    private void downloadEpisode(){
        if(detail==null||selectedEpisode<0||selectedEpisode>=detail.episodes().size())return;
        if(!EpisodeDownloadService.activeKey().isEmpty()){if(EpisodeDownloadService.activeKey().equals(WatchedEpisodes.key(titleId,detail.episodes().get(selectedEpisode).id()))){startService(new Intent(this,EpisodeDownloadService.class).setAction("cancel"));setStatus("Annullamento download…");}else Toast.makeText(this,"Un download è già in corso.",Toast.LENGTH_SHORT).show();return;}
        TitleParser.Episode episode=detail.episodes().get(selectedEpisode);String key=WatchedEpisodes.key(titleId,episode.id()),saved=EpisodeDownloadService.saved(this,key);
        if(!saved.isEmpty()){
            try(android.os.ParcelFileDescriptor file=getContentResolver().openFileDescriptor(android.net.Uri.parse(saved),"r")){if(file==null)throw new java.io.FileNotFoundException();startActivity(DownloadedVideoActivity.open(this,key,detail.name()+" · S"+detail.selectedSeason()+" E"+episode.number(),android.net.Uri.parse(saved)));return;}
            catch(Exception e){EpisodeDownloadService.prefs(this).edit().remove("file:"+key).apply();setStatus("File non più disponibile. Puoi scaricarlo di nuovo.");updateEpisodeActions();return;}
        }
        PlaybackSnapshot source=player==null?null:player.downloadSource();if(source==null){setStatus("Premi Play nel video, poi tocca Scarica.");return;}
        try{
            String label=detail.name()+" · S"+detail.selectedSeason()+" E"+episode.number();
            pendingDownload=new Intent(this,EpisodeDownloadService.class).putExtra("key",key).putExtra("label",label).putExtra("source",source.encode());
            pendingDownload.putExtra("download_meta",DownloadLibrary.metadata(titleId,episode.id(),detail.name(),detail.selectedSeason(),episode.number(),episode.name()));
            pendingDownload.putExtra("title",titleId).putExtra("episode",episode.id()).putExtra("season",detail.selectedSeason());
            startDownload();
        }catch(Exception e){pendingDownload=null;setStatus("Impossibile preparare il download: "+e.getMessage());}
    }
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);if(pendingDownload!=null)state.putParcelable("pending_download",pendingDownload);}
    private void resizePlayer(){
        // A 16:9 image plus control clearance prevents the provider's seek bar
        // from covering its central pause and +/-10 second touch targets.
        // Films retain the larger remaining-height layout used during playback.
        if(detail!=null&&!"tv".equals(detail.type())&&playerBox.getVisibility()==View.VISIBLE)return;
        int width=root.getWidth()-root.getPaddingLeft()-root.getPaddingRight();if(width<=0)return;
        int height=Math.max(dp(260),Math.round(width*9f/16f)+dp(56));
        LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)playerBox.getLayoutParams();
        if(lp.height!=height||lp.weight!=0){lp.height=height;lp.weight=0;playerBox.setLayoutParams(lp);}
    }
    private int dp(float v){return Ui.dp(this,v);}
    @Override public void onBackPressed(){
        if(player!=null&&player.exitFullscreen())return;
        if(playerBox.getVisibility()==View.VISIBLE){landscapeAvailable(false);playerVersion++;if(player!=null)player.clear();playerBox.setVisibility(View.GONE);controls.setVisibility(View.GONE);nowPlaying.setVisibility(View.GONE);episodeActions.setVisibility(View.GONE);plot.setVisibility(View.VISIBLE);
            if(detail!=null&&!"tv".equals(detail.type()))movie.setVisibility(View.VISIBLE);setStatus("");return;}
        super.onBackPressed();
    }
    @Override protected void onPause(){if(cast!=null)cast.pause();downloadHandler.removeCallbacks(downloadPoll);if(player!=null)player.pause();super.onPause();}
    @Override protected void onResume(){super.onResume();if(cast!=null)cast.resume();if(player!=null)player.resume();if(favorites!=null)updateFavorite();if(episodes!=null&&episodes.getAdapter() instanceof BaseAdapter)((BaseAdapter)episodes.getAdapter()).notifyDataSetChanged();downloadHandler.removeCallbacks(downloadPoll);downloadHandler.post(downloadPoll);}
    @Override protected void onDestroy(){detailVersion++;playerVersion++;downloadHandler.removeCallbacksAndMessages(null);if(seasonDialog!=null)seasonDialog.dismiss();pages.shutdownNow();if(thumbnails!=null)thumbnails.close();if(backdrop!=null)backdrop.close();if(player!=null)player.destroy();super.onDestroy();}
}
