package work.timelens.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor();
    private final Map<String,AppRow> rows=new LinkedHashMap<>();
    private LinearLayout page,content,appList;
    private View navigation;
    private FrostedBar materialBar;
    private Dialog dayDialog;
    private ScrollView scroll;
    private PermissionState permissions;
    private boolean setupVisible, dismissedSetupThisVisit, externalSettings;
    private boolean showRecovery, notificationsSkipped, notificationRequestInFlight;
    private PermissionSettings.Target pendingSettings;
    private int restoredPermissionGrants=-1;
    private String permissionFeedback;
    private TextView todayTotal,currentApp,status,toggle,empty,reference;
    private ReferenceTrack ring;
    private UsageRepository repository;
    private UsageRepository.Snapshot snapshot;
    private boolean visible;
    private final Runnable permissionRecheck=()->{if(visible&&!isDestroyed())refreshPermissions(false);};
    private int tab;
    private ScheduledFuture<?> refreshJob;
    private android.window.OnBackInvokedCallback backCallback;
    private boolean backRegistered;
    private final List<TextView> tabs=new ArrayList<>();
    private final List<ImageView> tabIcons=new ArrayList<>();
    private final List<LinearLayout> tabSurfaces=new ArrayList<>();
    private final BroadcastReceiver timerState=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){if(visible)renderCurrent();}};

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        Ui.configure(this);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        if(Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);
        else getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|(Ui.dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR));
        if(Build.VERSION.SDK_INT>=33)backCallback=()->{if(setupVisible)leaveSetup();else if(tab!=0)showTab(0);};
        repository=UsageRepository.get(this);
        if(state!=null){
            setupVisible=state.getBoolean("setup");
            dismissedSetupThisVisit=state.getBoolean("dismissed");
            externalSettings=state.getBoolean("external");
            pendingSettings=PermissionSettings.restore(state.getString("pendingSettings"));
            showRecovery=state.getBoolean("recovery");
            notificationsSkipped=state.getBoolean("notificationsSkipped");
            notificationRequestInFlight=state.getBoolean("requestInFlight");
            permissionFeedback=state.getString("permissionFeedback");
            restoredPermissionGrants=state.getInt("permissionGrants",-1);
        }
        LinearLayout root=Ui.readingColumn(this); root.setBackgroundColor(Ui.BG);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            if(Build.VERSION.SDK_INT>=30){
                Insets safe=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
                v.setPadding(safe.left,safe.top,safe.right,safe.bottom);
            }else{
                DisplayCutout cutout=insets.getDisplayCutout();
                v.setPadding(Math.max(insets.getSystemWindowInsetLeft(),cutout==null?0:cutout.getSafeInsetLeft()),insets.getSystemWindowInsetTop(),Math.max(insets.getSystemWindowInsetRight(),cutout==null?0:cutout.getSafeInsetRight()),insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        LinearLayout brand=Ui.row(this);brand.setPadding(dp(24),dp(14),dp(24),dp(10));
        ImageView mark=new ImageView(this);mark.setImageResource(R.drawable.ic_timer);mark.setImageTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));brand.addView(mark,new LinearLayout.LayoutParams(dp(24),dp(24)));
        TextView logo=Ui.text(this,"TimeLens",20,Ui.INK,true);logo.setPadding(dp(10),0,0,0);brand.addView(logo,new LinearLayout.LayoutParams(0,-2,1));
        root.addView(brand);
        scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);scroll.setVerticalScrollBarEnabled(false);
        page=Ui.column(this);page.setPadding(dp(24),dp(10),dp(24),dp(24));scroll.addView(page);FrameLayout region=new FrameLayout(this);region.addView(scroll,new FrameLayout.LayoutParams(-1,-1));root.addView(region,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout nav=Ui.row(this);nav.setPadding(dp(8),dp(4),dp(8),dp(4));
        String[] labels={"Today","History","Settings"};
        int[] navIds={R.id.nav_today,R.id.nav_history,R.id.nav_settings};
        int[] symbols={R.drawable.ic_today,R.drawable.ic_history,R.drawable.ic_settings};
        for(int i=0;i<labels.length;i++) {
            final int index=i;LinearLayout surface=Ui.column(this);surface.setGravity(Gravity.CENTER);surface.setPadding(dp(8),dp(10),dp(8),dp(10));surface.setMinimumHeight(dp(60));
            surface.setId(navIds[i]);
            ImageView symbol=new ImageView(this);symbol.setImageResource(symbols[i]);surface.addView(symbol,new LinearLayout.LayoutParams(dp(22),dp(22)));Ui.gap(surface,6);
            TextView t=Ui.text(this,labels[i],12,Ui.MUTED,true);surface.addView(t);surface.setContentDescription(labels[i]);symbol.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);t.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);surface.setFocusable(true);surface.setOnClickListener(v->{if(tab!=index){showTab(index);Ui.haptic(v,HapticFeedbackConstants.CLOCK_TICK);Ui.enter(page);}if(Ui.animationsEnabled())scroll.smoothScrollTo(0,0);else scroll.scrollTo(0,0);});
            LinearLayout.LayoutParams cell=new LinearLayout.LayoutParams(0,-2,1);cell.setMargins(dp(3),0,dp(3),0);nav.addView(surface,cell);tabs.add(t);tabIcons.add(symbol);tabSurfaces.add(surface);
        }
        materialBar=new FrostedBar(this,scroll,nav);navigation=materialBar;
        FrameLayout.LayoutParams barSize=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);barSize.setMargins(dp(12),0,dp(12),dp(12));region.addView(materialBar,barSize);
        materialBar.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{int bottom=materialBar.getVisibility()==View.VISIBLE?materialBar.getHeight()+dp(44):dp(24);if(page.getPaddingBottom()!=bottom)page.setPadding(dp(24),dp(10),dp(24),bottom);});
        scroll.getViewTreeObserver().addOnScrollChangedListener(()->materialBar.refresh());
        FrameLayout frame=new FrameLayout(this);frame.setBackgroundColor(Ui.BG);
        frame.addView(root,new FrameLayout.LayoutParams(-1,-1,Gravity.TOP|Gravity.CENTER_HORIZONTAL));setContentView(frame);root.requestApplyInsets();
        if(Build.VERSION.SDK_INT>=30){
            WindowInsetsController controller=getWindow().getInsetsController();
            if(controller!=null)controller.setSystemBarsAppearance(Ui.dark?0:WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        }
        if(Build.VERSION.SDK_INT>=33)registerReceiver(timerState,new IntentFilter(OverlayService.STATE_CHANGED),Context.RECEIVER_NOT_EXPORTED);else registerReceiver(timerState,new IntentFilter(OverlayService.STATE_CHANGED));
        tab=state==null?0:state.getInt("tab",0);
    }
    private int dp(float n){return Ui.dp(this,n);}
    @Override protected void onResume() {
        super.onResume();
        visible=true;
        PermissionState resumedState=PermissionState.read(this);
        if((permissions!=null&&!resumedState.sameAs(permissions))
                ||(restoredPermissionGrants>=0&&restoredPermissionGrants!=resumedState.grantMask()))permissionFeedback=null;
        restoredPermissionGrants=-1;
        permissions=resumedState;
        if(permissions.usage)showRecovery=false;
        if(pendingSettings!=null){
            settingsReturned(pendingSettings);
            pendingSettings=null;
        }
        externalSettings=false;
        if(!dismissedSetupThisVisit && !permissions.complete())setupVisible=true;
        renderCurrent();
        // Settings/AppOps changes can arrive just after onResume on an OEM build.
        // These bounded retries also cover returning with an unchanged activity instance.
        schedulePermissionRechecks();
        if(refreshJob!=null)refreshJob.cancel(false);
        refreshJob=worker.scheduleWithFixedDelay(()->{
            UsageRepository.Snapshot s=repository.refresh();
            main.post(()->{
                if(!visible||isDestroyed())return;
                snapshot=s;
                refreshPermissions(false);
                if(!setupVisible&&tab==0)updateToday();
            });
        },0,2,TimeUnit.SECONDS);
    }
    @Override protected void onPause() {
        visible=false;
        main.removeCallbacks(permissionRecheck);
        // A quick app switch can resume without onStop on newer Android releases.
        if(!externalSettings&&!notificationRequestInFlight&&!isChangingConfigurations()){
            dismissedSetupThisVisit=false;
            notificationsSkipped=false;
        }
        if(refreshJob!=null)refreshJob.cancel(false);
        worker.execute(repository::flush);
        super.onPause();
    }
    @Override protected void onDestroy() {
        if(Build.VERSION.SDK_INT>=33&&backRegistered)getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
        if(dayDialog!=null)dayDialog.dismiss();
        visible=false;main.removeCallbacksAndMessages(null);unregisterReceiver(timerState);worker.shutdown();super.onDestroy();
    }
    @Override public void onSaveInstanceState(Bundle out){
        out.putInt("tab",tab);
        out.putBoolean("setup",setupVisible);
        out.putBoolean("dismissed",dismissedSetupThisVisit);
        out.putBoolean("external",externalSettings);
        out.putString("pendingSettings",pendingSettings==null?null:pendingSettings.name());
        out.putBoolean("recovery",showRecovery);
        out.putBoolean("notificationsSkipped",notificationsSkipped);
        out.putBoolean("requestInFlight",notificationRequestInFlight);
        out.putString("permissionFeedback",permissionFeedback);
        if(permissions!=null)out.putInt("permissionGrants",permissions.grantMask());
        super.onSaveInstanceState(out);
    }
    @Override public void onBackPressed(){
        if(setupVisible){leaveSetup();return;}
        if(tab!=0){showTab(0);return;}
        super.onBackPressed();
    }
    private void clearPage(){
        page.removeAllViews();rows.clear();
        todayTotal=null;currentApp=null;status=null;toggle=null;empty=null;reference=null;ring=null;appList=null;content=null;
    }
    private void renderCurrent(){if(setupVisible)renderSetup();else showTab(tab);}
    private void schedulePermissionRechecks(){
        main.removeCallbacks(permissionRecheck);
        main.postDelayed(permissionRecheck,250);
        main.postDelayed(permissionRecheck,750);
        main.postDelayed(permissionRecheck,1500);
    }
    @Override public void onWindowFocusChanged(boolean focused){
        super.onWindowFocusChanged(focused);
        if(focused&&visible)schedulePermissionRechecks();
    }
    private void refreshPermissions(boolean manual){
        PermissionState actual=PermissionState.read(this);
        boolean changed=!actual.sameAs(permissions);
        permissions=actual;
        if(changed){
            permissionFeedback=null;
            if(actual.usage)showRecovery=false;
            if(!dismissedSetupThisVisit&&!actual.complete())setupVisible=true;
        }
        if(manual){
            permissionFeedback=actual.requiredStatus()+(!actual.notifications?" Timer notifications are off · optional.":"");
        }
        if(changed||manual){
            renderCurrent();
            if(setupVisible)scroll.scrollTo(0,0);
        }
    }
    private void settingsReturned(PermissionSettings.Target target){
        permissionFeedback=null;
        if(permissions.usage)showRecovery=false;
        if(target!=PermissionSettings.Target.APP_INFO){
            setupVisible=true;dismissedSetupThisVisit=false;
        }
        if(setupVisible)scroll.scrollTo(0,0);
        switch(target){
            case USAGE:
                showRecovery=!permissions.usage;
                permissionFeedback=permissions.usage?"Usage access is allowed.":"Usage access is still off. If you left without enabling it, try again. If Android blocked the switch, use the recovery steps below.";
                break;
            case OVERLAY:
                permissionFeedback=permissions.overlay?"Display over apps is allowed.":"Display over apps is still off. Select TimeLens in Android’s list and enable Allow display over other apps, then return here.";
                break;
            case NOTIFICATIONS:
            case NOTIFICATION_CHANNEL:
                permissionFeedback=permissions.notifications?"Timer notifications are allowed.":"Timer notifications are still off. Check both app notifications and the Live screen-time timer category, or continue without them.";
                break;
            case APP_INFO:
                if(showRecovery&&!permissions.usage)
                    permissionFeedback="Back from App info. Open Usage access again and enable TimeLens. Allowing restricted settings alone does not grant Usage access.";
                break;
        }
    }
    private void openSetup(){
        setupVisible=true;dismissedSetupThisVisit=false;permissionFeedback=null;permissions=PermissionState.read(this);
        scroll.smoothScrollTo(0,0);renderSetup();
    }
    private void leaveSetup(){
        setupVisible=false;dismissedSetupThisVisit=true;permissionFeedback=null;
        showTab(tab);scroll.smoothScrollTo(0,0);
    }
    private void renderSetup(){
        clearPage();navigation.setVisibility(View.GONE);page.setPadding(dp(24),dp(10),dp(24),dp(24));
        updateBackCallback();
        PermissionSetup.build(this,page,permissions,showRecovery,notificationsSkipped,permissionFeedback,new PermissionSetup.Actions(){
            public void usage(){usageSettings();}
            public void overlay(){overlaySettings();}
            public void notifications(){notificationSettings();}
            public void appInfo(){appInfoSettings();}
            public void recovery(){showRecovery=true;permissionFeedback=null;renderSetup();}
            public void recheck(){refreshPermissions(true);}
            public void skipNotifications(){notificationsSkipped=true;permissionFeedback=null;renderSetup();scroll.smoothScrollTo(0,0);}
            public void dashboard(){leaveSetup();}
            public void start(){leaveSetup();startTimer();}
        });
    }
    private void showTab(int index) {
        tab=index;setupVisible=false;navigation.setVisibility(View.VISIBLE);clearPage();
        updateBackCallback();
        for(int i=0;i<tabs.size();i++){
            boolean selected=i==tab;
            tabs.get(i).setTextColor(selected?Ui.ACCENT:Ui.MUTED);
            tabIcons.get(i).setImageTintList(android.content.res.ColorStateList.valueOf(selected?Ui.ACCENT:Ui.MUTED));
            tabSurfaces.get(i).setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(Ui.RIPPLE),Ui.rounded(selected?Ui.TINT:0x00000000,28,this),null));
            tabSurfaces.get(i).setSelected(selected);
        }
        if(index==0)buildToday();else if(index==1)buildHistory();else buildSettings();
    }
    private void updateBackCallback(){
        if(Build.VERSION.SDK_INT<33)return;
        boolean intercept=setupVisible||tab!=0;
        if(intercept==backRegistered)return;
        if(intercept)getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,backCallback);
        else getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
        backRegistered=intercept;
    }
    private void heading(String eyebrow,String title,String subtitle) {
        if(!eyebrow.isEmpty()){page.addView(Ui.text(this,eyebrow,12,Ui.MUTED,true));Ui.gap(page,8);}page.addView(Ui.text(this,title,34,Ui.INK,true));Ui.gap(page,8);
        TextView sub=Ui.text(this,subtitle,14,Ui.MUTED,false);sub.setLineSpacing(dp(3),1);page.addView(sub);Ui.gap(page,24);
    }
    private void buildToday() {
        heading("", "Today", LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM",Locale.ENGLISH)));
        LinearLayout hero=Ui.card(this);
        LinearLayout caption=Ui.row(this);
        ImageView icon=new ImageView(this);icon.setImageResource(R.drawable.ic_today);
        icon.setImageTintList(android.content.res.ColorStateList.valueOf(Ui.MUTED));
        caption.addView(icon,new LinearLayout.LayoutParams(dp(18),dp(18)));
        TextView label=Ui.text(this,"Screen time",17,Ui.MUTED,false);label.setPadding(dp(8),0,0,0);caption.addView(label);hero.addView(caption);
        Ui.gap(hero,16);
        todayTotal=Ui.text(this,"—",48,Ui.INK,true);hero.addView(todayTotal);
        Ui.gap(hero,18);
        ring=new ReferenceTrack(this);hero.addView(ring,new LinearLayout.LayoutParams(-1,dp(12)));
        Ui.gap(hero,12);
        reference=Ui.text(this,"Daily reference · "+Ui.duration(Ui.prefs(this).getInt("goal",180)*60000L),13,Ui.MUTED,false);hero.addView(reference);
        Ui.gap(hero,18);Ui.divider(hero);Ui.gap(hero,14);
        currentApp=Ui.text(this,"Waiting for Android usage data",14,Ui.MUTED,false);hero.addView(currentApp);
        page.addView(hero);Ui.gap(page,16);
        buildAccessCard(page);Ui.gap(page,28);
        page.addView(Ui.text(this,"App activity",22,Ui.INK,true));Ui.gap(page,6);
        page.addView(Ui.text(this,"Time spent in each app today",14,Ui.MUTED,false));Ui.gap(page,16);
        appList=Ui.column(this);page.addView(appList);empty=Ui.text(this,"Allow usage access to see your app activity.",17,Ui.MUTED,false);appList.addView(empty);
        Ui.gap(page,20);page.addView(Ui.text(this,"Includes active app use. Screen-off time, the lock screen and the home screen are excluded.",13,Ui.MUTED,false));
        if(snapshot!=null)updateToday();
    }
    private void buildAccessCard(LinearLayout parent) {
        PermissionState state=PermissionState.read(this);
        LinearLayout card=Ui.card(this);
        card.addView(Ui.text(this,state.canTrack()?"Floating timer":"Finish setting up",18,Ui.INK,true));
        Ui.gap(card,8);
        card.addView(Ui.text(this,state.canTrack()?"A small clock and today’s total. Tap it for your current app’s time.":state.requiredStatus(),17,Ui.MUTED,false));
        Ui.gap(card,16);
        if(!state.canTrack()){
            card.addView(Ui.button(this,"Continue permission setup",true,this::openSetup));
        }else{
            toggle=Ui.button(this,OverlayService.running?"Stop floating timer":"Start floating timer",!OverlayService.running,()->{
                if(OverlayService.running){
                    toggle.setText("Stopping timer…");stopService(new Intent(this,OverlayService.class));
                    Ui.prefs(this).edit().putBoolean("enabled",false).apply();
                }else startTimer();
            });
            card.addView(toggle);
            if(!state.notifications){
                Ui.gap(card,12);
                card.addView(Ui.text(this,"Timer notifications are off. You can stop the timer here.",14,Ui.MUTED,false));
                Ui.gap(card,10);card.addView(Ui.button(this,"Review permissions",false,this::openSetup));
            }
        }
        parent.addView(card);
    }
    private void updateToday() {
        if(snapshot==null||todayTotal==null||tab!=0)return;
        todayTotal.setText(snapshot.permission?Ui.duration(snapshot.usage.total):"—");
        String active=snapshot.usage.activePackage;
        currentApp.setText(snapshot.issue!=null?snapshot.issue:!snapshot.permission?"Usage Access is needed to show your time.":active==null?"Between apps · your timer is paused":"Now: "+Ui.appName(this,active)+"  ·  "+Ui.duration(snapshot.usage.perApp.getOrDefault(active,0L))+" today");
        long goal=Ui.prefs(this).getInt("goal",180)*60000L;
        ring.setUsage(snapshot.permission?snapshot.usage.total:0,goal);
        reference.setText(snapshot.permission?Ui.duration(goal)+" daily reference · "+Math.round(snapshot.usage.total*100.0/Math.max(1,goal))+"%":"Allow usage access to see daily progress");
        List<Map.Entry<String,Long>> entries=new ArrayList<>(snapshot.usage.perApp.entrySet());entries.sort((a,b)->Long.compare(b.getValue(),a.getValue()));
        List<String> order=new ArrayList<>();for(Map.Entry<String,Long> e:entries)order.add(e.getKey());
        if(!order.equals(new ArrayList<>(rows.keySet()))) {
            appList.removeAllViews();rows.clear();
            if(entries.isEmpty()){empty=Ui.text(this,snapshot.permission?"No recorded app usage yet. Use an app, then return here.":"Grant Usage Access to see your app breakdown.",14,Ui.MUTED,false);appList.addView(empty);}
            for(Map.Entry<String,Long> e:entries){AppRow r=new AppRow(e.getKey());rows.put(e.getKey(),r);appList.addView(r.view);Ui.gap(appList,10);}
        } else if(entries.isEmpty())empty.setText(snapshot.permission?"No recorded app usage yet. Use an app, then return here.":"Grant Usage Access to see your app breakdown.");
        for(Map.Entry<String,Long> e:entries){AppRow r=rows.get(e.getKey());r.duration.setText(Ui.duration(e.getValue()));r.progress.setProgress(snapshot.usage.total==0?0:(int)(e.getValue()*1000/snapshot.usage.total),Ui.animationsEnabled());}
        if(toggle!=null){toggle.setText(OverlayService.running?"Stop floating timer":"Start floating timer");}
        if(materialBar!=null)materialBar.refresh();
    }
    private final class AppRow {
        final LinearLayout view;final TextView duration;final ProgressBar progress;
        AppRow(String pkg){
            view=Ui.card(MainActivity.this);view.setPadding(dp(16),dp(16),dp(16),dp(16));LinearLayout line=Ui.row(MainActivity.this);
            ImageView icon=new ImageView(MainActivity.this);icon.setBackground(Ui.rounded(Ui.LINE,10,MainActivity.this));icon.setClipToOutline(true);try{icon.setImageDrawable(getPackageManager().getApplicationIcon(pkg));}catch(PackageManager.NameNotFoundException e){icon.setImageResource(R.drawable.ic_launcher);}line.addView(icon,new LinearLayout.LayoutParams(dp(36),dp(36)));
            LinearLayout names=Ui.column(MainActivity.this);names.setPadding(dp(12),0,dp(8),0);TextView name=Ui.text(MainActivity.this,Ui.appName(MainActivity.this,pkg),15,Ui.INK,true);names.addView(name);line.addView(names,new LinearLayout.LayoutParams(0,-2,1));
            duration=Ui.text(MainActivity.this,"0s",14,Ui.INK,true);
            if(getResources().getConfiguration().fontScale>1.2f){Ui.gap(names,6);names.addView(duration);}else line.addView(duration);
            view.addView(line);Ui.gap(view,12);
            progress=new ProgressBar(MainActivity.this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(1000);progress.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));progress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Ui.LINE));view.addView(progress,new LinearLayout.LayoutParams(-1,dp(4)));
        }
    }
    private void buildHistory() {
        heading("", "History", "Your last seven days, saved on this phone.");
        content=Ui.column(this);page.addView(content);content.addView(Ui.text(this,"Loading saved days…",14,Ui.MUTED,false));
        worker.execute(()->{repository.flush();Map<LocalDate,Long> history=repository.history();main.post(()->{
            if(!visible||tab!=1)return;content.removeAllViews();
            if(snapshot!=null&&snapshot.permission)history.put(snapshot.day,snapshot.usage.total);
            long max=history.values().stream().mapToLong(Long::longValue).max().orElse(1);
            for(int i=0;i<7;i++){
                LocalDate date=LocalDate.now().minusDays(i);Long ms=history.get(date);
                LinearLayout c=Ui.card(this);
                String day=i==0?"Today":i==1?"Yesterday":date.format(DateTimeFormatter.ofPattern("EEE, d MMM",Locale.ENGLISH));
                LinearLayout line=getResources().getConfiguration().fontScale>1.2f?Ui.column(this):Ui.row(this);
                line.addView(Ui.text(this,day,16,Ui.INK,true),new LinearLayout.LayoutParams(getResources().getConfiguration().fontScale>1.2f?-1:0,-2,getResources().getConfiguration().fontScale>1.2f?0:1));
                if(getResources().getConfiguration().fontScale>1.2f)Ui.gap(line,6);
                line.addView(Ui.text(this,ms==null?"No saved data":Ui.duration(ms),15,ms==null?Ui.MUTED:Ui.INK,true));c.addView(line);
                if(ms!=null){
                    Ui.gap(c,12);ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(1000);
                    bar.setProgress((int)(ms*1000/Math.max(1,max)));bar.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));
                    bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Ui.LINE));c.addView(bar,new LinearLayout.LayoutParams(-1,dp(4)));
                    Ui.gap(c,12);c.addView(Ui.text(this,"View app activity →",13,Ui.ACCENT,true));
                    c.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(Ui.RIPPLE),Ui.rounded(Ui.CARD,18,this),null));
                    c.setOnClickListener(v->{Ui.haptic(v,HapticFeedbackConstants.CLOCK_TICK);showDay(date);});c.setFocusable(true);c.setMinimumHeight(dp(64));
                    c.setContentDescription(day+", "+Ui.duration(ms)+". View app activity.");
                }
                content.addView(c);Ui.gap(content,12);
            }
            Ui.gap(content,12);content.addView(Ui.text(this,"History starts when you first open TimeLens. Android keeps recent activity for a limited period; older missing records are shown as unavailable.",12,Ui.MUTED,false));
        });});
    }
    private void showDay(LocalDate date) {
        worker.execute(()->{Map<String,Long> data=repository.appsFor(date);main.post(()->{
            if(!visible||isDestroyed()||(dayDialog!=null&&dayDialog.isShowing()))return;
            Dialog dialog=new Dialog(this,R.style.FloatingMaterialTheme);dayDialog=dialog;dialog.setOnDismissListener(d->dayDialog=null);
            LinearLayout body=Ui.card(this);body.setBackgroundColor(Color.TRANSPARENT);
            body.addView(Ui.text(this,date.format(DateTimeFormatter.ofPattern("d MMMM yyyy",Locale.ENGLISH)),22,Ui.INK,true));Ui.gap(body,16);
            ScrollView detailScroll=new ScrollView(this);detailScroll.setVerticalScrollBarEnabled(false);
            LinearLayout apps=Ui.column(this);
            List<Map.Entry<String,Long>> entries=new ArrayList<>(data.entrySet());entries.sort((a,b)->Long.compare(b.getValue(),a.getValue()));
            for(Map.Entry<String,Long> e:entries){
                LinearLayout row=Ui.column(this);row.setPadding(0,dp(14),0,dp(14));
                row.addView(Ui.text(this,Ui.appName(this,e.getKey()),16,Ui.INK,true));Ui.gap(row,5);
                row.addView(Ui.text(this,Ui.duration(e.getValue())+" in this app",17,Ui.MUTED,false));
                apps.addView(row);Ui.divider(apps);
            }
            if(data.isEmpty())apps.addView(Ui.text(this,"No app activity saved for this day.",17,Ui.MUTED,false));
            detailScroll.addView(apps);body.addView(detailScroll,new LinearLayout.LayoutParams(-1,0,1));
            Ui.gap(body,16);body.addView(Ui.button(this,"Done",false,dialog::dismiss));
            dialog.setContentView(body);new WindowMaterial(this,dialog.getWindow(),true);dialog.show();
            if(dialog.getWindow()!=null){
                Rect bounds=Build.VERSION.SDK_INT>=30?getWindowManager().getCurrentWindowMetrics().getBounds():new Rect(0,0,getResources().getDisplayMetrics().widthPixels,getResources().getDisplayMetrics().heightPixels);
                dialog.getWindow().setGravity(Gravity.BOTTOM);
                dialog.getWindow().setLayout(Math.min(bounds.width()-dp(24),dp(560)),(int)(bounds.height()*0.72));
            }
        });});
    }
    private void buildSettings() {
        heading("", "Settings", "A little awareness, your way.");
        LinearLayout appearance=Ui.card(this);
        appearance.addView(Ui.text(this,"Floating timer",20,Ui.INK,true));Ui.gap(appearance,8);
        appearance.addView(Ui.text(this,"Quiet by default. More detail with a tap.",17,Ui.MUTED,false));Ui.gap(appearance,24);
        TimerCapsule preview=new TimerCapsule(this);
        boolean expanded=!Ui.prefs(this).getBoolean("compact",true);
        preview.setExpanded(expanded);
        long total=snapshot==null?0:snapshot.usage.total;
        String active=snapshot==null?null:snapshot.usage.activePackage;
        preview.update(total,active==null?null:Ui.appName(this,active),snapshot==null?0:snapshot.usage.perApp.getOrDefault(active,0L),snapshot!=null&&snapshot.issue!=null);
        int available=Math.max(dp(48),getResources().getDisplayMetrics().widthPixels-dp(96));
        LinearLayout.LayoutParams previewSize=new LinearLayout.LayoutParams(TimerCapsule.width(this,expanded,available),-2);
        previewSize.gravity=Gravity.CENTER_HORIZONTAL;appearance.addView(preview,previewSize);
        Ui.gap(appearance,24);
        Switch details=Ui.settingToggle(this,appearance,"Show app detail",expanded);
        details.setOnCheckedChangeListener((button,checked)->{
            Ui.prefs(this).edit().putBoolean("compact",!checked).apply();preview.setExpanded(checked);
            previewSize.width=TimerCapsule.width(this,checked,available);preview.setLayoutParams(previewSize);
        });
        preview.setOnClickListener(v->details.performClick());
        Ui.gap(appearance,8);
        appearance.addView(Ui.text(this,"Tap the timer to expand or collapse. Drag it to an edge. Stop it from Today or the notification.",14,Ui.MUTED,false));Ui.gap(appearance,16);
        appearance.addView(Ui.button(this,"Reset timer position",false,()->{Ui.prefs(this).edit().remove("overlay_x").remove("overlay_y").apply();if(OverlayService.running)startService(new Intent(this,OverlayService.class).setAction(OverlayService.RESET_POSITION));else Toast.makeText(this,"Position reset for next start",Toast.LENGTH_SHORT).show();}));
        page.addView(appearance);Ui.gap(page,16);
        LinearLayout goal=Ui.card(this);goal.addView(Ui.text(this,"Daily reference",20,Ui.INK,true));Ui.gap(goal,8);
        goal.addView(Ui.text(this,"A personal reference for your day. It never blocks apps.",17,Ui.MUTED,false));Ui.gap(goal,16);
        int minutes=Ui.prefs(this).getInt("goal",180);
        TextView goalText=Ui.text(this,Ui.duration(minutes*60000L)+" per day",26,Ui.INK,true);goal.addView(goalText);Ui.gap(goal,10);
        SeekBar seek=new SeekBar(this);seek.setMax(46);seek.setProgress((minutes-30)/15);seek.setMinimumHeight(dp(48));
        seek.setContentDescription("Daily screen-time reference in fifteen-minute steps");seek.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));goal.addView(seek);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar bar,int value,boolean user){int m=30+value*15;goalText.setText(Ui.duration(m*60000L)+" per day");if(user){Ui.prefs(MainActivity.this).edit().putInt("goal",m).apply();Ui.haptic(bar,HapticFeedbackConstants.CLOCK_TICK);}}public void onStartTrackingTouch(SeekBar bar){}public void onStopTrackingTouch(SeekBar bar){}});
        page.addView(goal);Ui.gap(page,16);
        LinearLayout feel=Ui.card(this);feel.addView(Ui.text(this,"Appearance & feedback",20,Ui.INK,true));Ui.gap(feel,12);
        Switch transparency=Ui.settingToggle(this,feel,"Reduce transparency",Ui.prefs(this).getBoolean("reduce_transparency",false));
        transparency.setOnCheckedChangeListener((button,checked)->{Ui.prefs(this).edit().putBoolean("reduce_transparency",checked).apply();materialBar.refresh();});
        Switch motion=Ui.settingToggle(this,feel,"Animations",Ui.prefs(this).getBoolean("motion",true));
        motion.setOnCheckedChangeListener((button,checked)->{Ui.prefs(this).edit().putBoolean("motion",checked).apply();Ui.motion=checked;});
        Switch haptics=Ui.settingToggle(this,feel,"Haptic feedback",Ui.prefs(this).getBoolean("haptics",true));
        haptics.setOnCheckedChangeListener((button,checked)->Ui.prefs(this).edit().putBoolean("haptics",checked).apply());
        Ui.gap(feel,8);feel.addView(Ui.text(this,"Frosted controls become solid when needed for readability or battery saving.",14,Ui.MUTED,false));page.addView(feel);Ui.gap(page,16);
        PermissionState state=PermissionState.read(this);
        LinearLayout access=Ui.card(this);
        access.addView(Ui.text(this,"Permissions & battery",18,Ui.INK,true));Ui.gap(access,14);
        access.addView(Ui.text(this,"Usage access: "+(state.usage?"Allowed":"Not allowed")+"\nDisplay over apps: "+(state.overlay?"Allowed":"Not allowed")+"\nNotifications: "+(state.notifications?"Allowed":"Off · optional"),17,Ui.MUTED,false));
        Ui.gap(access,16);access.addView(Ui.button(this,"Review permission setup",!state.complete(),this::openSetup));Ui.gap(access,10);
        access.addView(Ui.button(this,"Open app & battery settings",false,this::appInfoSettings));Ui.gap(access,10);
        LinearLayout shortcuts=Ui.column(this);
        shortcuts.addView(Ui.button(this,"Usage access settings",false,this::usageSettings));Ui.gap(shortcuts,10);
        shortcuts.addView(Ui.button(this,"Display over apps settings",false,this::overlaySettings));Ui.gap(shortcuts,10);
        shortcuts.addView(Ui.button(this,"Notification settings",false,this::notificationSettings));
        shortcuts.setVisibility(View.GONE);
        TextView disclosure=Ui.button(this,"Show Android settings",false,()->{});
        disclosure.setOnClickListener(v->{Ui.haptic(v,HapticFeedbackConstants.CLOCK_TICK);boolean open=shortcuts.getVisibility()==View.VISIBLE;shortcuts.setVisibility(open?View.GONE:View.VISIBLE);disclosure.setText(open?"Show Android settings":"Hide Android settings");});
        access.addView(disclosure);Ui.gap(access,10);access.addView(shortcuts);
        Ui.gap(access,14);access.addView(Ui.text(this,"If Samsung pauses the timer, set TimeLens battery use to Unrestricted and remove it from Sleeping apps. After a restart, open TimeLens and start the timer again.",13,Ui.MUTED,false));page.addView(access);Ui.gap(page,16);
        LinearLayout about=Ui.card(this);about.addView(Ui.text(this,"Designed around your privacy",18,Ui.INK,true));Ui.gap(about,10);about.addView(Ui.text(this,"TimeLens reads app names and activity timestamps through Android Usage Access. It cannot read chats, capture the screen, count reels or track audio playing with the screen off. There is no Internet permission. Some apps, including banking and permission screens, can hide overlays. Split screen uses the latest resumed app as an approximation.",13,Ui.MUTED,false));Ui.gap(about,14);about.addView(Ui.text(this,"TimeLens "+versionName()+" · Personal test build",11,Ui.ACCENT,true));page.addView(about);
    }
    private String versionName(){
        try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}
        catch(PackageManager.NameNotFoundException e){return "";}
    }
    private void usageSettings(){
        openSettings(PermissionSettings.Target.USAGE);
    }
    private void overlaySettings(){
        openSettings(PermissionSettings.Target.OVERLAY);
    }
    private void appInfoSettings(){
        openSettings(PermissionSettings.Target.APP_INFO);
    }
    private void notificationSettings(){
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED
                && (!Ui.prefs(this).getBoolean("notifications_requested",false) || shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))){
            notificationRequestInFlight=true;
            Ui.prefs(this).edit().putBoolean("notifications_requested",true).apply();
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},30);
        }else{
            PermissionState actual=PermissionState.read(this);
            openSettings(actual.notificationChannelBlocked?PermissionSettings.Target.NOTIFICATION_CHANNEL:PermissionSettings.Target.NOTIFICATIONS);
        }
    }
    @Override public void onRequestPermissionsResult(int code,String[] names,int[] results){
        super.onRequestPermissionsResult(code,names,results);
        if(code!=30)return;
        notificationRequestInFlight=false;
        setupVisible=true;dismissedSetupThisVisit=false;
        permissions=PermissionState.read(this);
        permissionFeedback=permissions.notifications?null:"Notifications are still off. Try again to review Android’s settings, or continue without them.";
        renderCurrent();
    }
    private void openSettings(PermissionSettings.Target target){
        permissionFeedback=null;pendingSettings=target;externalSettings=true;
        if(!PermissionSettings.open(this,PermissionSettings.intents(this,target))){
            externalSettings=false;pendingSettings=null;
            permissions=PermissionState.read(this);
            permissionFeedback="Android could not open this settings screen. Open Settings → Apps → Special app access for Usage access or Display over other apps. For notifications or restricted settings, open Apps → TimeLens. Return here and tap Check permissions again.";
            setupVisible=true;renderSetup();
        }
    }
    private void startTimer(){
        permissions=PermissionState.read(this);
        if(!permissions.canTrack()){openSetup();return;}
        try{
            if(toggle!=null)toggle.setText("Starting timer…");
            startForegroundService(new Intent(this,OverlayService.class));
        }catch(RuntimeException e){
            permissionFeedback="The timer could not start. Review permissions and battery settings, then try again.";
            setupVisible=true;renderSetup();
        }
    }
    /** A quiet, segmented reference track. Text always carries the actual value. */
    private static final class ReferenceTrack extends View {
        float fraction;
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        final SpringValue trace;
        ReferenceTrack(Context c){
            super(c);setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            trace=new SpringValue(0,0.001,value->{fraction=value;invalidate();},null);
        }
        void setUsage(long total,long goal){trace.target(Math.min(1,total/(float)Math.max(1,goal)));}
        @Override protected void onDetachedFromWindow(){trace.stop();super.onDetachedFromWindow();}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);float gap=Ui.dp(getContext(),3),width=(getWidth()-23*gap)/24f;
            for(int i=0;i<24;i++){
                float left=i*(width+gap);paint.setColor(Ui.LINE);
                canvas.drawRoundRect(left,0,left+width,getHeight(),width/2,width/2,paint);
                float fill=Math.max(0,Math.min(1,fraction*24-i));
                if(fill>0){canvas.save();canvas.clipRect(left,0,left+width*fill,getHeight());paint.setColor(Ui.ACCENT);canvas.drawRoundRect(left,0,left+width,getHeight(),width/2,width/2,paint);canvas.restore();}
            }
        }
    }
}
