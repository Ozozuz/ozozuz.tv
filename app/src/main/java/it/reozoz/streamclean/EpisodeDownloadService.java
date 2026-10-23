package it.reozoz.streamclean;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.provider.DocumentsContract;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** One explicit user-requested download at a time, kept alive while the app is backgrounded. */
public final class EpisodeDownloadService extends Service {
    private static final String CHANNEL="episode_downloads";
    private static final int NOTIFICATION=42;
    private static volatile String active="",message="";
    private static volatile int percent;
    private final AtomicBoolean cancelled=new AtomicBoolean();
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private Uri destination;
    private PowerManager.WakeLock wakeLock;
    private String jobKey="",label="";
    private int lastProgress=-2;
    static String activeKey(){return active;}
    static String progressText(){return percent>0?percent+"%":message;}
    static android.content.SharedPreferences prefs(Context c){return c.getSharedPreferences("episode_downloads_v1",MODE_PRIVATE);}
    static String saved(Context c,String key){return prefs(c).getString("file:"+key,"");}
    static void recover(Context c){
        if(!active.isEmpty())return;String old=prefs(c).getString("pending_uri","");if(!old.isEmpty()){delete(c,Uri.parse(old));prefs(c).edit().remove("pending_uri").apply();}
    }
    private static void delete(Context c,Uri uri){try{DocumentsContract.deleteDocument(c.getContentResolver(),uri);}catch(Exception ignored){}}
    @Override public void onCreate(){super.onCreate();getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(CHANNEL,"Download puntate",NotificationManager.IMPORTANCE_LOW));}
    @Override public int onStartCommand(Intent intent,int flags,int id){
        if(intent==null){stopSelf();return START_NOT_STICKY;}
        if("cancel".equals(intent.getAction())){cancelled.set(true);if(active.isEmpty())stopSelf();return START_NOT_STICKY;}
        if(!active.isEmpty())return START_NOT_STICKY;
        jobKey=intent.getStringExtra("key");label=intent.getStringExtra("label");destination=intent.getParcelableExtra("destination");String encoded=intent.getStringExtra("source");
        if(jobKey==null||label==null||destination==null||!"content".equals(destination.getScheme())){stopSelf();return START_NOT_STICKY;}
        active=jobKey;percent=0;message="Preparazione…";cancelled.set(false);
        prefs(this).edit().putString("pending_uri",destination.toString()).remove("error:"+jobKey).apply();
        if(Build.VERSION.SDK_INT>=29)startForeground(NOTIFICATION,notification(),android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);else startForeground(NOTIFICATION,notification());
        worker.execute(()->{
            boolean success=false;
            try{
                wakeLock=getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,getPackageName()+":episode-download");wakeLock.acquire(2*60*60*1000L);
                PlaybackSnapshot snapshot=PlaybackSnapshot.decode(encoded);new EpisodeDownloadEngine(this,snapshot,cancelled,(p,m)->{
                    percent=p;message=m;if(p!=lastProgress){lastProgress=p;getSystemService(NotificationManager.class).notify(NOTIFICATION,notification());}
                }).run(destination);
                if(cancelled.get())throw new CancellationException();
                prefs(this).edit().putString("file:"+jobKey,destination.toString()).remove("error:"+jobKey).apply();success=true;
            }catch(Exception e){prefs(this).edit().putString("error:"+jobKey,cancelled.get()?"Download annullato.":"Download non riuscito: "+(e.getMessage()==null?"errore video o rete.":e.getMessage())).apply();}
            finally{
                if(wakeLock!=null&&wakeLock.isHeld())wakeLock.release();if(!success)delete(this,destination);prefs(this).edit().remove("pending_uri").apply();active="";percent=0;
                stopForeground(STOP_FOREGROUND_REMOVE);
                if(success){PendingIntent open=PendingIntent.getActivity(this,jobKey.hashCode(),DownloadedVideoActivity.open(this,jobKey,label,destination),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
                    getSystemService(NotificationManager.class).notify(43,new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download_done).setContentTitle(label).setContentText("Download completato").setContentIntent(open).setAutoCancel(true).build());}
                stopSelf();
            }
        });return START_NOT_STICKY;
    }
    private Notification notification(){
        PendingIntent cancel=PendingIntent.getService(this,1,new Intent(this,EpisodeDownloadService.class).setAction("cancel"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent open=PendingIntent.getActivity(this,2,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle(label).setContentText(message).setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setProgress(100,percent,percent==0).addAction(new Notification.Action.Builder(null,"Annulla",cancel).build()).build();
    }
    @Override public void onDestroy(){cancelled.set(true);worker.shutdownNow();super.onDestroy();}
    @Override public void onTimeout(int startId,int type){cancelled.set(true);stopSelf();}
    @Override public IBinder onBind(Intent intent){return null;}
}
