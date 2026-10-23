package it.reozoz.streamclean;

import android.app.Activity;
import android.graphics.*;
import android.util.LruCache;
import android.widget.ImageView;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.util.*;
import java.util.concurrent.*;

/** Bounded CDN image loader, safe when episode rows are recycled during scrolling. */
final class ThumbnailLoader {
    private static final LruCache<String,Bitmap> CACHE=new LruCache<String,Bitmap>(8*1024*1024){
        protected int sizeOf(String k,Bitmap b){return b.getByteCount();}
    };
    private final Activity activity;
    private final URI origin;
    private final ThreadPoolExecutor executor=new ThreadPoolExecutor(3,3,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(96));
    private final ConcurrentHashMap<String,List<WeakReference<ImageView>>> pending=new ConcurrentHashMap<>();
    private volatile boolean closed;
    ThumbnailLoader(Activity activity,URI origin){this.activity=activity;this.origin=origin;}
    void bind(ImageView view,String filename){
        view.setImageDrawable(null);view.setTag(null);
        if(closed || filename==null || !filename.matches("[a-zA-Z0-9_-]+\\.(webp|png|jpg|jpeg)"))return;
        String url="https://cdn."+origin.getHost()+"/images/"+filename;view.setTag(url);
        Bitmap cached=CACHE.get(url);if(cached!=null){view.setImageBitmap(cached);return;}
        List<WeakReference<ImageView>> refs=new CopyOnWriteArrayList<>();refs.add(new WeakReference<>(view));
        List<WeakReference<ImageView>> existing=pending.putIfAbsent(url,refs);
        if(existing!=null){existing.add(new WeakReference<>(view));return;}
        try{executor.execute(()->{
            Bitmap result=null;
            try{
                byte[] data=SafeHttp.get(URI.create(url),Collections.singleton("cdn."+origin.getHost()),3*1024*1024,true);
                BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(data,0,data.length,bounds);
                if(bounds.outWidth>0&&bounds.outHeight>0&&bounds.outWidth<=12000&&bounds.outHeight<=12000){
                    BitmapFactory.Options opts=new BitmapFactory.Options();opts.inSampleSize=1;
                    while(bounds.outWidth/opts.inSampleSize>480||bounds.outHeight/opts.inSampleSize>320)opts.inSampleSize*=2;
                    result=BitmapFactory.decodeByteArray(data,0,data.length,opts);if(result!=null)CACHE.put(url,result);
                }
            }catch(Exception ignored){}
            final Bitmap bitmap=result;
            if(closed)return;
            activity.runOnUiThread(()->{
                List<WeakReference<ImageView>> targets=pending.remove(url);
                if(bitmap==null||targets==null||closed||activity.isDestroyed())return;
                for(WeakReference<ImageView> ref:targets){ImageView target=ref.get();if(target!=null&&url.equals(target.getTag()))target.setImageBitmap(bitmap);}
            });
        });}catch(RejectedExecutionException ignored){pending.remove(url);}
    }
    void close(){closed=true;pending.clear();executor.shutdownNow();}
}
