package it.reozoz.streamclean;

import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import java.net.URI;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,shadows=DomainUpdateTest.Catalog.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class DomainUpdateTest {
    @Implements(value=SafeHttp.class,isInAndroidSdk=false)
    public static class Catalog {
        static boolean fail;
        static volatile boolean called;
        static volatile URI requested;
        @Implementation public static DomainResolver.Resolved resolveHome(URI origin)throws Exception{
            requested=origin;called=true;
            if(fail)throw new java.io.IOException("Invalid catalog");
            return new DomainResolver.Resolved(URI.create("https://new.invalid/"),new HomeParser.Home(null,List.of(),"cdn.new.invalid"));
        }
    }
    private static void finishLoad(MainActivity activity)throws Exception{
        long deadline=System.nanoTime()+3_000_000_000L;
        while(System.nanoTime()<deadline){
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            if(Catalog.called&&loadEnabled(activity.getWindow().getDecorView()))return;
            Thread.sleep(5);
        }
        fail("Catalogue load timed out");
    }
    private static boolean loadEnabled(View view){
        if(view instanceof Button button&&"Carica".contentEquals(button.getText()))return button.isEnabled();
        if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)if(loadEnabled(group.getChildAt(i)))return true;
        return false;
    }
    @Test public void persistsTheValidatedDomain()throws Exception{
        Catalog.fail=false;Catalog.called=false;
        try(var controller=Robolectric.buildActivity(MainActivity.class)){
            var activity=controller.get();activity.getPreferences(0).edit().putString("origin","https://old.invalid/").commit();
            controller.setup();
            finishLoad(activity);
            assertEquals("https://new.invalid/",activity.getPreferences(0).getString("origin",""));
        }
    }
    @Test public void failedResolutionKeepsTheSavedDomain()throws Exception{
        Catalog.fail=true;Catalog.called=false;
        try(var controller=Robolectric.buildActivity(MainActivity.class)){
            var activity=controller.get();activity.getPreferences(0).edit().putString("origin","https://old.invalid/").commit();
            controller.setup();finishLoad(activity);
            assertEquals("https://old.invalid/",activity.getPreferences(0).getString("origin",""));
        }
    }
    @Test public void freshInstallStartsFromCurrentDefault()throws Exception{
        Catalog.fail=false;Catalog.called=false;Catalog.requested=null;
        try(var controller=Robolectric.buildActivity(MainActivity.class)){
            var activity=controller.get();activity.getPreferences(0).edit().clear().commit();
            controller.setup();finishLoad(activity);
            assertEquals(DomainResolver.DEFAULT_ORIGIN,Catalog.requested);
        }
    }

}
