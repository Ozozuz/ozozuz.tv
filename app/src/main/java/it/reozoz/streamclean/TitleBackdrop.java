package it.reozoz.streamclean;

import android.content.Context;
import android.graphics.*;
import android.util.LruCache;
import android.view.View;
import java.net.URI;
import java.util.Collections;
import java.util.concurrent.*;

/** Decorative, fixed backdrop behind native title and episode controls. */
final class TitleBackdrop extends View {
    private static final int BASE=Ui.BG;
    private static final LruCache<String,Bitmap> CACHE=new LruCache<String,Bitmap>(8*1024*1024){
        protected int sizeOf(String key,Bitmap value){return value.getByteCount();}
    };
    private final ExecutorService loader=Executors.newSingleThreadExecutor();
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Paint scrim=new Paint();
    private final RectF target=new RectF();
    private Bitmap art;
    private String current="";
    private volatile boolean closed;
    private int version;

    TitleBackdrop(Context context){
        super(context);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        ColorMatrix tones=new ColorMatrix();tones.setSaturation(.15f);paint.setColorFilter(new ColorMatrixColorFilter(tones));paint.setAlpha(110);
    }

    void show(URI origin,String filename){
        if(filename==null || !filename.matches("[a-zA-Z0-9_-]+\\.(webp|png|jpg|jpeg)"))return;
        String url="https://cdn."+origin.getHost()+"/images/"+filename;
        // Season changes retain the current artwork without reloading or flickering.
        if(url.equals(current))return;
        current=url;final int request=++version;
        Bitmap cached=CACHE.get(url);
        if(cached!=null){art=cached;invalidate();return;}
        loader.execute(()->{
            try{
                byte[] bytes=SafeHttp.get(URI.create(url),Collections.singleton("cdn."+origin.getHost()),3*1024*1024,true);
                BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
                BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);
                if(bounds.outWidth<=0||bounds.outHeight<=0||bounds.outWidth>12000||bounds.outHeight>12000)return;
                BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;
                while(bounds.outWidth/options.inSampleSize>1280 || bounds.outHeight/options.inSampleSize>1280)options.inSampleSize*=2;
                Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
                if(bitmap==null||closed)return;
                CACHE.put(url,bitmap);
                post(()->{if(!closed && request==version){art=bitmap;invalidate();}});
            }catch(Exception ignored){/* Decoration must never block browsing or playback. */}
        });
    }
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){
        super.onSizeChanged(w,h,oldw,oldh);
        scrim.setShader(new LinearGradient(0,0,0,Math.max(1,h),
            new int[]{0x460c0e10,0x920c0e10,0xe00c0e10,0xff0c0e10},new float[]{0f,.3f,.75f,1f},Shader.TileMode.CLAMP));
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);canvas.drawColor(BASE);
        if(art==null || getWidth()==0 || getHeight()==0)return;
        float scale=Math.max((float)getWidth()/art.getWidth(),(float)getHeight()/art.getHeight());
        float w=art.getWidth()*scale,h=art.getHeight()*scale;
        target.set((getWidth()-w)/2f,(getHeight()-h)/2f,(getWidth()+w)/2f,(getHeight()+h)/2f);
        canvas.drawBitmap(art,null,target,paint);

        canvas.drawRect(0,0,getWidth(),getHeight(),scrim);
    }
    void close(){closed=true;version++;loader.shutdownNow();art=null;}
}
