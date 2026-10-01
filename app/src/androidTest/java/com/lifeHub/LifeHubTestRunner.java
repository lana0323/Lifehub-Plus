package com.lifeHub;
public class LifeHubTestRunner extends androidx.test.runner.AndroidJUnitRunner {
    @Override public void onStart() {
        com.lifeHub.login.LoginManager.setLoggedIn(getTargetContext(),true,"qa-default");
        super.onStart();
    }
}
