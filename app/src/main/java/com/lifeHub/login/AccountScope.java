package com.lifeHub.login;

import android.content.Context;
import android.content.SharedPreferences;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Account identity is resolved locally; no username or caller-supplied owner enters a DB filename. */
public final class AccountScope {
    private static final String REGISTRY="account_storage";
    public static String keyFor(String username) {
        try {
            byte[] digest=MessageDigest.getInstance("SHA-256").digest(username.getBytes(StandardCharsets.UTF_8));
            StringBuilder result=new StringBuilder("user_");
            for(byte value:digest)result.append(String.format(java.util.Locale.ROOT,"%02x",value & 255));
            return result.toString();
        } catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    public static String currentKey(Context context) {
        return LoginManager.isLoggedIn(context)?keyFor(LoginManager.getCurrentUser(context)):"signed_out";
    }
    public static synchronized void initialize(Context context) {
        SharedPreferences registry=context.getSharedPreferences(REGISTRY,0);
        if(registry.contains("legacy_owner"))return;
        // Freeze the upgrade-time owner BEFORE any login change. Never hand shared history to the next account.
        String owner=LoginManager.isLoggedIn(context)?currentKey(context):"unassigned";
        if(!registry.edit().putString("legacy_owner",owner).commit())throw new IllegalStateException("Cannot save legacy ownership");
    }
    public static String storageName(Context context,String base) {
        initialize(context);
        String key=currentKey(context);
        return key.equals(context.getSharedPreferences(REGISTRY,0).getString("legacy_owner","unassigned"))?base:base+"_"+key;
    }
    public static SharedPreferences preferences(Context context,String base) {
        return context.getSharedPreferences(storageName(context,base),0);
    }
    public static String session(Context context) {
        return currentKey(context)+":"+context.getSharedPreferences("user_login",0).getLong("session_generation",0);
    }
}
