package work.timelens.app;

import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.widget.TextView;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** Device-level regression checks; no external test framework or fake grant reader. */
public final class PermissionFlowInstrumentation extends Instrumentation {
    private Bundle arguments;
    private Activity activity;
    private int assertions;
    private final StringBuilder shellTrace=new StringBuilder();
    private volatile boolean activityResumed=true;

    @Override public void onCreate(Bundle args) { arguments=args;start(); }

    @Override public void onStart() {
        Bundle result=new Bundle();
        String scenario=arguments.getString("scenario","usage");
        try {
            if("channel".equals(scenario)){
                getTargetContext().getSystemService(NotificationManager.class).createNotificationChannel(
                        new NotificationChannel("timer","Live screen-time timer",NotificationManager.IMPORTANCE_NONE));
            }
            activity=startActivitySync(new Intent(getTargetContext(),MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            runOnMainSync(()->activity.getApplication().registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks(){
                public void onActivityCreated(Activity a,Bundle saved){}
                public void onActivityStarted(Activity a){}
                public void onActivityResumed(Activity a){if(a==activity)activityResumed=true;}
                public void onActivityPaused(Activity a){if(a==activity)activityResumed=false;}
                public void onActivityStopped(Activity a){}
                public void onActivitySaveInstanceState(Activity a,Bundle saved){}
                public void onActivityDestroyed(Activity a){}
            }));
            waitForIdleSync();
            switch(scenario){
                case "usage":
                    expect("Make time visible.");
                    expect("Know where your day goes");
                    assertVisible("Open usage access");
                    capture("usage");
                    // Exercise an actual settings round trip without granting access.
                    tap("Open usage access");
                    awaitPaused();
                    shell("am start --activity-reorder-to-front -n work.timelens.app/.MainActivity");
                    await("Is the switch blocked?");
                    expect("Usage access is still off.");
                    expect("Allow restricted settings");
                    verifyIntent("Open App info",Settings.ACTION_APPLICATION_DETAILS_SETTINGS,true);
                    capture("recovery");
                    tap("Not now · view dashboard");
                    expect("Continue permission setup");
                    tap("Continue permission setup");
                    expect("Is the switch blocked?");
                    shell("input keyevent KEYCODE_HOME");
                    awaitPaused();
                    shell("appops set work.timelens.app GET_USAGE_STATS allow");
                    shell("am start --activity-reorder-to-front -n work.timelens.app/.MainActivity");
                    await("Keep the total in sight");
                    break;
                case "overlay":
                    expect("Keep the total in sight");
                    expect("1 of 3 permissions allowed");
                    verifyIntent("Open display settings",Settings.ACTION_MANAGE_OVERLAY_PERMISSION,true);
                    capture("overlay");
                    shell("input keyevent KEYCODE_BACK");
                    await("Continue permission setup");
                    tap("Continue permission setup");
                    expect("Keep the total in sight");
                    break;
                case "notifications":
                    expect("A quick way to stop");
                    expect("Recommended, but optional.");
                    tap("Continue without notifications");
                    expect("Your timer is ready");
                    capture("ready-optional");
                    tap("Start floating timer");
                    awaitRunning();
                    await("Stop floating timer");
                    tap("Stop floating timer");
                    awaitStopped();
                    // Skipping lasts this visit; another foreground visit offers setup again.
                    shell("input keyevent KEYCODE_HOME");
                    awaitPaused();
                    shell("am start --activity-reorder-to-front -n work.timelens.app/.MainActivity");
                    await("A quick way to stop");
                    break;
                case "complete":
                    expect("App activity");
                    absent("SET UP TIMELENS");
                    capture("dashboard");
                    tap("History");
                    expect("Your last seven days");
                    shell("input keyevent KEYCODE_BACK");
                    await("App activity");
                    // A real OS revocation must reopen setup on return.
                    shell("input keyevent KEYCODE_HOME");
                    awaitPaused();
                    shell("appops set work.timelens.app GET_USAGE_STATS deny");
                    shell("am start --activity-reorder-to-front -n work.timelens.app/.MainActivity");
                    await("Know where your day goes");
                    break;
                case "channel":
                    expect("A quick way to stop");
                    expect("2 of 3 permissions allowed");
                    verifyIntent("Set up notifications",Settings.ACTION_APP_NOTIFICATION_SETTINGS,false);
                    break;
                case "large":
                    expect("Know where your day goes");
                    tap("Usage switch disabled?");
                    expect("Is the switch blocked?");
                    runOnMainSync(()->checkTextLayout(activity.getWindow().getDecorView()));
                    capture("large-text");
                    break;
                case "design":
                case "design-large":
                    expect("App activity");
                    runOnMainSync(()->checkTextLayout(activity.getWindow().getDecorView()));
                    capture("today");
                    tap("History");await("Yesterday");capture("history");
                    await("View app activity");tap("View app activity →");
                    verifyDayDialog();
                    tap("Settings");expect("Quiet by default.");
                    runOnMainSync(()->checkTextLayout(activity.getWindow().getDecorView()));
                    capture("settings");
                    tap("Today");tap("Start floating timer");awaitRunning();
                    shell("input keyevent KEYCODE_HOME");awaitPaused();
                    verifyCapsule("design-large".equals(scenario));
                    runOnMainSync(()->activity.stopService(new Intent(activity,OverlayService.class)));
                    awaitStopped();
                    break;
                default:throw new AssertionError("Unknown scenario "+scenario);
            }
            result.putString("timelens_success","true");
            result.putString("stream","PASS "+scenario+": "+assertions+" assertions\n");
            finish(Activity.RESULT_OK,result);
        }catch(Throwable error){
            try{capture("failure");}catch(Exception ignored){}
            result.putString("timelens_success","false");
            result.putString("stream","FAIL "+scenario+": "+error+"\n"+android.util.Log.getStackTraceString(error)+"\n"+shellTrace);
            finish(Activity.RESULT_CANCELED,result);
        }
    }

    private View find(View view,String text,boolean exact){
        if(view instanceof TextView){
            String value=((TextView)view).getText().toString();
            if(exact?value.equals(text):value.contains(text))return view;
        }
        if(view instanceof ViewGroup){
            ViewGroup parent=(ViewGroup)view;
            for(int i=0;i<parent.getChildCount();i++){
                View found=find(parent.getChildAt(i),text,exact);if(found!=null)return found;
            }
        }
        return null;
    }
    private boolean contains(String text){
        boolean[] found={false};runOnMainSync(()->found[0]=find(activity.getWindow().getDecorView(),text,false)!=null);return found[0];
    }
    private void expect(String text){if(!contains(text))throw new AssertionError("Missing text: "+text);assertions++;}
    private void absent(String text){if(contains(text))throw new AssertionError("Unexpected text: "+text);assertions++;}
    private void assertVisible(String label){
        boolean[] visible={false};
        runOnMainSync(()->{
            View button=find(activity.getWindow().getDecorView(),label,true);
            android.graphics.Rect area=new android.graphics.Rect();
            visible[0]=button!=null&&button.getGlobalVisibleRect(area)&&area.height()>=button.getHeight();
        });
        if(!visible[0])throw new AssertionError("Primary action is below the visible screen: "+label);
        assertions++;
    }
    private void await(String text){
        long until=SystemClock.uptimeMillis()+8000;
        while(SystemClock.uptimeMillis()<until){waitForIdleSync();if(contains(text)){assertions++;return;}SystemClock.sleep(100);}
        throw new AssertionError("Did not return to: "+text);
    }
    private void awaitPaused(){
        long until=SystemClock.uptimeMillis()+10000;
        while(activityResumed&&SystemClock.uptimeMillis()<until)SystemClock.sleep(50);
        if(activityResumed)throw new AssertionError("Android did not leave the app after the navigation action");
        assertions++;
    }
    private void tap(String text){
        runOnMainSync(()->{
            View target=find(activity.getWindow().getDecorView(),text,true);
            while(target!=null&&!target.isClickable()&&target.getParent() instanceof View)target=(View)target.getParent();
            if(target==null||!target.isClickable())throw new AssertionError("Missing action: "+text);
            if(!target.performClick())throw new AssertionError("Action failed: "+text);
        });
        waitForIdleSync();assertions++;
    }
    private void verifyIntent(String text,String action,boolean packageData){
        IntentFilter filter=new IntentFilter(action);
        if(packageData)filter.addDataScheme("package");
        ActivityMonitor monitor=addMonitor(filter,null,true);
        tap(text);
        if(monitor.getHits()!=1)throw new AssertionError("Wrong settings intent for "+text);
        assertions++;removeMonitor(monitor);
    }
    private void awaitRunning(){
        long until=SystemClock.uptimeMillis()+5000;
        while(!OverlayService.running&&SystemClock.uptimeMillis()<until)SystemClock.sleep(100);
        if(!OverlayService.running)throw new AssertionError("Foreground timer did not start");
        waitForIdleSync();assertions++;
    }
    private void awaitStopped(){
        long until=SystemClock.uptimeMillis()+5000;
        while(OverlayService.running&&SystemClock.uptimeMillis()<until)SystemClock.sleep(100);
        if(OverlayService.running)throw new AssertionError("Foreground timer did not stop");assertions++;
    }
    private AccessibilityNodeInfo capsule(boolean expanded){
        AccessibilityServiceInfo serviceInfo=getUiAutomation().getServiceInfo();
        serviceInfo.flags|=AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        getUiAutomation().setServiceInfo(serviceInfo);
        long until=SystemClock.uptimeMillis()+8000;
        while(SystemClock.uptimeMillis()<until){
            for(AccessibilityWindowInfo window:getUiAutomation().getWindows()){
                AccessibilityNodeInfo found=capsuleNode(window.getRoot(),expanded);
                if(found!=null){assertions++;return found;}
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Live timer accessibility action missing, expanded="+expanded);
    }
    private AccessibilityNodeInfo capsuleNode(AccessibilityNodeInfo node,boolean expanded){
        if(node==null)return null;
        String label=String.valueOf(node.getContentDescription());
        if(label.startsWith("Screen time today,")&&label.contains(expanded?"Hide app detail":"Show app detail"))return node;
        for(int i=0;i<node.getChildCount();i++){
            AccessibilityNodeInfo found=capsuleNode(node.getChild(i),expanded);if(found!=null)return found;
        }
        return null;
    }
    private android.graphics.Rect capsuleBounds(AccessibilityNodeInfo node){
        android.graphics.Rect bounds=new android.graphics.Rect();node.getBoundsInScreen(bounds);return bounds;
    }
    private void verifyCapsule(boolean large)throws Exception{
        AccessibilityNodeInfo compact=capsule(false);
        android.graphics.Rect compactBounds=capsuleBounds(compact);
        if(compactBounds.height()<Ui.dp(activity,48))throw new AssertionError("Capsule touch target is below 48dp");
        if(!large&&compactBounds.width()>Ui.dp(activity,136))throw new AssertionError("Compact timer is too wide");
        if(!compact.isClickable())throw new AssertionError("Capsule is not accessible as a control");
        assertions+=3;capture("capsule-compact");
        if(!compact.performAction(AccessibilityNodeInfo.ACTION_CLICK))throw new AssertionError("Cannot expand capsule");
        android.graphics.Rect expandedBounds=capsuleBounds(capsule(true));
        long expandedUntil=SystemClock.uptimeMillis()+4000;
        while(expandedBounds.height()<=compactBounds.height()&&SystemClock.uptimeMillis()<expandedUntil){
            SystemClock.sleep(100);expandedBounds=capsuleBounds(capsule(true));
        }
        if(expandedBounds.height()<=compactBounds.height())throw new AssertionError("App detail did not expand");
        assertions++;capture("capsule-expanded");
        if(!capsule(true).performAction(AccessibilityNodeInfo.ACTION_CLICK))throw new AssertionError("Cannot collapse capsule");
        compactBounds=capsuleBounds(capsule(false));
        int originalX=Ui.prefs(getTargetContext()).getInt("overlay_x",Ui.dp(activity,16));
        int displayWidth=getTargetContext().getResources().getDisplayMetrics().widthPixels;
        int displayHeight=getTargetContext().getResources().getDisplayMetrics().heightPixels;
        shell("input touchscreen swipe "+compactBounds.centerX()+" "+compactBounds.centerY()+" "+(displayWidth-Ui.dp(activity,32))+" "+(compactBounds.centerY()+Ui.dp(activity,120))+" 450");
        long until=SystemClock.uptimeMillis()+5000;
        while(Ui.prefs(getTargetContext()).getInt("overlay_x",originalX)==originalX&&SystemClock.uptimeMillis()<until)SystemClock.sleep(100);
        if(Ui.prefs(getTargetContext()).getInt("overlay_x",originalX)==originalX)throw new AssertionError("Drag position was not saved");
        android.graphics.Rect moved=capsuleBounds(capsule(false));
        if(moved.left<0||moved.top<0||moved.right>displayWidth||moved.bottom>displayHeight)throw new AssertionError("Capsule moved outside the display");
        assertions+=2;capture("capsule-docked");
    }
    private void verifyDayDialog()throws Exception{
        long until=SystemClock.uptimeMillis()+5000;
        while(SystemClock.uptimeMillis()<until){
            AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();
            if(root!=null){
                for(AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText("Done")){
                    if(node.isClickable()){
                        android.graphics.Rect bounds=capsuleBounds(node);
                        if(bounds.height()<Ui.dp(activity,48))throw new AssertionError("Day detail close action is too small");
                        capture("day-detail");
                        if(!node.performAction(AccessibilityNodeInfo.ACTION_CLICK))throw new AssertionError("Cannot close day detail");
                        assertions+=2;waitForIdleSync();return;
                    }
                }
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Day detail has no visible Done action");
    }
    private void checkTextLayout(View view){
        if(view instanceof TextView){
            TextView text=(TextView)view;
            if(text.getLayout()!=null){
                for(int i=0;i<text.getLineCount();i++){
                    if(text.getLayout().getEllipsisCount(i)>0)throw new AssertionError("Truncated setup label: "+text.getText());
                }
            }
            if(text.isClickable()&&text.getHeight()<Ui.dp(activity,48))throw new AssertionError("Small touch target: "+text.getText());
            assertions++;
        }
        if(view instanceof ViewGroup){ViewGroup parent=(ViewGroup)view;for(int i=0;i<parent.getChildCount();i++)checkTextLayout(parent.getChildAt(i));}
    }
    private String shell(String command)throws Exception{
        try(FileInputStream in=new ParcelFileDescriptor.AutoCloseInputStream(getUiAutomation().executeShellCommand(command))){
            String output=new String(in.readAllBytes(),StandardCharsets.UTF_8);
            shellTrace.append(command).append('\n').append(output).append('\n');
            return output;
        }
    }
    private void capture(String name)throws Exception{
        waitForIdleSync();
        Bitmap bitmap=getUiAutomation().takeScreenshot();
        if(bitmap==null)throw new AssertionError("Screenshot unavailable");
        File file=new File(getTargetContext().getFilesDir(),"qa-"+name+".png");
        try(FileOutputStream out=new FileOutputStream(file)){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}
        bitmap.recycle();
    }
}
