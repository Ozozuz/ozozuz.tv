package it.reozoz.streamclean;

import android.app.*;
import android.content.*;
import android.net.*;
import android.os.*;
import com.google.android.gms.cast.*;
import com.google.android.gms.cast.framework.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/** Keeps only the selected stream reachable on Wi-Fi while a real Cast session is active. */
public final class CastRelayService extends Service {
 interface Listener{void changed(String key,boolean success,String message);}
 private static final Set<Listener> listeners=new CopyOnWriteArraySet<>();
 private static volatile boolean preparing;
 private static volatile String loadedKey="";
 private static final String CHANNEL="cast_playback";
 private static final int NOTICE=57;
 private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private final Handler main=new Handler(Looper.getMainLooper());
 private volatile CastHttpServer relay;
 private volatile int generation;
 private String key="",title="";
 private CastContext context;
 private PowerManager.WakeLock wake;
 static void addListener(Listener l){listeners.add(l);}static void removeListener(Listener l){listeners.remove(l);}static boolean preparing(){return preparing;}static String loadedKey(){return loadedKey;}
 private static void emit(String key,boolean success,String message){for(Listener l:listeners)l.changed(key,success,message);}
 private final CastSessions sessions=new CastSessions(){public void onSessionEnded(CastSession s,int error){stopSelf();}public void onSessionResumeFailed(CastSession s,int error){stopSelf();}};
 @Override public void onCreate(){super.onCreate();getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(CHANNEL,"Trasmissione video",NotificationManager.IMPORTANCE_LOW));}
 @Override public int onStartCommand(Intent intent,int flags,int startId){
  if(intent==null){stopSelf();return START_NOT_STICKY;}
  if("stop".equals(intent.getAction())){if(context!=null)context.getSessionManager().endCurrentSession(true);stopSelf();return START_NOT_STICKY;}
  key=intent.getStringExtra("key");title=intent.getStringExtra("title");if(key==null||title==null){stopSelf();return START_NOT_STICKY;}
  int request=++generation;loadedKey="";preparing=true;emit(key,false,"");
  if(Build.VERSION.SDK_INT>=29)startForeground(NOTICE,notification(),android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);else startForeground(NOTICE,notification());
  final CastSession session;final InetAddress receiver;
  try{if(context==null){context=CastContext.getSharedInstance(this);context.getSessionManager().addSessionManagerListener(sessions,CastSession.class);}session=context.getSessionManager().getCurrentCastSession();
   if(session==null||!session.isConnected()||session.getCastDevice()==null)throw new IOException("Nessun dispositivo Cast collegato.");receiver=session.getCastDevice().getInetAddress();if(!(receiver instanceof Inet4Address))throw new IOException("Collega telefono e TV alla stessa rete Wi-Fi IPv4.");
   if(wake==null){wake=getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,getPackageName()+":cast");wake.setReferenceCounted(false);}wake.acquire(6*60*60*1000L);
  }catch(Exception e){fail(request,e.getMessage());return START_NOT_STICKY;}
  CastHttpServer old=relay;if(old!=null)old.close();relay=null;
  worker.execute(()->{
   CastHttpServer server=null;
   try{
    InetAddress address=wifiAddress(receiver);CastHttpServer.Endpoint endpoint;String path,mime;
    Uri file=intent.getData();
    if(file!=null){if(!"content".equals(file.getScheme()))throw new IOException("File locale non valido.");endpoint=local(file);path="video.mp4";mime="video/mp4";}
    else{PlaybackSnapshot snapshot=intent.hasExtra("source")?PlaybackSnapshot.decode(intent.getStringExtra("source")):DownloadSource.resolve(HomeParser.origin(intent.getStringExtra("origin")),intent.getLongExtra("titleId",0),intent.getLongExtra("episodeId",0));endpoint=new CastHlsSource(snapshot);path="master.m3u8";mime="application/vnd.apple.mpegurl";}
    if(request!=generation||Thread.currentThread().isInterrupted())return;
    server=new CastHttpServer(address,receiver,endpoint);if(endpoint instanceof CastHlsSource)((CastHlsSource)endpoint).server(server);
    final CastHttpServer ready=server;final String url=server.url(path),type=mime;
    main.post(()->{
     if(request!=generation){ready.close();return;}relay=ready;
     try{
      if(!session.isConnected()||session.getRemoteMediaClient()==null)throw new IOException("Dispositivo Cast disconnesso.");
      MediaMetadata metadata=new MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE);metadata.putString(MediaMetadata.KEY_TITLE,title);
      MediaInfo info=new MediaInfo.Builder(url).setContentType(type).setStreamType(MediaInfo.STREAM_TYPE_BUFFERED).setMetadata(metadata).build();
      session.getRemoteMediaClient().load(new MediaLoadRequestData.Builder().setMediaInfo(info).setAutoplay(true).setCurrentTime(Math.max(0,intent.getLongExtra("position",0))).build()).setResultCallback(result->{
       if(request!=generation)return;if(!result.getStatus().isSuccess()){fail(request,"Il dispositivo non ha accettato il video ("+result.getStatus().getStatusCode()+"). Controlla Wi-Fi e compatibilità del formato.");return;}
       preparing=false;loadedKey=key;getSystemService(NotificationManager.class).notify(NOTICE,notification());emit(key,true,"Riproduzione sulla TV avviata.");
      });
     }catch(Exception e){fail(request,e.getMessage());}
    });
   }catch(Exception e){if(server!=null)server.close();main.post(()->fail(request,e.getMessage()));}
  });return START_NOT_STICKY;
 }
 private InetAddress wifiAddress(InetAddress receiver)throws IOException{
  ConnectivityManager cm=getSystemService(ConnectivityManager.class);
  for(Network network:cm.getAllNetworks()){
   NetworkCapabilities caps=cm.getNetworkCapabilities(network);LinkProperties links=cm.getLinkProperties(network);if(caps==null||links==null||!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))continue;
   for(LinkAddress link:links.getLinkAddresses())if(link.getAddress() instanceof Inet4Address&&link.getAddress().isSiteLocalAddress()){
    byte[] a=link.getAddress().getAddress(),b=receiver.getAddress();int bits=link.getPrefixLength();boolean same=true;
    for(int i=0;i<4;i++){int n=Math.min(8,Math.max(0,bits-i*8)),mask=(255<<(8-n))&255;if((a[i]&mask)!=(b[i]&mask)){same=false;break;}}
    if(same)return link.getAddress();
   }
  }throw new IOException("Telefono e dispositivo Cast devono essere sulla stessa rete Wi-Fi; evita reti ospiti con isolamento client.");
 }
 private CastHttpServer.Endpoint local(Uri uri)throws Exception{
  final long size;try(ParcelFileDescriptor fd=getContentResolver().openFileDescriptor(uri,"r")){if(fd==null||fd.getStatSize()<=0)throw new IOException("File MP4 non disponibile o non locale.");size=fd.getStatSize();}
  return (path,range,head)->{
   if(!path.equals("video.mp4"))return new CastHttpServer.Response(404,"text/plain",0,Collections.emptyMap(),null);
   CastHttpServer.Range selected=CastHttpServer.Range.parse(range,size);Map<String,String> headers=new HashMap<>();headers.put("Accept-Ranges","bytes");if(selected.partial())headers.put("Content-Range","bytes "+selected.start()+"-"+selected.end()+"/"+size);
   InputStream body=null;if(!head){ParcelFileDescriptor fd=getContentResolver().openFileDescriptor(uri,"r");if(fd==null)throw new FileNotFoundException();ParcelFileDescriptor.AutoCloseInputStream input=new ParcelFileDescriptor.AutoCloseInputStream(fd);try{input.getChannel().position(selected.start());body=input;}catch(Exception e){input.close();throw e;}}
   return new CastHttpServer.Response(selected.partial()?206:200,"video/mp4",selected.length(),headers,body);
  };
 }
 private void fail(int request,String reason){if(request!=generation)return;preparing=false;emit(key,false,"Cast non riuscito: "+(reason==null?"errore di rete o riproduzione.":reason));stopSelf();}
 private Notification notification(){
  PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,CastPlaybackActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,CastRelayService.class).setAction("stop"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  return new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_media_play).setContentTitle(title).setContentText(preparing?"Preparazione trasmissione…":"Trasmissione attiva · telefono connesso al Wi-Fi").setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).addAction(new Notification.Action.Builder(null,"Interrompi",stop).build()).build();
 }
 @Override public void onDestroy(){generation++;preparing=false;loadedKey="";CastHttpServer old=relay;if(old!=null)old.close();relay=null;worker.shutdownNow();
  if(context!=null)try{context.getSessionManager().removeSessionManagerListener(sessions,CastSession.class);context.getSessionManager().endCurrentSession(true);}catch(RuntimeException ignored){}if(wake!=null&&wake.isHeld())wake.release();stopForeground(STOP_FOREGROUND_REMOVE);emit(key,false,"");super.onDestroy();}
 @Override public IBinder onBind(Intent i){return null;}
}
