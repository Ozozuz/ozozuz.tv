package it.reozoz.streamclean;
import android.content.*;
import android.os.Build;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.mediarouter.app.MediaRouteButton;
import com.google.android.gms.cast.framework.*;
import com.google.android.gms.cast.framework.media.widget.MiniControllerFragment;
import java.util.concurrent.*;
import java.util.function.*;

/** SDK device picker and controls, with an explicit send-current-episode action. */
final class CastUi implements CastRelayService.Listener {
 private static final Executor BOOT=Executors.newSingleThreadExecutor(r->new Thread(r,"cast-init"));
 private final AppCompatActivity activity;
 private final Supplier<CastRequest> source;
 private final Runnable loaded;
 private final MediaRouteButton route;
 private final Button send;
 private CastContext context;
 private boolean resumed,registered,miniInflated,miniAllowed=true;
 private FrameLayout mini;
 private final CastSessions sessions=new CastSessions(){
  public void onSessionStarted(CastSession s,String id){refresh();if(source!=null&&source.get()!=null)send();}
  public void onSessionResumed(CastSession s,boolean suspended){refresh();}
  public void onSessionEnded(CastSession s,int error){refresh();}
  public void onSessionStartFailed(CastSession s,int error){refresh();Toast.makeText(activity,"Collegamento Cast non riuscito ("+error+").",Toast.LENGTH_LONG).show();}
 };
 CastUi(AppCompatActivity activity,Supplier<CastRequest> source,Runnable loaded){
  this.activity=activity;this.source=source;this.loaded=loaded;route=new MediaRouteButton(activity);route.setAlwaysVisible(true);route.setContentDescription("Scegli dispositivo Google Cast o apri i controlli TV");route.setEnabled(false);
  route.setOnLongClickListener(v->{try{activity.startActivity(new Intent(Settings.ACTION_CAST_SETTINGS));}catch(ActivityNotFoundException e){Toast.makeText(activity,"Trasmissione schermo non disponibile su questo dispositivo.",Toast.LENGTH_LONG).show();}return true;});
  send=Ui.button(activity,"TV ▶",false);send.setContentDescription("Riproduci il video selezionato sul dispositivo Cast collegato");send.setEnabled(false);send.setVisibility(View.GONE);send.setPadding(Ui.dp(activity,6),0,Ui.dp(activity,6),0);send.setOnClickListener(v->send());
  try{CastContext.getSharedInstance(activity,BOOT).addOnSuccessListener(androidx.core.content.ContextCompat.getMainExecutor(activity),c->{if(activity.isDestroyed())return;context=c;CastButtonFactory.setUpMediaRouteButton(activity.getApplicationContext(),route);route.setEnabled(true);if(resumed)register();}).addOnFailureListener(androidx.core.content.ContextCompat.getMainExecutor(activity),e->{if(!activity.isDestroyed())route.setContentDescription("Google Cast non disponibile: Google Play Services richiesti");});}
  catch(RuntimeException e){route.setContentDescription("Google Cast non disponibile su questo dispositivo");}
 }
 void addTo(LinearLayout bar){bar.addView(route,new LinearLayout.LayoutParams(Ui.dp(activity,48),Ui.dp(activity,44)));if(source!=null){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(Ui.dp(activity,64),Ui.dp(activity,44));lp.leftMargin=Ui.dp(activity,4);bar.addView(send,lp);}}
 void mini(LinearLayout root){mini=new FrameLayout(activity);mini.setId(R.id.cast_mini_host);mini.setVisibility(View.GONE);root.addView(mini,new LinearLayout.LayoutParams(-1,-2));}
 void showMini(boolean allowed){miniAllowed=allowed;if(mini!=null)mini.setVisibility(allowed&&miniInflated?View.VISIBLE:View.GONE);}
 void resume(){resumed=true;if(context!=null)register();}
 private void register(){
  if(!registered){context.getSessionManager().addSessionManagerListener(sessions,CastSession.class);CastRelayService.addListener(this);registered=true;}
  if(mini!=null&&!miniInflated&&!activity.getSupportFragmentManager().isStateSaved()){
   // SDK styles and its button array are initialized only by onInflate().
   // Inflate the official fragment tag, including when restoring saved state.
   activity.getLayoutInflater().inflate(R.layout.cast_controls,mini,true);miniInflated=true;mini.setVisibility(miniAllowed?View.VISIBLE:View.GONE);
  }refresh();CastRequest current=source==null?null:source.get();if(current!=null&&current.key().equals(CastRelayService.loadedKey())&&loaded!=null)loaded.run();
 }
 void pause(){resumed=false;if(registered){context.getSessionManager().removeSessionManagerListener(sessions,CastSession.class);CastRelayService.removeListener(this);registered=false;}}
 void refresh(){CastSession session=context==null?null:context.getSessionManager().getCurrentCastSession();boolean available=source!=null&&source.get()!=null&&session!=null&&session.isConnected();send.setVisibility(available?View.VISIBLE:View.GONE);send.setEnabled(available&&!CastRelayService.preparing());}
 private void send(){
  if(context==null||source==null)return;CastSession session=context.getSessionManager().getCurrentCastSession();CastRequest request=source.get();
  if(session==null||!session.isConnected()||request==null){Toast.makeText(activity,"Scegli prima un video e un dispositivo Cast.",Toast.LENGTH_SHORT).show();return;}
  try{if(Build.VERSION.SDK_INT>=33&&activity.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)activity.requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},7101);
   activity.startForegroundService(request.intent(activity));Toast.makeText(activity,"Preparazione trasmissione…",Toast.LENGTH_SHORT).show();}
  catch(Exception e){Toast.makeText(activity,"Cast non avviato: "+e.getMessage(),Toast.LENGTH_LONG).show();}
 }
 public void changed(String key,boolean success,String message){refresh();CastRequest current=source==null?null:source.get();if(current!=null&&current.key().equals(key)){if(success&&loaded!=null)loaded.run();if(!message.isEmpty())Toast.makeText(activity,message,Toast.LENGTH_LONG).show();}}
}
