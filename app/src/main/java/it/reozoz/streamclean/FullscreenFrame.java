package it.reozoz.streamclean;

import android.app.Activity;
import android.graphics.Color;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.core.view.*;

/** Reparents the same player into a full-window surface, without reloading it. */
final class FullscreenFrame {
    private final Activity activity;
    private final View content;
    private final Runnable exit;
    private final Handler handler=new Handler(Looper.getMainLooper());

    private FrameLayout overlay;
    private ImageButton close;
    private final Runnable hide=()->{if(close!=null)close.setVisibility(View.GONE);};
    private ViewGroup parent;
    private ViewGroup.LayoutParams params;
    private int index;
    FullscreenFrame(Activity activity,View content,Runnable exit){this.activity=activity;this.content=content;this.exit=exit;}
    boolean active(){return overlay!=null;}
    void enter(){
        if(active())return;parent=(ViewGroup)content.getParent();index=parent.indexOfChild(content);params=content.getLayoutParams();parent.removeView(content);
        overlay=new FrameLayout(activity);overlay.setBackgroundColor(Color.BLACK);overlay.addView(content,new FrameLayout.LayoutParams(-1,-1));
        close=Ui.iconButton(activity,R.drawable.ic_back,"Esci dal fullscreen");close.setBackground(Ui.ripple(activity,0x99000000,0,4));close.setOnClickListener(v->exit.run());
        FrameLayout.LayoutParams button=new FrameLayout.LayoutParams(Ui.dp(activity,44),Ui.dp(activity,44),Gravity.TOP|Gravity.START);button.setMargins(Ui.dp(activity,8),Ui.dp(activity,8),Ui.dp(activity,8),0);overlay.addView(close,button);
        ViewCompat.setOnApplyWindowInsetsListener(overlay,(v,insets)->{androidx.core.graphics.Insets cutout=insets.getInsets(WindowInsetsCompat.Type.displayCutout());v.setPadding(cutout.left,cutout.top,cutout.right,cutout.bottom);return insets;});
        ((ViewGroup)activity.getWindow().getDecorView()).addView(overlay,new FrameLayout.LayoutParams(-1,-1));ViewCompat.requestApplyInsets(overlay);
        content.setOnTouchListener((v,event)->{if(event.getActionMasked()==MotionEvent.ACTION_DOWN)showClose();return false;});showClose();
    }
    private void showClose(){if(close==null)return;close.setVisibility(View.VISIBLE);handler.removeCallbacks(hide);handler.postDelayed(hide,3000);}
    void leave(){
        if(!active())return;handler.removeCallbacks(hide);content.setOnTouchListener(null);overlay.removeView(content);((ViewGroup)overlay.getParent()).removeView(overlay);overlay=null;close=null;
        parent.addView(content,Math.min(index,parent.getChildCount()),params);parent=null;
    }
}
