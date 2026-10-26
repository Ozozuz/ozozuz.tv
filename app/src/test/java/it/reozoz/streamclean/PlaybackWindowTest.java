package it.reozoz.streamclean;

import android.view.*;
import android.webkit.WebView;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={26,35})
@LooperMode(LooperMode.Mode.PAUSED)
public class PlaybackWindowTest {
    public static class Host extends AppCompatActivity {
        FrameLayout box;
        @Override public void onCreate(android.os.Bundle state){setTheme(R.style.AppTheme);super.onCreate(state);box=new FrameLayout(this);setContentView(box);}
    }
    private static boolean awake(Host a){return (a.getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)!=0;}
    @Test public void screenStaysAwakeOnlyDuringForegroundPlayback(){
        try(ActivityController<Host> c=Robolectric.buildActivity(Host.class).setup()){
            Host a=c.get();PlaybackWindow window=new PlaybackWindow(a);window.awake(true);assertTrue(awake(a));
            window.pause();assertFalse(awake(a));window.resume();assertTrue(awake(a));window.awake(false);assertFalse(awake(a));
            window.awake(true);window.close();assertFalse(awake(a));
        }
    }
    @Test public void fullscreenUsesWholePlayerAndRestoresItsParent(){
        try(ActivityController<Host> c=Robolectric.buildActivity(Host.class).setup()){
            Host a=c.get();View content=new View(a);FrameLayout.LayoutParams original=new FrameLayout.LayoutParams(200,100);a.box.addView(content,original);
            FullscreenFrame frame=new FullscreenFrame(a,content,()->{});frame.enter();ViewGroup overlay=(ViewGroup)content.getParent();
            assertNotSame(a.box,overlay);assertEquals(-1,content.getLayoutParams().height);assertEquals(-1,content.getLayoutParams().width);
            assertEquals(2,overlay.getChildCount());assertTrue(overlay.getChildAt(1) instanceof ImageButton);
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(3100,TimeUnit.MILLISECONDS);assertEquals(View.GONE,overlay.getChildAt(1).getVisibility());
            frame.leave();assertSame(a.box,content.getParent());assertSame(original,content.getLayoutParams());assertFalse(frame.active());
        }
    }
    @Test public void embeddedLandscapeClearsWakeFlagOnBackgroundAndPlayerClose(){
        try(ActivityController<Host> c=Robolectric.buildActivity(Host.class).setup()){
            Host a=c.get();int before=a.getRequestedOrientation();EmbeddedPlayer player=new EmbeddedPlayer(a,a.box,new TextView(a));
            player.forceLandscape();assertTrue(awake(a));assertEquals(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,a.getRequestedOrientation());
            player.pause();assertFalse(awake(a));player.resume();assertTrue(awake(a));player.clear();assertFalse(awake(a));assertEquals(before,a.getRequestedOrientation());
            assertTrue(a.box.getChildAt(0) instanceof WebView);player.destroy();assertFalse(awake(a));
        }
    }
    private static View find(View view,java.util.function.Predicate<View> predicate){
        if(predicate.test(view))return view;if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View found=find(group.getChildAt(i),predicate);if(found!=null)return found;}return null;
    }
    @Test public void offlineFullscreenKeepsTheVideoSurfaceAttached()throws Exception{
        android.content.Context app=RuntimeEnvironment.getApplication();ManagedDownloads.Output output=ManagedDownloads.prepare(app,42,11,28759);
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(output.temporary())){out.write(new byte[]{1,2,3,4});}
        android.net.Uri uri=ManagedDownloads.complete(app,output);
        try(ActivityController<DownloadedVideoActivity> c=Robolectric.buildActivity(DownloadedVideoActivity.class,DownloadedVideoActivity.open(app,"42:28759","South Park",uri)).setup()){
            DownloadedVideoActivity a=c.get();View decor=a.getWindow().getDecorView();VideoView video=(VideoView)find(decor,v->v instanceof VideoView);assertNotNull(video);android.view.ViewParent parent=video.getParent();
            View button=find(decor,v->"Attiva o disattiva orientamento orizzontale forzato".equals(v.getContentDescription()));button.performClick();
            assertSame(parent,video.getParent());assertEquals(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,a.getRequestedOrientation());
            assertEquals(View.GONE,((View)button.getParent()).getVisibility());a.onBackPressed();assertSame(parent,video.getParent());assertEquals(View.VISIBLE,((View)button.getParent()).getVisibility());
        }
    }
}
