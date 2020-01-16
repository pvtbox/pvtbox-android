package net.pvtbox.android.service.signalserver;


import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.util.Log;

import com.bumptech.glide.Glide;

import net.pvtbox.android.api.AuthHttpClient;
import net.pvtbox.android.application.Const;
import net.pvtbox.android.service.PreferenceService;
import net.pvtbox.android.db.DataBaseService;
import net.pvtbox.android.service.PvtboxService;
import net.pvtbox.android.tools.FileTool;
import net.pvtbox.android.tools.JSON;

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
public class WipeTool {
    @NonNull
    private static final String TAG = WipeTool.class.getSimpleName();
    private final Context context;
    private final DataBaseService dataBaseService;
    private final PreferenceService preferenceService;
    private final FileTool fileTool;
    private final AuthHttpClient httpClient;

    public WipeTool(Context context,
                    DataBaseService dataBaseService,
                    PreferenceService preferenceService,
                    AuthHttpClient httpClient,
                    FileTool fileTool) {
        this.dataBaseService = dataBaseService;
        this.preferenceService = preferenceService;
        this.fileTool = fileTool;
        this.context = context;
        this.httpClient = httpClient;
    }


    public void logoutAndClose(String actionUuid) {
        logout(actionUuid);
        sendBroadcastMessageClose();
    }

    private void logout(@Nullable String actionUuid) {
        PvtboxService.stopService(context, false);
        String userHash = preferenceService.getUserHash();
        if (userHash != null) {
            httpClient.logout(userHash);
        }
        preferenceService.setLoggedIn(false);
        if (actionUuid != null && userHash != null) {
            httpClient.remoteActionDone(actionUuid, userHash);
        }
    }

    private void sendBroadcastMessageClose() {
        Intent intent = new Intent(Const.LOGOUT_INTENT);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }


    private void wipeAndClose(String actionUuid) {
        wipeAction(actionUuid);
        sendBroadcastMessageClose();
    }

    private void wipeAction(@Nullable String actionUuid) {
        String userHash = preferenceService.getUserHash();
        if (PvtboxService.isServiceRunning(context)) {
            PvtboxService.stopService(context, true);
        } else {
            wipe();
            preferenceService.setUserHash(null);
            preferenceService.setLoggedIn(false);
        }
        if (actionUuid != null && userHash != null) {
            httpClient.remoteActionDone(actionUuid, userHash);
        }
    }

    public void wipe() {
        preferenceService.dropSetting();
        preferenceService.writeFirstStart();

        fileTool.deleteDirectory(Const.DEFAULT_PATH);
        deleteSystemData();
        dataBaseService.clearDb();
        Glide.get(context).clearDiskCache();
        String filesDir = context.getFilesDir().getAbsolutePath();
        fileTool.deleteDirectory(FileTool.buildPath(filesDir, "Pictures"));
        fileTool.deleteDirectory(FileTool.buildPath(filesDir, "Movies"));
    }

    private void deleteSystemData() {
        fileTool.deleteDirectory(Const.INTERNAL_PATH);
    }

    public void executeAction(@NonNull JSONObject action) {
        switch (Objects.requireNonNull(JSON.optString(action, "action_type", ""))) {
            case "logout":
                logoutAndClose(JSON.optString(action, "action_uuid"));
                break;
            case "wipe":
                wipeAndClose(JSON.optString(action, "action_uuid"));
                break;
            case "credentials":
                JSONObject data = action.optJSONObject("action_data");
                updateUserHash(JSON.optString(action, "action_uuid"),
                        JSON.optString(data, "user_hash"));
                break;
        }
    }

    private void updateUserHash(String actionUuid, String userHash) {
        if (!Objects.equals(preferenceService.getUserHash(), userHash)) {
            preferenceService.setUserHash(userHash);
            Log.d(TAG, "updateUserHash: starting service");
            PvtboxService.startPbService(context, null);
        }
        if (actionUuid != null) {
            httpClient.remoteActionDone(actionUuid, userHash);
        }
    }
}
