package net.pvtbox.android.service.signalserver;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.util.Log;

import net.pvtbox.android.application.Const;
import net.pvtbox.android.service.PreferenceService;
import net.pvtbox.android.db.DataBaseService;
import net.pvtbox.android.service.PvtboxService;
import net.pvtbox.android.service.sync.SyncService;
import net.pvtbox.android.tools.JSON;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;

import me.leolin.shortcutbadger.ShortcutBadger;

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
public class SignalServerService extends SignalServerClient {

    private final static String TAG = SignalServerService.class.getSimpleName();

    private SyncService syncService;
    private final WipeTool wipeTool;
    private final HttpLoader httpLoader;

    @Nullable
    private static SignalServerClient instance = null;
    @Nullable
    private Runnable onConnectedCallback = null;
    @Nullable
    private Runnable onDisconnectedCallback = null;
    @Nullable
    private PvtboxService pvtboxService;

    public SignalServerService(@NonNull Context context,
                               PreferenceService preferenceService,
                               DataBaseService dataBaseService,
                               HttpLoader httpLoader,
                               WipeTool wipeTool,
                               @Nullable PvtboxService pvtboxService) {
        super("main", context, preferenceService, dataBaseService);
        instance = this;
        this.wipeTool = wipeTool;
        this.httpLoader = httpLoader;
        this.pvtboxService = pvtboxService;
        handler.post(this::init);
    }

    public static boolean IsConnected() {
        return instance != null && instance.isConnected();
    }

    private void init() {
        httpLoader.setSignalServerService(this);
    }

    public void setSyncService(SyncService syncService) {
        this.syncService = syncService;
    }

