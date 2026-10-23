package work.timelens.app;

import java.time.*;
import java.util.*;
import static work.timelens.app.UsageTimeline.Kind.*;

/** Executable domain regressions; no Android SDK or test framework required. */
public final class UsageTimelineTest {
    static int passed;
    static UsageTimeline.Event e(long at,UsageTimeline.Kind kind,String pkg,String activity){return new UsageTimeline.Event(at,kind,pkg,activity);}
    static UsageTimeline.Event e(long at,UsageTimeline.Kind kind,String pkg){return e(at,kind,pkg,"Main");}
    static UsageTimeline.Event state(long at,UsageTimeline.Kind kind){return e(at,kind,null,null);}
    static UsageTimeline.Summary run(long start,long end,UsageTimeline.Event...events){return UsageTimeline.summarize(Arrays.asList(events),start,end,p->!p.equals("home")&&!p.equals("system"));}
    static void equal(String label,long expected,long actual){if(expected!=actual)throw new AssertionError(label+": expected "+expected+" got "+actual);passed++;}
    public static void main(String[] args){
        equal("closed session",100,run(0,200,e(20,RESUME,"A"),e(120,PAUSE,"A")).total);
        equal("open session reaches now",180,run(0,200,e(20,RESUME,"A")).total);
        UsageTimeline.Summary sw=run(0,200,e(0,RESUME,"A"),e(60,RESUME,"B"),e(70,PAUSE,"A"));
        equal("late old-app pause cannot close new app",140,sw.perApp.get("B"));equal("no app-switch double counting",200,sw.total);
        UsageTimeline.Summary rotation=run(0,200,e(0,RESUME,"A","First"),e(60,RESUME,"A","Second"),e(70,PAUSE,"A","First"));
        equal("old activity class pause does not close new activity",200,rotation.total);
        equal("screen off excluded",50,run(0,500,e(0,RESUME,"A"),state(50,SCREEN_OFF)).total);
        equal("lock excluded until unlock",150,run(0,500,e(0,RESUME,"A"),state(50,LOCK),state(100,SCREEN_OFF),state(350,SCREEN_ON),state(400,UNLOCK)).total);
        equal("unlock after resume preserves confirmed interval",100,run(0,100,e(0,RESUME,"A"),state(20,UNLOCK),state(30,SCREEN_ON)).total);
        equal("midnight clips carried session",100,run(100,200,e(50,RESUME,"A")).total);
        equal("pause before range gives zero",0,run(100,200,e(10,RESUME,"A"),e(50,PAUSE,"A")).total);
        equal("missing resume is not invented",0,run(0,200,e(100,PAUSE,"A")).total);
        equal("launcher omitted",100,run(0,300,e(0,RESUME,"A"),e(100,RESUME,"home")).total);
        equal("out-of-order input sorted",100,run(0,200,e(150,PAUSE,"A"),e(50,RESUME,"A")).total);
        equal("duplicate resume cannot add time",200,run(0,200,e(0,RESUME,"A"),e(0,RESUME,"A")).total);
        equal("stop closes same activity",80,run(0,200,e(0,RESUME,"A"),e(80,STOP,"A")).total);
        equal("shutdown reset does not invent downtime",50,run(0,1000,e(0,RESUME,"A"),state(100,SHUTDOWN),state(900,STARTUP),e(950,RESUME,"B")).total);
        equal("non-active pause ignored",200,run(0,200,e(0,RESUME,"A"),e(80,PAUSE,"B")).total);
        equal("exclusive end",0,run(0,100,e(100,RESUME,"A")).total);
        equal("zero-length range",0,run(10,10,e(0,RESUME,"A")).total);
        equal("excluded transient system activity creates gap",100,run(0,200,e(0,RESUME,"A"),e(50,RESUME,"system"),e(150,RESUME,"B")).total);
        ZoneId ny=ZoneId.of("America/New_York");LocalDate dst=LocalDate.of(2026,3,8);
        long from=dst.atStartOfDay(ny).toInstant().toEpochMilli(),to=dst.plusDays(1).atStartOfDay(ny).toInstant().toEpochMilli();
        equal("DST day is 23 hours, not assumed 24",23*3600000L,run(from,to,e(from-100,RESUME,"A")).total);
        List<UsageTimeline.Event> random=new ArrayList<>();Random rng=new Random(42);long at=0;
        for(int i=0;i<1000;i++){at+=rng.nextInt(100)+1;random.add(e(at,RESUME,"app"+rng.nextInt(5)));if(i%7==0)random.add(state(at+1,SCREEN_OFF));if(i%7==1)random.add(state(at+1,SCREEN_ON));}
        UsageTimeline.Summary result=UsageTimeline.summarize(random,0,at+100,p->true);
        if(result.total>at+100||result.total<0)throw new AssertionError("Total cannot exceed observation window");passed++;
        equal("total equals sum of app durations",result.total,result.perApp.values().stream().mapToLong(Long::longValue).sum());
        System.out.println("PASS: "+passed+" counting assertions (switches, lock, midnight, reboot, DST, overlap, bounds)");
    }
}
