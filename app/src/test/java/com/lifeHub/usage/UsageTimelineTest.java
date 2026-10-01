package com.lifeHub.usage;
import org.junit.Test;
import static org.junit.Assert.*;
import static com.lifeHub.usage.UsageTimeline.*;
import java.util.*;
import java.time.*;

public class UsageTimelineTest {
    private Event e(long t,int type,String app){return new Event(t,type,app,"Main");}
    @Test public void differentDaysAreNotRepeatedAndCrossMidnightIsSplit() {
        Result r=aggregate(Arrays.asList(e(50,RESUME,"one"),e(130,PAUSE,"one"),e(230,RESUME,"two"),e(240,PAUSE,"two")),new long[]{0,100,200,300},280,true);
        assertArrayEquals(new long[]{50,30,10},r.totals);
        assertArrayEquals(new long[]{50,30,0},r.apps.get("one"));
        assertArrayEquals(new long[]{0,0,10},r.apps.get("two"));
    }
    @Test public void boundsClipLookbackAndOpenSessionAndIgnoreFuture() {
        Result r=aggregate(Arrays.asList(e(20,RESUME,"a"),e(250,PAUSE,"a")),new long[]{100,200,300},220,true);
        assertArrayEquals(new long[]{100,20},r.totals);
        assertEquals(0,aggregate(Arrays.asList(e(20,RESUME,"a")),new long[]{100,200},180,false).totals[0]);
    }
    @Test public void switchesAndDelayedOldActivityPauseNeverDoubleCount() {
        Result r=aggregate(Arrays.asList(new Event(10,RESUME,"a","Old"),new Event(20,RESUME,"a","New"),new Event(30,PAUSE,"a","Old"),
                new Event(40,PAUSE,"a","New"),e(50,RESUME,"b"),e(60,RESUME,"c"),e(70,PAUSE,"b"),e(80,PAUSE,"c")),new long[]{0,100},90,true);
        assertEquals(60,r.totals[0]);assertEquals(30,r.totalFor("a"));assertEquals(10,r.totalFor("b"));assertEquals(20,r.totalFor("c"));
    }
    @Test public void lockScreenOffAndBackgroundServicesAreExcluded() {
        Result r=aggregate(Arrays.asList(e(10,RESUME,"a"),e(20,SCREEN_OFF,null),e(25,RESUME,"ghost"),e(40,SCREEN_ON,null),e(45,RESUME,"a"),
                e(50,LOCK,null),e(55,RESUME,"ghost"),e(60,UNLOCK,null),e(65,RESUME,"b"),e(70,PAUSE,"b"),e(80,19,"service")),new long[]{0,100},90,true);
        assertEquals(20,r.totals[0]);assertFalse(r.apps.containsKey("ghost"));assertFalse(r.apps.containsKey("service"));
    }
    @Test public void rebootDoesNotCountPoweredOffGapOrUnclosedSession() {
        Result r=aggregate(Arrays.asList(e(10,RESUME,"a"),e(20,SHUTDOWN,null),e(25,RESUME,"a"),e(1000,STARTUP,null),e(1010,RESUME,"b"),e(1020,PAUSE,"b")),new long[]{0,2000},1500,true);
        assertEquals(20,r.totals[0]);
        Result abrupt=aggregate(Arrays.asList(e(10,RESUME,"a"),e(1000,STARTUP,null),e(1010,RESUME,"b"),e(1020,PAUSE,"b")),new long[]{0,2000},1500,true);
        assertEquals(10,abrupt.totals[0]);assertFalse(abrupt.apps.containsKey("a"));
    }
    @Test public void fullPackageNamesRemainSeparateAndDuplicateEventsAreHarmless() {
        Result r=aggregate(Arrays.asList(e(10,RESUME,"first.reader"),e(10,RESUME,"first.reader"),e(20,PAUSE,"first.reader"),
                e(20,PAUSE,"first.reader"),e(30,RESUME,"other.reader"),e(50,PAUSE,"other.reader")),new long[]{0,100},90,true);
        assertEquals(2,r.apps.size());assertEquals(10,r.totalFor("first.reader"));assertEquals(20,r.totalFor("other.reader"));assertEquals(30,r.totals[0]);
    }
    @Test public void absentHistoryIsNotClaimedAsZeroObservedUsage() {
        Result r=aggregate(Arrays.asList(e(120,SCREEN_ON,null),e(130,SCREEN_OFF,null)),new long[]{0,100,200,300},280,true);
        assertArrayEquals(new boolean[]{false,true,false},r.observed);assertArrayEquals(new long[]{0,0,0},r.totals);
        assertFalse(aggregate(Collections.emptyList(),new long[]{0,100},90,true).observed[0]);
    }
    @Test public void localMidnightRespectsBothDaylightSavingTransitions() {
        TimeZone zone=TimeZone.getTimeZone("America/Los_Angeles");
        long[] spring=dayBoundaries(ZonedDateTime.of(2026,3,8,12,0,0,0,zone.toZoneId()).toInstant().toEpochMilli(),zone,7);
        long[] fall=dayBoundaries(ZonedDateTime.of(2026,11,1,12,0,0,0,zone.toZoneId()).toInstant().toEpochMilli(),zone,7);
        assertEquals(23*3600000L,spring[7]-spring[6]);assertEquals(25*3600000L,fall[7]-fall[6]);
        assertEquals(LocalDate.of(2026,3,2),Instant.ofEpochMilli(spring[0]).atZone(zone.toZoneId()).toLocalDate());
    }
}
