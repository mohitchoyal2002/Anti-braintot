package work.timelens.app;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.time.*;
import java.util.*;

/** Atomic daily snapshots; app-private storage, never uploaded or backed up. */
final class UsageStore extends SQLiteOpenHelper {
    UsageStore(Context context) { super(context,"timelens.db",null,1); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE days (day TEXT PRIMARY KEY, total_ms INTEGER NOT NULL, updated_ms INTEGER NOT NULL, zone TEXT NOT NULL)");
        db.execSQL("CREATE TABLE apps (day TEXT NOT NULL, package TEXT NOT NULL, duration_ms INTEGER NOT NULL, PRIMARY KEY(day,package))");
        db.execSQL("CREATE TABLE events (at INTEGER NOT NULL, kind TEXT NOT NULL, package TEXT NOT NULL, activity TEXT NOT NULL, PRIMARY KEY(at,kind,package,activity))");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion) { throw new IllegalStateException("Unsupported database version"); }
    void save(LocalDate day,UsageTimeline.Summary summary,long updated) {
        SQLiteDatabase db=getWritableDatabase(); db.beginTransaction();
        try {
            ContentValues d=new ContentValues(); d.put("day",day.toString()); d.put("total_ms",summary.total); d.put("updated_ms",updated); d.put("zone",ZoneId.systemDefault().getId());
            db.insertWithOnConflict("days",null,d,SQLiteDatabase.CONFLICT_REPLACE);
            db.delete("apps","day=?",new String[]{day.toString()});
            for(Map.Entry<String,Long> e:summary.perApp.entrySet()) {
                ContentValues a=new ContentValues(); a.put("day",day.toString()); a.put("package",e.getKey()); a.put("duration_ms",e.getValue()); db.insertOrThrow("apps",null,a);
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    Map<String,Long> appTotals(LocalDate day) {
        Map<String,Long> result=new LinkedHashMap<>();
        try(Cursor c=getReadableDatabase().query("apps",new String[]{"package","duration_ms"},"day=?",new String[]{day.toString()},null,null,"duration_ms DESC")) {
            while(c.moveToNext()) result.put(c.getString(0),c.getLong(1));
        }
        return result;
    }
    Map<LocalDate,Long> history() {
        Map<LocalDate,Long> result=new LinkedHashMap<>();
        try(Cursor c=getReadableDatabase().query("days",new String[]{"day","total_ms"},null,null,null,null,"day DESC","30")) {
            while(c.moveToNext()) result.put(LocalDate.parse(c.getString(0)),c.getLong(1));
        }
        return result;
    }
    List<UsageTimeline.Event> loadEvents(long since) {
        List<UsageTimeline.Event> result=new ArrayList<>();
        try(Cursor c=getReadableDatabase().query("events",null,"at>=?",new String[]{Long.toString(since)},null,null,"at ASC, rowid ASC")) {
            while(c.moveToNext()) result.add(new UsageTimeline.Event(c.getLong(c.getColumnIndexOrThrow("at")),UsageTimeline.Kind.valueOf(c.getString(c.getColumnIndexOrThrow("kind"))),c.getString(c.getColumnIndexOrThrow("package")),c.getString(c.getColumnIndexOrThrow("activity"))));
        }
        return result;
    }
    void saveEvents(List<UsageTimeline.Event> events,long keepSince) {
        SQLiteDatabase db=getWritableDatabase(); db.beginTransaction();
        try {
            for(UsageTimeline.Event e:events) {
                ContentValues v=new ContentValues(); v.put("at",e.at); v.put("kind",e.kind.name()); v.put("package",e.pkg); v.put("activity",e.activity);
                db.insertWithOnConflict("events",null,v,SQLiteDatabase.CONFLICT_IGNORE);
            }
            db.delete("events","at<?",new String[]{Long.toString(keepSince)});
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
}
