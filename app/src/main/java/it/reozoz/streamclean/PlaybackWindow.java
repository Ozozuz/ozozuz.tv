package it.reozoz.streamclean;

import android.app.Activity;
import android.os.Build;
import android.view.*;
import androidx.core.view.*;

/** Owns only playback's window state; never changes the device-wide screen timeout. */
final class PlaybackWindow {
    private final Activity activity;
    private final boolean originalAwake;
    private boolean foreground=true,awake,fullscreen;
    private int previousUi,previousCutout,previousBehavior;
    private boolean statusVisible=true,navigationVisible=true;
    PlaybackWindow(Activity activity){this.activity=activity;originalAwake=(activity.getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)!=0;}
    void awake(boolean value){awake=value;applyAwake();}
    void resume(){foreground=true;applyAwake();if(fullscreen)hideBars();}
    void pause(){foreground=false;applyAwake();}
    private void applyAwake(){if(originalAwake||foreground&&awake)activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);}
    void fullscreen(boolean value){
        if(value==fullscreen){if(value)hideBars();return;}
        Window window=activity.getWindow();View decor=window.getDecorView();WindowInsetsControllerCompat bars=WindowCompat.getInsetsController(window,decor);
        if(value){
            previousUi=decor.getSystemUiVisibility();previousBehavior=bars.getSystemBarsBehavior();
            WindowInsetsCompat insets=ViewCompat.getRootWindowInsets(decor);
            statusVisible=insets==null||(insets.isVisible(WindowInsetsCompat.Type.statusBars()));
            navigationVisible=insets==null||(insets.isVisible(WindowInsetsCompat.Type.navigationBars()));
            if(Build.VERSION.SDK_INT>=28){previousCutout=window.getAttributes().layoutInDisplayCutoutMode;WindowManager.LayoutParams p=window.getAttributes();p.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;window.setAttributes(p);}
            fullscreen=true;hideBars();
        }else{
            fullscreen=false;bars.setSystemBarsBehavior(previousBehavior);
            if(statusVisible)bars.show(WindowInsetsCompat.Type.statusBars());else bars.hide(WindowInsetsCompat.Type.statusBars());
            if(navigationVisible)bars.show(WindowInsetsCompat.Type.navigationBars());else bars.hide(WindowInsetsCompat.Type.navigationBars());
            if(Build.VERSION.SDK_INT<30)decor.setSystemUiVisibility(previousUi);
            if(Build.VERSION.SDK_INT>=28){WindowManager.LayoutParams p=window.getAttributes();p.layoutInDisplayCutoutMode=previousCutout;window.setAttributes(p);}
        }
    }
    private void hideBars(){WindowInsetsControllerCompat bars=WindowCompat.getInsetsController(activity.getWindow(),activity.getWindow().getDecorView());bars.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);bars.hide(WindowInsetsCompat.Type.systemBars());}
    void close(){fullscreen(false);awake=false;foreground=false;applyAwake();}
}
