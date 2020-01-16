package net.pvtbox.android.service;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.util.Log;

import com.bugfender.sdk.Bugfender;

import net.pvtbox.android.BuildConfig;
import net.pvtbox.android.R;
import net.pvtbox.android.api.AuthHttpClient;
import net.pvtbox.android.api.EventsHttpClient;
import net.pvtbox.android.api.ShareHttpClient;
import net.pvtbox.android.application.App;
import net.pvtbox.android.application.Const;
import net.pvtbox.android.db.DataBaseService;
import net.pvtbox.android.db.model.DeviceRealm;
import net.pvtbox.android.service.monitor.Monitor;
import net.pvtbox.android.tools.JSON;
import net.pvtbox.android.tools.SpeedTool;
import net.pvtbox.android.service.signalserver.HttpLoader;
import net.pvtbox.android.service.signalserver.ShareSignalServerService;
import net.pvtbox.android.service.signalserver.SignalServerService;
import net.pvtbox.android.service.signalserver.WipeTool;
import net.pvtbox.android.service.sync.SyncService;

import net.pvtbox.android.tools.FileTool;
import net.pvtbox.android.tools.PatchTool;
import net.pvtbox.android.service.transport.Connectivity.ConnectivityService;
import net.pvtbox.android.service.transport.Downloads.DownloadManager;
import net.pvtbox.android.ui.start.EmulatorDetector;
import net.pvtbox.android.ui.start.StartActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;

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
public class PvtboxService extends Service {

    private static final String TAG = PvtboxService.class.getSimpleName();

    private boolean inited = false;
    @Nullable
    private HandlerThread handlerThread;
    private Handler handler;

    private SignalServerService signalServerService;
    private ConnectivityService connectivityService;
    private DownloadManager downloadManager;

    private ShareSignalServerService shareSignalServerService;
    @Nullable
    private ConnectivityService shareConnectivityService;
    private DownloadManager shareDownloadManager;

    private DeviceStatusBroadcaster deviceStatusBroadcaster;
    @Nullable
    private BroadcastReceiver receiverStop;

    private PreferenceService preferenceService;
    private EventsHttpClient eventsHttpClient;
    private ShareHttpClient shareHttpClient;
    private OperationService operationService;
    private Monitor monitor;
    private WipeTool wipeTool;
    private DataBaseService dataBaseService;
    private SpeedTool speedTool;
    private AuthHttpClient authHttpClient;

    @Nullable
    private JSONObject loginData = null;

