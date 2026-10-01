package com.lifeHub.usage;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.lifeHub.R;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public class HealthActivity extends com.lifeHub.login.AccountActivity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private int generation;
    private Spinner spinner;
    private UsageRepository.Report report;
    private List<String> packages=new ArrayList<>();
    private String selectedPackage;
    private final int[] names={R.id.weekday_name0,R.id.weekday_name1,R.id.weekday_name2,R.id.weekday_name3,R.id.weekday_name4,R.id.weekday_name5,R.id.weekday_name6};
    private final int[] totalNames={R.id.weekday_total_name0,R.id.weekday_total_name1,R.id.weekday_total_name2,R.id.weekday_total_name3,R.id.weekday_total_name4,R.id.weekday_total_name5,R.id.weekday_total_name6};
    private final int[] times={R.id.weekday_time0,R.id.weekday_time1,R.id.weekday_time2,R.id.weekday_time3,R.id.weekday_time4,R.id.weekday_time5,R.id.weekday_time6};
    private final int[] bars={R.id.weekday_progressBar0,R.id.weekday_progressBar1,R.id.weekday_progressBar2,R.id.weekday_progressBar3,R.id.weekday_progressBar4,R.id.weekday_progressBar5,R.id.weekday_progressBar6};
    private final int[] totalTimes={R.id.weekday_total_time0,R.id.weekday_total_time1,R.id.weekday_total_time2,R.id.weekday_total_time3,R.id.weekday_total_time4,R.id.weekday_total_time5,R.id.weekday_total_time6};
    private final int[] totalBars={R.id.weekday_total_progressBar0,R.id.weekday_total_progressBar1,R.id.weekday_total_progressBar2,R.id.weekday_total_progressBar3,R.id.weekday_total_progressBar4,R.id.weekday_total_progressBar5,R.id.weekday_total_progressBar6};
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);if(!isAccountReady())return;setContentView(R.layout.activity_health);
        selectedPackage=state==null?null:state.getString("selectedPackage");
        spinner=findViewById(R.id.health_spinner);
        findViewById(R.id.health_activity_btn_back).setOnClickListener(v->finish());
        findViewById(R.id.health_refresh).setOnClickListener(v->{
            if(!UsageRepository.hasPermission(this))startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
            else refresh();
        });
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(AdapterView<?> parent) {}
            public void onItemSelected(AdapterView<?> parent,View view,int position,long id) {
                if(position<packages.size()){selectedPackage=packages.get(position);renderCharts();}
            }
        });
    }
    @Override protected void onResume(){super.onResume();
        if (!isAccountReady()) return;refresh();}
    @Override protected void onPause(){generation++;super.onPause();}
    @Override protected void onDestroy(){generation++;worker.shutdownNow();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putString("selectedPackage",selectedPackage);}
    private void refresh() {
        final int request=++generation;
        ((TextView)findViewById(R.id.health_status)).setText(R.string.usage_loading);
        worker.execute(()->{
            UsageRepository.Report next=UsageRepository.load(getApplicationContext());
            runOnUiThread(()->{
                if(isDestroyed() || isFinishing() || request!=generation)return;
                report=next;
                boolean ready=next.status==UsageRepository.Status.READY;
                ((TextView)findViewById(R.id.health_status)).setText(ready?R.string.usage_method:
                        next.status==UsageRepository.Status.PERMISSION_REQUIRED?R.string.usage_permission_help:R.string.usage_unavailable);
                ((Button)findViewById(R.id.health_refresh)).setText(next.status==UsageRepository.Status.PERMISSION_REQUIRED?R.string.usage_grant:R.string.update_button);
                findViewById(R.id.weekday_total_linearLayout).setVisibility(ready?View.VISIBLE:View.GONE);
                findViewById(R.id.weekday_total_title).setVisibility(ready?View.VISIBLE:View.GONE);
                packages=new ArrayList<>(next.usage.apps.keySet());
                packages.sort((a,b)->Long.compare(next.usage.totalFor(b),next.usage.totalFor(a)));
                List<String> labels=new ArrayList<>();
                for(String pkg:packages) {
                    String label=next.names.getOrDefault(pkg,pkg);
                    // Keep package identity even when two installed apps have identical display names.
                    if(Collections.frequency(new ArrayList<>(next.names.values()),label)>1)label+=" ("+pkg+")";
                    labels.add(label);
                }
                spinner.setVisibility(ready && !packages.isEmpty()?View.VISIBLE:View.GONE);
                findViewById(R.id.weekday_linearLayout).setVisibility(ready && !packages.isEmpty()?View.VISIBLE:View.GONE);
                ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,labels);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);spinner.setAdapter(adapter);
                if(!packages.isEmpty()) {
                    int index=packages.indexOf(selectedPackage);if(index<0)index=0;
                    selectedPackage=packages.get(index);spinner.setSelection(index);
                }
                if(ready)renderCharts();
            });
        });
    }
    private void renderCharts() {
        if(report==null)return;
        UsageTimeline.Result data=report.usage;
        Locale locale=getResources().getConfiguration().getLocales().get(0);
        SimpleDateFormat date=new SimpleDateFormat("MMM d",locale);
        long[] app=data.apps.get(selectedPackage);
        for(int i=0;i<7;i++) {
            String label=i==6?getString(R.string.today):date.format(new Date(data.boundaries[i]));
            ((TextView)findViewById(names[i])).setText(label);
            ((TextView)findViewById(totalNames[i])).setText(label);
            renderRow(times[i],bars[i],app==null?0:app[i],data.observed[i],data.boundaries[i+1]-data.boundaries[i]);
            renderRow(totalTimes[i],totalBars[i],data.totals[i],data.observed[i],data.boundaries[i+1]-data.boundaries[i]);
        }
    }
    private void renderRow(int time,int bar,long millis,boolean observed,long dayLength) {
        ((TextView)findViewById(time)).setText(observed?UsageRepository.duration(this,millis):getString(R.string.usage_no_records));
        ((ProgressBar)findViewById(bar)).setProgress(observed?(int)Math.min(100,millis*100/dayLength):0);
    }
}
