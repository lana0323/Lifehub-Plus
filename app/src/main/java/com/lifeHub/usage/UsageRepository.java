package com.lifeHub.usage;

import android.app.AppOpsManager;
import android.app.KeyguardManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.PowerManager;
import java.util.*;

/** One event query feeds both the daily summary and the seven-day details. Call off the UI thread. */
public final class UsageRepository {
    public enum Status { READY, PERMISSION_REQUIRED, UNAVAILABLE }
    public static final class Report {
        public final Status status;
        public final UsageTimeline.Result usage;
        public final Map<String,String> names;
        Report(Status status,UsageTimeline.Result usage,Map<String,String> names) {
            this.status=status;this.usage=usage;this.names=names;
        }
    }
    public static boolean hasPermission(Context context) {
        AppOpsManager ops=(AppOpsManager)context.getSystemService(Context.APP_OPS_SERVICE);
        return ops!=null && ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),context.getPackageName())==AppOpsManager.MODE_ALLOWED;
    }
    public static Report load(Context context) {
        long now=System.currentTimeMillis();
        long[] boundaries=UsageTimeline.dayBoundaries(now,TimeZone.getDefault(),7);
        UsageTimeline.Result empty=UsageTimeline.aggregate(Collections.emptyList(),boundaries,now,false);
        if(!hasPermission(context))return new Report(Status.PERMISSION_REQUIRED,empty,Collections.emptyMap());
        UsageStatsManager manager=(UsageStatsManager)context.getSystemService(Context.USAGE_STATS_SERVICE);
        if(manager==null)return new Report(Status.UNAVAILABLE,empty,Collections.emptyMap());
        try {
            Calendar seed=Calendar.getInstance();seed.setTimeInMillis(boundaries[0]);seed.add(Calendar.DAY_OF_YEAR,-1);
            // A look-back day reconstructs an activity already in progress at the first midnight.
            UsageEvents source=manager.queryEvents(seed.getTimeInMillis(),now);
            if(source==null)return new Report(Status.UNAVAILABLE,empty,Collections.emptyMap());
            List<UsageTimeline.Event> events=new ArrayList<>();
            UsageEvents.Event event=new UsageEvents.Event();
            while(source.hasNextEvent()) {
                source.getNextEvent(event);
                String activity=event.getClassName();
                events.add(new UsageTimeline.Event(event.getTimeStamp(),event.getEventType(),event.getPackageName(),activity));
            }
            PowerManager power=(PowerManager)context.getSystemService(Context.POWER_SERVICE);
            KeyguardManager keyguard=(KeyguardManager)context.getSystemService(Context.KEYGUARD_SERVICE);
            boolean interactive=power!=null && power.isInteractive() && (keyguard==null || !keyguard.isKeyguardLocked());
            UsageTimeline.Result result=UsageTimeline.aggregate(events,boundaries,now,interactive);
            Map<String,String> names=new HashMap<>();
            PackageManager pm=context.getPackageManager();
            for(String pkg:result.apps.keySet()) {
                try { names.put(pkg,pm.getApplicationLabel(pm.getApplicationInfo(pkg,0)).toString()); }
                catch(PackageManager.NameNotFoundException e) { names.put(pkg,pkg); }
            }
            return new Report(Status.READY,result,names);
        } catch(SecurityException e) { return new Report(Status.PERMISSION_REQUIRED,empty,Collections.emptyMap()); }
        catch(RuntimeException e) { return new Report(Status.UNAVAILABLE,empty,Collections.emptyMap()); }
    }
    public static String duration(Context context,long millis) {
        return context.getString(com.lifeHub.R.string.usage_duration,millis/3600000,(millis/60000)%60,(millis/1000)%60);
    }
}
