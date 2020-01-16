package net.pvtbox.android.ui.settings;

import android.app.Activity;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.util.Log;

import com.amirarcane.lockscreen.activity.EnterPinActivity;
import com.bugfender.sdk.Bugfender;

import net.pvtbox.android.api.AuthHttpClient;
import net.pvtbox.android.application.Const;
import net.pvtbox.android.service.PreferenceService;
import net.pvtbox.android.db.DataBaseService;
import net.pvtbox.android.service.PvtboxService;

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
public class SettingsPresenterImpl implements SettingsPresenter {
    @NonNull
    private static final String TAG = SettingsPresenterImpl.class.getSimpleName();
    private SettingsView view;
    private final PreferenceService preferenceService;
    private final Activity context;
    private boolean isEnableAutoAlert;
    private final AuthHttpClient httpClient;

    private final DataBaseService dataBaseService;

    public SettingsPresenterImpl(PreferenceService preferenceService,
                                 DataBaseService dataBaseService,
                                 AuthHttpClient httpClient,
                                 Activity context) {
        this.preferenceService = preferenceService;
        this.httpClient = httpClient;
        this.context = context;
        this.dataBaseService = dataBaseService;
    }


    @Override
    public void onClickLogout() {
        view.showAlertLogout();
    }

    @Override
    public void setView(SettingsView settingsView) {
        this.view = settingsView;
    }

    @Override
    public void onStart() {
        String mail = preferenceService.getMail();
        String licenceType = preferenceService.getLicenseType();

        view.showLicenceType(licenceType);
        view.showMail(mail);

        boolean autoCameraUpdate = preferenceService.getAutoCameraUpdate();
        view.showAutoCameraUpdate(autoCameraUpdate);

        boolean canUseCellular = preferenceService.canUseCellular();
        view.showCellularUpdate(canUseCellular);
        if (canUseCellular) {
            view.showEnableRoaming();
            view.setRoamingChecked(preferenceService.canUseRoaming());
        } else {
            view.hideEnableRoaming();
        }

        boolean autoStart = preferenceService.isAutoStart();
        view.setAutoStartChecked(autoStart);

        view.setStatisticChecked(preferenceService.isStatisticEnabled());

        view.setDownloadMediaChecked(preferenceService.isMediaDownloadEnabled());

        view.setPasscodeChecked(EnterPinActivity.isPinSet(context));
    }

    @Override
    public void changeCheckAutoCamera(boolean isEnable) {
        if (preferenceService.getAutoCameraUpdate() == isEnable) {
            return;
        }
        isEnableAutoAlert = isEnable;
        if (isEnableAutoAlert) {
            if (isNodeOnline()) {
                executeSettingAutoUpdateCamera();
            } else {
                view.showAlertCameraAutoUpdate();
            }
        } else {
            executeSettingAutoUpdateCamera();
        }
    }

    @Override
    public void confirmCameraAutoUpdate() {
        executeSettingAutoUpdateCamera();
    }

    @Override
    public void confirmWipeAndLogOut() {
        sendServerLogout();
        wipe();
    }


    @Override
    public void confirmLogout() {
        sendServerLogout();
        logout();
    }

    private void sendServerLogout() {
        String userHash = preferenceService.getUserHash();
        if (userHash != null) {
            httpClient.logout(userHash);
        }
    }


    private void logout() {
        preferenceService.setLoggedIn(false);
        PvtboxService.stopService(context, false);
        view.openLogin();
    }

    private void wipe() {
        preferenceService.setUserHash(null);
        preferenceService.setLoggedIn(false);
        PvtboxService.stopService(context, true);
        view.openLogin();
    }


    private void executeSettingAutoUpdateCamera() {
        preferenceService.setAutoCameraUpdate(isEnableAutoAlert);
        Intent intent = new Intent(Const.RESTART_MONITOR_INTENT);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    @Override
    public void onClickOnlyWifi() {
        preferenceService.setCanUseCellular(false);
        preferenceService.setCanUseRoaming(false);
        view.hideEnableRoaming();
        Log.d(TAG, "onClickOnlyWifi: starting service");
        PvtboxService.startPbService(context, null);
    }

    @Override
    public void onClickWifiEndCellular() {
        preferenceService.setCanUseCellular(true);
        view.showEnableRoaming();
    }

    @Override
    public void onRoamingChecked(boolean checked) {
        preferenceService.setCanUseRoaming(checked);
        if (!checked) {
            Log.d(TAG, "onRoamingChecked: starting service");
            PvtboxService.startPbService(context, null);
        }
    }

    @Override
    public void onAutoStartChecked(boolean checked) {
        preferenceService.setAutoStart(checked);
    }

    @Override
    public void onStatisticChecked(boolean checked) {
        boolean statisticWasEnabled = preferenceService.isStatisticEnabled();
        if (statisticWasEnabled && !checked) {
            Bugfender.setForceEnabled(false);
            Bugfender.disableReflection(true);
        } else if (!statisticWasEnabled && checked) {
            Bugfender.enableLogcatLogging();
            Bugfender.enableCrashReporting();
            Bugfender.disableReflection(true);
        }
        preferenceService.setStatisticEnabled(checked);
    }

    @Override
    public void onDownloadMediaChecked(boolean checked) {
        preferenceService.setMediaDownloadEnabled(checked);
    }

    @Override
    public void onPasscodeChecked(boolean checked) {
        if (EnterPinActivity.isPinSet(context) == checked) return;
        if (EnterPinActivity.isPinSet(context)) {
            Intent intent = EnterPinActivity.getIntent(context, false);
            context.startActivityForResult(intent, 1);
        } else {
            Intent intent = EnterPinActivity.getIntent(context, true);
            context.startActivity(intent);
        }
    }

    private boolean isNodeOnline() {
        return dataBaseService.isNodeOnline();
    }
}
