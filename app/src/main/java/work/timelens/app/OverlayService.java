package work.timelens.app;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.concurrent.*;

public final class OverlayService extends Service {
    static final String STOP="work.timelens.app.STOP";
    static final String STATE_CHANGED="work.timelens.app.TIMER_STATE";
    static final String RESET_POSITION="work.timelens.app.RESET_POSITION";
    static volatile boolean running;
    private final Handler main=new Handler(Looper.getMainLooper());
    private ScheduledExecutorService worker;
    private WindowManager windows;
    private LinearLayout pill;
    private TextView totalText,appText;
    private WindowManager.LayoutParams layout;
    private UsageRepository repository;
    private volatile UsageRepository.Snapshot snapshot;
    private PowerManager power;
    private KeyguardManager keyguard;
    private String namedPackage,appLabel;
    private long notificationMinute=-1;
    private boolean shuttingDown;
    private SpringValue settleX,settleY;
    private android.graphics.drawable.GradientDrawable pillBackground;
    private Boolean solidBackground;
    private final BroadcastReceiver displayReceiver=new BroadcastReceiver() {
        @Override public void onReceive(Context c,Intent i) { if(!power.isInteractive() || keyguard.isKeyguardLocked()) hide(); else requestRefresh(); }
    };
    private final Runnable tick=new Runnable() {
        @Override public void run() { render(); main.postDelayed(this,1000); }
    };
    @Override public void onCreate() {
        super.onCreate(); Ui.configure(this); repository=UsageRepository.get(this); windows=getSystemService(WindowManager.class); power=getSystemService(PowerManager.class); keyguard=getSystemService(KeyguardManager.class);
        worker=Executors.newSingleThreadScheduledExecutor();
        NotificationManager nm=getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("timer","Live screen-time timer",NotificationManager.IMPORTANCE_LOW));
        IntentFilter filter=new IntentFilter(); filter.addAction(Intent.ACTION_SCREEN_OFF); filter.addAction(Intent.ACTION_SCREEN_ON); filter.addAction(Intent.ACTION_USER_PRESENT);
        if(Build.VERSION.SDK_INT>=33) registerReceiver(displayReceiver,filter,Context.RECEIVER_NOT_EXPORTED); else registerReceiver(displayReceiver,filter);
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if(intent!=null && STOP.equals(intent.getAction())) {
            Ui.prefs(this).edit().putBoolean("enabled",false).apply(); stopSelf(); return START_NOT_STICKY;
        }
        if(intent!=null && RESET_POSITION.equals(intent.getAction()) && running) {
            settleX.stop();settleY.stop();layout.x=Ui.dp(this,16);layout.y=Ui.dp(this,100);clamp();moveWindow();savePosition();return START_STICKY;
        }
        if(!repository.hasUsageAccess() || !Settings.canDrawOverlays(this)) { Ui.prefs(this).edit().putBoolean("enabled",false).apply(); stopSelf(); return START_NOT_STICKY; }
        if(Build.VERSION.SDK_INT>=34) startForeground(1,notification(0),ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE); else startForeground(1,notification(0));
        if(!running) {
            running=true; Ui.prefs(this).edit().putBoolean("enabled",true).apply();
            createPill(); worker.scheduleWithFixedDelay(this::refresh,0,2,TimeUnit.SECONDS); main.post(tick);
            sendBroadcast(new Intent(STATE_CHANGED).setPackage(getPackageName()));
        }
        return START_STICKY;
    }
    private Notification notification(long total) {
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,OverlayService.class).setAction(STOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,"timer").setSmallIcon(R.drawable.ic_timer).setContentTitle("TimeLens · "+Ui.duration(total)+" today").setContentText("Your screen-time timer is visible. Tap to open.").setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).addAction(new Notification.Action.Builder(null,"Stop timer",stop).build()).build();
    }
    private void requestRefresh() { if(worker!=null && !worker.isShutdown()) worker.execute(this::refresh); }
    private void refresh() {
        if(shuttingDown || !power.isInteractive() || keyguard.isKeyguardLocked()) return;
        if(!repository.hasUsageAccess() || !Settings.canDrawOverlays(this)) {
            main.post(()->{ Ui.prefs(this).edit().putBoolean("enabled",false).apply(); stopSelf(); }); return;
        }
        snapshot=repository.refresh(); main.post(this::render);
    }
    private void createPill() {
        pill=Ui.column(this); pill.setPadding(Ui.dp(this,14),Ui.dp(this,10),Ui.dp(this,14),Ui.dp(this,10));
        pillBackground=Ui.rounded(Ui.CARD,21,this);pillBackground.setStroke(Ui.dp(this,1),Ui.LINE);pill.setBackground(pillBackground);pill.setElevation(Ui.dp(this,8));
        totalText=Ui.text(this,"TODAY  00:00:00",14,Ui.LIME,true); appText=Ui.text(this,"Starting timer…",12,Ui.INK,false); appText.setMaxLines(1); appText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        pill.addView(totalText); Ui.gap(pill,3); pill.addView(appText); pill.setContentDescription("TimeLens live screen-time timer. Drag to move. Tap to toggle app detail.");
        layout=new WindowManager.LayoutParams(overlayWidth(),WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);
        layout.gravity=Gravity.TOP|Gravity.START; layout.x=Ui.prefs(this).getInt("overlay_x",Ui.dp(this,16)); layout.y=Ui.prefs(this).getInt("overlay_y",Ui.dp(this,100));
        settleX=new SpringValue(layout.x,0.4,v->{layout.x=Math.round(v);moveWindow();},this::savePosition);
        settleY=new SpringValue(layout.y,0.4,v->{layout.y=Math.round(v);moveWindow();},this::savePosition);
        pill.setOnTouchListener(new View.OnTouchListener() {
            float startX,startY; int initialX,initialY; boolean moved;
            VelocityTracker velocity;
            @Override public boolean onTouch(View v,android.view.MotionEvent e) {
                if(velocity!=null)velocity.addMovement(e);
                switch(e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        settleX.stop();settleY.stop();if(velocity!=null)velocity.recycle();velocity=VelocityTracker.obtain();velocity.addMovement(e);
                        startX=e.getRawX(); startY=e.getRawY(); initialX=layout.x; initialY=layout.y; moved=false;pill.setAlpha(0.9f);return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx=e.getRawX()-startX,dy=e.getRawY()-startY;
                        if(Math.abs(dx)+Math.abs(dy)>Ui.dp(OverlayService.this,8)) moved=true;
                        if(moved){layout.x=Math.round(softBound(initialX+dx,Ui.dp(OverlayService.this,8),maxX()));layout.y=Math.round(softBound(initialY+dy,Ui.dp(OverlayService.this,28),maxY()));moveWindow();}
                        return true;
                    case MotionEvent.ACTION_UP:
                        pill.setAlpha(1);
                        if(moved){velocity.computeCurrentVelocity(1000);float vx=velocity.getXVelocity(),vy=velocity.getYVelocity();float projected=layout.x+vx*0.099f;float targetX=projected>(Ui.dp(OverlayService.this,8)+maxX())/2f?maxX():Ui.dp(OverlayService.this,8);float targetY=Math.max(Ui.dp(OverlayService.this,28),Math.min(maxY(),layout.y+vy*0.06f));settleX.release(layout.x,targetX,vx);settleY.release(layout.y,targetY,vy);v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);}else v.performClick();
                        velocity.recycle();velocity=null;return true;
                    case MotionEvent.ACTION_CANCEL:
                        pill.setAlpha(1);clamp();moveWindow();savePosition();if(velocity!=null){velocity.recycle();velocity=null;}return true;
                    default:return false;
                }
            }
        });
        pill.setOnClickListener(v->{
            boolean compact=Ui.prefs(this).getBoolean("compact",false);
            Ui.prefs(this).edit().putBoolean("compact",!compact).apply();render();
        });
    }
    private android.graphics.Rect screenBounds() {
        android.graphics.Rect bounds;
        if(Build.VERSION.SDK_INT>=30) bounds=windows.getCurrentWindowMetrics().getBounds();
        else { android.util.DisplayMetrics m=new android.util.DisplayMetrics();windows.getDefaultDisplay().getRealMetrics(m);bounds=new android.graphics.Rect(0,0,m.widthPixels,m.heightPixels); }
        return bounds;
    }
    private int overlayWidth(){return Math.max(Ui.dp(this,48),Math.min(screenBounds().width()-Ui.dp(this,16),Ui.dp(this,205*Math.max(1,Math.min(1.5f,getResources().getConfiguration().fontScale)))));}
    private int maxX(){return Math.max(Ui.dp(this,8),screenBounds().width()-layout.width-Ui.dp(this,8));}
    private int maxY(){return Math.max(Ui.dp(this,28),screenBounds().height()-Math.max(pill==null?0:pill.getHeight(),Ui.dp(this,76))-Ui.dp(this,56));}
    private void clamp() {
        layout.x=Math.max(Ui.dp(this,8),Math.min(layout.x,maxX()));
        layout.y=Math.max(Ui.dp(this,28),Math.min(layout.y,maxY()));
    }
    private float softBound(float value,float low,float high){
        float edge=value<low?low:value>high?high:value,overshoot=value-edge,dimension=Ui.dp(this,32);
        return edge+(overshoot*dimension*0.55f)/(dimension+0.55f*Math.abs(overshoot));
    }
    private void moveWindow(){
        try{if(pill!=null&&pill.isAttachedToWindow())windows.updateViewLayout(pill,layout);}catch(IllegalArgumentException ignored){}
    }
    private void savePosition(){
        Ui.prefs(this).edit().putInt("overlay_x",layout.x).putInt("overlay_y",layout.y).apply();
    }
    private void render() {
        if(!running || pill==null) return;
        boolean solid=Ui.prefs(this).getBoolean("solid",false);
        if(solidBackground==null||solidBackground!=solid){solidBackground=solid;pillBackground.setColor((Ui.CARD & 0x00ffffff) | (solid?0xff000000:0xef000000));}
        if(!power.isInteractive() || keyguard.isKeyguardLocked() || !Settings.canDrawOverlays(this)) {hide();return;}
        if(!pill.isAttachedToWindow()) { try {clamp();windows.addView(pill,layout);}catch(RuntimeException e){stopSelf();return;} }
        UsageRepository.Snapshot s=snapshot; if(s==null)return;
        long extra=s.usage.activePackage!=null && s.issue==null && s.day.equals(java.time.LocalDate.now())?Math.max(0,Math.min(3000,System.currentTimeMillis()-s.asOf)):0;
        totalText.setText("TODAY  "+Ui.clock(s.usage.total+extra));
        String pkg=s.usage.activePackage;
        if(!java.util.Objects.equals(namedPackage,pkg) || appLabel==null) {namedPackage=pkg;appLabel=Ui.appName(this,pkg);}
        appText.setText(s.issue!=null?"Waiting for usage data":appLabel+"  ·  "+Ui.clock(s.usage.perApp.getOrDefault(pkg,0L)+extra));
        appText.setVisibility(Ui.prefs(this).getBoolean("compact",false)?View.GONE:View.VISIBLE);
        long minute=(s.usage.total+extra)/60000;
        if(minute!=notificationMinute) {notificationMinute=minute;getSystemService(NotificationManager.class).notify(1,notification(s.usage.total+extra));}
    }
    private void hide() {if(settleX!=null)settleX.stop();if(settleY!=null)settleY.stop();if(pill!=null && pill.isAttachedToWindow()) try{windows.removeView(pill);}catch(IllegalArgumentException ignored){} }
    @Override public void onConfigurationChanged(android.content.res.Configuration c) {
        super.onConfigurationChanged(c);Ui.configure(this);solidBackground=null;
        if(pill!=null){
            totalText.setTextColor(Ui.LIME);appText.setTextColor(Ui.INK);pillBackground.setStroke(Ui.dp(this,1),Ui.LINE);
            if(pill.isAttachedToWindow()){settleX.stop();settleY.stop();layout.width=overlayWidth();clamp();moveWindow();}
            render();
        }
    }
    @Override public void onDestroy() {
        shuttingDown=true; running=false; main.removeCallbacksAndMessages(null); hide();
        sendBroadcast(new Intent(STATE_CHANGED).setPackage(getPackageName()));
        try{unregisterReceiver(displayReceiver);}catch(IllegalArgumentException ignored){}
        if(worker!=null){worker.execute(()->{try{repository.refresh();repository.flush();}catch(RuntimeException ignored){}});worker.shutdown();}
        stopForeground(STOP_FOREGROUND_REMOVE); super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
