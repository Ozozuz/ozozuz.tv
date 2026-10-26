package it.reozoz.streamclean;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.provider.DocumentsContract;
import java.net.URI;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Single download worker; season queues persist IDs, never expiring stream tokens. */
public final class EpisodeDownloadService extends Service {
    private static final String CHANNEL="episode_downloads";
    private static final int NOTIFICATION=42;
    private static volatile String active="",message="";
    private static volatile int percent;
    private final AtomicBoolean cancelled=new AtomicBoolean();
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private String jobKey="",label="";
    private Uri destination;
    private PowerManager.WakeLock wakeLock;
    private int lastProgress=-2;
    private boolean seasonMode;
    static String activeKey(){return active;}
    static String progressText(){return message+(percent>0?" · "+percent+"%":"");}
    static SharedPreferences prefs(Context c){return c.getSharedPreferences("episode_downloads_v1",MODE_PRIVATE);}
    static String saved(Context c,String key){return prefs(c).getString("file:"+key,"");}
    static boolean exists(Context c,String key){String value=saved(c,key);if(value.isEmpty())return false;
        try(ParcelFileDescriptor f=c.getContentResolver().openFileDescriptor(Uri.parse(value),"r")){return f!=null;}catch(Exception e){return false;}}
    static void recover(Context c){
        if(!active.isEmpty())return;String old=prefs(c).getString("pending_uri","");
        if(!old.isEmpty()){delete(c,Uri.parse(old));prefs(c).edit().remove("pending_uri").apply();}
        ManagedDownloads.cleanup(c);EpisodeDownloadEngine.cleanupInterrupted(c);
    }
    private static void delete(Context c,Uri uri){if(uri!=null)try{ManagedDownloads.delete(c,uri);}catch(Exception ignored){}}
    private void check(){if(cancelled.get()||Thread.currentThread().isInterrupted())throw new CancellationException();}
    @Override public void onCreate(){super.onCreate();getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(CHANNEL,"Download puntate",NotificationManager.IMPORTANCE_LOW));}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null){stopSelf();return START_NOT_STICKY;}
        if("cancel".equals(intent.getAction())){cancelled.set(true);if(active.isEmpty())stopSelf();return START_NOT_STICKY;}
        if(!active.isEmpty())return START_NOT_STICKY;
        seasonMode="season".equals(intent.getAction());jobKey=intent.getStringExtra("key");label=intent.getStringExtra("label");
        if(jobKey==null||label==null){stopSelf();return START_NOT_STICKY;}
        active=jobKey;percent=0;message="Preparazione…";cancelled.set(false);lastProgress=-2;
        prefs(this).edit().remove("error:"+jobKey).apply();
        if(Build.VERSION.SDK_INT>=29)startForeground(NOTIFICATION,notification(),android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);else startForeground(NOTIFICATION,notification());
        worker.execute(()->{
            Intent completed=null;
            try{
                wakeLock=getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,getPackageName()+":episode-download");wakeLock.acquire(6*60*60*1000L);
                completed=seasonMode?season(intent):single(intent);
            }catch(Exception e){
                boolean stopped=cancelled.get()||e instanceof CancellationException;
                prefs(this).edit().putString("error:"+jobKey,stopped?"Download annullato. Gli episodi completati sono conservati.":"Download non riuscito: "+reason(e)).apply();
                if(stopped&&seasonMode)clearQueue();
            }finally{
                delete(this,destination);destination=null;prefs(this).edit().remove("pending_uri").apply();
                if(wakeLock!=null&&wakeLock.isHeld())wakeLock.release();final Intent open=completed;
                main.post(()->{stopForeground(STOP_FOREGROUND_REMOVE);
                    if(open!=null){PendingIntent action=PendingIntent.getActivity(this,jobKey.hashCode(),open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
                        getSystemService(NotificationManager.class).notify(43,new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download_done).setContentTitle(label).setContentText("Download terminato").setContentIntent(action).setAutoCancel(true).build());}
                    stopSelf(startId);active="";percent=0;
                });
            }
        });return START_NOT_STICKY;
    }
    private Intent single(Intent intent)throws Exception{
        PlaybackSnapshot source=PlaybackSnapshot.decode(intent.getStringExtra("source"));
        long title=intent.getLongExtra("title",0),episode=intent.getLongExtra("episode",0);int season=intent.getIntExtra("season",-1);
        Uri file=download(source,title,season,episode,jobKey,intent.getStringExtra("download_meta"),(p,m)->progress(p,m));
        return DownloadedVideoActivity.open(this,jobKey,label,file);
    }
    private Uri download(PlaybackSnapshot source,long title,int season,long episode,String key,String metadata,EpisodeDownloadEngine.Progress progress)throws Exception{
        Uri completed=ManagedDownloads.finished(this,title,season,episode);
        if(completed==null){
            ManagedDownloads.Output output=ManagedDownloads.prepare(this,title,season,episode);destination=output.uri();
            if(!prefs(this).edit().putString("pending_uri",destination.toString()).commit())throw new java.io.IOException("Impossibile salvare lo stato del download.");
            new EpisodeDownloadEngine(this,source,cancelled,progress).run(destination);check();
            completed=ManagedDownloads.complete(this,output);destination=completed;
        }
        save(key,completed,metadata);destination=null;return completed;
    }
    private Intent season(Intent intent)throws Exception{
        URI origin=HomeParser.origin(intent.getStringExtra("origin"));SeasonDownloadPlan plan;
        if(intent.getBooleanExtra("resume",false)){
            SeasonDownloadPlan old=SeasonDownloadPlan.decode(prefs(this).getString("queue_plan",""));
            plan=new SeasonDownloadPlan(origin,old.titleId(),old.slug(),old.name(),old.season(),old.jobs());
        }else{
            plan=SeasonDownloadPlan.create(origin,SafeHttp.detail(origin,intent.getLongExtra("title",0),intent.getStringExtra("slug"),intent.getIntExtra("season",-1)));
        }
        if(!jobKey.equals(plan.key()))throw new IllegalArgumentException("Stagione inattesa.");
        persist(plan);check();
        List<SeasonDownloadPlan.Job> jobs=new ArrayList<>(plan.jobs());int total=jobs.size(),done=0,skipped=0,failed=0;
        for(int i=0;i<total;i++){
            check();SeasonDownloadPlan.Job job=jobs.get(i);String key=plan.episodeKey(job),prefix=(i+1)+"/"+total+" · E"+job.number();
            progress(0,prefix+" · Preparazione");
            try{
                if(exists(this,key)){skipped++;}
                else{
                    PlaybackSnapshot source=DownloadSource.resolve(plan.origin(),plan.titleId(),job.id());check();
                    download(source,plan.titleId(),plan.season(),job.id(),key,DownloadLibrary.metadata(plan.titleId(),job.id(),plan.name(),plan.season(),job.number(),job.name()),(p,m)->progress(p,prefix+" · "+m));done++;
                }
            }catch(Exception e){check();failed++;prefs(this).edit().putString("error:"+key,"Download non riuscito: "+reason(e)).apply();}
            finally{delete(this,destination);destination=null;prefs(this).edit().remove("pending_uri").apply();}
            persist(new SeasonDownloadPlan(plan.origin(),plan.titleId(),plan.slug(),plan.name(),plan.season(),new ArrayList<>(jobs.subList(i+1,total))));
        }
        clearQueue();prefs(this).edit().putString("error:"+jobKey,"Stagione terminata: "+done+" scaricati, "+skipped+" già presenti, "+failed+" non riusciti.").apply();
        return new Intent(this,DownloadsActivity.class);
    }
    private void save(String key,Uri file,String metadata)throws java.io.IOException{
        android.content.SharedPreferences.Editor edit=prefs(this).edit().putString("file:"+key,file.toString()).remove("error:"+key).remove("pending_uri");if(metadata!=null)edit.putString("meta:"+key,metadata);
        if(!edit.commit())throw new java.io.IOException("Impossibile salvare il download nella libreria.");
    }
    private void persist(SeasonDownloadPlan plan)throws Exception{if(!prefs(this).edit().putString("queue_plan",plan.encode()).putString("queue_key",plan.key()).remove("queue_folder").commit())throw new java.io.IOException("Impossibile salvare la coda dei download.");}
    private void clearQueue(){prefs(this).edit().remove("queue_plan").remove("queue_key").remove("queue_folder").apply();}
    private static String reason(Exception e){String m=e.getMessage();return m==null?"errore video o rete.":m;}
    private void progress(int p,String text){percent=p;message=text;if(p!=lastProgress){lastProgress=p;getSystemService(NotificationManager.class).notify(NOTIFICATION,notification());}}
    private Notification notification(){
        PendingIntent cancel=PendingIntent.getService(this,1,new Intent(this,EpisodeDownloadService.class).setAction("cancel"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent open=PendingIntent.getActivity(this,2,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle(label).setContentText(progressText()).setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setProgress(100,percent,percent==0).addAction(new Notification.Action.Builder(null,"Annulla",cancel).build()).build();
    }
    @Override public void onDestroy(){cancelled.set(true);worker.shutdownNow();super.onDestroy();}
    @Override public void onTimeout(int startId,int type){cancelled.set(true);stopSelf();}
    @Override public IBinder onBind(Intent intent){return null;}
}
