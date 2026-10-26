package it.reozoz.streamclean;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;
import java.util.List;
import java.util.function.Predicate;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.LooperMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={26,35},qualifiers="w320dp-h800dp-xhdpi",shadows=TitlePageTest.TitleResponse.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class TitlePageTest {
    @Implements(value=SafeHttp.class,isInAndroidSdk=false)
    public static class TitleResponse {
        @Implementation public static TitleParser.Detail detail(URI origin,long id,String slug,int season){
            return new TitleParser.Detail(id,slug,"South Park","Quattro ragazzi nella cittadina di South Park.","tv",
                List.of(new TitleParser.Season(11,"",14)),11,
                List.of(new TitleParser.Episode(28758,1,"Le mie più sentite scuse a Jesse Jackson","Anteprima episodio.",true,"")),false,"");
        }
    }
    private static View find(View view,Predicate<View> predicate){
        if(predicate.test(view))return view;
        if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View found=find(group.getChildAt(i),predicate);if(found!=null)return found;}
        return null;
    }
    private static ActivityController<TitleActivity> open()throws Exception{
        Intent intent=new Intent(RuntimeEnvironment.getApplication(),TitleActivity.class).putExtra("origin","https://catalog.invalid/").putExtra("id",42L).putExtra("slug","south-park");
        ActivityController<TitleActivity> controller=Robolectric.buildActivity(TitleActivity.class,intent).setup();
        long end=System.nanoTime()+3_000_000_000L;
        while(find(controller.get().getWindow().getDecorView(),v->v instanceof TextView&&"South Park".contentEquals(((TextView)v).getText()))==null&&System.nanoTime()<end){Shadows.shadowOf(Looper.getMainLooper()).idle();Thread.sleep(5);}
        return controller;
    }
    private static View layout(TitleActivity activity){
        View decor=activity.getWindow().getDecorView();int width=activity.getResources().getDisplayMetrics().widthPixels,height=activity.getResources().getDisplayMetrics().heightPixels;
        decor.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));decor.layout(0,0,width,height);return decor;
    }
    @Test public void openedTitleCanToggleFavoritesAndToolbarFitsSmallScreen()throws Exception{
        try(ActivityController<TitleActivity> controller=open()){
            TitleActivity activity=controller.get();View decor=layout(activity);
            ImageButton star=(ImageButton)find(decor,v->"Aggiungi ai preferiti".equals(v.getContentDescription()));
            assertNotNull(star);assertTrue(star.isEnabled());assertNotNull(star.getDrawable());
            assertNotNull(find(decor,v->v instanceof TextView&&"stagione 11  ▾".contentEquals(((TextView)v).getText())));
            ViewGroup bar=(ViewGroup)star.getParent();int edge=0;
            for(int i=0;i<bar.getChildCount();i++){View item=bar.getChildAt(i);if(item.getVisibility()!=View.GONE){assertTrue(item.getLeft()>=edge);assertTrue(item.getRight()<=bar.getWidth());edge=item.getRight();}}
            assertTrue(star.getWidth()>=Ui.dp(activity,44));
            assertNull(find(decor,v->v.getVisibility()==View.VISIBLE&&"Forza orientamento orizzontale".equals(v.getContentDescription())));
            star.performClick();assertTrue(new Favorites(activity).contains(42));assertTrue(star.isSelected());
            assertEquals("Rimuovi dai preferiti",star.getContentDescription());
            star.performClick();assertFalse(new Favorites(activity).contains(42));assertFalse(star.isSelected());
        }
    }
    @Test public void seasonDownloadStartsInAppWithoutAFolderPicker()throws Exception{
        try(ActivityController<TitleActivity> controller=open()){
            TitleActivity activity=controller.get();View decor=layout(activity);
            View button=find(decor,v->v instanceof TextView&&"↓ stagione".contentEquals(((TextView)v).getText()));assertNotNull(button);button.performClick();
            Intent service=Shadows.shadowOf(RuntimeEnvironment.getApplication()).getNextStartedService();assertNotNull(service);assertEquals(EpisodeDownloadService.class.getName(),service.getComponent().getClassName());
            assertEquals("season",service.getAction());assertFalse(service.hasExtra("folder"));assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
        }
    }
    @Test @Config(sdk=35,qualifiers="w360dp-h800dp-xhdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void renderHeaderWithRealVectorIcons()throws Exception{
        try(ActivityController<TitleActivity> controller=open()){
            View decor=layout(controller.get());Bitmap bitmap=Bitmap.createBitmap(decor.getWidth(),decor.getHeight(),Bitmap.Config.ARGB_8888);decor.draw(new Canvas(bitmap));
            File image=new File(System.getProperty("java.io.tmpdir"),"ozozuz-title-toolbar.png");
            try(FileOutputStream output=new FileOutputStream(image)){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,output));}
            bitmap.recycle();
        }
    }
}
