package work.timelens.app;

import android.app.*;
import android.app.usage.*;
import android.content.*;
import android.content.pm.*;
import android.os.*;
import java.time.*;
import java.util.*;

/** Owns OS event ingestion, overlap deduplication, and daily persistence. */
final class UsageRepository {
    static final class Snapshot {
        final LocalDate day;
        final long asOf;
        final UsageTimeline.Summary usage;
        final boolean permission;
        final String issue;
        Snapshot(LocalDate day,long asOf,UsageTimeline.Summary usage,boolean permission,String issue) {
            this.day=day; this.asOf=asOf; this.usage=usage; this.permission=permission; this.issue=issue;
        }
    }
    private static UsageRepository instance;
    static synchronized UsageRepository get(Context c) {
        if(instance==null) instance=new UsageRepository(c.getApplicationContext());
        return instance;
    }
    private final Context context;
    private final UsageStatsManager manager;
    private final UsageStore store;
    private final List<UsageTimeline.Event> events=new ArrayList<>();
    private final Set<UsageTimeline.Event> seen=new HashSet<>();
    private final Set<String> excluded=new HashSet<>(Arrays.asList("android","com.android.systemui"));
    private LocalDate currentDay;
    private ZoneId zone=ZoneId.systemDefault();
    private long cursor,lastSaved;
    private Snapshot latest;
    private UsageRepository(Context c) {
        context=c; manager=c.getSystemService(UsageStatsManager.class); store=new UsageStore(c);
        Intent home=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        for(ResolveInfo r:c.getPackageManager().queryIntentActivities(home,0)) if(r.activityInfo!=null) excluded.add(r.activityInfo.packageName);
    }
    boolean hasUsageAccess() {
        AppOpsManager ops=context.getSystemService(AppOpsManager.class);
        try {
            return ops!=null && ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,android.os.Process.myUid(),context.getPackageName())==AppOpsManager.MODE_ALLOWED;
        } catch(RuntimeException unavailable) { return false; }
    }
    private long midnight(LocalDate day) { return day.atStartOfDay(zone).toInstant().toEpochMilli(); }
    synchronized Snapshot refresh() {
        long now=System.currentTimeMillis(); LocalDate today=LocalDate.now();
        if(!hasUsageAccess()) {
            events.clear(); seen.clear(); cursor=0; currentDay=null;
            return latest=new Snapshot(today,now,new UsageTimeline.Summary(Collections.emptyMap(),null),false,null);
        }
        if(!context.getSystemService(UserManager.class).isUserUnlocked())
            return latest!=null?latest:new Snapshot(today,now,new UsageTimeline.Summary(Collections.emptyMap(),null),true,"Unlock the phone to read usage.");
        // Reset replay state after timezone or wall-clock changes.
        ZoneId currentZone=ZoneId.systemDefault();
        if(currentDay==null || !zone.equals(currentZone) || now<cursor || today.isBefore(currentDay)) {
            zone=currentZone; currentDay=today; cursor=midnight(today)-24*60*60*1000L; events.clear(); seen.clear();
            events.addAll(store.loadEvents(cursor)); seen.addAll(events);
        }
        try {
            long from=Math.max(midnight(currentDay)-24*60*60*1000L,cursor-10_000L);
            UsageEvents usageEvents=manager.queryEvents(from,now);
            if(usageEvents==null) return latest!=null?latest:new Snapshot(today,now,new UsageTimeline.Summary(Collections.emptyMap(),null),true,"Android usage data is temporarily unavailable.");
            UsageEvents.Event raw=new UsageEvents.Event();
            while(usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(raw);
                UsageTimeline.Kind kind=kind(raw.getEventType());
                if(kind==null) continue;
                UsageTimeline.Event e=new UsageTimeline.Event(raw.getTimeStamp(),kind,raw.getPackageName(),raw.getClassName());
                if(seen.add(e)) events.add(e);
            }
            cursor=now;
            if(!today.equals(currentDay)) {
                // Finalize observed days before changing the local calendar boundary.
                for(LocalDate d=currentDay;d.isBefore(today);d=d.plusDays(1)) {
                    UsageTimeline.Summary s=UsageTimeline.summarize(events,midnight(d),midnight(d.plusDays(1)),p->!excluded.contains(p));
                    store.save(d,s,now);
                }
                currentDay=today;
                long cutoff=midnight(today)-24*60*60*1000L;
                events.removeIf(e->e.at<cutoff); seen.clear(); seen.addAll(events);
            }
            UsageTimeline.Summary s=UsageTimeline.summarize(events,midnight(today),now,p->!excluded.contains(p));
            latest=new Snapshot(today,now,s,true,null);
            if(now-lastSaved>=30_000L) { store.save(today,s,now); store.saveEvents(events,midnight(today)-24*60*60*1000L); lastSaved=now; }
            return latest;
        } catch(SecurityException e) {
            return latest=new Snapshot(today,now,new UsageTimeline.Summary(Collections.emptyMap(),null),false,"Usage Access was revoked. Enable it to resume.");
        } catch(RuntimeException e) {
            return latest=new Snapshot(today,now,latest!=null?new UsageTimeline.Summary(latest.usage.perApp,null):new UsageTimeline.Summary(Collections.emptyMap(),null),true,"Usage is temporarily unavailable. Reopen TimeLens to retry.");
        }
    }
    synchronized void flush() { if(latest!=null && latest.permission && latest.issue==null) { store.save(latest.day,latest.usage,latest.asOf); store.saveEvents(events,midnight(latest.day)-24*60*60*1000L); } }
    synchronized Map<LocalDate,Long> history() { return store.history(); }
    synchronized Map<String,Long> appsFor(LocalDate day) { return store.appTotals(day); }
    private static UsageTimeline.Kind kind(int type) {
        switch(type) {
            case UsageEvents.Event.ACTIVITY_RESUMED:return UsageTimeline.Kind.RESUME;
            case UsageEvents.Event.ACTIVITY_PAUSED:return UsageTimeline.Kind.PAUSE;
            case UsageEvents.Event.ACTIVITY_STOPPED:return UsageTimeline.Kind.STOP;
            case UsageEvents.Event.SCREEN_INTERACTIVE:return UsageTimeline.Kind.SCREEN_ON;
            case UsageEvents.Event.SCREEN_NON_INTERACTIVE:return UsageTimeline.Kind.SCREEN_OFF;
            case UsageEvents.Event.KEYGUARD_SHOWN:return UsageTimeline.Kind.LOCK;
            case UsageEvents.Event.KEYGUARD_HIDDEN:return UsageTimeline.Kind.UNLOCK;
            case UsageEvents.Event.DEVICE_SHUTDOWN:return UsageTimeline.Kind.SHUTDOWN;
            case UsageEvents.Event.DEVICE_STARTUP:return UsageTimeline.Kind.STARTUP;
            default:return null;
        }
    }
}
