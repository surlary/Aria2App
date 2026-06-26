package com.gianlu.aria2app;

import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.annotation.NonNull;

import com.gianlu.aria2app.api.ConnectivityChangedReceiver;
import com.gianlu.aria2app.api.ErrorHandler;
import com.gianlu.aria2app.api.NetInstanceHolder;
import com.gianlu.aria2app.api.search.SearchApi;
import com.gianlu.aria2app.profiles.ProfilesManager;
import com.gianlu.aria2app.services.NotificationService;
import com.gianlu.commonutils.analytics.AnalyticsApplication;
import com.gianlu.commonutils.preferences.CommonPK;
import com.gianlu.commonutils.preferences.Prefs;
import com.gianlu.commonutils.preferences.PrefsStorageModule;
import com.gianlu.commonutils.ui.Toaster;
import com.yarolegovich.mp.io.MaterialPreferences;

import java.util.HashSet;
import java.util.Set;

public final class ThisApplication extends AnalyticsApplication implements ErrorHandler.Listener {
    public static final boolean DEBUG_UPDATER = false;
    public static final boolean DEBUG_NOTIFICATION = false;
    private static final String TAG = ThisApplication.class.getSimpleName();
    private final Set<String> checkedVersionFor = new HashSet<>();
    private final SharedPreferences.OnSharedPreferenceChangeListener toggleNotificationServiceListener = (sharedPreferences, key) -> {
        if (key.equals(PK.A2_ENABLE_NOTIFS.key())) {
            if (Prefs.getBoolean(PK.A2_ENABLE_NOTIFS, true))
                NotificationService.start(ThisApplication.this);
            else
                NotificationService.stop(ThisApplication.this);
        }
    };
    private ConnectivityChangedReceiver connectivityChangedReceiver;

    public boolean shouldCheckVersion() {
        try {
            return !checkedVersionFor.contains(ProfilesManager.get(this).getCurrent().id);
        } catch (ProfilesManager.NoCurrentProfileException ignored) {
            return true;
        }
    }

    public void checkedVersion() {
        try {
            checkedVersionFor.add(ProfilesManager.get(this).getCurrent().id);
        } catch (ProfilesManager.NoCurrentProfileException ignored) {
        }
    }

    @Override
    protected boolean isDebug() {
        return BuildConfig.DEBUG;
    }

    @NonNull
    @Override
    protected String getGithubProjectName() {
        return "Aria2App";
    }

    @Override
    public void onCreate() {
        super.onCreate();
        SearchApi.get().cacheSearchEngines();
        MaterialPreferences.setStorageModule(new PrefsStorageModule.Factory());

        ErrorHandler.setup(Prefs.getInt(PK.A2_UPDATE_INTERVAL) * 1000, this);

        // Backward compatibility
        if (!Prefs.has(PK.A2_CUSTOM_INFO)) {
            Set<String> defaultValues = new HashSet<>();
            defaultValues.add(CustomDownloadInfo.Info.DOWNLOAD_SPEED.name());
            defaultValues.add(CustomDownloadInfo.Info.REMAINING_TIME.name());
            Prefs.putSet(PK.A2_CUSTOM_INFO, defaultValues);
        }

        deprecatedBackwardCompatibility();

        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(toggleNotificationServiceListener);

        connectivityChangedReceiver = new ConnectivityChangedReceiver(this);
        getApplicationContext().registerReceiver(connectivityChangedReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    }

    @Override
    public void onTerminate() {
        getApplicationContext().unregisterReceiver(connectivityChangedReceiver);
        PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(toggleNotificationServiceListener);

        super.onTerminate();
    }

    @SuppressWarnings("deprecation")
    private void deprecatedBackwardCompatibility() {
        if (Prefs.has(PK.A2_QUICK_OPTIONS) || Prefs.has(PK.A2_GLOBAL_QUICK_OPTIONS)) {
            Set<String> set = new HashSet<>();
            set.addAll(Prefs.getSet(PK.A2_QUICK_OPTIONS, new HashSet<>()));
            set.addAll(Prefs.getSet(PK.A2_GLOBAL_QUICK_OPTIONS, new HashSet<>()));
            Prefs.putSet(PK.A2_QUICK_OPTIONS_MIXED, set);
            Prefs.remove(PK.A2_QUICK_OPTIONS);
            Prefs.remove(PK.A2_GLOBAL_QUICK_OPTIONS);
        }

        if (Prefs.has(PK.A2_TUTORIAL_DISCOVERIES)) {
            Set<String> set = Prefs.getSet(PK.A2_TUTORIAL_DISCOVERIES, null);
            if (set != null) Prefs.putSet(CommonPK.TUTORIAL_DISCOVERIES, set);
            Prefs.remove(PK.A2_TUTORIAL_DISCOVERIES);
        }
    }

    @Override
    public void onFatal(@NonNull Throwable ex) {
        NetInstanceHolder.close();
        Toaster.with(this).message(R.string.fatalExceptionMessage).show();
        LoadingActivity.startActivity(this, ex);
        Log.wtf(TAG, ex);
    }

    @Override
    public void onSubsequentExceptions() {
        NetInstanceHolder.close();
        LoadingActivity.startActivity(this, null);
    }

    @Override
    public void onException(@NonNull Throwable ex) {
        Log.e(TAG, "Uncaught exception.", ex);
    }
}
