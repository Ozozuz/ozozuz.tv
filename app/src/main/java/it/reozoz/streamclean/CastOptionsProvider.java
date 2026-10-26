package it.reozoz.streamclean;
import android.content.Context;
import com.google.android.gms.cast.CastMediaControlIntent;
import com.google.android.gms.cast.framework.*;
import com.google.android.gms.cast.framework.media.*;
import java.util.List;
public final class CastOptionsProvider implements OptionsProvider {
 public CastOptions getCastOptions(Context context){
  NotificationOptions notification=new NotificationOptions.Builder().setTargetActivityClassName(CastPlaybackActivity.class.getName()).build();
  CastMediaOptions media=new CastMediaOptions.Builder().setExpandedControllerActivityClassName(CastPlaybackActivity.class.getName()).setNotificationOptions(notification).build();
  return new CastOptions.Builder().setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID).setCastMediaOptions(media).build();
 }
 public List<SessionProvider> getAdditionalSessionProviders(Context c){return null;}
}
