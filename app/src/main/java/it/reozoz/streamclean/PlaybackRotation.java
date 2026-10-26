package it.reozoz.streamclean;

import android.app.Activity;
import android.content.pm.ActivityInfo;

/** App-specific request: it never changes the user's global auto-rotation setting. */
final class PlaybackRotation {
    private final Activity activity;
    private int original;
    private boolean forced;
    PlaybackRotation(Activity activity){this.activity=activity;}
    boolean forced(){return forced;}
    void landscape(){if(!forced){original=activity.getRequestedOrientation();forced=true;}activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);}
    void restore(){if(forced){forced=false;activity.setRequestedOrientation(original);}}
}
