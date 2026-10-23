package work.timelens.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
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
    private LinearLayout page,content,appList,navigation;
    private ScrollView scroll;
    private PermissionState permissions;
    private boolean setupVisible, dismissedSetupThisVisit, externalSettings;
    private boolean usageSettingsPending, showRecovery, notificationsSkipped, notificationRequestInFlight;
    private String permissionFeedback;
    private TextView todayTotal,currentApp,status,toggle,empty;
    private Ring ring;
    private UsageRepository repository;
    private UsageRepository.Snapshot snapshot;
    private boolean visible;
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
            usageSettingsPending=state.getBoolean("usagePending");
            showRecovery=state.getBoolean("recovery");
            notificationsSkipped=state.getBoolean("notificationsSkipped");
            notificationRequestInFlight=state.getBoolean("requestInFlight");
        }
        LinearLayout root=Ui.column(this); root.setBackgroundColor(Ui.BG);
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
        LinearLayout brand=Ui.row(this);brand.setPadding(dp(24),dp(18),dp(24),dp(12));
        ImageView mark=new ImageView(this);mark.setImageResource(R.drawable.ic_launcher);brand.addView(mark,new LinearLayout.LayoutParams(dp(30),dp(30)));
        TextView logo=Ui.text(this,"TimeLens",23,Ui.INK,true);logo.setPadding(dp(10),0,0,0);brand.addView(logo,new LinearLayout.LayoutParams(0,-2,1));
        root.addView(brand);
        scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);scroll.setVerticalScrollBarEnabled(false);
        page=Ui.column(this);page.setPadding(dp(24),dp(10),dp(24),dp(24));scroll.addView(page);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout nav=Ui.row(this);navigation=nav;nav.setPadding(dp(16),dp(8),dp(16),dp(12));nav.setBackgroundColor(Ui.BG);
        String[] labels={"Today","History","Settings"};
        int[] symbols={R.drawable.ic_today,R.drawable.ic_history,R.drawable.ic_settings};
        for(int i=0;i<labels.length;i++) {
            final int index=i;LinearLayout surface=Ui.column(this);surface.setGravity(Gravity.CENTER);surface.setPadding(dp(8),dp(10),dp(8),dp(10));surface.setMinimumHeight(dp(64));
            ImageView symbol=new ImageView(this);symbol.setImageResource(symbols[i]);surface.addView(symbol,new LinearLayout.LayoutParams(dp(22),dp(22)));Ui.gap(surface,6);
            TextView t=Ui.text(this,labels[i],11,Ui.MUTED,true);surface.addView(t);surface.setContentDescription(labels[i]);surface.setFocusable(true);surface.setOnClickListener(v->{showTab(index);scroll.smoothScrollTo(0,0);});
            LinearLayout.LayoutParams cell=new LinearLayout.LayoutParams(0,-2,1);cell.setMargins(dp(3),0,dp(3),0);nav.addView(surface,cell);tabs.add(t);tabIcons.add(symbol);tabSurfaces.add(surface);
        }
        root.addView(nav);setContentView(root);root.requestApplyInsets();
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
        if(!resumedState.sameAs(permissions))permissionFeedback=null;
        permissions=resumedState;
        if(usageSettingsPending){
            showRecovery=!permissions.usage;
            permissionFeedback=permissions.usage?null:"Usage access is still off.";
            usageSettingsPending=false;
        }
        externalSettings=false;
        if(!dismissedSetupThisVisit && !permissions.complete())setupVisible=true;
        renderCurrent();
        if(refreshJob!=null)refreshJob.cancel(false);
        refreshJob=worker.scheduleWithFixedDelay(()->{
            UsageRepository.Snapshot s=repository.refresh();
            main.post(()->{
                if(!visible||isDestroyed())return;
                snapshot=s;
                PermissionState actual=PermissionState.read(this);
                if(!actual.sameAs(permissions)){
                    permissions=actual;
                    permissionFeedback=null;
                    if(!dismissedSetupThisVisit&&!actual.complete())setupVisible=true;
                    renderCurrent();
                }
                if(!setupVisible&&tab==0)updateToday();
            });
        },0,2,TimeUnit.SECONDS);
    }
    @Override protected void onPause() {
        visible=false;
        if(refreshJob!=null)refreshJob.cancel(false);
        worker.execute(repository::flush);
        super.onPause();
    }
    @Override protected void onStop() {
        if(!externalSettings&&!notificationRequestInFlight&&!isChangingConfigurations()){
            dismissedSetupThisVisit=false;
            notificationsSkipped=false;
        }
        super.onStop();
    }
    @Override protected void onDestroy() {
        if(Build.VERSION.SDK_INT>=33&&backRegistered)getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
        visible=false;main.removeCallbacksAndMessages(null);unregisterReceiver(timerState);worker.shutdown();super.onDestroy();
    }
    @Override public void onSaveInstanceState(Bundle out){
        out.putInt("tab",tab);
        out.putBoolean("setup",setupVisible);
        out.putBoolean("dismissed",dismissedSetupThisVisit);
        out.putBoolean("external",externalSettings);
        out.putBoolean("usagePending",usageSettingsPending);
        out.putBoolean("recovery",showRecovery);
        out.putBoolean("notificationsSkipped",notificationsSkipped);
        out.putBoolean("requestInFlight",notificationRequestInFlight);
        super.onSaveInstanceState(out);
    }
    @Override public void onBackPressed(){
        if(setupVisible){leaveSetup();return;}
        if(tab!=0){showTab(0);return;}
        super.onBackPressed();
    }
    private void clearPage(){
        page.removeAllViews();rows.clear();
        todayTotal=null;currentApp=null;status=null;toggle=null;empty=null;ring=null;appList=null;content=null;
    }
    private void renderCurrent(){if(setupVisible)renderSetup();else showTab(tab);}
    private void openSetup(){
        setupVisible=true;dismissedSetupThisVisit=false;permissionFeedback=null;permissions=PermissionState.read(this);
        scroll.smoothScrollTo(0,0);renderSetup();
    }
    private void leaveSetup(){
        setupVisible=false;dismissedSetupThisVisit=true;permissionFeedback=null;
        showTab(tab);scroll.smoothScrollTo(0,0);
    }
    private void renderSetup(){
        clearPage();navigation.setVisibility(View.GONE);
        updateBackCallback();
        PermissionSetup.build(this,page,permissions,showRecovery,notificationsSkipped,permissionFeedback,new PermissionSetup.Actions(){
            public void usage(){usageSettings();}
            public void overlay(){overlaySettings();}
            public void notifications(){notificationSettings();}
            public void appInfo(){appInfoSettings();}
            public void recovery(){showRecovery=true;permissionFeedback=null;renderSetup();}
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
            tabs.get(i).setTextColor(selected?Ui.LIME:Ui.MUTED);
            tabIcons.get(i).setImageTintList(android.content.res.ColorStateList.valueOf(selected?Ui.LIME:Ui.MUTED));
            tabSurfaces.get(i).setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(Ui.RIPPLE),Ui.rounded(selected?Ui.TINT:Ui.BG,20,this),null));
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
        page.addView(Ui.text(this,eyebrow,11,Ui.LIME,true));Ui.gap(page,10);page.addView(Ui.text(this,title,32,Ui.INK,true));Ui.gap(page,8);
        TextView sub=Ui.text(this,subtitle,14,Ui.MUTED,false);sub.setLineSpacing(dp(3),1);page.addView(sub);Ui.gap(page,24);
    }
    private void buildToday() {
        heading(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMM",Locale.ENGLISH)).toUpperCase(Locale.ENGLISH),"Your time, in focus.","See your day across all your apps.");
        LinearLayout hero=Ui.card(this);hero.setBackground(Ui.rounded(Ui.TINT,24,this));
        hero.addView(Ui.text(this,"TODAY'S SCREEN TIME",11,Ui.LIME,true));Ui.gap(hero,12);
        todayTotal=Ui.text(this,"—",48,Ui.INK,true);hero.addView(todayTotal);
        TextView precise=Ui.text(this,"Active app time · resets at local midnight",12,Ui.MUTED,false);hero.addView(precise);Ui.gap(hero,18);
        ring=new Ring(this);hero.addView(ring,new LinearLayout.LayoutParams(-1,dp(116)));
        hero.addView(Ui.text(this,"Daily reference: "+Ui.duration(Ui.prefs(this).getInt("goal",180)*60000L),14,Ui.MUTED,false));
        currentApp=Ui.text(this,"Waiting for Android usage data",13,Ui.INK,false);currentApp.setPadding(0,dp(12),0,0);hero.addView(currentApp);page.addView(hero);Ui.gap(page,16);
        buildAccessCard(page);Ui.gap(page,24);
        LinearLayout title=Ui.row(this);title.addView(Ui.text(this,"Where your time went",20,Ui.INK,true),new LinearLayout.LayoutParams(0,-2,1));title.addView(Ui.text(this,"TODAY",10,Ui.MUTED,true));page.addView(title);Ui.gap(page,14);
        appList=Ui.column(this);page.addView(appList);empty=Ui.text(this,"Grant Usage Access to see your app breakdown.",14,Ui.MUTED,false);appList.addView(empty);
        Ui.gap(page,20);page.addView(Ui.text(this,"Screen-off time, the lock screen, System UI and the home launcher are excluded. Updates usually appear within 2 seconds.",12,Ui.MUTED,false));
        if(snapshot!=null)updateToday();
    }
    private void buildAccessCard(LinearLayout parent) {
        PermissionState state=PermissionState.read(this);
        LinearLayout card=Ui.card(this);
        card.addView(Ui.text(this,state.canTrack()?"Your floating timer":"Finish setting up",18,Ui.INK,true));
        Ui.gap(card,8);
        card.addView(Ui.text(this,state.canTrack()?"Today’s total and the current app’s time, visible across your apps.":"Allow Usage access for your app breakdown and Display over apps for the floating timer.",15,Ui.MUTED,false));
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
        ring.setUsage(snapshot.usage.total,Ui.prefs(this).getInt("goal",180)*60000L);
        List<Map.Entry<String,Long>> entries=new ArrayList<>(snapshot.usage.perApp.entrySet());entries.sort((a,b)->Long.compare(b.getValue(),a.getValue()));
        List<String> order=new ArrayList<>();for(Map.Entry<String,Long> e:entries)order.add(e.getKey());
        if(!order.equals(new ArrayList<>(rows.keySet()))) {
            appList.removeAllViews();rows.clear();
            if(entries.isEmpty()){empty=Ui.text(this,snapshot.permission?"No recorded app usage yet. Use an app, then return here.":"Grant Usage Access to see your app breakdown.",14,Ui.MUTED,false);appList.addView(empty);}
            for(Map.Entry<String,Long> e:entries){AppRow r=new AppRow(e.getKey());rows.put(e.getKey(),r);appList.addView(r.view);Ui.gap(appList,10);}
        } else if(entries.isEmpty())empty.setText(snapshot.permission?"No recorded app usage yet. Use an app, then return here.":"Grant Usage Access to see your app breakdown.");
        for(Map.Entry<String,Long> e:entries){AppRow r=rows.get(e.getKey());r.duration.setText(Ui.duration(e.getValue()));r.progress.setProgress(snapshot.usage.total==0?0:(int)(e.getValue()*1000/snapshot.usage.total),android.animation.ValueAnimator.areAnimatorsEnabled());}
        if(toggle!=null){toggle.setText(OverlayService.running?"Stop floating timer":"Start floating timer");}
    }
    private final class AppRow {
        final LinearLayout view;final TextView duration;final ProgressBar progress;
        AppRow(String pkg){
            view=Ui.card(MainActivity.this);view.setPadding(dp(15),dp(14),dp(15),dp(14));LinearLayout line=Ui.row(MainActivity.this);
            ImageView icon=new ImageView(MainActivity.this);icon.setBackground(Ui.rounded(Ui.LINE,10,MainActivity.this));icon.setClipToOutline(true);try{icon.setImageDrawable(getPackageManager().getApplicationIcon(pkg));}catch(PackageManager.NameNotFoundException e){icon.setImageResource(R.drawable.ic_launcher);}line.addView(icon,new LinearLayout.LayoutParams(dp(36),dp(36)));
            LinearLayout names=Ui.column(MainActivity.this);names.setPadding(dp(12),0,dp(8),0);TextView name=Ui.text(MainActivity.this,Ui.appName(MainActivity.this,pkg),15,Ui.INK,true);name.setMaxLines(1);name.setEllipsize(android.text.TextUtils.TruncateAt.END);names.addView(name);line.addView(names,new LinearLayout.LayoutParams(0,-2,1));
            duration=Ui.text(MainActivity.this,"0s",14,Ui.LIME,true);line.addView(duration);view.addView(line);Ui.gap(view,10);
            progress=new ProgressBar(MainActivity.this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(1000);progress.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.LIME));progress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Ui.LINE));view.addView(progress,new LinearLayout.LayoutParams(-1,dp(4)));
        }
    }
    private void buildHistory() {
        heading("YOUR RECENT DAYS","Small moments add up.","Daily totals and app breakdowns are saved privately on this phone.");
        content=Ui.column(this);page.addView(content);content.addView(Ui.text(this,"Loading saved days…",14,Ui.MUTED,false));
        worker.execute(()->{repository.flush();Map<LocalDate,Long> history=repository.history();main.post(()->{
            if(!visible||tab!=1)return;content.removeAllViews();
            if(snapshot!=null&&snapshot.permission)history.put(snapshot.day,snapshot.usage.total);
            long max=history.values().stream().mapToLong(Long::longValue).max().orElse(1);
            for(int i=0;i<7;i++){
                LocalDate date=LocalDate.now().minusDays(i);Long ms=history.get(date);
                LinearLayout c=Ui.card(this);LinearLayout line=Ui.row(this);String day=i==0?"Today":i==1?"Yesterday":date.format(DateTimeFormatter.ofPattern("EEE, d MMM",Locale.ENGLISH));
                line.addView(Ui.text(this,day,16,Ui.INK,true),new LinearLayout.LayoutParams(0,-2,1));line.addView(Ui.text(this,ms==null?"No saved data":Ui.duration(ms),14,ms==null?Ui.MUTED:Ui.LIME,true));c.addView(line);
                if(ms!=null){Ui.gap(c,12);ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(1000);bar.setProgress((int)(ms*1000/Math.max(1,max)));bar.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.LIME));c.addView(bar,new LinearLayout.LayoutParams(-1,dp(5)));c.setOnClickListener(v->showDay(date));c.setContentDescription(day+", "+Ui.duration(ms)+". Tap for app breakdown.");}
                content.addView(c);Ui.gap(content,12);
            }
            Ui.gap(content,12);content.addView(Ui.text(this,"History starts when you first open TimeLens. Android keeps recent activity for a limited period; older missing records are shown as unavailable.",12,Ui.MUTED,false));
        });});
    }
    private void showDay(LocalDate date) {
        worker.execute(()->{Map<String,Long> data=repository.appsFor(date);main.post(()->{if(!visible)return;
            LinearLayout body=Ui.card(this);View handle=new View(this);handle.setBackground(Ui.rounded(Ui.MUTED,4,this));LinearLayout.LayoutParams handleSize=new LinearLayout.LayoutParams(dp(32),dp(4));handleSize.gravity=Gravity.CENTER_HORIZONTAL;body.addView(handle,handleSize);Ui.gap(body,22);body.addView(Ui.text(this,date.format(DateTimeFormatter.ofPattern("d MMMM yyyy",Locale.ENGLISH)),22,Ui.INK,true));Ui.gap(body,18);
            for(Map.Entry<String,Long> e:data.entrySet()){TextView text=Ui.text(this,Ui.appName(this,e.getKey())+"  ·  "+Ui.duration(e.getValue()),14,Ui.INK,false);text.setPadding(0,dp(9),0,dp(9));body.addView(text);}
            if(data.isEmpty())body.addView(Ui.text(this,"No app breakdown saved for this day.",14,Ui.MUTED,false));
            Dialog dialog=new Dialog(this);Ui.gap(body,20);body.addView(Ui.button(this,"Done",false,dialog::dismiss));ScrollView scroll=new ScrollView(this);scroll.addView(body);dialog.setContentView(scroll);dialog.show();if(dialog.getWindow()!=null){dialog.getWindow().setBackgroundDrawable(Ui.rounded(Ui.CARD,24,this));dialog.getWindow().setGravity(Gravity.BOTTOM);dialog.getWindow().setLayout(getResources().getDisplayMetrics().widthPixels-dp(24),(int)(getResources().getDisplayMetrics().heightPixels*0.72));}
        });});
    }
    private void buildSettings() {
        heading("MAKE IT YOURS","A calmer kind of tracker.","Everything stays on your phone. No accounts, no ads, no cloud.");
        LinearLayout goal=Ui.card(this);goal.addView(Ui.text(this,"Your daily reference",18,Ui.INK,true));Ui.gap(goal,8);goal.addView(Ui.text(this,"A gentle progress ring. This does not block apps.",13,Ui.MUTED,false));Ui.gap(goal,12);
        int minutes=Ui.prefs(this).getInt("goal",180);TextView goalText=Ui.text(this,Ui.duration(minutes*60000L)+" per day",24,Ui.LIME,true);goal.addView(goalText);
        SeekBar seek=new SeekBar(this);seek.setMax(46);seek.setProgress((minutes-30)/15);seek.setContentDescription("Daily screen-time reference in fifteen-minute steps");seek.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.LIME));goal.addView(seek);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean user){int m=30+p*15;goalText.setText(Ui.duration(m*60000L)+" per day");if(user)Ui.prefs(MainActivity.this).edit().putInt("goal",m).apply();}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});page.addView(goal);Ui.gap(page,16);
        LinearLayout appearance=Ui.card(this);appearance.addView(Ui.text(this,"Floating timer",18,Ui.INK,true));Ui.gap(appearance,14);
        Switch compact=new Switch(this);compact.setText("Show total only");compact.setTextColor(Ui.INK);compact.setChecked(Ui.prefs(this).getBoolean("compact",false));compact.setOnCheckedChangeListener((b,checked)->Ui.prefs(this).edit().putBoolean("compact",checked).apply());appearance.addView(compact);Ui.gap(appearance,14);
        Switch solid=new Switch(this);solid.setText("Solid timer background");solid.setTextColor(Ui.INK);solid.setChecked(Ui.prefs(this).getBoolean("solid",false));solid.setOnCheckedChangeListener((b,checked)->Ui.prefs(this).edit().putBoolean("solid",checked).apply());appearance.addView(solid);Ui.gap(appearance,14);
        appearance.addView(Ui.text(this,"Drag the floating pill to move it. Tap to switch app detail on or off. Stop it from Today or the notification. It hides while the phone is locked.",13,Ui.MUTED,false));Ui.gap(appearance,14);
        appearance.addView(Ui.button(this,"Reset timer position",false,()->{Ui.prefs(this).edit().remove("overlay_x").remove("overlay_y").apply();if(OverlayService.running)startService(new Intent(this,OverlayService.class).setAction(OverlayService.RESET_POSITION));else Toast.makeText(this,"Position reset for next start",Toast.LENGTH_SHORT).show();}));page.addView(appearance);Ui.gap(page,16);
        PermissionState state=PermissionState.read(this);
        LinearLayout access=Ui.card(this);
        access.addView(Ui.text(this,"Permissions & battery",18,Ui.INK,true));Ui.gap(access,14);
        access.addView(Ui.text(this,"Usage access: "+(state.usage?"Allowed":"Not allowed")+"\nDisplay over apps: "+(state.overlay?"Allowed":"Not allowed")+"\nNotifications: "+(state.notifications?"Allowed":"Off · optional"),15,Ui.MUTED,false));
        Ui.gap(access,16);access.addView(Ui.button(this,"Review permission setup",true,this::openSetup));Ui.gap(access,10);
        access.addView(Ui.button(this,"Usage access settings",false,this::usageSettings));Ui.gap(access,10);
        access.addView(Ui.button(this,"Display over apps settings",false,this::overlaySettings));Ui.gap(access,10);
        access.addView(Ui.button(this,"Notification settings",false,this::notificationSettings));Ui.gap(access,10);
        access.addView(Ui.button(this,"Open app & battery settings",false,this::appInfoSettings));
        Ui.gap(access,14);access.addView(Ui.text(this,"If Samsung pauses the timer, set TimeLens battery use to Unrestricted and remove it from Sleeping apps. After a restart, open TimeLens and start the timer again.",13,Ui.MUTED,false));page.addView(access);Ui.gap(page,16);
        LinearLayout about=Ui.card(this);about.addView(Ui.text(this,"Designed around your privacy",18,Ui.INK,true));Ui.gap(about,10);about.addView(Ui.text(this,"TimeLens reads app names and activity timestamps through Android Usage Access. It cannot read chats, capture the screen, count reels or track audio playing with the screen off. There is no Internet permission. Some apps, including banking and permission screens, can hide overlays. Split screen uses the latest resumed app as an approximation.",13,Ui.MUTED,false));Ui.gap(about,14);about.addView(Ui.text(this,"TimeLens "+versionName()+" · Personal test build",11,Ui.LIME,true));page.addView(about);
    }
    private String versionName(){
        try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}
        catch(PackageManager.NameNotFoundException e){return "";}
    }
    private void usageSettings(){
        usageSettingsPending=true;permissionFeedback=null;
        openSettings(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS,Uri.parse("package:"+getPackageName())),new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
    }
    private void overlaySettings(){
        permissionFeedback=null;
        openSettings(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())),new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
    }
    private void appInfoSettings(){
        openSettings(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())),new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS));
    }
    private void notificationSettings(){
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED
                && (!Ui.prefs(this).getBoolean("notifications_requested",false) || shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))){
            notificationRequestInFlight=true;
            Ui.prefs(this).edit().putBoolean("notifications_requested",true).apply();
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},30);
        }else{
            openSettings(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()),new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));
        }
    }
    @Override public void onRequestPermissionsResult(int code,String[] names,int[] results){
        super.onRequestPermissionsResult(code,names,results);
        if(code!=30)return;
        notificationRequestInFlight=false;
        permissions=PermissionState.read(this);
        permissionFeedback=permissions.notifications?null:"Notifications are still off. Try again to review Android’s settings, or continue without them.";
        renderCurrent();
    }
    private void openSettings(Intent intent,Intent fallback){
        externalSettings=true;
        try{startActivity(intent);}
        catch(ActivityNotFoundException|SecurityException e){
            try{startActivity(fallback);}
            catch(ActivityNotFoundException|SecurityException unavailable){
                externalSettings=false;usageSettingsPending=false;
                permissionFeedback="This settings screen is unavailable. Open Android Settings → Apps → TimeLens to review access.";
                setupVisible=true;permissions=PermissionState.read(this);renderSetup();
            }
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
    private static final class Ring extends FrameLayout {
        final TextView percent;
        long total,goal=180*60000L;
        float visibleFraction;
        final SpringValue trace;
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        Ring(Context c){
            super(c);setWillNotDraw(false);setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            percent=Ui.text(c,"0%",24,Ui.INK,true);percent.setGravity(Gravity.CENTER);
            percent.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            addView(percent,new FrameLayout.LayoutParams(-1,-1));
            trace=new SpringValue(0,0.001,v->{visibleFraction=v;invalidate();},null);
        }
        void setUsage(long total,long goal){this.total=total;this.goal=goal;percent.setText(Math.round(total*100.0/Math.max(1,goal))+"%");setContentDescription(percent.getText()+" of your "+Ui.duration(goal)+" daily reference");trace.target(Math.min(1,total/(float)Math.max(1,goal)));invalidate();}
        @Override protected void onDetachedFromWindow(){trace.stop();super.onDetachedFromWindow();}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);float density=getResources().getDisplayMetrics().density;float size=Math.min(getHeight()-12*density,104*density),left=(getWidth()-size)/2,top=6*density;RectF bounds=new RectF(left,top,left+size,top+size);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(9*density);paint.setStrokeCap(Paint.Cap.ROUND);paint.setColor(Ui.LINE);canvas.drawArc(bounds,-90,360,false,paint);paint.setColor(Ui.LIME);canvas.drawArc(bounds,-90,Math.max(0,Math.min(360,360f*visibleFraction)),false,paint);

        }
    }
}
