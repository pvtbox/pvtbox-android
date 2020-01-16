package net.pvtbox.android.service.sync;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import androidx.annotation.NonNull;

import net.pvtbox.android.db.DataBaseService;
import net.pvtbox.android.service.signalserver.SignalServerService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.locks.ReentrantLock;

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
public class SyncService {
    public final static long MAX_EVENTS_TOTAL = 5000;
    public final static long PACK_EVENTS = 100;
    private final static String TAG = SyncService.class.getName();
    private final DataBaseService dataBaseService;
    private final SignalServerService signalServerService;
    private final ReentrantLock lock = new ReentrantLock();
    private boolean working = true;
    private boolean fetchingChanges = true;
    private long processingFileEventsCount = 0;
    private long receivedEventsCount = 0;
    private final long eventsBetweenChecksLimit = MAX_EVENTS_TOTAL;

    private final HandlerThread handlerThread = new HandlerThread(
            "SyncServiceThread",
            HandlerThread.NORM_PRIORITY);
    @NonNull
    private final Handler handler;
    @NonNull
    private ArrayList<String> collaboratedFolders = new ArrayList<>();
    private HashMap<String, JSONObject> shareList = new HashMap<>();
    private final Callback onInitialSyncDone;
    private boolean initialFetch = true;

    private final Runnable eventsCheckRunnable = this::sendEventsCheck;

    public interface Callback {
        void call();
    }

    public SyncService(
            DataBaseService dataBaseService,
            SignalServerService signalServerService,
            Callback onInitialSyncDone) {
        this.dataBaseService = dataBaseService;
        this.signalServerService = signalServerService;
        this.onInitialSyncDone = onInitialSyncDone;
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
    }

    public void onDestroy() {
        handlerThread.quitSafely();
        working = false;
    }

    public void eventList(@NonNull JSONArray eventList, boolean markChecked) {
        try {
            lock.lock();
            processingFileEventsCount += eventList.length();
        } finally {
            lock.unlock();
        }

        handler.post(() -> {
            dataBaseService.updateOwnDeviceRemotes(false, processingFileEventsCount);
            if (!working) return;
            long eventsCount = eventList.length();

            Log.i(TAG, String.format("processing eventList: %d", eventsCount));
            if (eventsCount != 0) {
                dataBaseService.saveFileEvents(eventList, markChecked, initialFetch);
                if (!working) return;

                receivedEventsCount += eventsCount;
            }

            if (eventsCount > 0 && eventsCount < PACK_EVENTS) {
                if (shareList != null) {
                    dataBaseService.updateShareList(shareList);
                }
                dataBaseService.updateCollaboratedFolders(collaboratedFolders);

                handler.removeCallbacks(eventsCheckRunnable);
                if (markChecked) {
                    handler.postDelayed(eventsCheckRunnable, 20 * 60 * 1000);
                } else {
                    handler.postDelayed(eventsCheckRunnable, 60 * 1000);
                }
            }
            if (initialFetch) {
                if (eventsCount < PACK_EVENTS) {
                    Log.i(TAG, "eventList: initial fetch done");
                    dataBaseService.clearProcessingFiles();
                    onInitialSyncDone.call();
                    initialFetch = false;
                    fetchingChanges = false;
                }
            } else {
                fetchingChanges = false;

            }

            try {
                lock.lock();
                processingFileEventsCount -= eventsCount;
                if (processingFileEventsCount < 0) processingFileEventsCount = 0;
                if (processingFileEventsCount == 0) {
                    if (receivedEventsCount >= eventsBetweenChecksLimit) {
                        fetchingChanges = true;
                        handler.removeCallbacks(eventsCheckRunnable);
                        handler.post(eventsCheckRunnable);
                    }
                }
                dataBaseService.updateOwnDeviceRemotes(fetchingChanges, processingFileEventsCount);
            } finally {
                lock.unlock();
            }
        });
    }

    private void sendEventsCheck() {
        receivedEventsCount = 0;
        signalServerService.sendEventsCheck();
        try {
            lock.lock();
            fetchingChanges = true;
        } finally {
            lock.unlock();
        }
    }

    public void setCollaboratedFolders(@NonNull ArrayList<String> collaboratedFolders) {
        handler.post(() -> {
            this.collaboratedFolders = collaboratedFolders;
            dataBaseService.updateCollaboratedFolders(collaboratedFolders);
        });
    }

    public void removeShare(String uuid) {
        handler.post(() -> {
            shareList.remove(uuid);
            dataBaseService.disableShare(uuid);
        });
    }

    public void addShare(String uuid, @NonNull JSONObject sharingInfo) {
        handler.post(() -> {
            shareList.put(uuid, sharingInfo);
            dataBaseService.saveShareLink(sharingInfo);
        });
    }

    public void updateShare(HashMap<String, JSONObject> share) {
        handler.post(() -> {
            this.shareList = share;
            dataBaseService.updateShareList(shareList);
        });
    }

    public void patchesInfo(@NonNull JSONArray info) {
        Log.d(TAG, "patchesInfo");
        dataBaseService.savePatchesInfo(info);
    }
}
