package net.pvtbox.android.ui.start;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.StrictMode;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import android.util.Log;
import android.view.ContextThemeWrapper;

import com.amirarcane.lockscreen.activity.EnterPinActivity;
import com.nabinbhandari.android.permissions.PermissionHandler;
import com.nabinbhandari.android.permissions.Permissions;

import net.pvtbox.android.R;
import net.pvtbox.android.application.Const;
import net.pvtbox.android.tools.AutoStartUtils;
import net.pvtbox.android.ui.BaseActivity;
import net.pvtbox.android.ui.login.LoginActivity;
import net.pvtbox.android.ui.main_screen.MainActivity;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

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
public class StartActivity extends BaseActivity {

    private static final String TAG = StartActivity.class.getSimpleName();

    private static final String[] requiredPermissions = {
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
    };
    private boolean firstTry = true;

    private static final int PASSCODE = 2;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        setTheme(R.style.AppTheme_PBNoActionBar);
        doNotShowFreeLicenseNotification = true;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);

        requestPermission(this);

        StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
        StrictMode.setVmPolicy(builder.build());
    }

    private void openLogin() {
        new Handler().postDelayed(() -> {
            Intent intent = new Intent(this, LoginActivity.class);
            intent.putExtra(Const.INIT_INTENT, getIntent());
            startActivity(intent);
            finish();
        }, 300);
    }

    private void openIntro() {
        new Handler().postDelayed(() -> {
            Intent intent = new Intent(this, IntroActivity.class);
            intent.putExtra(Const.INIT_INTENT, getIntent());
            startActivity(intent);
            finish();
        }, 300);
    }

    private void openMain() {
        new Handler().postDelayed(() -> {
            if (EnterPinActivity.isPinSet(this)) {
                Intent intent = EnterPinActivity.getIntent(this, false);
                startActivityForResult(intent, PASSCODE);
            } else {
                startMainActivityDelayed();
            }
        }, 300);
    }

    private void openDialogDeletePvtboxFolder() {
        AlertDialog.Builder builder = new AlertDialog.Builder(
                new ContextThemeWrapper(this, R.style.AppTheme));
        builder.setMessage(R.string.do_you_want_delete_folder_private_box)
                .setPositiveButton(
                        R.string.add_sync, (dialog, id) -> launch())
                .setNegativeButton(
                        R.string.delete_folder, (dialog, id) -> {
                            fileTool.deleteDirectory(Const.DEFAULT_PATH);
                            launch();
                        });
        builder.setCancelable(false);
        builder.create();
        builder.show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == AutoStartUtils.AUTO_START_REQUEST) {
            start();
        } else if (requestCode == PASSCODE) {
            if (resultCode == EnterPinActivity.RESULT_BACK_PRESSED) {
                finishAffinity();
            } else if (resultCode == EnterPinActivity.RESULT_CANCELED) {
                launch();
            } else {
                startMainActivityDelayed();
            }
        }
    }

    private void startMainActivityDelayed() {
        new Handler().postDelayed(() -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra(Const.INIT_INTENT, getIntent());
            startActivity(intent);
            finish();
        }, 700);
    }

    private void requestPermission(@NonNull Activity activity) {
        Permissions.check(activity, requiredPermissions,
                R.string.write_and_read_ext_storage, new Permissions.Options()
                        .setSettingsDialogTitle("Warning!").setRationaleDialogTitle("Info"),
                new PermissionHandler() {
                    @Override
                    public void onGranted() {
                        Log.i(TAG, "onGranted");
                        AutoStartUtils.StartPowerSaverIntent(
                                activity, () -> start());
                    }

                    @Override
                    public void onDenied(Context context, @NotNull ArrayList<String> deniedPermissions) {
                        if (firstTry) {
                            firstTry = false;
                            new Handler().post(() -> requestPermission(activity));
                        } else {
                            activity.finish();
                        }
                    }

                    @Override
                    public boolean onBlocked(Context context, @NonNull ArrayList<String> blockedList) {
                        firstTry = false;
                        activity.finish();
                        return super.onBlocked(context, blockedList);
                    }

                    @Override
                    public void onJustBlocked(Context context, @NonNull ArrayList<String> justBlockedList, @NonNull ArrayList<String> deniedPermissions) {
                        firstTry = false;
                        activity.finish();
                        super.onJustBlocked(context, justBlockedList, deniedPermissions);
                    }
                });
    }

    private void start() {
        if (EmulatorDetector.isEmulator(this)) {
            AlertDialog.Builder builder = new AlertDialog.Builder(
                    new ContextThemeWrapper(this, R.style.AppTheme));
            builder.setMessage(R.string.emulator_prohibited)
                    .setPositiveButton(
                            R.string.ok, (dialog, id) -> finishAffinity());
            builder.setCancelable(false);
            builder.create();
            builder.show();
            return;
        }
        if (preferenceService.isFirstStart()) {
            dataBaseService.clearDb();
            if (!fileTool.isFolderEmpty(Const.INTERNAL_PATH)) {
                fileTool.deleteDirectory(Const.INTERNAL_PATH);
            }
            if (!fileTool.isFolderEmpty(Const.DEFAULT_PATH)) {
                openDialogDeletePvtboxFolder();
                return;
            }
        }
        launch();
    }

    private void launch() {
        if (preferenceService.isLoggedIn()) {
            Log.d(TAG, "Service is already started");
            openMain();
        } else {
            if (preferenceService.isFirstStart()) {
                preferenceService.writeFirstStart();
                openIntro();
            } else {
                openLogin();
            }
        }
    }
}
