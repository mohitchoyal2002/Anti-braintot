package work.timelens.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** One glanceable component shared by the live window and its settings preview. */
final class TimerCapsule extends LinearLayout {
    private final ImageView clock;
    private final TextView total, today, appName, appDuration;
    private final LinearLayout detail;
    private final GradientDrawable surface;
    private boolean expanded;
    private String spokenTime="Starting timer";

    TimerCapsule(Context c) {
        super(c);
        setOrientation(VERTICAL);
        setPadding(Ui.dp(c,14),Ui.dp(c,12),Ui.dp(c,14),Ui.dp(c,12));
        setMinimumHeight(Ui.dp(c,48));
        surface=Ui.rounded(Ui.CARD,24,c);
        setBackground(new android.graphics.drawable.RippleDrawable(
                ColorStateList.valueOf(Ui.RIPPLE),surface,Ui.rounded(0xffffffff,24,c)));
        setElevation(Ui.dp(c,4));
        LinearLayout summary=Ui.row(c);
        clock=new ImageView(c);
        clock.setImageResource(R.drawable.ic_timer);
        summary.addView(clock,new LayoutParams(Ui.dp(c,20),Ui.dp(c,20)));
        today=Ui.text(c,"Today",14,Ui.MUTED,false);
        today.setPadding(Ui.dp(c,8),0,Ui.dp(c,8),0);
        summary.addView(today,new LayoutParams(0,-2,1));
        total=Ui.text(c,"0s",16,Ui.INK,true);
        summary.addView(total,new LayoutParams(-2,-2));
        addView(summary);
        detail=Ui.row(c);
        detail.setPadding(0,Ui.dp(c,12),0,0);
        ImageView appMark=new ImageView(c);
        appMark.setImageResource(R.drawable.ic_apps);
        appMark.setImageTintList(ColorStateList.valueOf(Ui.MUTED));
        LayoutParams markSize=new LayoutParams(Ui.dp(c,18),Ui.dp(c,18));
        markSize.gravity=Gravity.TOP;markSize.topMargin=Ui.dp(c,3);
        detail.addView(appMark,markSize);
        LinearLayout words=Ui.column(c);
        words.setPadding(Ui.dp(c,10),0,0,0);
        appName=Ui.text(c,"Current app",14,Ui.INK,true);
        appDuration=Ui.text(c,"Waiting for usage data",13,Ui.MUTED,false);
        words.addView(appName);Ui.gap(words,3);words.addView(appDuration);
        detail.addView(words,new LayoutParams(0,-2,1));
        addView(detail);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        summary.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        detail.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        setFocusable(true);
        setAccessibilityDelegate(new AccessibilityDelegate(){
            @Override public void onInitializeAccessibilityNodeInfo(View host,AccessibilityNodeInfo info){
                super.onInitializeAccessibilityNodeInfo(host,info);
                info.setClassName(Button.class.getName());
                info.addAction(new AccessibilityNodeInfo.AccessibilityAction(
                        AccessibilityNodeInfo.ACTION_CLICK,expanded?"Hide app detail":"Show app detail"));
            }
        });
        setExpanded(false);applyAppearance();
    }

    void setExpanded(boolean value) {
        expanded=value;
        today.setVisibility(value?VISIBLE:GONE);
        detail.setVisibility(value?VISIBLE:GONE);
        LayoutParams totalSize=(LayoutParams)total.getLayoutParams();
        totalSize.leftMargin=Ui.dp(getContext(),value?0:8);
        totalSize.width=value?LayoutParams.WRAP_CONTENT:0;
        totalSize.weight=value?0:1;
        total.setLayoutParams(totalSize);
        updateDescription();
    }
    boolean isExpanded(){return expanded;}

    void update(long totalMs,String name,long appMs,boolean waiting) {
        total.setText(Ui.duration(totalMs));
        appName.setText(waiting?"Usage data unavailable":name);
        appDuration.setText(waiting?"Open TimeLens to review access":Ui.duration(appMs)+" in this app today");
        spokenTime="Screen time today, "+Ui.duration(totalMs)+
                (expanded?". "+appName.getText()+", "+appDuration.getText():"");
        updateDescription();
    }
    private void updateDescription(){
        setContentDescription(spokenTime+". "+(expanded?"Hide app detail":"Show app detail")+". Drag to move.");
    }
    void applyAppearance(){
        // Opaque content keeps contrast stable over any app, including photos and video.
        surface.setColor(Ui.CARD);surface.setStroke(Ui.dp(getContext(),1),Ui.LINE);
        clock.setImageTintList(ColorStateList.valueOf(Ui.ACCENT));
        total.setTextColor(Ui.INK);today.setTextColor(Ui.MUTED);
        appName.setTextColor(Ui.INK);appDuration.setTextColor(Ui.MUTED);
    }
    static int width(Context c,boolean expanded,int available) {
        float scale=Math.max(1,c.getResources().getConfiguration().fontScale);
        return Math.max(Ui.dp(c,48),Math.min(available,Ui.dp(c,(expanded?224:132)*scale)));
    }
}
