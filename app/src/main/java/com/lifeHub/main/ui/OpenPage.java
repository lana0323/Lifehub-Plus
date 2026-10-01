package com.lifeHub.main.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import androidx.appcompat.app.AppCompatActivity;
import com.lifeHub.R;
import com.lifeHub.login.LoginActivity;
import com.lifeHub.login.LoginManager;

public class OpenPage extends AppCompatActivity {
    private static final long SPLASH_TIME = 500;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long readyAt;
    private boolean navigated;
    private final Runnable navigate = () -> {
        if (navigated || isFinishing() || isDestroyed()) return;
        navigated = true;
        startActivity(new Intent(this, LoginManager.isLoggedIn(this) ? MainPage.class : LoginActivity.class));
        finish();
    };
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        com.lifeHub.login.AccountScope.initialize(this);
        setContentView(R.layout.activity_open_page);
        readyAt = state == null ? SystemClock.elapsedRealtime() + SPLASH_TIME : Math.min(state.getLong("readyAt"), SystemClock.elapsedRealtime() + SPLASH_TIME);
    }
    @Override protected void onResume() {
        super.onResume();
        handler.postDelayed(navigate, Math.max(0, readyAt - SystemClock.elapsedRealtime()));
    }
    @Override protected void onPause() { handler.removeCallbacks(navigate); super.onPause(); }
    @Override protected void onDestroy() { handler.removeCallbacks(navigate); super.onDestroy(); }
    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); out.putLong("readyAt", readyAt); }
}
