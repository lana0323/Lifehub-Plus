package com.lifeHub.usage;

import java.util.*;

/** Recorded foreground usage, attributed to the most recently resumed activity (no double counting).
 * Pure Java so day boundaries and lifecycle event sequences can be verified without a device.
 */
public final class UsageTimeline {
    public static final int RESUME=1, PAUSE=2, SCREEN_ON=15, SCREEN_OFF=16,
            UNLOCK=18, LOCK=17, STOP=23, SHUTDOWN=26, STARTUP=27;
    public static final class Event {
        public final long time;
        public final int type;
        public final String app, activity;
        public Event(long time, int type, String app, String activity) {
            this.time=time; this.type=type; this.app=app; this.activity=activity;
        }
    }
    public static final class Result {
        public final long[] boundaries, totals;
        public final boolean[] observed;
        public final Map<String,long[]> apps=new LinkedHashMap<>();
        public final long asOf;
        Result(long[] boundaries, long asOf) {
            this.boundaries=boundaries; this.asOf=asOf;
            totals=new long[boundaries.length-1]; observed=new boolean[totals.length];
        }
        public long totalFor(String app) {
            long sum=0; long[] values=apps.get(app);
            if(values!=null)for(long value:values)sum+=value;
            return sum;
        }
        private void observe(long time) {
            for(int i=0;i<totals.length;i++)if(time>=boundaries[i] && time<boundaries[i+1])observed[i]=true;
        }
        private void add(String app,long start,long end) {
            if(app==null || end<=start)return;
            for(int i=0;i<totals.length;i++) {
                long duration=Math.min(Math.min(end,asOf),boundaries[i+1])-Math.max(start,boundaries[i]);
                if(duration>0) {
                    apps.computeIfAbsent(app,key->new long[totals.length])[i]+=duration;
                    totals[i]+=duration; observed[i]=true;
                }
            }
        }
    }
    public static long[] dayBoundaries(long now,TimeZone zone,int days) {
        Calendar c=Calendar.getInstance(zone); c.setTimeInMillis(now);
        c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);
        c.add(Calendar.DAY_OF_YEAR,1-days);
        long[] result=new long[days+1];
        for(int i=0;i<=days;i++){result[i]=c.getTimeInMillis();c.add(Calendar.DAY_OF_YEAR,1);}
        return result;
    }
    public static Result aggregate(List<Event> events,long[] boundaries,long now,boolean countOpenSession) {
        Result result=new Result(boundaries,now);
        List<Event> sorted=new ArrayList<>(events);
        sorted.sort(Comparator.comparingLong(e->e.time));
        String active=null,activity=null;long start=0;
        boolean screenOn=true,locked=false;
        for(Event e:sorted) {
            if(e.time>now)continue;
            if(e.type==RESUME || e.type==PAUSE || e.type==STOP || e.type==SCREEN_ON || e.type==SCREEN_OFF
                    || e.type==LOCK || e.type==UNLOCK || e.type==SHUTDOWN || e.type==STARTUP) result.observe(e.time);
            switch(e.type) {
                case STARTUP:
                    // No matching close before reboot: its duration is unknown, never bridge powered-off time.
                    active=null;activity=null;screenOn=true;locked=false;break;
                case SCREEN_ON: screenOn=true;break;
                case UNLOCK: locked=false;break;
                case RESUME:
                    if(e.app==null || e.app.isEmpty() || !screenOn || locked)break;
                    if(active!=null)result.add(active,start,e.time);
                    active=e.app;activity=e.activity;start=e.time;break;
                case PAUSE: case STOP:
                    if(Objects.equals(active,e.app) && (e.activity==null || activity==null || Objects.equals(activity,e.activity))) {
                        result.add(active,start,e.time);active=null;activity=null;
                    }
                    break;
                case SCREEN_OFF: case LOCK: case SHUTDOWN:
                    result.add(active,start,e.time);active=null;activity=null;
                    if(e.type==SCREEN_OFF)screenOn=false;
                    if(e.type==LOCK)locked=true;
                    if(e.type==SHUTDOWN){screenOn=false;locked=true;}
                    break;
                default: break; // Background services, notifications, etc. are not foreground usage.
            }
        }
        if(countOpenSession && screenOn && !locked)result.add(active,start,now);
        return result;
    }
}
