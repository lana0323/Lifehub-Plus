package com.lifeHub.main.ui;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.lifeHub.R;
import com.lifeHub.databinding.ActivityMainPageBinding;

public class MainPage extends com.lifeHub.login.AccountActivity {

    private ActivityMainPageBinding binding;
    private NavController navController;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(!isAccountReady())return;

        binding = ActivityMainPageBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        NavHostFragment navHostFragment =
                (NavHostFragment) getSupportFragmentManager()
                        .findFragmentById(R.id.nav_host_fragment_activity_main_page);

        if (navHostFragment == null) {
            throw new IllegalStateException("NavHostFragment not found. Check activity_main_page.xml");
        }

        navController = navHostFragment.getNavController();
        navHostFragment.getChildFragmentManager().registerFragmentLifecycleCallbacks(new androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks() {
            @Override public void onFragmentViewCreated(androidx.fragment.app.FragmentManager fm, androidx.fragment.app.Fragment fragment, View view, Bundle state) {
                if (android.provider.Settings.Global.getFloat(getContentResolver(), android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f) return;
                view.setAlpha(0f); view.setTranslationY(12f*getResources().getDisplayMetrics().density);
                view.animate().alpha(1f).translationY(0f).setDuration(220).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
            }
        },false);


        BottomNavigationView navView = binding.navView;
        MaterialToolbar topAppBar = binding.topAppBar;

        navView.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            NavDestination current = navController.getCurrentDestination();

            if (id == R.id.navigation_home) {
                if (current == null || current.getId() != R.id.navigation_home) {
                    navController.navigate(R.id.navigation_home);
                }
                return true;

            } else if (id == R.id.navigation_ai) {

                boolean popped = navController.popBackStack(R.id.navigation_ai, false);
                if (!popped) {

                    if (current == null || current.getId() != R.id.navigation_ai) {
                        navController.navigate(R.id.navigation_ai);
                    }
                }
                return true;

            } else if (id == R.id.navigation_profile) {
                if (current == null || current.getId() != R.id.navigation_profile) {
                    navController.navigate(R.id.navigation_profile);
                }
                return true;
            }

            return false;
        });

        navView.setOnItemReselectedListener(item -> {
        });

        topAppBar.setVisibility(View.GONE);

        if (savedInstanceState == null) {
            navView.setSelectedItemId(R.id.navigation_home);
        }
    }
}
