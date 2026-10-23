package it.reozoz.streamclean;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import java.io.ByteArrayInputStream;

final class EmbeddedPlayer {
    private final Activity activity;
    private final FrameLayout container;
    private final TextView status;
    private final WebView web;
    private View fullscreen;
    private WebChromeClient.CustomViewCallback fullscreenCallback;
    private volatile String embed="";
    private volatile MediaAllowlist media=new MediaAllowlist();
    private volatile PlaybackSnapshot downloadSource;

    @SuppressLint("SetJavaScriptEnabled")
    EmbeddedPlayer(Activity activity,FrameLayout container,TextView status) {
        this.activity=activity;this.container=container;this.status=status;
        web=new WebView(activity);web.setBackgroundColor(Color.BLACK);
        WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);s.setAllowContentAccess(false);
        s.setAllowFileAccessFromFileURLs(false);s.setAllowUniversalAccessFromFileURLs(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setJavaScriptCanOpenWindowsAutomatically(false);s.setSupportMultipleWindows(true);
        s.setGeolocationEnabled(false);s.setMediaPlaybackRequiresUserGesture(true);
        s.setSafeBrowsingEnabled(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
        web.setDownloadListener((u,a,c,m,l)->status.setText("Download esterni bloccati."));
        web.setWebViewClient(new WebViewClient(){
            public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return r.isForMainFrame() || !PlayerPolicy.frame(r.getUrl().toString());}
            public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){
                String url=r.getUrl().toString();MediaAllowlist scope=media;
                if(scope.playlist(url) && "GET".equals(r.getMethod())) {
                    try {
                        PlaylistHttp.Response response=PlaylistHttp.get(java.net.URI.create(url),r.getRequestHeaders(),scope);
                        if(scope==media&&response.status()==200&&"vixcloud.cc".equals(r.getUrl().getHost())&&r.getUrl().getPath().matches("/playlist/[0-9]+")&&r.getUrl().getPath().equals(java.net.URI.create(embed).getPath().replace("/embed/","/playlist/")))
                            downloadSource=new PlaybackSnapshot(java.net.URI.create(url),response.source(),new String(response.body(),java.nio.charset.StandardCharsets.UTF_8),java.util.Collections.unmodifiableMap(new java.util.HashMap<>(r.getRequestHeaders())));
                        return new WebResourceResponse(response.mime(),"UTF-8",response.status(),response.reason(),response.headers(),new ByteArrayInputStream(response.body()));
                    }catch(Exception e){
                        activity.runOnUiThread(()->status.setText("Errore durante il caricamento della playlist. Premi Ricarica player."));
                        return new WebResourceResponse("text/plain","UTF-8",502,"Playlist unavailable",java.util.Collections.emptyMap(),new ByteArrayInputStream(new byte[0]));
                    }
                }
                if(PlayerPolicy.resource(url) || scope.allows(url))return null;
                return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));
            }
            public void onReceivedSslError(WebView v,SslErrorHandler h,android.net.http.SslError e){h.cancel();activity.runOnUiThread(()->status.setText("Connessione HTTPS del player non valida."));}
            public void onReceivedHttpError(WebView v,WebResourceRequest r,WebResourceResponse response){
                if(r.getUrl().toString().equals(embed))activity.runOnUiThread(()->status.setText("Player HTTP "+response.getStatusCode()+". Premi Ricarica player per un nuovo link."));
            }
            public void onReceivedError(WebView v,WebResourceRequest r,WebResourceError e){
                if(r.isForMainFrame() || r.getUrl().toString().equals(embed))activity.runOnUiThread(()->status.setText("Errore del player: "+e.getDescription()));
            }
        });
        web.setWebChromeClient(new WebChromeClient(){
            public boolean onCreateWindow(WebView v,boolean d,boolean gesture,Message result){return false;}
            public void onPermissionRequest(PermissionRequest request){request.deny();}
            public void onGeolocationPermissionsShowPrompt(String origin,GeolocationPermissions.Callback c){c.invoke(origin,false,false);}
            public boolean onShowFileChooser(WebView v,ValueCallback<Uri[]> callback,FileChooserParams params){callback.onReceiveValue(null);return true;}
            public void onShowCustomView(View view,CustomViewCallback callback){
                if(fullscreen!=null){callback.onCustomViewHidden();return;}
                fullscreen=view;fullscreenCallback=callback;
                ((FrameLayout)activity.getWindow().getDecorView()).addView(view,new FrameLayout.LayoutParams(-1,-1));
                activity.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            }
            public void onHideCustomView(){exitFullscreen();}
        });
        container.addView(web,new FrameLayout.LayoutParams(-1,-1));
    }
    void load(TitleParser.Player source) {
        media=new MediaAllowlist();downloadSource=null;embed=source.embed().toString();
        String html="<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'><meta http-equiv='Content-Security-Policy' content=\"default-src 'none'; frame-src https://vixcloud.cc; style-src 'unsafe-inline'\"><style>html,body,iframe{margin:0;width:100%;height:100%;border:0;background:#000;overflow:hidden}</style></head><body><iframe src=\""+android.text.TextUtils.htmlEncode(embed)+"\" allow='autoplay; fullscreen; picture-in-picture' allowfullscreen></iframe></body></html>";
        web.loadDataWithBaseURL(source.page().toString(),html,"text/html","UTF-8",null);
        status.setText("Premi Play nel video · finestre esterne bloccate");
    }
    PlaybackSnapshot downloadSource(){return downloadSource;}
    void clear(){downloadSource=null;media=new MediaAllowlist();exitFullscreen();web.stopLoading();web.loadUrl("about:blank");embed="";}
    boolean exitFullscreen(){
        if(fullscreen==null)return false;
        ((FrameLayout)activity.getWindow().getDecorView()).removeView(fullscreen);fullscreen=null;
        activity.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        if(fullscreenCallback!=null){WebChromeClient.CustomViewCallback c=fullscreenCallback;fullscreenCallback=null;c.onCustomViewHidden();}
        return true;
    }
    void pause(){web.onPause();}
    void resume(){web.onResume();}
    void destroy(){exitFullscreen();container.removeView(web);web.stopLoading();web.destroy();}
}
