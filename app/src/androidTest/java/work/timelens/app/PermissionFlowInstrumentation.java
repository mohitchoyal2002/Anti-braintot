package work.timelens.app;

import android.app.Activity;
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
            waitForIdleSync();
            switch(scenario){
                case "usage":
                    expect("Make time visible.");
                    expect("Know where your day goes");
                    assertVisible("Open usage access");
                    capture("usage");
                    // Exercise an actual settings round trip without granting access.
                    tap("Open usage access");
                    SystemClock.sleep(500);
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
                    shell("appops set work.timelens.app GET_USAGE_STATS allow");
                    shell("am start --activity-reorder-to-front -n work.timelens.app/.MainActivity");
                    await("Keep the total in sight");
                    break;
                case "overlay":
                    expect("Keep the total in sight");
                    expect("1 of 3 permissions allowed");
                    verifyIntent("Allow floating timer",Settings.ACTION_MANAGE_OVERLAY_PERMISSION,true);
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
                    SystemClock.sleep(300);
                    shell("am start --activity-reorder-to-front -n work.timelens.app/.MainActivity");
                    await("A quick way to stop");
                    break;
                case "complete":
                    expect("Your time, in focus.");
                    absent("SET UP TIMELENS");
                    capture("dashboard");
                    tap("History");
                    expect("YOUR RECENT DAYS");
                    shell("input keyevent KEYCODE_BACK");
                    await("Your time, in focus.");
                    // A real OS revocation must reopen setup on return.
                    shell("input keyevent KEYCODE_HOME");
                    shell("appops set work.timelens.app GET_USAGE_STATS deny");
                    shell("am start --activity-reorder-to-front -n work.timelens.app/.MainActivity");
                    await("Know where your day goes");
                    break;
                case "channel":
                    expect("A quick way to stop");
                    expect("2 of 3 permissions allowed");
                    verifyIntent("Allow timer notifications",Settings.ACTION_APP_NOTIFICATION_SETTINGS,false);
                    break;
                case "large":
                    expect("Know where your day goes");
                    tap("Usage switch disabled?");
                    expect("Is the switch blocked?");
                    runOnMainSync(()->checkTextLayout(activity.getWindow().getDecorView()));
                    capture("large-text");
                    break;
                default:throw new AssertionError("Unknown scenario "+scenario);
            }
            result.putString("timelens_success","true");
            result.putString("stream","PASS "+scenario+": "+assertions+" assertions\n");
            finish(Activity.RESULT_OK,result);
        }catch(Throwable error){
            result.putString("timelens_success","false");
            result.putString("stream","FAIL "+scenario+": "+error+"\n"+android.util.Log.getStackTraceString(error));
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
            return new String(in.readAllBytes(),StandardCharsets.UTF_8);
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
