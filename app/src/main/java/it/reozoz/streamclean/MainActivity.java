package it.reozoz.streamclean;

import android.app.*;
import android.os.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import android.text.*;
import android.util.LruCache;
import java.net.URI;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private final int bg=Ui.BG,panel=Ui.SURFACE,muted=Ui.MUTED;
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private final ThreadPoolExecutor images = new ThreadPoolExecutor(3,3,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(180),new ThreadPoolExecutor.DiscardPolicy());
    private final LruCache<String,Bitmap> cache = new LruCache<>(16 * 1024 * 1024) {
        protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); }
    };
    private LinearLayout content,address,socialFooter;
    private Button domainToggle;
    private TextView status;
    private EditText url, search;
    private Button load;
    private final Map<String,Button> filterButtons=new LinkedHashMap<>();
    private HomeParser.Home home, searchHome;
    private URI currentOrigin;
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private final ThreadPoolExecutor searches = new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(1),new ThreadPoolExecutor.DiscardOldestPolicy());
    private volatile int searchVersion;
    private boolean searching;
    private String searchError = "";
    private String filter = "all";
    private volatile int generation;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        EpisodeDownloadService.recover(this);
        LinearLayout root = column(); root.setBackgroundColor(bg);
        root.setPadding(dp(18),dp(12),dp(18),0);
        root.setFocusableInTouchMode(true); root.requestFocus();
        // Respect Android 15's enforced edge-to-edge system bars.
        root.setOnApplyWindowInsetsListener((v,insets) -> {
            v.setPadding(dp(18), insets.getSystemWindowInsetTop()+dp(12), dp(18), insets.getSystemWindowInsetBottom()); return insets;
        });
        setContentView(root); root.requestApplyInsets();
        LinearLayout masthead=new LinearLayout(this);masthead.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand=Ui.text(this,"",18,Ui.TEXT,true);brand.setSingleLine(true);brand.setEllipsize(TextUtils.TruncateAt.END);
        SpannableString wordmark=new SpannableString("ozozuz.tv");wordmark.setSpan(new android.text.style.ForegroundColorSpan(Ui.ACCENT),7,9,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);brand.setText(wordmark);
        masthead.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        domainToggle=button("dominio");Ui.navigation(domainToggle,false);domainToggle.setContentDescription("Mostra o nascondi indirizzo del catalogo");
        masthead.addView(domainToggle,new LinearLayout.LayoutParams(-2,dp(44)));root.addView(masthead);
        root.addView(Ui.rule(this),Ui.margin(this,-1,1,4,16));
        address = new LinearLayout(this); address.setGravity(Gravity.CENTER_VERTICAL); address.setPadding(0,0,0,dp(12));
        url = input("URL HTTPS della home"); url.setSingleLine(true); url.setInputType(17); url.setTypeface(Typeface.MONOSPACE); url.setTextSize(12);
        url.setText(getPreferences(0).getString("origin","https://streamingcommunityz.build/"));
        address.addView(url,new LinearLayout.LayoutParams(0,dp(46),1));
        load = button("Carica"); LinearLayout.LayoutParams loadParams=new LinearLayout.LayoutParams(-2,dp(46)); loadParams.leftMargin=dp(8); address.addView(load,loadParams); root.addView(address);
        address.setVisibility(View.GONE);
        domainToggle.setOnClickListener(v->showAddress(address.getVisibility()!=View.VISIBLE));
        load.setOnClickListener(v -> fetch());
        search = input("Cerca film e serie TV…"); search.setSingleLine(true); search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH); root.addView(search,Ui.margin(this,-1,48,4,12));
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
            public void onTextChanged(CharSequence s,int start,int before,int count) { scheduleSearch(); }
            public void afterTextChanged(Editable e) {}
        });
        LinearLayout filters = new LinearLayout(this);
        for(String[] entry:new String[][]{{"all","tutti"},{"movie","film"},{"tv","serie tv"}}){
            Button b=button(entry[1]);b.setTag(entry[1]);b.setPadding(dp(8),0,dp(8),0);filterButtons.put(entry[0],b);
            b.setOnClickListener(v->{filter=entry[0];updateFilters();render();});
            LinearLayout.LayoutParams item=new LinearLayout.LayoutParams(0,dp(44),1);if(filterButtons.size()>1)item.leftMargin=dp(8);filters.addView(b,item);
        }
        updateFilters();
        root.addView(filters);
        root.addView(Ui.rule(this),Ui.margin(this,-1,1,12,0));
        status=Ui.mono(this,"",11,muted); status.setPadding(0,dp(6),0,dp(8)); root.addView(status);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setVerticalScrollBarEnabled(false);
        content = column(); scroll.addView(content); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        socialFooter=column();socialFooter.addView(Ui.rule(this),Ui.margin(this,-1,1,8,4));
        LinearLayout socialLinks=new LinearLayout(this);
        for(String[] entry:new String[][]{
            {"GitHub","https://github.com/Ozozuz","Profilo GitHub di Ozozuz"},
            {"X","https://x.com/0zozuz","Profilo X / Twitter di Ozozuz"},
            {"Repository","https://github.com/Ozozuz/ozozuz.tv","Repository ozozuz.tv su GitHub"}
        }){
            Button link=button(entry[0]);Ui.navigation(link,false);link.setPadding(dp(6),0,dp(6),0);link.setContentDescription(entry[2]+". Apre il browser");
            link.setOnClickListener(v->openProfile(entry[1]));socialLinks.addView(link,new LinearLayout.LayoutParams(0,dp(44),1));
        }
        socialFooter.addView(socialLinks);root.addView(socialFooter);
        fetch();
    }

    private void openProfile(String target){
        try{startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse(target)).addCategory(android.content.Intent.CATEGORY_BROWSABLE));}
        catch(android.content.ActivityNotFoundException e){Toast.makeText(this,"Nessun browser disponibile.",Toast.LENGTH_SHORT).show();}
    }
    private void showAddress(boolean open){
        address.setVisibility(open?View.VISIBLE:View.GONE);domainToggle.setText(open?"[dominio]":"dominio");Ui.navigation(domainToggle,open);
    }
    private void updateFilters(){
        for(Map.Entry<String,Button> item:filterButtons.entrySet()){
            boolean active=filter.equals(item.getKey());Button b=item.getValue();String label=(String)b.getTag();
            b.setText(active?"["+label+"]":label);Ui.navigation(b,active);
        }
    }
    private void fetch() {
        final URI origin;
        try { origin=HomeParser.origin(url.getText().toString()); }
        catch (Exception e) { showAddress(true); status.setText("URL non valido: usa https://dominio/"); return; }
        currentOrigin=origin;
        searchVersion++; searchHandler.removeCallbacksAndMessages(null); searches.getQueue().clear();
        searchHome=null; searching=false; searchError="";
        final int request=++generation;
        home=null; content.removeAllViews(); images.getQueue().clear(); load.setEnabled(false);
        status.setText("Caricamento del catalogo…");
        network.execute(()->{
            try {
                HomeParser.Home result=SafeHttp.home(origin);
                runOnUiThread(()->{
                    if(isDestroyed() || request!=generation) return;
                    home=result; load.setEnabled(true); url.setText(origin.toString());
                    getPreferences(0).edit().putString("origin",origin.toString()).apply();
                    status.setText(origin.getHost()); scheduleSearch();
                });
            } catch(Exception e) {
                runOnUiThread(()->{
                    if(isDestroyed() || request!=generation) return;
                    load.setEnabled(true); showAddress(true); status.setText("Caricamento non riuscito");
                    content.addView(text(e.getMessage()==null ? "Errore di rete. Riprova." : e.getMessage(),16,muted));
                    content.addView(text("Puoi cambiare dominio qui sopra e premere Carica.",14,muted));
                });
            }
        });
    }

    private void scheduleSearch() {
        int version=++searchVersion;
        searchHandler.removeCallbacksAndMessages(null); searches.getQueue().clear();
        searchHome=null; searchError="";
        String query=search.getText().toString().trim();
        searching=query.length()>=2 && currentOrigin!=null;
        render();
        if(!searching) return;
        final URI origin=currentOrigin; final int request=generation;
        searchHandler.postDelayed(()->searches.execute(()->{
            try {
                HomeParser.Home result=SafeHttp.search(origin,query);
                runOnUiThread(()->{
                    if(isDestroyed() || version!=searchVersion || request!=generation) return;
                    searchHome=result; searching=false; render();
                });
            } catch(Exception e) {
                runOnUiThread(()->{
                    if(isDestroyed() || version!=searchVersion || request!=generation) return;
                    searching=false; searchError="Ricerca non riuscita. "+(e.getMessage()==null?"Riprova.":e.getMessage()); render();
                });
            }
        }),400);
    }

    private boolean matches(HomeParser.Title t) {
        return filter.equals("all") || filter.equals(t.type());
    }

    private void render() {
        if(content==null) return;
        String query=search.getText().toString().trim();
        if(socialFooter!=null)socialFooter.setVisibility(query.isEmpty()?View.VISIBLE:View.GONE);
        HomeParser.Home displayed=query.isEmpty()?home:searchHome;
        content.removeAllViews(); images.getQueue().clear();
        if(!query.isEmpty() && query.length()<2) {content.addView(text("Scrivi almeno 2 caratteri per cercare nel catalogo.",16,muted)); return;}
        if(searching) {content.addView(text("Ricerca nel catalogo…",16,muted)); return;}
        if(!searchError.isEmpty()) {content.addView(text(searchError,16,muted)); return;}
        if(displayed==null) return;
        boolean found=false;
        if(displayed.hero()!=null && filter.equals("all") && search.getText().toString().trim().isEmpty()) {
            LinearLayout hero=column(); hero.setPadding(dp(20),dp(20),dp(20),dp(20)); hero.setBackground(Ui.surface(this,Ui.SURFACE,Ui.BORDER,5));
            hero.addView(Ui.section(this,"in evidenza",12));
            TextView heroName=Ui.text(this,displayed.hero().name(),23,Ui.TEXT,true); heroName.setMaxLines(2); heroName.setEllipsize(TextUtils.TruncateAt.END); hero.addView(heroName,Ui.margin(this,-1,-2,9,8));
            TextView plot=text(displayed.hero().plot(),14,muted); plot.setMaxLines(3); plot.setEllipsize(TextUtils.TruncateAt.END); plot.setLineSpacing(dp(3),1); hero.addView(plot);
            HomeParser.Title item=displayed.hero(); Button info=Ui.button(this,"Apri scheda >",true); info.setOnClickListener(v->details(item)); hero.addView(info,Ui.margin(this,-2,44,16,0)); content.addView(hero);
        }
        for(HomeParser.Section section:displayed.sections()) {
            List<HomeParser.Title> titles=new ArrayList<>();
            for(HomeParser.Title t:section.titles()) if(matches(t)) titles.add(t);
            if(titles.isEmpty()) continue; found=true;
            TextView heading=Ui.section(this,section.name(),18); heading.setPadding(0,dp(24),0,dp(12)); content.addView(heading);
            HorizontalScrollView carousel=new HorizontalScrollView(this); carousel.setHorizontalScrollBarEnabled(false);
            LinearLayout row=new LinearLayout(this);
            for(HomeParser.Title title:titles) row.addView(card(title));
            carousel.addView(row); content.addView(carousel);
        }
        if(!found) content.addView(text("Nessun risultato con i filtri selezionati.",16,muted));
        content.addView(Ui.rule(this),Ui.margin(this,-1,1,24,20));
    }

    private View card(HomeParser.Title title) {
        LinearLayout card=column(); card.setPadding(0,0,dp(12),0); card.setLayoutParams(new LinearLayout.LayoutParams(dp(146),-2));
        ImageView image=new ImageView(this); image.setBackground(shape(panel)); image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setClipToOutline(true); image.setContentDescription("Copertina: "+title.name()); card.addView(image,new LinearLayout.LayoutParams(-1,dp(196)));
        image(image,title.poster());
        TextView name=Ui.text(this,title.name(),13,Ui.TEXT,true); name.setMaxLines(2); name.setEllipsize(TextUtils.TruncateAt.END); name.setPadding(0,dp(8),0,0); card.addView(name);
        String kind=title.type().equals("tv") ? "Serie TV" : title.type().equals("movie") ? "Film" : "Titolo";
        card.addView(Ui.mono(this,kind+(title.score().trim().isEmpty()?"":" · "+title.score()),10,muted));
        card.setOnClickListener(v->details(title)); return card;
    }

    private void image(ImageView view,String uri) {
        if(uri.isEmpty()) return;
        view.setTag(uri); Bitmap existing=cache.get(uri);
        if(existing!=null) {view.setImageBitmap(existing);return;}
        final int request=generation; final String cdn="cdn."+currentOrigin.getHost();
        images.execute(()->{
            try {
                if(request!=generation || isDestroyed()) return;
                byte[] data=SafeHttp.get(URI.create(uri),Collections.singleton(cdn),3*1024*1024,true);
                BitmapFactory.Options bounds=new BitmapFactory.Options(); bounds.inJustDecodeBounds=true;
                BitmapFactory.decodeByteArray(data,0,data.length,bounds);
                if(bounds.outWidth<=0 || bounds.outHeight<=0 || bounds.outWidth>12000 || bounds.outHeight>12000) return;
                BitmapFactory.Options opts=new BitmapFactory.Options(); opts.inSampleSize=1;
                while(bounds.outWidth/opts.inSampleSize>600 || bounds.outHeight/opts.inSampleSize>900) opts.inSampleSize*=2;
                Bitmap bitmap=BitmapFactory.decodeByteArray(data,0,data.length,opts); if(bitmap==null)return;
                cache.put(uri,bitmap);
                runOnUiThread(()->{if(!isDestroyed() && request==generation && uri.equals(view.getTag()))view.setImageBitmap(bitmap);});
            } catch(Exception ignored) { /* A missing cover never prevents catalog browsing. */ }
        });
    }

    private void details(HomeParser.Title t) {
        if(currentOrigin==null)return;
        android.content.Intent intent=new android.content.Intent(this,TitleActivity.class);
        intent.putExtra("origin",currentOrigin.toString());intent.putExtra("id",t.id());intent.putExtra("slug",t.slug());
        startActivity(intent);
    }
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private TextView text(String value,int size,int color){return Ui.text(this,value,size,color,false);}
    private EditText input(String hint){EditText e=new EditText(this);Ui.field(e);e.setHint(hint);return e;}
    private Button button(String label){return Ui.button(this,label,false);}
    private GradientDrawable shape(int color){return Ui.surface(this,color,Ui.BORDER,4);}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    @Override protected void onDestroy(){generation++;searchVersion++;searchHandler.removeCallbacksAndMessages(null);searches.shutdownNow();network.shutdownNow();images.shutdownNow();super.onDestroy();}
}
