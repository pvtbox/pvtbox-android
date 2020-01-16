package net.pvtbox.android.application;

import android.app.Activity;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.util.Log;

import com.bugfender.sdk.Bugfender;

import net.pvtbox.android.BuildConfig;
import net.pvtbox.android.db.DataBaseMigration;
import net.pvtbox.android.service.PreferenceService;
import net.pvtbox.android.service.PvtboxService;
import net.pvtbox.android.tools.JSON;

import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.Objects;

import io.realm.Realm;
import io.realm.RealmConfiguration;

/**
*  
*  Pvtbox. Fast and secure file transfer & sync directly across your devices. 
*  Copyright © 2020  Pb Private Cloud Solutions Ltd. 
*  
*  Licensed under the Apache License, Version 2.0 (the "License");
*  you may not use this file except in compliance with the License.
*  You may obtain a copy of the License at
*     http://www.apache.org/licenses/LICENSE-2.0
*  
*  Unless required by applicable law or agreed to in writing, software
*  distributed under the License is distributed on an "AS IS" BASIS,
*  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
*  See the License for the specific language governing permissions and
*  limitations under the License.
*  
**/
public class App extends Application implements Application.ActivityLifecycleCallbacks {

    private static final String TAG = App.class.getSimpleName();
    @Nullable
    private static App application = null;
    public boolean shouldShowFreeLicenseMessage = false;

    private PreferenceService preferenceService;
    private long activeActivities = 0;
    private BroadcastReceiver receiverOperationsProgress;
    @Nullable
    private Intent operationProgressIntent = null;
    @NonNull
    private String signalServerUrl = "signalserver.pvtbox.net";

    @Nullable
    public static App getApplication() {
        return application;
    }

    @Override
    public void onCreate() {
        preferenceService = new PreferenceService(
                getSharedPreferences(Const.SETTINGS_NAME, Context.MODE_PRIVATE));
        shouldShowFreeLicenseMessage = Const.FREE_LICENSE.equals(
                preferenceService.getLicenseType());
        super.onCreate();
        application = this;
        Bugfender.init(this, BuildConfig.BUGFENDER_KEY, true);
        Bugfender.disableReflection(true);
        regBroadcastReceiverOperations();

        if (preferenceService.isStatisticEnabled()) {
            Bugfender.enableLogcatLogging();
            Bugfender.enableCrashReporting();
        }
        initRealm();
        registerActivityLifecycleCallbacks(this);
    }

    private void initRealm() {
        File pathForDb = getFilesDir().getAbsoluteFile();
        Log.d(TAG, "path for Db:" + pathForDb);
        Realm.init(this);
        RealmConfiguration realmConfig = new RealmConfiguration
                .Builder()
                .schemaVersion(5)
                .migration(new DataBaseMigration())
                .directory(pathForDb)
                .compactOnLaunch()
                .build();
        Realm.setDefaultConfiguration(realmConfig);
    }

    public void onServiceStopped() {
        operationProgressIntent = null;
    }

    @Override
    public void onTerminate() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiverOperationsProgress);
        application = null;
        super.onTerminate();
    }

    @Override
    public void onActivityCreated(@NotNull Activity activity, Bundle bundle) {
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        activity.overridePendingTransition(0,0);
        activeActivities += 1;
        if (activeActivities == 1) {
            Log.d(TAG, "onForeground");
            if (!PvtboxService.isServiceRunning(getBaseContext())) {
                String userHash = preferenceService.getUserHash();
                preferenceService.setExited(false);
                if (userHash != null && !userHash.isEmpty() && preferenceService.isLoggedIn()) {
                    Log.d(TAG, "onActivityStarted: starting service");
                    PvtboxService.startPbService(getBaseContext(), null);
                } else {
                    Log.d(TAG, "onActivityStarted: skip service start, userHash is empty");
                }
            } else {
                Log.d(TAG, "onActivityStarted: service is already running");
            }
            new Handler().postDelayed(() -> {
                Intent i = new Intent(Const.RESTART_MONITOR_INTENT);
                LocalBroadcastManager.getInstance(this).sendBroadcast(i);
            }, 1000);
        }
    }

    private void regBroadcastReceiverOperations() {
        IntentFilter filter = new IntentFilter(Const.OPERATIONS_PROGRESS_INTENT);

        receiverOperationsProgress = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "receiver Operations progress");
                operationProgressIntent = intent;
            }

        };
        LocalBroadcastManager.getInstance(this).registerReceiver(
                receiverOperationsProgress, filter);
    }

    @Nullable
    public Intent getCurrentOperationProgress() {
        return operationProgressIntent;
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        activity.overridePendingTransition(0,0);
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
        activity.overridePendingTransition(0,0);
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
        activeActivities -= 1;
        activity.overridePendingTransition(0,0);
    }

    @Override
    public void onActivitySaveInstanceState(@NotNull Activity activity, @NotNull Bundle bundle) {
    }

    @Override
    public void onActivityDestroyed(@NotNull Activity activity) {
    }

    public void setServers(@NonNull JSONArray servers) {
        for (int i = 0; i < servers.length(); ++i) {
            JSONObject server = servers.optJSONObject(i);
            if ("SIGN".equals(JSON.optString(server, "server_type"))) {
                signalServerUrl = Objects.requireNonNull(JSON.optString(server, "server_url", ""));
                return;
            }
        }
    }

    @NonNull
    public String getSignalServerUrl() {
        return signalServerUrl;
    }
}
