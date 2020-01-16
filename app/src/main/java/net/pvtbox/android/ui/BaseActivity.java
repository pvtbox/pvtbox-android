package net.pvtbox.android.ui;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.coordinatorlayout.widget.CoordinatorLayout;

import com.google.android.material.snackbar.Snackbar;
import com.squareup.seismic.ShakeDetector;

import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.text.TextUtils;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import net.pvtbox.android.R;
import net.pvtbox.android.api.AuthHttpClient;
import net.pvtbox.android.api.CollaborationsHttpClient;
import net.pvtbox.android.application.App;
import net.pvtbox.android.application.Const;
import net.pvtbox.android.service.PreferenceService;
import net.pvtbox.android.db.DataBaseService;
import net.pvtbox.android.service.signalserver.ShareSignalServerService;
import net.pvtbox.android.tools.FileTool;
import net.pvtbox.android.ui.login.LoginActivity;
import net.pvtbox.android.ui.support.SupportDialog;

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
@SuppressLint("Registered")
public class BaseActivity extends AppCompatActivity {
    private final static String TAG = BaseActivity.class.getSimpleName();

    private CoordinatorLayout coordinator;

    protected PreferenceService preferenceService;
    protected DataBaseService dataBaseService;
    protected FileTool fileTool;

    protected AuthHttpClient authHttpClient;
    private CollaborationsHttpClient collaborationsHttpClient;

    @Nullable
    private BroadcastReceiver receiverDiskSpaceError;
    @Nullable
    private BroadcastReceiver receiverMessage;
    @Nullable
    private BroadcastReceiver receiverOperationsProgress;
    private BroadcastReceiver receiverLogout;
    @Nullable
    private BroadcastReceiver receiverLicense;
    @Nullable
    private Snackbar snackBarOperations;

    protected boolean doNotShowFreeLicenseNotification = false;

