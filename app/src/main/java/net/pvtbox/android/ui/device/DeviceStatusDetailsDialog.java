package net.pvtbox.android.ui.device;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Handler;
import android.util.Log;
import android.view.ContextThemeWrapper;

import androidx.annotation.NonNull;

import net.pvtbox.android.R;
import net.pvtbox.android.db.DataBaseService;
import net.pvtbox.android.db.model.DeviceRealm;

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

class DeviceStatusDetailsDialog {

    private static final String TAG = DeviceStatusDetailsDialog.class.getSimpleName();
    private final AlertDialog dialog;
    @NonNull
    private final Handler handler;
    private final Context context;
    private final DataBaseService dataBaseService;
    private boolean working;

    DeviceStatusDetailsDialog(Context context, DataBaseService dataBaseService) {
        this.dataBaseService = dataBaseService;
        AlertDialog.Builder builder = new AlertDialog.Builder(
                new ContextThemeWrapper(context, R.style.AppTheme));

        this.context = context;
        handler = new Handler();
        dialog = builder.create();
        dialog.setOnDismissListener(d -> {
            working = false;
            dialog.cancel();
        });
    }

    public void show() {
        if (working) return;
        working = true;
        updateContent();
        dialog.show();
    }

    public void dissmiss() {
        if (!working) return;
        dialog.dismiss();
    }

    private void updateContent() {
        Log.d(TAG, "updateContent");
        if (!working) return;
        DeviceRealm device = dataBaseService.getOwnDevice();
        updateTitle(Objects.requireNonNull(device));
        updateStatusMessage(device);
        handler.postDelayed(this::updateContent, 1000);
    }

    private void updateTitle(@NonNull DeviceRealm device) {
        dialog.setTitle(device.isPaused() ? R.string.paused_status : device.getStatus());
    }

    private void updateStatusMessage(@NonNull DeviceRealm device) {
        String networkStatus = device.isOnline() ?
                context.getString(R.string.ok) :
                context.getString(R.string.connecting_info);
        String localStatus =
                device.isInitialSyncing() ? context.getString(R.string.waitingInitialSync) :
                        device.isProcessingOperation() ?
                                context.getString(R.string.performing_operation) :
                                device.isImportingCamera() ?
                                        context.getString(R.string.start_import_camera) :
                                        device.getProcessingLocalCount() > 0 ?
                                                context.getString(R.string.files_total,
                                                        device.getProcessingLocalCount()) :
                                                context.getString(R.string.done);


        String remoteStatus = device.getRemotesCount() > 0 ?
                context.getString(R.string.files_total, device.getRemotesCount()) :
                device.isDownloadingShare() ? context.getString(R.string.processing_share) :
                        device.isFetchingChanges() ? context.getString(R.string.fetching_changes) :
                                context.getString(R.string.done);

        String currentDownloadName = device.getCurrentDownloadName();
        if (currentDownloadName == null) {
            if (device.isInitialSyncing()) {
                currentDownloadName = context.getString(R.string.waitingInitialSync);
            } else {
                currentDownloadName = context.getString(R.string.waiting_for_nodes);
            }
        }
        String downloadsStatus = device.isPaused() ? context.getString(R.string.paused_status) :
                device.isInitialSyncing() ?
                        context.getString(R.string.waitingInitialSync) :
                        device.getDownloadsCount() > 0 ?
                                context.getString(
                                        R.string.download_status,
                                        currentDownloadName,
                                        device.getDownloadsCount()) :
                                context.getString(R.string.done) + '\n';

        String message = context.getString(
                R.string.status_details,
                networkStatus, localStatus, remoteStatus, downloadsStatus);
        dialog.setMessage(message);
    }
}
