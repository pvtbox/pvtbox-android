package net.pvtbox.android.service;

import net.pvtbox.android.R;
import net.pvtbox.android.db.DataBaseService;
import net.pvtbox.android.db.model.DeviceRealm;
import net.pvtbox.android.service.signalserver.SignalServerService;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Timer;
import java.util.TimerTask;

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
class DeviceStatusBroadcaster {

    private final PreferenceService preferenceService;
    private final SignalServerService signalServerService;
    private final DataBaseService dataBaseService;

    private int sentStatus = 0;
    private long sentDiskUsage = 0;
    private double sentDownloadSpeed = 0.0;
    private double sentUploadSpeed = 0.0;

    private final Timer timer = new Timer("DeviceStatusBroadcasterThread", true);

    public DeviceStatusBroadcaster(SignalServerService signalServerService,
                                   PreferenceService preferenceService,
                                   DataBaseService dataBaseService) {
        this.signalServerService = signalServerService;
        this.preferenceService = preferenceService;
        this.dataBaseService = dataBaseService;
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                checkAndBroadcastStatus(false);
            }
        }, 2000, 10000);
    }

    public void onDestroy() {
        timer.cancel();
        if (preferenceService.getUserHash() == null || preferenceService.getUserHash().isEmpty()) {
            try {
                sendStatus(
                        sentUploadSpeed, sentDownloadSpeed, 5, sentDiskUsage);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    public void checkAndBroadcastStatus(boolean force) {
        if (!signalServerService.isConnected()) return;
        DeviceRealm device = dataBaseService.getOwnDevice();
        if (device == null) return;
        int status = device.isPaused() ? 8 : device.getStatus();
        double uploadSpeed = device.getUploadSpeed();
        double downloadSpeed = device.getDownloadSpeed();
        long diskUsage = device.getDiskUsage();
        if (force || sentStatus != status ||
                sentDiskUsage != diskUsage ||
                sentDownloadSpeed != downloadSpeed ||
                sentUploadSpeed != uploadSpeed) {
            try {
                sendStatus(
                        uploadSpeed,
                        downloadSpeed,
                        convertStatus(status),
                        diskUsage);
            } catch (JSONException e) {
                e.printStackTrace();
                return;
            }
            sentStatus = status;
            sentDiskUsage = diskUsage;
            sentDownloadSpeed = downloadSpeed;
            sentUploadSpeed = uploadSpeed;
        }
    }

    private int convertStatus(int status) {
        switch (status) {
            case 8:
                return 8;
            case R.string.synced_status:
                return 4;
            default:
                return 3;
        }
    }

    private void sendStatus(double uploadSpeed, double downloadSpeed, int status, long diskSpace) throws JSONException {
        JSONObject data = new JSONObject()
                .putOpt("operation", "node_status")
                .putOpt("data", new JSONObject()
                        .putOpt("disk_usage", diskSpace)
                        .putOpt("upload_speed", uploadSpeed)
                        .putOpt("download_speed", downloadSpeed)
                        .putOpt("node_status", status));
        String msg = data.toString();
        signalServerService.sendString(msg);
    }

}
