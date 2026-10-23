package work.timelens.app;

import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;
import java.util.*;

final class Ui {
    static final int BG=Color.rgb(11,16,20), CARD=Color.rgb(22,30,35), INK=Color.rgb(241,245,237), MUTED=Color.rgb(148,162,168), LIME=Color.rgb(200,247,141), LINE=Color.rgb(43,55,61);
    static int dp(Context c,float v) { return Math.round(c.getResources().getDisplayMetrics().density*v); }
    static GradientDrawable rounded(int color,int radius,Context c) { GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(c,radius)); return d; }
    static TextView text(Context c,String value,int size,int color,boolean bold) {
        TextView t=new TextView(c); t.setText(value); t.setTextSize(size); t.setTextColor(color); t.setFontFeatureSettings("tnum");
        t.setTypeface(Typeface.create(bold?"sans-serif-medium":"sans-serif",Typeface.NORMAL));
        t.setLetterSpacing(size>=28?-0.025f:size<=12?0.055f:0f);
        if(size>=13&&size<=16)t.setLineSpacing(dp(c,3),1);
        t.setIncludeFontPadding(false);return t;
    }
    static LinearLayout column(Context c) { LinearLayout l=new LinearLayout(c); l.setOrientation(LinearLayout.VERTICAL); return l; }
    static LinearLayout row(Context c) { LinearLayout l=new LinearLayout(c); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    static LinearLayout card(Context c) {
        LinearLayout l=column(c); l.setPadding(dp(c,20),dp(c,20),dp(c,20),dp(c,20)); l.setBackground(rounded(CARD,24,c)); return l;
    }
    static void gap(LinearLayout parent,int dp) { View v=new View(parent.getContext()); parent.addView(v,new LinearLayout.LayoutParams(1,dp(parent.getContext(),dp))); }
    static TextView button(Context c,String label,boolean primary,Runnable action) {
        TextView b=text(c,label,15,primary?BG:INK,true); b.setGravity(Gravity.CENTER); b.setPadding(dp(c,18),dp(c,15),dp(c,18),dp(c,15));
        b.setMinHeight(dp(c,52));
        b.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(primary?0x22000000:0x22ffffff),rounded(primary?LIME:LINE,18,c),null));
        SpringValue press=new SpringValue(1,0.001,v->{b.setScaleX(v);b.setScaleY(v);},null);
        b.setOnTouchListener((view,event)->{switch(event.getActionMasked()){case MotionEvent.ACTION_DOWN:press.target(0.975f);break;case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:press.target(1);break;}return false;});
        b.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View v){}public void onViewDetachedFromWindow(View v){press.stop();}});
        b.setOnClickListener(v->{v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);action.run();}); b.setFocusable(true); return b;
    }
    static String duration(long ms) {
        long minutes=Math.max(0,ms)/60000,h=minutes/60,m=minutes%60;
        if(h>0) return h+"h "+m+"m";
        if(minutes>0)return minutes+"m";
        return Math.max(0,ms)/1000+"s";
    }
    static String clock(long ms) {
        long sec=Math.max(0,ms)/1000;
        return String.format(Locale.US,"%02d:%02d:%02d",sec/3600,(sec/60)%60,sec%60);
    }
    static String appName(Context c,String pkg) {
        if(pkg==null)return "Between apps";
        try { return c.getPackageManager().getApplicationLabel(c.getPackageManager().getApplicationInfo(pkg,0)).toString(); }
        catch(Exception e) { return pkg; }
    }
    static android.content.SharedPreferences prefs(Context c) { return c.getSharedPreferences("timelens",Context.MODE_PRIVATE); }
    private Ui() {}
}
