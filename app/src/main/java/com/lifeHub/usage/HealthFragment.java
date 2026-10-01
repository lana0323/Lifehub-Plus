package com.lifeHub.usage;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import com.lifeHub.R;
import java.util.*;
import java.util.concurrent.*;

public class HealthFragment extends Fragment {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private int generation;
    private final int[][] rows={
            {R.id.app_name0,R.id.app_time0,R.id.progressBar0},
            {R.id.app_name1,R.id.app_time1,R.id.progressBar1},
            {R.id.app_name2,R.id.app_time2,R.id.progressBar2},
            {R.id.app_name3,R.id.app_time3,R.id.progressBar3},
            {R.id.app_name4,R.id.app_time4,R.id.progressBar4}};
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,@Nullable ViewGroup container,@Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_health,container,false);
    }
    @Override public void onViewCreated(@NonNull View view,@Nullable Bundle state) {
        view.findViewById(R.id.health_btn_back).setOnClickListener(v->NavHostFragment.findNavController(this).popBackStack());
        view.findViewById(R.id.update_button).setOnClickListener(v->{
            if(!UsageRepository.hasPermission(requireContext()))startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));else refresh();
        });
        view.findViewById(R.id.detail_button).setOnClickListener(v->startActivity(new Intent(requireContext(),HealthActivity.class)));
    }
    @Override public void onResume(){super.onResume();refresh();}
    @Override public void onPause(){generation++;super.onPause();}
    @Override public void onDestroyView(){generation++;super.onDestroyView();}
    @Override public void onDestroy(){worker.shutdownNow();super.onDestroy();}
    private void refresh() {
        View view=getView();if(view==null)return;
        android.content.Context context=requireContext().getApplicationContext();
        int request=++generation;
        ((TextView)view.findViewById(R.id.phone_usage_time_advice)).setText(R.string.usage_loading);
        worker.execute(()->{
            UsageRepository.Report report=UsageRepository.load(context);
            view.post(()->{if(getView()==view && isAdded() && request==generation)render(view,report);});
        });
    }
    private void render(View view,UsageRepository.Report report) {
        boolean ready=report.status==UsageRepository.Status.READY;
        TextView total=view.findViewById(R.id.phone_usage_time);
        TextView hint=view.findViewById(R.id.phone_usage_time_advice);
        ((Button)view.findViewById(R.id.update_button)).setText(report.status==UsageRepository.Status.PERMISSION_REQUIRED?R.string.usage_grant:R.string.update_button);
        view.findViewById(R.id.detail_button).setVisibility(ready?View.VISIBLE:View.GONE);
        List<String> packages=new ArrayList<>(report.usage.apps.keySet());
        packages.removeIf(pkg->report.usage.apps.get(pkg)[6]==0);
        packages.sort((a,b)->Long.compare(report.usage.apps.get(b)[6],report.usage.apps.get(a)[6]));
        view.findViewById(R.id.linearLayout).setVisibility(ready && !packages.isEmpty()?View.VISIBLE:View.GONE);
        if(!ready){total.setText("—");hint.setText(report.status==UsageRepository.Status.PERMISSION_REQUIRED?R.string.usage_permission_help:R.string.usage_unavailable);return;}
        long millis=report.usage.totals[6];
        total.setText(report.usage.observed[6]?UsageRepository.duration(requireContext(),millis):getString(R.string.usage_no_records));
        hint.setText(R.string.usage_method);
        for(int i=0;i<rows.length;i++) {
            boolean exists=i<packages.size();
            for(int id:rows[i])view.findViewById(id).setVisibility(exists?View.VISIBLE:View.GONE);
            if(!exists)continue;
            String pkg=packages.get(i), label=report.names.getOrDefault(pkg,pkg);
            if(Collections.frequency(new ArrayList<>(report.names.values()),label)>1)label+=" ("+pkg+")";
            long value=report.usage.apps.get(pkg)[6];
            ((TextView)view.findViewById(rows[i][0])).setText(label);
            ((TextView)view.findViewById(rows[i][1])).setText(UsageRepository.duration(requireContext(),value));
            ((ProgressBar)view.findViewById(rows[i][2])).setProgress(millis==0?0:(int)(value*100/millis));
        }
    }
}
