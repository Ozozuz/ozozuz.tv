package it.reozoz.streamclean;

import android.os.Bundle;
import android.widget.FrameLayout;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.gms.cast.framework.media.uicontroller.UIMediaController;
import com.google.android.gms.cast.framework.media.widget.MiniControllerFragment;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.LooperMode;
import static org.junit.Assert.*;

/** Exercise real Android resources/lifecycle; only the remote-TV binding is replaced. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk={26,35},shadows=StartupTest.NoReceiverController.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class StartupTest {
    @Implements(value=UIMediaController.class,isInAndroidSdk=false,callThroughByDefault=false)
    public static class NoReceiverController {}

    public static class Host extends AppCompatActivity {
        FrameLayout box;
        @Override public void onCreate(Bundle state){
            setTheme(R.style.AppTheme);super.onCreate(state);
            box=new FrameLayout(this);box.setId(R.id.cast_mini_host);setContentView(box);
        }
        void controls(){getLayoutInflater().inflate(R.layout.cast_controls,box,true);}
    }

    @Test public void reproducesTheUnconfiguredSdkFragmentFailure(){
        try(ActivityController<Host> controller=Robolectric.buildActivity(Host.class).setup()){
            Host host=controller.get();
            MiniControllerFragment broken=new MiniControllerFragment();
            NullPointerException failure=assertThrows(NullPointerException.class,()->host.getSupportFragmentManager().beginTransaction()
                .replace(R.id.cast_mini_host,broken).commitNow());
            assertTrue(java.util.Arrays.stream(failure.getStackTrace()).anyMatch(frame->frame.getClassName().equals(MiniControllerFragment.class.getName())));
            // Remove the deliberately broken fragment before lifecycle teardown retries its view.
            host.getSupportFragmentManager().beginTransaction().remove(broken).commitNow();
        }
    }

    @Test public void configuredMiniControllerCreatesItsRealViews(){
        try(ActivityController<Host> controller=Robolectric.buildActivity(Host.class).setup()){
            Host host=controller.get();host.controls();
            MiniControllerFragment mini=(MiniControllerFragment)host.getSupportFragmentManager().findFragmentById(R.id.cast_mini_controller);
            assertNotNull(mini);assertNotNull(mini.getView());
            for(int slot=0;slot<mini.getButtonSlotCount();slot++)assertTrue(mini.getButtonTypeAt(slot)!=0);
        }
    }

    @Test public void configuredMiniControllerRestoresAfterActivityRecreation(){
        Bundle saved=new Bundle();
        try(ActivityController<Host> controller=Robolectric.buildActivity(Host.class).setup()){
            controller.get().controls();controller.pause().saveInstanceState(saved).stop();
        }
        try(ActivityController<Host> controller=Robolectric.buildActivity(Host.class).create(saved).start().resume().visible()){
            Host host=controller.get();host.controls();
            MiniControllerFragment mini=(MiniControllerFragment)host.getSupportFragmentManager().findFragmentById(R.id.cast_mini_controller);
            assertNotNull(mini.getView());assertTrue(mini.getButtonTypeAt(1)!=0);
        }
    }

    @Test public void homeStartsWithoutPlayServicesAndSurvivesResume(){
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class)){
            controller.get().getPreferences(0).edit().putString("origin","https://catalog.invalid/").commit();
            controller.setup();assertNotNull(controller.get().findViewById(R.id.cast_mini_host));
            controller.pause().stop().start().resume();
            assertFalse(controller.get().isFinishing());
        }
    }
}
