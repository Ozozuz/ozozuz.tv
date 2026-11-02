package it.reozoz.streamclean;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={26,35},qualifiers="w320dp-h800dp-xhdpi")
public class PlaybackSettingsTest {
    private static View find(View view,String content){
        if(content.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;
        if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View found=find(group.getChildAt(i),content);if(found!=null)return found;}
        return null;
    }
    @Test public void settingsStartWithRequestedDefaultsAndPersistNewChoices(){
        try(ActivityController<PlaybackSettingsActivity> controller=Robolectric.buildActivity(PlaybackSettingsActivity.class).setup()){
            var activity=controller.get();View decor=activity.getWindow().getDecorView();
            View audio=find(decor,"Lingua audio preferita: Inglese");assertNotNull(audio);audio.performClick();
            AlertDialog dialog=(AlertDialog)ShadowDialog.getLatestDialog();assertNotNull(dialog);
            dialog.getListView().performItemClick(null,2,2);
            assertEquals("it",new PlaybackPreferences(activity).audio());assertNotNull(find(decor,"Lingua audio preferita: Italiano"));
            View subtitles=find(decor,"Sottotitoli preferiti: Italiano");assertNotNull(subtitles);subtitles.performClick();
            dialog=(AlertDialog)ShadowDialog.getLatestDialog();dialog.getListView().performItemClick(null,1,1);
            assertEquals("off",new PlaybackPreferences(activity).subtitles());assertNotNull(find(decor,"Sottotitoli preferiti: Disattivati"));
        }
    }
}
