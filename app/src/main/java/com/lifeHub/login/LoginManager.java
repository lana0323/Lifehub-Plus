package com.lifeHub.login;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.net.Uri;


public class LoginManager {
    public static final java.util.concurrent.Executor AUTH_EXECUTOR = java.util.concurrent.Executors.newSingleThreadExecutor();

    private static final String PREF_NAME = "user_login";

    private static final String KEY_LOGGED_IN = "is_logged_in";
    private static final String KEY_CURRENT_USER = "current_user";

    private static final String PREFIX_PWD = "pwd_";
    private static final String PREFIX_HASH = "password_hash_";
    private static final String KEY_AVATAR_PREFIX = "avatar_uri_";


    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static boolean isLoggedIn(Context context) {
        return getPrefs(context).getBoolean(KEY_LOGGED_IN, false)
                && !TextUtils.isEmpty(getCurrentUser(context));
    }

    public static void setLoggedIn(Context context, boolean loggedIn, String username) {
        AccountScope.initialize(context);
        SharedPreferences.Editor editor = getPrefs(context).edit();
        editor.putLong("session_generation",getPrefs(context).getLong("session_generation",0)+1);
        editor.putBoolean(KEY_LOGGED_IN, loggedIn);
        if (loggedIn) {
            editor.putString(KEY_CURRENT_USER, username);
        } else {
            editor.remove(KEY_CURRENT_USER);
        }
        if(!editor.commit())throw new IllegalStateException("Cannot save session");
    }

    public static String getCurrentUser(Context context) {
        return getPrefs(context).getString(KEY_CURRENT_USER, "");
    }

    public static void logout(Context context) {
        setLoggedIn(context,false,"");
    }

    public static void clearAll(Context context) {
        AccountScope.initialize(context);
        long next=getPrefs(context).getLong("session_generation",0)+1;
        getPrefs(context).edit().clear().putLong("session_generation",next).commit();
    }

    public static void register(Context context, String username, String password) {
        if (TextUtils.isEmpty(username)) return;
        boolean saved = getPrefs(context).edit()
                .putString(PREFIX_HASH + username, PasswordHasher.hash(password))
                .remove(PREFIX_PWD + username).commit();
        if (!saved) throw new IllegalStateException("Could not save profile");
    }

    public static boolean hasRegisteredUser(Context context, String username) {
        if (TextUtils.isEmpty(username)) return false;
        return getPrefs(context).contains(PREFIX_HASH + username) || getPrefs(context).contains(PREFIX_PWD + username);
    }

    public static boolean checkLogin(Context context, String username, String password) {
        if (TextUtils.isEmpty(username) || TextUtils.isEmpty(password)) return false;
        SharedPreferences sp = getPrefs(context);
        if (sp.contains(PREFIX_HASH + username)) {
            return PasswordHasher.verify(password, sp.getString(PREFIX_HASH + username, null));
        }
        String savedPwd = sp.getString(PREFIX_PWD + username, null);
        if (savedPwd == null || !savedPwd.equals(password)) return false;
        // Upgrade a legacy credential only after successful verification; one atomic edit.
        register(context, username, password);
        return true;
    }

    public static void saveAvatarUri(Context context, String username, Uri uri) {
        String key = avatarKeyForUser(username);
        boolean saved = getPrefs(context).edit().putString(key, uri == null ? null : uri.toString()).commit();
        if (!saved) throw new IllegalStateException("Cannot save avatar preference");
        context.getSharedPreferences("user_prefs",Context.MODE_PRIVATE).edit().remove(key).apply();
    }
    public static Uri getAvatarUri(Context context, String username) {
        String key=avatarKeyForUser(username);
        String value=getPrefs(context).getString(key,null);
        if(value==null)value=context.getSharedPreferences("user_prefs",Context.MODE_PRIVATE).getString(key,null);
        return value==null || value.isEmpty()?null:Uri.parse(value);
    }


    private static String avatarKeyForUser(String username) {
        if (username == null) {
            username = "";
        }
        return KEY_AVATAR_PREFIX + username;
    }


}