    @Override
    protected void broadcastConnectedToServer() {
        if (onConnectedCallback != null) {
            onConnectedCallback.run();
        }
        Intent intent = new Intent(Const.NETWORK_STATUS);
        intent.putExtra(Const.NETWORK_STATUS_SIGNAL_CONNECTING, false);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    @Override
    protected void broadcastConnectingToServer(int header, int status) {
        if (onDisconnectedCallback != null) {
            onDisconnectedCallback.run();
        }
        Intent intent = new Intent(Const.NETWORK_STATUS);
        intent.putExtra(Const.NETWORK_STATUS_SIGNAL_CONNECTING, true);
        intent.putExtra(Const.NETWORK_STATUS_INFO_HEADER, header);
        intent.putExtra(Const.NETWORK_STATUS_INFO, status);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    @Nullable
    @Override
    protected String getUrl() {
        return buildUrlForConnect();
    }

    @Override
    protected void onAccessDenied() {
        preferenceService.setLoggedIn(false);
        Log.d(TAG, "start: starting service");
        stop(true);
        PvtboxService.startPbService(context, null);
    }

    @Nullable
    private String buildUrlForConnect() {
        if (serverUrl != null && !serverUrl.isEmpty()) {
            String url = "wss://" + serverUrl + "/ws/node/" + preferenceService.getUserHash() + "/" + preferenceService.getNodeHash();
            ArrayList<Long> eventsCheckParams = dataBaseService.getEventsCheckParams();
            long lastEventNumber = eventsCheckParams.get(0);
            long lastCheckedEventNumber = eventsCheckParams.get(1);
            long eventsCountFromCheckedToLast = eventsCheckParams.get(2);
            long firstUnknownPatch = eventsCheckParams.get(3);
            url += String.format(
                    "?last_event_id=%s&checked_event_id=%s&events_count_check=%s&no_send_changed_files=1&node_without_backup=1&max_events_per_request=%s&max_events_total=%s",
                    lastEventNumber, lastCheckedEventNumber, eventsCountFromCheckedToLast, SyncService.PACK_EVENTS, SyncService.MAX_EVENTS_TOTAL);

            if (firstUnknownPatch != 0) {
                url += "&direct_patch_event_id=" + firstUnknownPatch;
            }

            Log.d(TAG, "url:" + url);
            return url;
        }
        return null;
    }

    public void sendEventsCheck() {
        ArrayList<Long> eventsCheckParams = dataBaseService.getEventsCheckParams();
        JSONObject data;
        try {
            data = new JSONObject()
                    .putOpt("operation", "last_file_events")
                    .putOpt("data", new JSONObject()
                            .putOpt("last_event_id", eventsCheckParams.get(0))
                            .putOpt("checked_event_id", eventsCheckParams.get(0)))
                            .putOpt("events_count_check", Integer.toString(0));
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }

        sendString(data.toString());
    }

    @Override
    protected void handleSharingEnable(JSONObject json) {
        JSONObject data = json.optJSONObject("data");
        if (data == null) return;
        syncService.addShare(JSON.optString(data, "uuid", ""), data);
    }

    @Override
    protected void handleDisableShare(JSONObject json) {
        JSONObject data = json.optJSONObject("data");
        String uuid = JSON.optString(data, "uuid");
        if (uuid != null) {
            syncService.removeShare(uuid);
        }
    }

    @Override
    protected void handleSharingList(JSONObject json) {
        JSONArray data = json.optJSONArray("data");
        if (data == null || data.length() == 0) return;
        HashMap<String, JSONObject> shareMap = new HashMap<>(data.length());
        for (int i = 0; i < data.length(); ++i) {
            JSONObject info = data.optJSONObject(i);
            if (info == null) return;
            shareMap.put(JSON.optString(info, "uuid", ""), info);
        }
        syncService.updateShare(shareMap);
    }

    @Override
    protected void handleUploadAdd(JSONObject json) {
        JSONObject data = json.optJSONObject("data");
        if (data == null) return;
        httpLoader.load(data);
    }

    @Override
    protected void handleUploadCancel(JSONObject json) {
        String uploadId = JSON.optString(json, "data");
        if (uploadId == null) return;
        httpLoader.cancel(uploadId);
    }

    @Override
    protected void handleRemoteAction(JSONObject json) {
        JSONObject data = json.optJSONObject("data");
        if (data != null) {
            wipeTool.executeAction(data);
        }
    }

    @Override
    protected void handlePatchesInfo(JSONObject json) {
        JSONArray info = json.optJSONArray("data");
        if (info != null) {
            syncService.patchesInfo(info);
        }
    }

    @Override
    protected void handleCollaboratedFolders(JSONObject json) {
        JSONArray data = json.optJSONArray("data");
        if (data == null) return;
        ArrayList<String> list = new ArrayList<>(data.length());
        for (int i = 0; i < data.length(); ++i) {
            String uuid = JSON.optString(data, i);
            if (uuid != null) {
                list.add(uuid);
            }
        }
        syncService.setCollaboratedFolders(list);
    }

    @Override
    protected void handleMinStoredEvent(JSONObject json) {
        String minStoredEvent = JSON.optString(json, "data");
        if (minStoredEvent == null) return;
        preferenceService.setLastEventUuid(minStoredEvent);
    }

    @Override
    protected void handleNewNotificationsCount(JSONObject json) {
        JSONObject data = json.optJSONObject("data");
        long count = data != null ? data.optLong("count", 0L) : 0L;
        dataBaseService.updateOwnDeviceNewNotificationsCount(count);
        if (count > 0) {
            ShortcutBadger.applyCount(context, (int)count);
        } else {
            ShortcutBadger.removeCount(context);
        }
    }

    @Override
    protected void handelNodeStatus(JSONObject json) {
        dataBaseService.saveNodeStatus(
                JSON.optString(json, "node_id", ""), json.optJSONObject("data"));
    }

    @Override
    protected void handleFileEvents(JSONObject json) {
        String nodeId = JSON.optString(json, "node_id");
        JSONArray events = json.optJSONArray("data");
        if (events != null) {
            syncService.eventList(events, "__SERVER__".equals(nodeId));
        }
    }

    @Override
    protected void handleLicenceType(JSONObject json) {
        String licenseType = JSON.optString(json, "data", Const.FREE_LICENSE);
        String licenseTypeOld = preferenceService.getLicenseType();
        boolean needClear = Const.FREE_LICENSE.equals(licenseTypeOld) &&
                !Objects.equals(licenseType, licenseTypeOld);
        if (needClear) {
            stop(false);
            dataBaseService.setAllEventsUnchecked();
            start();
        }
        preferenceService.setLicenseType(licenseType);

        if (pvtboxService != null) {
            pvtboxService.onLicenseTypeChanged();
        }
    }

    @Override
    protected void handlePeerList(JSONObject json) {
        Log.d(TAG, "handlePeerList");
        JSONArray data = json.optJSONArray("data");
        if (data == null || data.length() == 0) {
            return;
        }
        dataBaseService.savePeerList(data);
        super.handlePeerList(json);
    }

    @Override
    protected void handlePeerConnect(JSONObject json) {
        JSONObject peer = json.optJSONObject("data");
        if (peer == null) return;
        dataBaseService.peerConnect(peer);
        super.handlePeerConnect(json);
    }

    @Override
    protected void handleDisconnectPeer(JSONObject json) {
        super.handleDisconnectPeer(json);
        String nodeId = JSON.optString(json, "node_id");
        if (nodeId == null) return;
        dataBaseService.disconnectNode(nodeId);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        super.onDestroy();
        pvtboxService = null;
        instance = null;
        if (syncService != null) syncService.onDestroy();
        context = null;
    }

    public void setOnConnectedCallback(@Nullable Runnable callback) {
        onConnectedCallback = callback;
    }

    public void setOnDisconnectedCallback(@Nullable Runnable callback) {
        onDisconnectedCallback = callback;
    }

    void sendUploadFailed(String uploadId) {
        JSONObject data;
        try {
            data = new JSONObject()
                    .putOpt("operation", "upload_failed")
                    .putOpt("data", new JSONObject()
                            .putOpt("upload_id", uploadId));
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }

        sendString(data.toString());
    }

    void sendUploadCompleted(String uploadId) {
        JSONObject data;
        try {
            data = new JSONObject()
                    .putOpt("operation", "upload_complete")
                    .putOpt("data", new JSONObject()
                            .putOpt("upload_id", uploadId));
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }

        sendString(data.toString());
    }
}
