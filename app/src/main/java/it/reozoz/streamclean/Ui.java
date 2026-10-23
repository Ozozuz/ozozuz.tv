package it.reozoz.streamclean;

import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.text.*;
import android.text.style.ForegroundColorSpan;
import android.view.*;
import android.widget.*;

/** Quiet terminal-inspired native UI: neutral panels, thin rules and a single green accent. */
final class Ui {
    static final int BG=0xff0c0e10,SURFACE=0xf5111418,TEXT=0xffdededb,MUTED=0xff95999e,ACCENT=0xff49f879,BORDER=0xff252a30;
    static int dp(Context c,float v){return Math.round(v*c.getResources().getDisplayMetrics().density);}
    static GradientDrawable surface(Context c,int fill,int stroke,float radius){
        GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(c,radius));
        if(stroke!=0)d.setStroke(dp(c,1),stroke);return d;
    }
    static Drawable ripple(Context c,int fill,int stroke,float radius){
        return new RippleDrawable(ColorStateList.valueOf(0x1849f879),surface(c,fill,stroke,radius),surface(c,0xffffffff,0,radius));
    }
    static TextView text(Context c,String value,float size,int color,boolean medium){
        TextView t=new TextView(c);t.setText(value);t.setTextSize(size);t.setTextColor(color);
        t.setTypeface(medium?Typeface.MONOSPACE:Typeface.create("sans-serif",Typeface.NORMAL),medium?Typeface.BOLD:Typeface.NORMAL);
        t.setIncludeFontPadding(false);return t;
    }
    static TextView mono(Context c,String value,float size,int color){
        TextView t=text(c,value,size,color,false);t.setTypeface(Typeface.MONOSPACE);return t;
    }
    static SpannableString accentPrefix(String prefix,String value){
        SpannableString s=new SpannableString(prefix+value);s.setSpan(new ForegroundColorSpan(ACCENT),0,prefix.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);return s;
    }
    static TextView section(Context c,String label,float size){
        TextView t=text(c,"",size,TEXT,true);t.setText(accentPrefix("// ",label));return t;
    }
    static Button button(Context c,String label,boolean primary){
        Button b=new Button(c);b.setText(label);b.setAllCaps(false);b.setTextSize(12);b.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);
        b.setMinHeight(0);b.setMinimumHeight(dp(c,44));b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(c,12),0,dp(c,12),0);
        b.setSingleLine(true);b.setEllipsize(TextUtils.TruncateAt.END);b.setStateListAnimator(null);styleButton(b,primary);return b;
    }
    static void styleButton(Button b,boolean primary){
        Context c=b.getContext();b.setBackgroundTintList(null);
        b.setBackground(ripple(c,primary?ACCENT:SURFACE,primary?ACCENT:BORDER,4));
        b.setTextColor(new ColorStateList(new int[][]{{-android.R.attr.state_enabled},{}},new int[]{0xff666c73,primary?BG:TEXT}));
    }
    static void navigation(Button b,boolean selected){
        b.setSelected(selected);
        b.setBackgroundTintList(null);b.setBackground(ripple(b.getContext(),selected?0xff13251a:Color.TRANSPARENT,0,3));
        b.setTextColor(new ColorStateList(new int[][]{{-android.R.attr.state_enabled},{}},new int[]{0xff666c73,selected?ACCENT:MUTED}));
    }
    static void selection(Button b,boolean selected){
        b.setSelected(selected);
        b.setBackgroundTintList(null);b.setBackground(ripple(b.getContext(),selected?0xff13251a:SURFACE,selected?ACCENT:BORDER,4));
        b.setTextColor(selected?ACCENT:TEXT);
    }
    static void field(EditText e){
        Context c=e.getContext();e.setBackgroundTintList(null);e.setBackground(surface(c,0xff0e1114,BORDER,4));
        e.setTextColor(TEXT);e.setHintTextColor(MUTED);e.setTextSize(14);e.setPadding(dp(c,12),0,dp(c,12),0);e.setSingleLine(true);
    }
    static View rule(Context c){View v=new View(c);v.setBackgroundColor(BORDER);return v;}
    static LinearLayout column(Context c){LinearLayout l=new LinearLayout(c);l.setOrientation(LinearLayout.VERTICAL);return l;}
    static LinearLayout.LayoutParams margin(Context c,int width,int height,int top,int bottom){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(width,height<0?height:dp(c,height));p.topMargin=dp(c,top);p.bottomMargin=dp(c,bottom);return p;
    }
}
