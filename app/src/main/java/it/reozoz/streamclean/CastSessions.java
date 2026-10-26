package it.reozoz.streamclean;
import com.google.android.gms.cast.framework.*;
abstract class CastSessions implements SessionManagerListener<CastSession> {
 public void onSessionStarting(CastSession s){} public void onSessionStarted(CastSession s,String id){}
 public void onSessionStartFailed(CastSession s,int error){} public void onSessionEnding(CastSession s){}
 public void onSessionEnded(CastSession s,int error){} public void onSessionResuming(CastSession s,String id){}
 public void onSessionResumed(CastSession s,boolean suspended){} public void onSessionResumeFailed(CastSession s,int error){}
 public void onSessionSuspended(CastSession s,int reason){}
}
