package work.timelens.app;

import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.ActivityNotFoundException;
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
    private volatile Activity activity;
    private int assertions;
    private final StringBuilder shellTrace=new StringBuilder();
    private volatile boolean activityResumed=true;

    @Override public void onCreate(Bundle args) { arguments=args;start(); }

    @Override public void onStart() {
        Bundle result=new Bundle();
        String scenario=arguments.getString("scenario","usage");
        try {
            if("design-large".equals(scenario)){
                Ui.prefs(getTargetContext()).edit().putBoolean("reduce_transparency",true).putBoolean("motion",false).putBoolean("haptics",false).commit();
            }
            if("channel".equals(scenario)){
                getTargetContext().getSystemService(NotificationManager.class).createNotificationChannel(
                        new NotificationChannel("timer","Live screen-time timer",NotificationManager.IMPORTANCE_NONE));
            }
            activity=startActivitySync(new Intent(getTargetContext(),MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            runOnMainSync(()->activity.getApplication().registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks(){
                public void onActivityCreated(Activity a,Bundle saved){if(a instanceof MainActivity){activity=a;activityResumed=false;}}
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
                    expect("0 of 2 required permissions allowed");
                    expect("Still needed: Usage access and Display over apps.");
                    assertVisible("Open usage access");
                    tap("Check permissions again");
                    expect("Still needed: Usage access and Display over apps.");
                    if(OverlayService.running)throw new AssertionError("Checking access started the timer");
                    assertions++;
                    capture("usage");
                    // Exercise an actual settings round trip without granting access.
                    tap("Open usage access");
                    awaitPaused();
                    // Recreate while Settings is on top: the pending target must survive.
                    runOnMainSync(()->activity.recreate());
                    shell("am start -W --activity-reorder-to-front -n work.timelens.app/.MainActivity");
                    await("Is the switch blocked?");
                    expect("Usage access is still off.");
                    expect("Allow restricted settings");
                    recreateAndAwait("Usage access is still off.");
                    verifyIntent("Open App info",Settings.ACTION_APPLICATION_DETAILS_SETTINGS,true);
                    tap("Open App info");
                    awaitPaused();
                    shell("am start -W --activity-reorder-to-front -n work.timelens.app/.MainActivity");
                    await("Back from App info.");
                    expect("Allowing restricted settings alone does not grant Usage access.");
                    capture("recovery");
                    tap("Not now · view dashboard");
                    expect("Continue permission setup");
                    tap("Continue permission setup");
                    expect("Is the switch blocked?");
                    shell("input keyevent KEYCODE_HOME");
                    awaitPaused();
                    shell("appops set work.timelens.app GET_USAGE_STATS allow");
                    shell("am start -W --activity-reorder-to-front -n work.timelens.app/.MainActivity");
                    await("Keep the total in sight");
                    expect("Still needed: Display over apps.");
                    break;
                case "overlay":
                    expect("Keep the total in sight");
                    expect("1 of 2 required permissions allowed");
                    verifyIntent("Open display settings",Settings.ACTION_MANAGE_OVERLAY_PERMISSION,true);
                    tap("Open display settings");
                    awaitPaused();
                    shell("am start -W --activity-reorder-to-front -n work.timelens.app/.MainActivity");
                    await("Display over apps is still off.");
                    recreateAndAwait("Display over apps is still off.");
                    expect("Still needed: Display over apps.");
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
                    shell("am start -W --activity-reorder-to-front -n work.timelens.app/.MainActivity");
                    await("A quick way to stop");
                    break;
                case "complete":
                    expect("App activity");
                    absent("SET UP TIMELENS");
                    capture("dashboard");
                    tapNavigation(R.id.nav_history);
                    expect("Your last seven days");
                    shell("input keyevent KEYCODE_BACK");
                    await("App activity");
                    // A real OS revocation must reopen setup on return.
                    shell("input keyevent KEYCODE_HOME");
                    awaitPaused();
                    shell("appops set work.timelens.app GET_USAGE_STATS deny");
                    shell("am start -W --activity-reorder-to-front -n work.timelens.app/.MainActivity");
                    await("Know where your day goes");
                    break;
                case "channel":
                    expect("A quick way to stop");
                    expect("2 of 2 required permissions allowed");
                    verifyIntent("Set up notifications",Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS,false);
                    break;
                case "settings":
                    onMainChecked(this::verifySettingsFallbacks);
                    verifyUnavailableSettings();
                    break;
                case "large":
                    expect("Know where your day goes");
                    tap("Usage switch disabled?");
                    expect("Is the switch blocked?");
                    auditCurrentLayout();
                    capture("large-text");
                    break;
                case "design":
                case "design-large":
                    expect("App activity");
                    auditCurrentLayout();
                    capture("today");
                    tapNavigation(R.id.nav_history);await("Yesterday");capture("history");
                    await("View app activity");tap("View app activity →");
                    verifyDayDialog();
                    tapNavigation(R.id.nav_settings);expect("Quiet by default.");
                    auditCurrentLayout();
                    capture("settings");
                    if("design".equals(scenario))verifyFeedbackSettings();
                    verifyAppIcon();
                    tapNavigation(R.id.nav_today);tap("Start floating timer");awaitRunning();
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
        if(view.getVisibility()!=View.VISIBLE)return null;
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
        boolean[] found={false};runOnMainSync(()->found[0]=activityResumed&&activity.hasWindowFocus()&&find(activity.getWindow().getDecorView(),text,false)!=null);return found[0];
    }
    private void expect(String text){await(text);}
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
        while(SystemClock.uptimeMillis()<until){
            if(!activityResumed){
                AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();
                if(root!=null&&!getTargetContext().getPackageName().contentEquals(String.valueOf(root.getPackageName()))){assertions++;return;}
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Android did not finish opening the external screen");
    }
    private void tap(String text){
        awaitAppFocus();
        onMainChecked(()->{
            View target=find(activity.getWindow().getDecorView(),text,true);
            while(target!=null&&!target.isClickable()&&target.getParent() instanceof View)target=(View)target.getParent();
            if(target==null||!target.isClickable())throw new AssertionError("Missing action: "+text);
            if(!target.performClick())throw new AssertionError("Action failed: "+text);
        });
        waitForIdleSync();assertions++;
    }
    private void onMainChecked(Runnable action){
        Throwable[] failure={null};
        runOnMainSync(()->{try{action.run();}catch(Throwable error){failure[0]=error;}});
        if(failure[0]!=null)throw new AssertionError("UI action failed",failure[0]);
    }
    private void tapNavigation(int id){
        awaitAppFocus();
        onMainChecked(()->{
            View target=activity.findViewById(id);
            if(target==null||!target.isShown()||!target.isClickable())throw new AssertionError("Navigation action is unavailable: "+id);
            if(!target.performClick()||!target.isSelected())throw new AssertionError("Navigation did not select: "+id);
        });
        waitForIdleSync();assertions++;
    }
    private void awaitAppFocus(){
        long until=SystemClock.uptimeMillis()+10000;
        while(SystemClock.uptimeMillis()<until){
            boolean[] focused={false};runOnMainSync(()->focused[0]=activityResumed&&activity.hasWindowFocus());
            if(focused[0])return;
            SystemClock.sleep(100);
        }
        throw new AssertionError("TimeLens did not regain foreground focus");
    }
    private void verifyIntent(String text,String action,boolean packageData){
        Intent[] launched={null};
        ActivityMonitor monitor=new ActivityMonitor(){
            @Override public ActivityResult onStartActivity(Intent intent){
                launched[0]=new Intent(intent);
                return new ActivityResult(Activity.RESULT_CANCELED,null);
            }
        };
        addMonitor(monitor);
        tap(text);
        removeMonitor(monitor);
        if(launched[0]==null||!action.equals(launched[0].getAction()))throw new AssertionError("Wrong settings intent for "+text);
        if(packageData&&!"package:work.timelens.app".equals(launched[0].getDataString()))throw new AssertionError("Wrong Settings package");
        if(Settings.ACTION_APP_NOTIFICATION_SETTINGS.equals(action)||Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS.equals(action)){
            if(!"work.timelens.app".equals(launched[0].getStringExtra(Settings.EXTRA_APP_PACKAGE)))throw new AssertionError("Missing notification package");
        }
        if(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS.equals(action)&&!"timer".equals(launched[0].getStringExtra(Settings.EXTRA_CHANNEL_ID)))throw new AssertionError("Wrong notification channel");
        assertions++;
    }
    private void recreateAndAwait(String text){
        Activity previous=activity;
        runOnMainSync(previous::recreate);
        long until=SystemClock.uptimeMillis()+8000;
        while((activity==previous||!activityResumed)&&SystemClock.uptimeMillis()<until)SystemClock.sleep(50);
        if(activity==previous)throw new AssertionError("Activity was not recreated");
        await(text);
    }
    private static final class LaunchProbe extends Activity {
        int attempts,failures;
        boolean securityFailure;
        Intent launched;
        @Override public void startActivity(Intent intent){
            attempts++;
            if(attempts<=failures){
                if(securityFailure)throw new SecurityException("Test: OEM blocked Settings");
                throw new ActivityNotFoundException("Test: no Settings handler");
            }
            launched=intent;
        }
    }
    private void verifySettingsFallbacks(){
        for(PermissionSettings.Target target:PermissionSettings.Target.values()){
            Intent[] intents=PermissionSettings.intents(getTargetContext(),target);
            for(boolean security:new boolean[]{false,true}){
                for(int failures=0;failures<=intents.length;failures++){
                    LaunchProbe probe=new LaunchProbe();probe.failures=failures;probe.securityFailure=security;
                    boolean opened=PermissionSettings.open(probe,intents);
                    if(opened!=(failures<intents.length)||probe.attempts!=Math.min(failures+1,intents.length))throw new AssertionError("Broken fallback for "+target);
                    if(opened&&probe.launched!=intents[failures])throw new AssertionError("Settings fallback order changed");
                    assertions++;
                }
            }
            if(!Settings.ACTION_SETTINGS.equals(intents[intents.length-1].getAction()))throw new AssertionError("Missing general Settings fallback");
        }
        Intent[] usage=PermissionSettings.intents(getTargetContext(),PermissionSettings.Target.USAGE);
        if(!Settings.ACTION_USAGE_ACCESS_SETTINGS.equals(usage[1].getAction())||usage[1].getData()!=null)throw new AssertionError("Usage list fallback needs no package URI");
        if(PermissionSettings.restore("unknown")!=null||PermissionSettings.restore(null)!=null)throw new AssertionError("Invalid restored target");
        assertions+=2;
    }
    private void verifyUnavailableSettings(){
        ActivityMonitor unavailable=new ActivityMonitor(){
            @Override public ActivityResult onStartActivity(Intent intent){throw new ActivityNotFoundException("Test: all Settings handlers absent");}
        };
        addMonitor(unavailable);
        tap("Open usage access");
        removeMonitor(unavailable);
        expect("Android could not open this settings screen.");
        recreateAndAwait("Android could not open this settings screen.");
        tap("Check permissions again");
        expect("0 of 2 required permissions allowed");
        absent("Start floating timer");
        if(OverlayService.running)throw new AssertionError("Failed settings navigation started the timer");
        assertions++;
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
        android.graphics.Rect originalBounds=new android.graphics.Rect(compactBounds);
        long collapsedUntil=SystemClock.uptimeMillis()+4000;
        do{
            compactBounds=capsuleBounds(capsule(false));
            if(compactBounds.height()<=originalBounds.height()&&compactBounds.width()==originalBounds.width())break;
            SystemClock.sleep(100);
        }while(SystemClock.uptimeMillis()<collapsedUntil);
        if(compactBounds.height()>originalBounds.height()||compactBounds.width()!=originalBounds.width())throw new AssertionError("Timer did not finish collapsing");
        if(compactBounds.left>originalBounds.left+Ui.dp(activity,4))throw new AssertionError("Expand/collapse moved the timer to the opposite edge");
        waitForIdleSync();assertions+=2;
        int originalX=Ui.prefs(getTargetContext()).getInt("overlay_x",Ui.dp(activity,16));
        int originalY=Ui.prefs(getTargetContext()).getInt("overlay_y",Ui.dp(activity,100));
        int displayWidth=getTargetContext().getResources().getDisplayMetrics().widthPixels;
        int displayHeight=getTargetContext().getResources().getDisplayMetrics().heightPixels;
        int targetX=compactBounds.centerX()<displayWidth/2?displayWidth-Ui.dp(activity,32):Ui.dp(activity,32);
        shell("input touchscreen swipe "+compactBounds.centerX()+" "+compactBounds.centerY()+" "+targetX+" "+(compactBounds.centerY()+Ui.dp(activity,120))+" 450");
        long until=SystemClock.uptimeMillis()+5000;
        while(Ui.prefs(getTargetContext()).getInt("overlay_x",originalX)==originalX&&Ui.prefs(getTargetContext()).getInt("overlay_y",originalY)==originalY&&SystemClock.uptimeMillis()<until)SystemClock.sleep(100);
        if(Ui.prefs(getTargetContext()).getInt("overlay_x",originalX)==originalX&&Ui.prefs(getTargetContext()).getInt("overlay_y",originalY)==originalY)throw new AssertionError("Drag position was not saved");
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
    private void verifyFeedbackSettings(){
        tap("Reduce transparency");
        if(!Ui.prefs(getTargetContext()).getBoolean("reduce_transparency",false))throw new AssertionError("Transparency preference did not save");
        if(WindowMaterial.allowed(getTargetContext()))throw new AssertionError("Reduce transparency did not disable glass");
        tap("Animations");if(Ui.animationsEnabled())throw new AssertionError("Motion preference was ignored");
        tap("Haptic feedback");if(Ui.prefs(getTargetContext()).getBoolean("haptics",true))throw new AssertionError("Haptic preference did not save");
        assertions+=4;
        tap("Reduce transparency");tap("Animations");tap("Haptic feedback");
    }
    private void verifyAppIcon()throws Exception{
        android.graphics.drawable.Drawable icon=getTargetContext().getPackageManager().getApplicationIcon("work.timelens.app");
        if(!(icon instanceof android.graphics.drawable.AdaptiveIconDrawable))throw new AssertionError("Launcher icon is not adaptive");
        if(android.os.Build.VERSION.SDK_INT>=33&&((android.graphics.drawable.AdaptiveIconDrawable)icon).getMonochrome()==null)throw new AssertionError("Themed launcher icon is missing");
        Bitmap bitmap=Bitmap.createBitmap(432,432,Bitmap.Config.ARGB_8888);
        icon.setBounds(0,0,432,432);icon.draw(new android.graphics.Canvas(bitmap));
        try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getFilesDir(),"qa-app-icon.png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}
        bitmap.recycle();assertions+=2;
    }
    private void checkTextLayout(View view){
        if(view.getVisibility()!=View.VISIBLE)return;
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
    private void auditCurrentLayout(){
        Throwable[] failure={null};
        runOnMainSync(()->{try{checkTextLayout(activity.getWindow().getDecorView());}catch(Throwable error){failure[0]=error;}});
        if(failure[0]!=null)throw new AssertionError("Layout audit failed",failure[0]);
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
