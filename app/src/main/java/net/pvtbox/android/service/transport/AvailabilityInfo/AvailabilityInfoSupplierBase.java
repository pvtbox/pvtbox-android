package net.pvtbox.android.service.transport.AvailabilityInfo;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.pvtbox.android.db.DataBaseService;
import net.pvtbox.android.service.transport.Connectivity.ConnectivityService;
import net.pvtbox.android.service.transport.Downloads.DownloadManager;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;

import proto.Proto;

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

public abstract class AvailabilityInfoSupplierBase {

    private final static String TAG = AvailabilityInfoSupplierBase.class.getSimpleName();
    @Nullable
    private Handler handler;
    final DataBaseService dataBaseService;
    private final ConnectivityService connectivityService;

    @NonNull
    final
    HashMap<String, Set<String>> subscriptions = new HashMap<>();
    private final DownloadManager downloadManager;


    AvailabilityInfoSupplierBase(
            DataBaseService dataBaseService,
            ConnectivityService connectivityService, DownloadManager downloadManager,
            @NonNull HandlerThread handlerThread) {
        this.dataBaseService = dataBaseService;
        this.connectivityService = connectivityService;
        handler = new Handler(handlerThread.getLooper());
        this.downloadManager = downloadManager;
    }

    public void onDestroy() {
        handler = null;
    }

    public void onNodeDisconnected(String nodeId) {
        Collection<Set<String>> values = subscriptions.values();
        Iterator<Set<String>> iterator = values.iterator();
        while (iterator.hasNext()) {
            Set<String> nodeSet = iterator.next();
            nodeSet.remove(nodeId);
            if (nodeSet.isEmpty()) {
                iterator.remove();
            }
        }
    }

    public void onAbort(@NonNull Proto.Message message, String nodeId) {
        String objectId = message.getObjId();
        Set<String> nodeSet = subscriptions.remove(objectId);
        if (nodeSet != null) {
            nodeSet.remove(nodeId);
            if (!nodeSet.isEmpty()) {
                subscriptions.put(objectId, nodeSet);
            }
        }
    }

    void addSubscriber(String objId, String nodeId) {
        Log.d(TAG, "add subscriber: " + objId);
        Set<String> nodeSet = subscriptions.get(objId);

        if (nodeSet == null) {
            nodeSet = new HashSet<>();
            subscriptions.put(objId, nodeSet);
        }
        nodeSet.add(nodeId);
    }

    @Nullable
    Proto.Message sendAlreadyDownloadedChunksIfAny(
            String nodeId, String objId, boolean isFile, boolean aggregate) {
        SortedMap<Long, Long> downloaded = downloadManager.getAlreadyDownloadedBlocks(objId);
        if (downloaded == null || downloaded.isEmpty()) return null;
        Proto.Message res = buildAvailabilityInfoResponse(objId, downloaded, isFile);
        if (!aggregate) sendAvailabilityInfoResponse(nodeId, res.toByteArray());
        return res;
    }

    @Nullable
    protected abstract Proto.Message onRequest(
            Proto.Message message, String nodeId, boolean aggregate);

    public void onRequest(Proto.Message message, String nodeId) {
        onRequest(message, nodeId, false);
    }

    public void onRequests(@NonNull Proto.Messages messages, String nodeId) {
        Proto.Messages.Builder responseBuilder = Proto.Messages.newBuilder();
        if (handler != null) {
            Log.i(TAG, "onRequests: process info requests");
            handler.post(() -> processRequests(
                    nodeId, messages.getMsgList().iterator(), responseBuilder));
        }
    }

    private void processRequests(
            String nodeId, @NonNull Iterator<Proto.Message> iterator,
            @NonNull Proto.Messages.Builder responseBuilder) {
        if (iterator.hasNext()) {
            Proto.Message msg = iterator.next();
            Proto.Message response = onRequest(msg, nodeId, true);
            if (response != null) responseBuilder.addMsg(response);
            if (handler != null) {
                handler.post(() -> processRequests(nodeId, iterator, responseBuilder));
            }
        } else if (responseBuilder.getMsgCount() > 0) {
            sendAvailabilityInfoResponses(nodeId, responseBuilder.build());
        }
    }

    @NonNull
    private Proto.Message buildAvailabilityInfoResponse(
            String objId, @NonNull SortedMap<Long, Long> downloaded, boolean isFile) {
        Proto.Message.Builder builder = Proto.Message.newBuilder()
                .setMagicCookie(0x7a52fa73)
                .setMtype(Proto.Message.MType.AVAILABILITY_INFO_RESPONSE)
                .setObjId(objId)
                .setObjType(isFile ?
                        Proto.Message.ObjectType.FILE : Proto.Message.ObjectType.PATCH);
        for (Map.Entry<Long, Long> entry : downloaded.entrySet()) {
            builder.addInfo(Proto.Info.newBuilder()
                    .setOffset(entry.getKey())
                    .setLength(entry.getValue())
                    .build());
        }
        return builder.build();
    }

    @NonNull
    Proto.Message buildAvailabilityInfoResponse(
            String objId, long offset, long length, boolean isFile) {
        return Proto.Message.newBuilder()
                .setMagicCookie(0x7a52fa73)
                .setMtype(Proto.Message.MType.AVAILABILITY_INFO_RESPONSE)
                .setObjId(objId)
                .setObjType(isFile ?
                        Proto.Message.ObjectType.FILE : Proto.Message.ObjectType.PATCH)
                .addInfo(Proto.Info.newBuilder()
                        .setOffset(offset)
                        .setLength(length)
                        .build())
                .build();
    }

    private void sendAvailabilityInfoResponses(String nodeId, @NonNull Proto.Messages messages) {
        Log.i(TAG, String.format("sendAvailabilityInfoResponses: to node %s", nodeId));
        connectivityService.sendMessage(
                messages.toByteArray(), nodeId, true);
    }

    void sendAvailabilityInfoResponse(String nodeId, @NonNull byte[] data) {
        Log.i(TAG, String.format("sendAvailabilityInfoResponse: to node %s", nodeId));
        connectivityService.sendMessage(data, nodeId, true);
    }
}