    private final BroadcastReceiver exitReceived = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (preferenceService != null) preferenceService.setExited(true);
            PvtboxService.stopServiceAsync(getBaseContext());
        }
    };
    @Nullable
    private Intent intent;
    private NotificationCompat.Builder notificationBuilder;
    private boolean performingLogin = false;
    @Nullable
    private BroadcastReceiver receiverDownloadsResume;
    @Nullable
    private BroadcastReceiver receiverDownloadsPause;
    private boolean paused = false;
    private int status = R.string.app_connecting;

    @Override
    public void onCreate() {
        Log.i(TAG, "onCreate");
        super.onCreate();
        Bugfender.init(this, BuildConfig.BUGFENDER_KEY, true);
        Bugfender.disableReflection(true);

        registerReceiver(exitReceived, new IntentFilter(Const.ACTION_EXIT));

        Intent notificationIntent = new Intent(this, StartActivity.class);
        notificationIntent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_CLEAR_TASK |
                        Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent =
                PendingIntent.getActivity(this, 0, notificationIntent, 0);

        Intent exitIntent = new Intent(Const.ACTION_EXIT);
        PendingIntent exitPendingIntent = PendingIntent.getBroadcast(this, 1, exitIntent, 0);

        String channelId = BuildConfig.APPLICATION_ID;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationManager manager = (NotificationManager) getSystemService(
                    Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                NotificationChannel channel = new NotificationChannel(
                        channelId, getString(R.string.app_name),
                        NotificationManager.IMPORTANCE_DEFAULT);
                manager.createNotificationChannel(channel);
            }
        }

        notificationBuilder = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.notification_icon)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .addAction(R.drawable.exit, getString(R.string.exit), exitPendingIntent);

        status = R.string.app_connecting;
        Notification notification = notificationBuilder
                .setContentTitle(getText(R.string.app_connecting))
                .setContentText(getText(R.string.tap_to_open))
                .setSubText(null)
                .build();
        startForeground(1, notification);
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy");
        unregisterReceiver(exitReceived);
        super.onDestroy();
        if (handlerThread == null) return;
        destroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!inited) {
            inited = true;
            handlerThread = new HandlerThread(
                    "PvtboxService", HandlerThread.NORM_PRIORITY);
            handlerThread.start();
            handler = new Handler(handlerThread.getLooper());
            handler.post(this::regBroadcastReceiverStop);
            handler.post(this::regBroadcastReceiverDownloads);
            handler.post(this::init);
        }
        this.intent = intent;
        Log.i(TAG, String.format(
                "onStartCommand, intent: %s, flags: %s, startId: %s, handler: %s, ",
                intent, flags, startId, handler));

        handler.post(this::start);
        return START_STICKY;
    }

    private void init() {
        Log.i(TAG, "init");
        Context context = getBaseContext();

        preferenceService = new PreferenceService(
                getSharedPreferences(Const.SETTINGS_NAME, Context.MODE_PRIVATE));
        if (preferenceService.isStatisticEnabled()) {
            Bugfender.enableLogcatLogging();
            Bugfender.enableCrashReporting();
        }
        preferenceService.setExited(false);

        authHttpClient = new AuthHttpClient(context, preferenceService);

        FileTool fileTool = new FileTool(context);

        fileTool.createDirectory(Const.DEFAULT_PATH);
        fileTool.createDirectory(Const.INTERNAL_PATH);
        fileTool.createDirectory(Const.COPIES_PATH);
        fileTool.createDirectory(Const.PATCHES_PATH);
        fileTool.createEmptyFile(FileTool.buildPathForCopyNamedHash(Const.EMPTY_FILE_HASH));

        dataBaseService = new DataBaseService(
                getBaseContext(), preferenceService, fileTool);
        dataBaseService.setupOwnDevice();
        dataBaseService.setOnSyncedCallback(() -> {
            if (!Const.FREE_LICENSE.equals(preferenceService.getLicenseType()) &&
                    status != R.string.app_synced) {
                Log.d(TAG, "show notification: synced");
                status = R.string.app_synced;
                Notification notification = notificationBuilder
                        .setContentTitle(getText(R.string.app_synced))
                        .setContentText(getText(R.string.tap_to_open))
                        .setSubText(null)
                        .build();
                NotificationManager mNotificationManager = (NotificationManager) getSystemService(
                        Context.NOTIFICATION_SERVICE);
                Objects.requireNonNull(mNotificationManager).notify(1, notification);
            }
            handler.postDelayed(this::cleanup, 60 * 1000);
        });
        dataBaseService.setOnSyncingCallback(() -> {
            if (!Const.FREE_LICENSE.equals(preferenceService.getLicenseType()) &&
                    status != R.string.app_syncing) {
                Log.d(TAG, "show notification: syncing");
                status = R.string.app_syncing;
                Notification notification = notificationBuilder
                        .setContentTitle(getText(R.string.app_syncing))
                        .setContentText(getText(R.string.tap_to_open))
                        .setSubText(null)
                        .build();
                NotificationManager mNotificationManager = (NotificationManager) getSystemService(
                        Context.NOTIFICATION_SERVICE);
                Objects.requireNonNull(mNotificationManager).notify(1, notification);
            }
        });
        dataBaseService.setOnPausedCallback(() -> {
            if (!Const.FREE_LICENSE.equals(preferenceService.getLicenseType()) &&
                    status != R.string.app_paused) {
                Log.d(TAG, "show notification: paused");
                status = R.string.app_paused;
                Notification notification = notificationBuilder
                        .setContentTitle(getText(R.string.app_paused))
                        .setContentText(getText(R.string.tap_to_open))
                        .setSubText(null)
                        .build();
                NotificationManager mNotificationManager = (NotificationManager) getSystemService(
                        Context.NOTIFICATION_SERVICE);
                Objects.requireNonNull(mNotificationManager).notify(1, notification);
            }
        });

        speedTool = new SpeedTool(dataBaseService);

        wipeTool = new WipeTool(
                context, dataBaseService, preferenceService, authHttpClient, fileTool);
        eventsHttpClient = new EventsHttpClient(context, preferenceService);
        shareHttpClient = new ShareHttpClient(context, preferenceService);
        operationService = new OperationService(
                context, dataBaseService, fileTool, eventsHttpClient,
                shareHttpClient);
        HttpLoader httpLoader = new HttpLoader(
                fileTool, dataBaseService, shareHttpClient, operationService,
                speedTool, getBaseContext());
        signalServerService = new SignalServerService(
                context, preferenceService, dataBaseService, httpLoader,
                wipeTool, this);
        operationService.setSignalServerService(signalServerService);
        shareSignalServerService = new ShareSignalServerService(
                context, preferenceService, operationService, dataBaseService, fileTool);

        PatchTool patchTool = new PatchTool(fileTool);

        monitor = new Monitor(
                context, operationService, fileTool, dataBaseService,
                preferenceService, eventsHttpClient, patchTool);

        connectivityService = new ConnectivityService(
                context, "main", signalServerService, speedTool, dataBaseService);

        signalServerService.setConnectivityService(connectivityService);
        downloadManager = new DownloadManager(
                context, connectivityService, fileTool, dataBaseService, patchTool, false, paused);

        shareConnectivityService = new ConnectivityService(
                context, "share", shareSignalServerService, speedTool, null);
        shareSignalServerService.setConnectivityService(shareConnectivityService);
        shareDownloadManager = new DownloadManager(
                context, shareConnectivityService, fileTool, dataBaseService, patchTool, true, paused);
        shareSignalServerService.setDownloadManager(shareDownloadManager);

        SyncService syncService = new SyncService(
                dataBaseService, signalServerService, () -> {
            downloadManager.onInitialSyncDone();
            monitor.onInitialSyncDone();
            dataBaseService.updateOwnDeviceInitialSyncing(false);
        });
        signalServerService
                .setSyncService(syncService);

        deviceStatusBroadcaster = new DeviceStatusBroadcaster(
                signalServerService, preferenceService, dataBaseService);
        signalServerService.setOnConnectedCallback(() -> {
            if (!Const.FREE_LICENSE.equals(preferenceService.getLicenseType()) &&
                    status != R.string.app_syncing) {
                Log.d(TAG, "show notification: syncing (network connected)");
                status = R.string.app_syncing;
                Notification notification = notificationBuilder
                        .setContentTitle(getText(R.string.app_syncing))
                        .setContentText(getText(R.string.tap_to_open))
                        .setSubText(null)
                        .build();
                NotificationManager mNotificationManager = (NotificationManager) getSystemService(
                        Context.NOTIFICATION_SERVICE);
                Objects.requireNonNull(mNotificationManager).notify(1, notification);
            }
            dataBaseService.updateOwnDeviceOnline(true);
            deviceStatusBroadcaster.checkAndBroadcastStatus(true);
        });
        signalServerService.setOnDisconnectedCallback(() -> {
            if (!Const.FREE_LICENSE.equals(preferenceService.getLicenseType()) &&
                    status != R.string.app_connecting) {
                Log.d(TAG, "show notification: connecting");
                status = R.string.app_connecting;
                Notification notification = notificationBuilder
                        .setContentTitle(getText(R.string.app_connecting))
                        .setContentText(getText(R.string.tap_to_open))
                        .setSubText(null)
                        .build();
                NotificationManager mNotificationManager = (NotificationManager) getSystemService(
                        Context.NOTIFICATION_SERVICE);
                Objects.requireNonNull(mNotificationManager).notify(1, notification);
            }
            dataBaseService.updateOwnDeviceOnline(false);
        });
    }

    private void cleanup() {
        if (handler == null || handlerThread == null || !handlerThread.isAlive()) return;
        if (!dataBaseService.cleanup()) {
            handler.postDelayed(this::cleanup, 60 * 1000);
        }
    }

    private void destroy() {
        Log.i(TAG, "destroy");
        inited = false;
        performingLogin = false;
        try {
            if (App.getApplication() != null) {
                App.getApplication().onServiceStopped();
            }
            Log.d(TAG, "destroy: 1");
            if (handlerThread == null) return;
            handler.removeCallbacksAndMessages(null);
            handler.post(() -> {
                if (speedTool != null) speedTool.onDestroy();
                if (eventsHttpClient != null) eventsHttpClient.onDestroy();
                if (shareHttpClient != null) shareHttpClient.onDestroy();
                if (deviceStatusBroadcaster != null) deviceStatusBroadcaster.onDestroy();
                if (shareSignalServerService != null) shareSignalServerService.onDestroy();
                if (signalServerService != null) signalServerService.onDestroy();
                if (monitor != null) monitor.onDestroy();
                if (operationService != null) operationService.onDestroy();
                if (shareDownloadManager != null) shareDownloadManager.onDestroy();
                if (shareConnectivityService != null) shareConnectivityService.onDestroy();
                if (downloadManager != null) downloadManager.onDestroy();
                if (connectivityService != null) connectivityService.onDestroy();
            });
            handlerThread.quitSafely();
            handlerThread = null;
            if (receiverStop != null) {
                LocalBroadcastManager.getInstance(this).unregisterReceiver(receiverStop);
                receiverStop = null;
            }
            if (receiverDownloadsPause != null) {
                LocalBroadcastManager.getInstance(this).unregisterReceiver(receiverDownloadsPause);
                receiverDownloadsPause = null;
            }
            if (receiverDownloadsResume != null) {
                LocalBroadcastManager.getInstance(this).unregisterReceiver(receiverDownloadsResume);
                receiverDownloadsResume = null;
            }
            stopSelf();
        } finally {
            Log.d(TAG, "destroyed");
        }
    }

    private void start() {
        Log.i(TAG, "start");
        if (preferenceService.isLoggedIn() && loginData != null) {
            Log.i(TAG, "start: start signal server service");
            startSignalServerService();
        } else {
            Intent i = new Intent(Const.NETWORK_STATUS);
            i.putExtra(Const.NETWORK_STATUS_SIGNAL_CONNECTING, true);
            i.putExtra(Const.NETWORK_STATUS_INFO_HEADER, R.string.connecting_to_server);
            i.putExtra(Const.NETWORK_STATUS_INFO, R.string.wait_while_connecting);
            LocalBroadcastManager.getInstance(getBaseContext()).sendBroadcast(i);

            login();
        }
    }

    private void login() {
        if (intent == null) {
            executeLogin();
            return;
        }

        String loginResponse = intent.getStringExtra("loginResponse");
        if (loginResponse == null) {
            executeLogin();
            return;
        }
        try {
            loginData = new JSONObject(loginResponse);
        } catch (JSONException e) {
            e.printStackTrace();
            executeLogin();
            return;
        }

        onLoggedIn(loginData);
    }

    private void onLoggedIn(@NonNull JSONObject response) {
        performingLogin = false;
        loginData = response;
        JSONArray servers = loginData.optJSONArray("servers");
        if (servers == null) {
            executeLogin();
            return;
        }
        preferenceService.setLoggedIn(true);
        preferenceService.setLastEventUuid(JSON.optString(response, "last_event_uuid"));
        connectivityService.init(servers);
        Objects.requireNonNull(shareConnectivityService).init(servers);
        signalServerService.setServers(servers);
        shareSignalServerService.setServers(servers);
        App app = App.getApplication();
        if (app != null) {
            app.setServers(servers);
        }
        String licenseTypeOld = preferenceService.getLicenseType();
        String licenseType = JSON.optString(response, "license_type");
        boolean needClear = Const.FREE_LICENSE.equals(licenseTypeOld) &&
                !Objects.equals(licenseTypeOld, licenseType);
        preferenceService.setLicenseType(licenseType);
        onLicenseTypeChanged();
        if (needClear) {
            dataBaseService.setAllEventsUnchecked();
        }
        startSignalServerService();
        JSONArray actions = response.optJSONArray("remote_actions");
        if (actions != null) {
            for (int i = 0; i < actions.length(); ++i) {
                wipeTool.executeAction(actions.optJSONObject(i));
            }
        }
    }

    public void onLicenseTypeChanged() {
        Notification notification;
        if (notificationBuilder == null) { return; }
        if (Const.FREE_LICENSE.equals(preferenceService.getLicenseType())) {
            notification = notificationBuilder
                    .setContentTitle(getText(R.string.app_is_free))
                    .setContentText(getText(R.string.app_sync_disabled))
                    .setSubText(getText(R.string.app_upgrade_license))
                    .build();
            if (App.getApplication() != null) {
                App.getApplication().shouldShowFreeLicenseMessage = true;

            }
        } else if (App.getApplication() != null) {
            App.getApplication().shouldShowFreeLicenseMessage = false;
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(Const.LICENSE_CHANGED));
    }

    private void executeLogin() {
        if (preferenceService.getUserHash() == null) return;
        if (performingLogin) return;
        performingLogin = true;
        authHttpClient.login(
                preferenceService.getUserHash(),
                response -> handler.post(() -> onLoggedIn(response)),
                error -> handler.post(() -> onLoginError(error)));
    }

    private void onLoginError(@Nullable JSONObject error) {
        performingLogin = false;
        handler.postDelayed(this::executeLogin, 1000);
        if (error != null) {
            String errcode = JSON.optString(error, "errcode");
            JSONArray actions = error.optJSONArray("remote_actions");
            if (actions != null && actions.length() > 0) {
                for (int i = 0; i < actions.length(); ++i) {
                    wipeTool.executeAction(actions.optJSONObject(i));
                }
            } else if ("USER_NOT_FOUND".equals(errcode) || "LICENSE_LIMIT".equals(errcode)) {
                wipeTool.logoutAndClose(null);
            }
        }
    }

    private void startSignalServerService() {
        Log.i(TAG, "startSignalServerService");
        if (!signalServerService.isStarted()) {
            Log.i(TAG, "startSignalServerService: starting");
            signalServerService.start();
        } else {
            Log.i(TAG, "startSignalServerService: already started");
        }
        if (intent == null) {
            Log.i(TAG, "startSignalServerService: intent==null");
            return;
        }
        String shareHash = intent.getStringExtra(Const.KEY_SHARE_HASH);
        if (shareHash != null) {
            shareHash = shareHash.trim()
                    .replace("\n", "").replace("\r", "");
        }
        String pathDownload = intent.getStringExtra(Const.KEY_SHARE_PATH_DOWNLOAD);
        intent.removeExtra(Const.KEY_SHARE_HASH);
        intent.removeExtra(Const.KEY_SHARE_PATH_DOWNLOAD);
        intent = null;
        if (shareHash != null && !shareHash.isEmpty()) {
            if (shareSignalServerService.isStarted()) {
                Log.i(TAG, "startSignalServerService: " +
                        "share signal server service already started");
                return;
            }
            shareSignalServerService.downloadDirectLink(shareHash, pathDownload);
        }
    }

    private void regBroadcastReceiverStop() {
        IntentFilter filter = new IntentFilter(Const.STOP_SERVICE_INTENT);
        receiverStop = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, @NonNull Intent intent) {
                handler.post(() -> {
                    if (intent.getBooleanExtra(Const.STOP_SERVICE_WIPE, false)) {
                        wipeTool.wipe();
                    }
                    destroy();
                    if (intent.getBooleanExtra(Const.STOP_SERVICE_WIPE, false)) {
                        preferenceService.setUserHash(null);
                        preferenceService.setLoggedIn(false);
                    }
                });
            }
        };
        LocalBroadcastManager.getInstance(this).registerReceiver(receiverStop, filter);
        filter = new IntentFilter(Const.LOGOUT_INTENT);
        LocalBroadcastManager.getInstance(this).registerReceiver(receiverStop, filter);
    }

    private void regBroadcastReceiverDownloads() {
        receiverDownloadsPause = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                handler.post(() -> {
                    paused = true;
                    dataBaseService.updateOwnDevicePaused(true);
                    if (downloadManager != null) {
                        downloadManager.pause();
                    }
                });
            }
        };
        LocalBroadcastManager.getInstance(this).registerReceiver(
                receiverDownloadsPause, new IntentFilter(Const.DOWNLOADS_PAUSE_OPERATION));

        receiverDownloadsResume = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                handler.post(() -> {
                    paused = false;
                    dataBaseService.updateOwnDevicePaused(false);
                    if (downloadManager != null) {
                        downloadManager.resume();
                    }
                });
            }
        };
        LocalBroadcastManager.getInstance(this).registerReceiver(
                receiverDownloadsResume, new IntentFilter(Const.DOWNLOADS_RESUME_OPERATION));
    }

    public static void stopService(@NonNull Context context, boolean wipe) {
        Log.d(TAG, "stopService");
        Intent intent = new Intent(Const.STOP_SERVICE_INTENT);
        intent.putExtra(Const.STOP_SERVICE_WIPE, wipe);
        LocalBroadcastManager.getInstance(context).sendBroadcastSync(intent);
    }

    private static void stopServiceAsync(@NonNull Context context) {
        Log.d(TAG, "stopServiceAsync");
        Intent intent = new Intent(Const.STOP_SERVICE_INTENT);
        intent.putExtra(Const.STOP_SERVICE_WIPE, false);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    public static boolean isServiceRunning(@NonNull Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null) {
            Log.i(TAG, "isServiceRunning: false");
            return false;
        }
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (PvtboxService.class.getName().equals(service.service.getClassName())) {
                Log.i(TAG, String.format(
                        "isServiceRunning: true, SignalServerService.IsConnected: %s",
                        SignalServerService.IsConnected()));
                return SignalServerService.IsConnected();
            }
        }
        Log.i(TAG, "isServiceRunning: false");
        return false;
    }

    public static void startPbService(@NonNull Context context, String hash, String path) {
        Log.i(TAG, "startPbService");
        try {
            Intent intent = new Intent(context, PvtboxService.class);
            intent.putExtra(Const.KEY_SHARE_HASH, hash);
            intent.putExtra(Const.KEY_SHARE_PATH_DOWNLOAD, path);
            startService(context, intent);
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }
    }

    public static void startPbService(@NonNull Context context, String loginResponse) {
        Log.i(TAG, "startPbService");
        try {
            Intent intent = new Intent(context, PvtboxService.class);
            intent.putExtra("loginResponse", loginResponse);
            startService(context, intent);
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }
    }

    private static void startService(@NonNull Context context, Intent intent) {
        if (EmulatorDetector.isEmulator(context)) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }
}