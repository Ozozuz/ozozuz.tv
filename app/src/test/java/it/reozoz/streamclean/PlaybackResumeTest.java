package it.reozoz.streamclean;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.net.URI;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={26,35},shadows={PlaybackResumeTest.Catalog.class,PlaybackResumeTest.Player.class})
@LooperMode(LooperMode.Mode.PAUSED)
public class PlaybackResumeTest {
    @Implements(value=SafeHttp.class,isInAndroidSdk=false)
    public static class Catalog {
        static long failEpisode;
        @Implementation public static TitleParser.Detail detail(URI origin,long id,String slug,int season){
            int number=season<0?3:season;
            return new TitleParser.Detail(id,slug,"South Park","","tv",List.of(new TitleParser.Season(3,"",2),new TitleParser.Season(17,"",2)),number,
                List.of(new TitleParser.Episode(number*100+1,1,"First","",true,""),new TitleParser.Episode(number*100+2,2,"Second","",true,"")),false,"");
        }
        @Implementation public static TitleParser.Player player(URI origin,long id,long episode)throws java.io.IOException{
            if(episode==failEpisode)throw new java.io.IOException("Unavailable episode");
            return new TitleParser.Player(origin,URI.create("https://vixcloud.cc/embed/"+episode));
        }
    }
    @Implements(value=EmbeddedPlayer.class,isInAndroidSdk=false,callThroughByDefault=false)
    public static class Player {
        @Implementation public void __constructor__(Activity activity,FrameLayout box,TextView status){}
        @Implementation public void load(TitleParser.Player source){}
    }
    private static void await(BooleanSupplier ready)throws Exception{
        long deadline=System.nanoTime()+3_000_000_000L;
        do{Shadows.shadowOf(Looper.getMainLooper()).idle();if(ready.getAsBoolean())return;Thread.sleep(5);}while(System.nanoTime()<deadline);
        fail("Asynchronous title operation timed out");
    }
    private static View find(View view,Class<?> type){
        if(type.isInstance(view))return view;
        if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View result=find(group.getChildAt(i),type);if(result!=null)return result;}
        return null;
    }
    private static boolean text(View view,String expected){
        if(view instanceof TextView label&&expected.contentEquals(label.getText()))return true;
        if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)if(text(group.getChildAt(i),expected))return true;
        return false;
    }
    private static PlaybackHistory history(){return new PlaybackHistory(RuntimeEnvironment.getApplication());}
    private static ActivityController<TitleActivity> open(String origin){
        Intent intent=new Intent(RuntimeEnvironment.getApplication(),TitleActivity.class).putExtra("origin",origin).putExtra("id",42L).putExtra("slug","south-park");
        return Robolectric.buildActivity(TitleActivity.class,intent).setup();
    }
    @Test public void reopeningFromNewDomainRestoresSeasonAndEpisode()throws Exception{
        history().record(42,17,1702);
        try(var controller=open("https://new.invalid/")){
            View decor=controller.get().getWindow().getDecorView();
            await(()->text(decor,"S17 · E2  Second"));assertTrue(text(decor,"stagione 17  ▾"));
            assertNull(findRotation(decor));
        }
    }
    private static View findRotation(View view){
        if("Forza orientamento orizzontale".equals(view.getContentDescription()))return view;
        if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View result=findRotation(group.getChildAt(i));if(result!=null)return result;}
        return null;
    }
    @Test public void selectingEarlierEpisodeReplacesHistoryAndSurvivesReopening()throws Exception{
        history().record(42,3,302);
        try(var controller=open("https://old.invalid/")){
            View decor=controller.get().getWindow().getDecorView();await(()->text(decor,"S3 · E2  Second"));
            ListView list=(ListView)find(decor,ListView.class);list.performItemClick(null,0,list.getAdapter().getItemId(0));
            await(()->new PlaybackHistory.Entry(3,301).equals(history().last(42)));
        }
        try(var controller=open("https://old.invalid/")){
            await(()->text(controller.get().getWindow().getDecorView(),"S3 · E1  First"));
        }
    }
    @Test public void failedPlayerDoesNotReplaceLastSuccessfulEpisode()throws Exception{
        history().record(42,3,302);Catalog.failEpisode=301;
        try(var controller=open("https://old.invalid/")){
            View decor=controller.get().getWindow().getDecorView();await(()->text(decor,"S3 · E2  Second"));
            ListView list=(ListView)find(decor,ListView.class);list.performItemClick(null,0,list.getAdapter().getItemId(0));
            await(()->text(decor,"Player non disponibile: Unavailable episode. Premi Ricarica per riprovare."));
            assertEquals(new PlaybackHistory.Entry(3,302),history().last(42));
        }finally{Catalog.failEpisode=0;}
    }
    @Test public void removedSeasonFallsBackWithoutErasingHistory()throws Exception{
        history().record(42,99,9901);
        try(var controller=open("https://old.invalid/")){
            await(()->text(controller.get().getWindow().getDecorView(),"stagione 3  ▾"));
            assertEquals(new PlaybackHistory.Entry(99,9901),history().last(42));
        }
    }
    @Test public void removedEpisodeStillRestoresItsSeason()throws Exception{
        history().record(42,17,1799);
        try(var controller=open("https://old.invalid/")){
            await(()->text(controller.get().getWindow().getDecorView(),"stagione 17  ▾"));
            assertEquals(new PlaybackHistory.Entry(17,1799),history().last(42));
        }
    }
    @Test public void browsingWithoutPlaybackDoesNotCreateHistory()throws Exception{
        try(var controller=open("https://old.invalid/")){
            await(()->text(controller.get().getWindow().getDecorView(),"stagione 3  ▾"));assertNull(history().last(42));
        }
    }
    @Test public void recreationRetainsTheCurrentEpisode()throws Exception{
        history().record(42,17,1702);Bundle saved=new Bundle();
        try(var controller=open("https://old.invalid/")){
            await(()->text(controller.get().getWindow().getDecorView(),"S17 · E2  Second"));
            controller.pause().saveInstanceState(saved).stop();
        }
        Intent intent=new Intent(RuntimeEnvironment.getApplication(),TitleActivity.class).putExtra("origin","https://old.invalid/").putExtra("id",42L).putExtra("slug","south-park");
        try(var controller=Robolectric.buildActivity(TitleActivity.class,intent).create(saved).start().resume().visible()){
            await(()->text(controller.get().getWindow().getDecorView(),"S17 · E2  Second"));
        }
    }
}
