package net.pvtbox.android.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;

import net.pvtbox.android.application.Const;

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
public class BootCompletedIntentReceiver extends BroadcastReceiver {

    private static final String TAG = BootCompletedIntentReceiver.class.getSimpleName();


    @Override
    public void onReceive(@NonNull Context context, @NonNull Intent intent) {
        Log.d(TAG, "onReceive");
        if ("android.intent.action.BOOT_COMPLETED".equals(intent.getAction()) ||
                "android.intent.action.QUICKBOOT_POWERON".equals(intent.getAction()) ||
                "com.htc.intent.action.QUICKBOOT_POWERON".equals(intent.getAction()) ||
                "android.intent.action.USER_PRESENT".equals(intent.getAction())) {
            Log.d(TAG, "onReceive: BOOT_COMPLETED");
            if (!PvtboxService.isServiceRunning(context)) {
                PreferenceService preferenceService = new PreferenceService(
                        context.getSharedPreferences(Const.SETTINGS_NAME, Context.MODE_PRIVATE));
                String userHash = preferenceService.getUserHash();
                if (preferenceService.isAutoStart()
                        && preferenceService.isLoggedIn()
                        && (!preferenceService.isExited()
                        || !"android.intent.action.USER_PRESENT".equals(intent.getAction()))
                        && userHash != null && !userHash.isEmpty()) {
                    Log.i(TAG, String.format("onReceive: BOOT_COMPLETED (%s): starting service",
                            intent.getAction()));
                    PvtboxService.startPbService(context, null);
                } else {
                    Log.i(TAG, String.format(
                            "onReceive: BOOT_COMPLETED (%s): skip service start. " +
                                    "isAutoStart: %s, isExited: %s, userHash: %s",
                            intent.getAction(), preferenceService.isAutoStart(),
                            preferenceService.isExited(), userHash));
                }
            } else {
                Log.i(TAG, String.format("onReceive: BOOT_COMPLETED (%s): service already running",
                        intent.getAction()));
            }
        }
    }
}