package com.lifeHub.login;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

/** Reject stale screens/restored forms after logout or account switching. */
public abstract class AccountActivity extends AppCompatActivity {
    private String boundSession;
    private boolean ready;
    public boolean isAccountReady(){return ready;}
    @Override protected void onCreate(Bundle state) {
        AccountScope.initialize(this);
        String current=AccountScope.session(this);
        String previous=state==null?getIntent().getStringExtra("account_session"):state.getString("account_session");
        ready=LoginManager.isLoggedIn(this) && (previous==null || previous.equals(current));
        boundSession=current;
        getIntent().putExtra("account_session",previous==null?current:previous);
        super.onCreate(ready?state:null);
        if(!ready)redirect();
    }
    @Override protected void onResume() {
        super.onResume();
        if(!LoginManager.isLoggedIn(this) || !AccountScope.session(this).equals(boundSession)){ready=false;redirect();}
    }
    private void redirect() {
        startActivity(new Intent(this,LoginActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        finish();
    }
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putString("account_session",boundSession);}
}