    private final BroadcastReceiver exitReceived = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            exitApp();
        }
    };
    @Nullable
    private AlertDialog freeLicenseDialog = null;
    private SensorManager sm;
    private ShakeDetector sd;
    private SupportDialog supportDialog;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        Context context = getBaseContext();
        preferenceService = new PreferenceService(
                getSharedPreferences(Const.SETTINGS_NAME, Context.MODE_PRIVATE));
        authHttpClient = new AuthHttpClient(context, preferenceService);
        collaborationsHttpClient = new CollaborationsHttpClient(context, preferenceService);
        setStatusBarColor();
        fileTool = new FileTool(context);
        dataBaseService = new DataBaseService(
                context, preferenceService, fileTool);

        IntentFilter filter = new IntentFilter(Const.ACTION_EXIT);
        registerReceiver(exitReceived, filter);

        super.onCreate(savedInstanceState);
        overridePendingTransition(0, 0);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        sd = new ShakeDetector(() -> {
            if (supportDialog == null) {
                supportDialog = new SupportDialog(
                        this, authHttpClient, () -> supportDialog = null);
                supportDialog.show();
            }
        });
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        regBroadcastReceiverOperations();
        if (!doNotShowFreeLicenseNotification) {
            regBroadcastReceiverLicense();
            onLicenseChanged();
        }
        overridePendingTransition(0, 0);
    }

    private void regBroadcastReceiverLicense() {
        IntentFilter filter = new IntentFilter(Const.LICENSE_CHANGED);

        receiverLicense = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "receiver license");
                onLicenseChanged();
            }
        };
        LocalBroadcastManager.getInstance(this).registerReceiver(receiverLicense, filter);
    }

    private void onLicenseChanged() {
        App app = App.getApplication();
        if (app != null && app.shouldShowFreeLicenseMessage &&
                freeLicenseDialog == null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(
                    new ContextThemeWrapper(getWindow().getContext(), R.style.AppTheme));
            builder
                    .setTitle(R.string.app_is_free)
                    .setMessage(String.format("%s\n\n%s\n",
                            getString(R.string.app_sync_disabled),
                            getString(R.string.app_upgrade_license)))
                    .setPositiveButton(R.string.ok, (dialog, id) -> {
                        App.getApplication().shouldShowFreeLicenseMessage = false;
                        freeLicenseDialog = null;
                    })
                    .setCancelable(false);
            builder.create();
            freeLicenseDialog = builder.show();
        }
    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        coordinator = findViewById(R.id.coordinator);
    }

    @Override
    protected void onDestroy() {
        sd.stop();
        super.onDestroy();
        unregisterReceiver(exitReceived);
        authHttpClient.onDestroy();
    }

    protected void exitApp() {
        preferenceService.setExited(true);
        finishAffinity();
        overridePendingTransition(0, 0);
    }

    private void setStatusBarColor() {
        Window window = getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(ContextCompat.getColor(this, R.color.dark_orange));
        }
    }

    private void regBroadcastReceiverDiskSpaceError() {
        IntentFilter filter = new IntentFilter(Const.DISK_SPACE_ERROR_INTENT);

        receiverDiskSpaceError = new BroadcastReceiver() {
            @SuppressLint("StringFormatMatches")
            @Override
            public void onReceive(Context context, Intent intent) {
                try {
                    onSpaceErrorReceived();
                } catch (Exception e) {
                    Log.e(TAG, "onSpaceErrorReceived error: ", e);
                    e.printStackTrace();
                }
            }

        };
        LocalBroadcastManager.getInstance(this).registerReceiver(receiverDiskSpaceError, filter);
    }

    private void onSpaceErrorReceived() {
        Toast.makeText(this, R.string.disk_space_error, Toast.LENGTH_LONG).show();
    }

    private void regBroadcastReceiverOperations() {
        IntentFilter filter = new IntentFilter(Const.OPERATIONS_PROGRESS_INTENT);

        receiverOperationsProgress = new BroadcastReceiver() {
            @Override
            public void onReceive(@NonNull Context context, @NonNull Intent intent) {
                Log.d(TAG, String.format("receiver Operations progress: %s", intent.getExtras()));
                try {
                    showOperationsSnackBar(context, intent, false);
                } catch (Exception e) {
                    Log.e(TAG, "showOperationsSnackBar error: ", e);
                }
            }

        };
        LocalBroadcastManager.getInstance(this).registerReceiver(
                receiverOperationsProgress, filter);
        App app = App.getApplication();
        if (app != null) {
            Intent i = app.getCurrentOperationProgress();
            if (i != null) {
                showOperationsSnackBar(this, i, true);
            }
        }
    }

    private void showOperationsSnackBar(@NonNull Context context, @NonNull Intent intent, boolean isInit) {
        String message = intent.getStringExtra(Const.OPERATIONS_PROGRESS_MESSAGE);
        boolean showAndDismiss = intent.getBooleanExtra(
                Const.OPERATIONS_PROGRESS_SHOW_AND_DISMISS, true);
        if (message == null || isInit && showAndDismiss) return;
        String path = intent.getStringExtra(Const.OPERATIONS_PROGRESS_OPEN_ACTION);
        boolean showShareCancel = intent.getBooleanExtra(
                Const.OPERATIONS_PROGRESS_SHOW_SHARE_CANCEL, false);

        showSnack(context, message, showAndDismiss, path, showShareCancel);
    }

    public void showSnack(@NonNull Context context, @NonNull String message,
                          boolean showAndDismiss, @Nullable String path, boolean showShareCancel) {
        if (snackBarOperations == null) {
            try {
                snackBarOperations = Snackbar.make(
                        coordinator, message,
                        showAndDismiss ? Snackbar.LENGTH_LONG : Snackbar.LENGTH_INDEFINITE);
            } catch (IllegalArgumentException e) {
                return;
            }
            View view = snackBarOperations.getView();
            view.setBackgroundColor(context.getResources().getColor(R.color.white));
            TextView tv = view.findViewById(com.google.android.material.R.id.snackbar_text);
            if (tv != null) {
                tv.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                tv.setGravity(Gravity.CENTER_HORIZONTAL);
                tv.setEllipsize(TextUtils.TruncateAt.MIDDLE);
                tv.setMaxLines(4);
            }
            snackBarOperations.addCallback(new Snackbar.Callback() {
                @Override
                public void onDismissed(Snackbar snackbar, int event) {
                    snackBarOperations = null;
                }

                @Override
                public void onShown(Snackbar snackbar) {
                }
            });
        } else {
            snackBarOperations.setText(message);
            snackBarOperations.setDuration(
                    showAndDismiss ? Snackbar.LENGTH_LONG : Snackbar.LENGTH_INDEFINITE);
        }
        if (path != null && !path.isEmpty()) {
            snackBarOperations.setAction(R.string.open, view ->
                    fileTool.openFile(FileTool.getNameFromPath(path), path));
        }
        else if (showShareCancel) {
            snackBarOperations.setAction(R.string.cancel, view
                    -> ShareSignalServerService.CancelDownloads());
        } else {
            snackBarOperations.setAction(null, null);
        }
        snackBarOperations.show();
    }

    private void regBroadcastReceiverLogout() {
        IntentFilter filter = new IntentFilter(Const.LOGOUT_INTENT);
        receiverLogout = new BroadcastReceiver() {
            @Override
            public void onReceive(@NonNull Context context, Intent intent) {
                onLogoutReceived(context);
            }
        };
        LocalBroadcastManager.getInstance(this).registerReceiver(receiverLogout, filter);
    }

    private void onLogoutReceived(@NonNull Context context) {
        Toast.makeText(getApplicationContext(), R.string.logged_out_by_action, Toast.LENGTH_LONG).show();
        Intent intentLogin = new Intent(context.getApplicationContext(), LoginActivity.class);
        intentLogin.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intentLogin.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(intentLogin);
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        regBroadcastReceiverDiskSpaceError();
        regBroadcastReceiverLogout();
        overridePendingTransition(0, 0);
        sd.start(sm);
    }


    @Override
    protected void onPause() {
        Log.d(TAG, "onPause:");
        sd.stop();
        super.onPause();
        unregisterOperationProgressReceiver();
        if (receiverLicense != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(receiverLicense);
            receiverLicense = null;
        }
        if (receiverDiskSpaceError != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(receiverDiskSpaceError);
            receiverDiskSpaceError = null;
        }
        if (receiverMessage != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(receiverMessage);
            receiverMessage = null;
        }
        if (receiverLogout != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(receiverLogout);
        }
        if (snackBarOperations != null) {
            snackBarOperations.dismiss();
        }
        overridePendingTransition(0, 0);
    }

    protected void unregisterOperationProgressReceiver() {
        if (receiverOperationsProgress != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(receiverOperationsProgress);
            receiverOperationsProgress = null;
        }
    }

    public DataBaseService getDataBaseService() {
        return dataBaseService;
    }

    public AuthHttpClient getAuthHttpClient() {
        return authHttpClient;
    }

    public PreferenceService getPreferenceService() {
        return preferenceService;
    }

    public CollaborationsHttpClient getCollaborationsHttpClient() {
        return collaborationsHttpClient;
    }

    public FileTool getFileTool() {
        return fileTool;
    }
}
