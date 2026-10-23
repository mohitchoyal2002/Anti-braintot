package work.timelens.app;

import java.util.*;
import java.util.function.Predicate;

/** Pure deterministic reducer. One latest-resumed app owns each instant. */
public final class UsageTimeline {
    public enum Kind { RESUME, PAUSE, STOP, SCREEN_ON, SCREEN_OFF, UNLOCK, LOCK, STARTUP, SHUTDOWN }
    public static final class Event {
        public final long at;
        public final Kind kind;
        public final String pkg, activity;
        public Event(long at, Kind kind, String pkg, String activity) {
            this.at=at; this.kind=kind; this.pkg=pkg==null?"":pkg; this.activity=activity==null?"":activity;
        }
        @Override public boolean equals(Object o) {
            if (!(o instanceof Event)) return false;
            Event e=(Event)o;
            return at==e.at && kind==e.kind && Objects.equals(pkg,e.pkg) && Objects.equals(activity,e.activity);
        }
        @Override public int hashCode() { return Objects.hash(at,kind,pkg,activity); }
    }
    public static final class Summary {
        public final Map<String,Long> perApp;
        public final long total;
        public final String activePackage;
        Summary(Map<String,Long> perApp, String activePackage) {
            this.perApp=Collections.unmodifiableMap(new LinkedHashMap<>(perApp));
            this.total=perApp.values().stream().mapToLong(Long::longValue).sum();
            this.activePackage=activePackage;
        }
    }
    private static boolean matches(Event active, Event e) {
        if (active==null || !Objects.equals(active.pkg,e.pkg)) return false;
        return active.activity.isEmpty() || e.activity.isEmpty() || active.activity.equals(e.activity);
    }
    private static void add(Map<String,Long> sums, Event active, long began, long until,
                            long start, long end, boolean enabled, Predicate<String> included) {
        if (active==null || !enabled || active.pkg.isEmpty() || !included.test(active.pkg)) return;
        long duration=Math.min(until,end)-Math.max(began,start);
        if (duration>0) sums.merge(active.pkg,duration,Long::sum);
    }
    public static Summary summarize(List<Event> input,long start,long end,Predicate<String> included) {
        if (end<start) throw new IllegalArgumentException("End before start");
        List<Event> events=new ArrayList<>(input);
        events.sort(Comparator.comparingLong(e->e.at)); // stable order for equal timestamps
        Map<String,Long> sums=new HashMap<>();
        Event active=null;
        long began=start;
        boolean screenOn=true, unlocked=true;
        for (Event e:events) {
            if(e.at>=end) break; // query ranges are [begin,end)
            switch(e.kind) {
                case RESUME:
                    add(sums,active,began,e.at,start,end,screenOn&&unlocked,included);
                    active=e; began=e.at;
                    break;
                case PAUSE: case STOP:
                    if(matches(active,e)) {
                        add(sums,active,began,e.at,start,end,screenOn&&unlocked,included);
                        active=null;
                    }
                    break;
                case SCREEN_OFF: case LOCK:
                    add(sums,active,began,e.at,start,end,screenOn&&unlocked,included);
                    began=e.at;
                    if(e.kind==Kind.SCREEN_OFF) screenOn=false; else unlocked=false;
                    break;
                case SCREEN_ON: case UNLOCK:
                    // Never backfill the locked/screen-off interval on resumption.
                    add(sums,active,began,e.at,start,end,screenOn&&unlocked,included);
                    if(e.kind==Kind.SCREEN_ON) screenOn=true; else unlocked=true;
                    began=e.at;
                    break;
                case STARTUP: case SHUTDOWN:
                    // Android reset markers do not establish an exact close time.
                    // Discard an unclosed interval instead of inventing reboot usage.
                    active=null; began=e.at; screenOn=true; unlocked=true;
                    break;
            }
        }
        add(sums,active,began,end,start,end,screenOn&&unlocked,included);
        String current=active!=null && screenOn && unlocked && !active.pkg.isEmpty() && included.test(active.pkg)?active.pkg:null;
        return new Summary(sums,current);
    }
    private UsageTimeline() {}
}
