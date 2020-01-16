package net.pvtbox.android.service.transport.Downloads;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.pvtbox.android.application.Const;
import net.pvtbox.android.tools.CollectionUtils;
import net.pvtbox.android.tools.FileTool;
import net.pvtbox.android.tools.PatchTool;
import net.pvtbox.android.service.transport.Connectivity.ConnectivityService;


import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.SortedMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;

import proto.Proto;

import static net.pvtbox.android.tools.diskspace.DiskSpaceTool.checkDiskSpace;

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
public class DownloadTask {

    @NonNull
    private String TAG = DownloadTask.class.getSimpleName();

    private static final long downloadChunkSize = Const.DEFAULT_CHUNK_SIZE;
    private static final long downloadPartSize = downloadChunkSize * 16;
    private static final long maxNodeChunkRequests = 128;
    private static final long timeoutCheckInterval = 15_000;
    private static final long timeout = 20_000;
    private static final int timeoutsLimit = 2;

    public interface Callback {
        void call(DownloadTask task);
    }

    public interface PartDownloadedCallback {
        void call(Type type, String objId, long offset, long length);
    }

    public enum Type {
        File, Patch
    }

    public final Type type;
    public final String objectId;
    public final String eventUuid;
    public final String fileUuid;
    public final String name;
    private final long priority;
    public final long size;
    public final String hash;

    public long received = 0;
    private long lastReceived = 0;

    public boolean started = false;
    private boolean ready = false;
    private boolean finished = false;
    private boolean initialized = false;

    @NonNull
    private final Map<String, TreeMap<Long, Long>> nodesAvailableChunks = new HashMap<>();
    @NonNull
    private final Map<String, TreeMap<Long, Long>> nodesRequestedChunks = new HashMap<>();
    @NonNull
    private final Map<String, Long> nodesLastReceiveTime = new HashMap<>();
    @NonNull
    private final Map<String, Long> nodesDownloadedChunksCount = new HashMap<>();
    @NonNull
    private final Map<String, Integer> nodesTimeoutsCount = new HashMap<>();

    @NonNull
    private final TreeMap<Long, Long> wantedChunks = new TreeMap<>();
    @NonNull
    private final TreeMap<Long, Long> downloadedChunks = new TreeMap<>();

    private final Handler handler;

    private final ConnectivityService connectivityService;
    private final Context context;
    private final FileTool fileTool;

    private final String path;
    @NonNull
    private final String downloadPath;

    private final Runnable checkTimeoutsRunnable = this::checkTimeouts;

    private final Callback onReady;
    private final Callback onNotReady;
    private final Callback onCompleted;
    private final Callback onFinishing;
    private final Callback onProgress;
    private final PartDownloadedCallback onPartDownloaded;

    public DownloadTask(
            Type type, long priority, String fileUuid, String objectId, String eventUuid,
            String name, long size, String hashsum, String path, Context context,
            ConnectivityService connectivityService, @NonNull HandlerThread handlerThread, FileTool fileTool,
            Callback onReady, Callback onCompleted, Callback onNotReady,
            Callback onProgress, Callback onFinishing,
            PartDownloadedCallback onPartDownloaded) {
        TAG += "_" + objectId;
        this.type = type;
        this.priority = priority;
        this.fileUuid = fileUuid;
        this.objectId = objectId;
        this.eventUuid = eventUuid;
        this.name = name;
        this.size = size;
        this.hash = hashsum;
        this.path = path;
        this.downloadPath = path + ".download";

        this.onReady = onReady;
        this.onCompleted = onCompleted;
        this.onNotReady = onNotReady;
        this.onProgress = onProgress;
        this.onFinishing = onFinishing;
        this.onPartDownloaded = onPartDownloaded;

        this.connectivityService = connectivityService;

        this.fileTool = fileTool;
        this.context = context;
        this.handler = new Handler(handlerThread.getLooper());

        initWantedChunks();

        Log.i(TAG, String.format(
                "create download task: %s, hash: %s, name: %s", objectId, hashsum, name));
    }

    public static final Comparator<DownloadTask> Comparator = (lhs, rhs) -> {
        long res = lhs.equals(rhs) ? 0 : (
                lhs.priority == rhs.priority ? (
                        lhs.size - lhs.received == rhs.size - rhs.received ? (
                                lhs.eventUuid.compareTo(rhs.eventUuid)) : (
                                lhs.size - lhs.received - (rhs.size - rhs.received))) :
                        rhs.priority - lhs.priority);
        return res > 0 ? 1 : res < 0 ? -1 : 0;
    };

    public void onNodeDisconnected(String nodeId) {
        Log.i(TAG, String.format("onNodeDisconnected: %s", nodeId));
        onDisconnect(nodeId, false, true);
    }

    private void onDisconnect(String nodeId, boolean connectionAlive, boolean timeoutLimitExceed) {
        Log.i(TAG, "disconnect node:" + nodeId);

        nodesRequestedChunks.remove(nodeId);
        if (timeoutLimitExceed) {
            nodesAvailableChunks.remove(nodeId);
            nodesTimeoutsCount.remove(nodeId);
            if (connectionAlive) {
                connectivityService.reconnect(nodeId);
            }
        }
        nodesLastReceiveTime.remove(nodeId);
        nodesDownloadedChunksCount.remove(nodeId);

        if (connectionAlive) {
            sendAbort(nodeId);
        }

        if (nodesAvailableChunks.isEmpty()) {
            checkDownloadNotReady(
                    started ? nodesRequestedChunks : nodesAvailableChunks);
        } else {
            downloadChunks();
        }
    }

    public void onAvailabilityInfoReceived(@NonNull List<Proto.Info> info, String nodeId) {
        Log.i(TAG, String.format("onAvailabilityInfoReceived from node %s", nodeId));

        if (storeAvailabilityInfo(info, nodeId)) {
            if (!ready) {
                ready = true;
                onReady.call(this);
            }
        }
        if (started && !finished) {
            downloadNextChunks(nodeId, 0);
            cleanNodesLastReceiveTime();
            checkDownloadNotReady(nodesRequestedChunks);
        }
    }

    public boolean start() {
        Log.i(TAG, "start");

        check();

        if (finished) return false;

        started = true;

        if (!initialized) {
            initialized = true;

            for (Map.Entry<Long, Long> downloadedChunk : downloadedChunks.entrySet()) {
                removeFromChunks(
                        downloadedChunk.getKey(), downloadedChunk.getValue(), wantedChunks);
            }

            received = CollectionUtils.sum(downloadedChunks.values());
        }

        if (!checkDiskSpace(size - received, context)) {
            Log.w(TAG, "start: disk space low");
            stop();
            return false;
        }

        lastReceived = received;
        onProgress.call(this);

        downloadChunks();

        handler.postDelayed(checkTimeoutsRunnable, timeoutCheckInterval);
        return true;
    }

    public void check() {
        if (!finished && FileTool.isExist(path)) {
            Log.i(TAG, "file exist");
            stop();
            finished = true;
            handler.post(() -> onCompleted.call(this));
        }
    }

    public void cancel() {
        Log.i(TAG, "cancel");
        handler.post(() -> completeDownload(true));
    }

    @Nullable
    public SortedMap<Long, Long> getDownloadedChunks() {
        if (downloadedChunks.isEmpty()) {
            return null;
        }
        return downloadedChunks;
    }

    public void stop() {
        Log.i(TAG, "stop");
        started = false;
        handler.removeCallbacks(checkTimeoutsRunnable);
        for (String nodeId : nodesLastReceiveTime.keySet()) {
            sendAbort(nodeId);
        }
    }

    private void initWantedChunks() {
        wantedChunks.put(0L, size);
    }

    private void downloadChunks() {
        if (!started || finished) {
            Log.w(TAG, "downloadChunks: not started or finished");
            return;
        }

        Log.i(TAG, "downloadChunks");

        ArrayList<String> nodes = new ArrayList<>(nodesAvailableChunks.keySet());
        Collections.shuffle(nodes);
        for (String nodeId : nodes) {
            if (nodesRequestedChunks.get(nodeId) == null) {
                downloadNextChunks(nodeId, 0);
            }
        }
        cleanNodesLastReceiveTime();
        checkDownloadNotReady(nodesRequestedChunks);
    }

    public static void removeFromChunks(Long offset, Long length, @Nullable TreeMap<Long, Long> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }

        Long leftChunkKey = null;
        Long leftChunkValue = null;
        for (Map.Entry<Long, Long> entry : chunks.entrySet()) {
            if (entry.getKey() + entry.getValue() > offset) {
                leftChunkKey = entry.getKey();
                leftChunkValue = entry.getValue();
                break;
            }
        }

        Long rightChunkKey = null;
        Long rightChunkValue = null;
        for (Map.Entry<Long, Long> entry : chunks.descendingMap().entrySet()) {
            if (entry.getKey() <= offset + length &&
                    entry.getKey() + entry.getValue() > offset) {
                rightChunkKey = entry.getKey();
                rightChunkValue = entry.getValue();
                break;
            }
        }

        if (leftChunkKey != null && rightChunkKey != null) {
            chunks.subMap(
                    leftChunkKey, true,
                    rightChunkKey, true)
                    .clear();
        } else if (rightChunkKey != null) {
            chunks.remove(rightChunkKey);
        }

        if (leftChunkKey != null &&
                leftChunkKey < offset &&
                leftChunkKey + leftChunkValue >= offset) {
            chunks.put(leftChunkKey, offset - leftChunkKey);
        }

        if (rightChunkKey != null &&
                rightChunkKey + rightChunkValue > offset + length) {
            chunks.put(
                    offset + length,
                    rightChunkKey + rightChunkValue - offset - length);
        }
    }

    private boolean storeAvailabilityInfo(@NonNull List<Proto.Info> info, String nodeId) {
        TreeMap<Long, Long> availableChunks = nodesAvailableChunks.get(nodeId);
        if (availableChunks == null) {
            availableChunks = new TreeMap<>();
        }
        boolean newAdded = false;
        for (Proto.Info partInfo : info) {
            if (partInfo.getLength() == 0) {
                continue;
            }
            if (availableChunks.isEmpty()) {
                availableChunks.put(partInfo.getOffset(), partInfo.getLength());
                newAdded = true;
                continue;
            }
            Long resultOffset = partInfo.getOffset();
            Long resultLength = partInfo.getLength();
            Map.Entry<Long, Long> leftChunk = null;
            for (Map.Entry<Long, Long> entry : availableChunks.descendingMap().entrySet()) {
                if (entry.getKey() <= partInfo.getOffset()) {
                    leftChunk = entry;
                    break;
                }
            }
            if (leftChunk != null) {
                if (leftChunk.getKey() <= partInfo.getOffset() &&
                        leftChunk.getKey() + leftChunk.getValue() >=
                                partInfo.getOffset() + partInfo.getLength()) {
                    continue;
                }

                if (partInfo.getOffset() <= leftChunk.getKey() + leftChunk.getValue()) {
                    resultOffset = leftChunk.getKey();
                    resultLength = partInfo.getOffset() + partInfo.getLength() - resultOffset;
                }
            }

            Map.Entry<Long, Long> rightChunk = null;
            for (Map.Entry<Long, Long> entry : availableChunks.descendingMap().entrySet()) {
                if (entry.getKey() <= resultOffset + resultLength) {
                    rightChunk = entry;
                    break;
                }
            }
            if (rightChunk != null) {
                if (partInfo.getOffset() + partInfo.getLength() <=
                        rightChunk.getKey() + rightChunk.getValue()) {
                    resultLength = rightChunk.getKey() + rightChunk.getValue() - resultOffset;
                }
            }

            availableChunks.subMap(
                    resultOffset, true,
                    resultOffset + resultLength, true).clear();
            availableChunks.put(resultOffset, resultLength);
            newAdded = true;
        }
        if (newAdded) {
            nodesAvailableChunks.put(nodeId, availableChunks);
        }
        return newAdded;
    }

    private void downloadNextChunks(String nodeId, long timeFromLastReceivedChunk) {
        if (!started || finished) {
            Log.w(TAG, "downloadNextChunks: not started or finished");
            return;
        }
        Log.i(TAG, String.format("downloadNextChunks from %s, timeFromLastReceivedChunk: %d",
                nodeId, timeFromLastReceivedChunk));
        long totalRequested = CollectionUtils.sumTreeMap(nodesRequestedChunks.values());

        TreeMap<Long, Long> availableChunks;
        if (totalRequested + received >= size) {
            if (nodesRequestedChunks.get(nodeId) == null || timeFromLastReceivedChunk > 700) {
                availableChunks = getEndRaceChunksToDownload(nodeId);
            } else {
                return;
            }
        } else {
            availableChunks = getAvailableChunksToDownload(nodeId);
        }
        if (availableChunks == null || availableChunks.isEmpty()) {
            return;
        }

        int index = ThreadLocalRandom.current().nextInt(availableChunks.size());
        Long availableOffset = new ArrayList<>(availableChunks.keySet()).get(index);
        Long availableLength = availableChunks.get(availableOffset);
        if (availableLength == null) availableLength = 0L;
        int partsCount = (int) Math.ceil((double) availableLength / (double) downloadPartSize);
        int partToDownload = ThreadLocalRandom.current().nextInt(partsCount);
        Long offset = availableOffset + partToDownload * downloadPartSize;
        Long length = Math.min(downloadPartSize, availableOffset + availableLength - offset);

        TreeMap<Long, Long> requestedChunks = nodesRequestedChunks.get(nodeId);
        if (requestedChunks == null) {
            requestedChunks = new TreeMap<>();
        }
        requestedChunks.put(offset, length);
        nodesRequestedChunks.put(nodeId, requestedChunks);
        nodesLastReceiveTime.put(nodeId, new Date().getTime());

        requestChunks(offset, length, nodeId);
    }

    @Nullable
    private TreeMap<Long, Long> getEndRaceChunksToDownload(String nodeId) {
        Log.i(TAG, String.format("getEndRaceChunksToDownload from %s", nodeId));
        TreeMap<Long, Long> availableChunks = nodesAvailableChunks.get(nodeId);
        if (availableChunks == null) {
            return null;
        }
        for (Map.Entry<Long, Long> entry : downloadedChunks.entrySet()) {
            removeFromChunks(entry.getKey(), entry.getValue(), availableChunks);
        }
        if (availableChunks.isEmpty()) {
            return null;
        }
        TreeMap<Long, Long> availableFromOtherNodes = new TreeMap<>(availableChunks);
        TreeMap<Long, Long> nodeRequestedChunks = nodesRequestedChunks.get(nodeId);
        if (nodeRequestedChunks != null && !nodeRequestedChunks.isEmpty()) {
            for (Map.Entry<Long, Long> entry : nodeRequestedChunks.entrySet()) {
                removeFromChunks(entry.getKey(), entry.getValue(), availableFromOtherNodes);
            }
        }
        if (availableFromOtherNodes.isEmpty()) {
            return availableChunks;
        } else {
            return availableFromOtherNodes;
        }
    }

    @Nullable
    private TreeMap<Long, Long> getAvailableChunksToDownload(String nodeId) {
        Log.i(TAG, String.format("getAvailableChunksToDownload from %s", nodeId));
        TreeMap<Long, Long> availableChunks = nodesAvailableChunks.get(nodeId);
        if (availableChunks == null) {
            return null;
        }
        for (TreeMap<Long, Long> nodeRequestedChunks : nodesRequestedChunks.values()) {
            for (Map.Entry<Long, Long> entry : nodeRequestedChunks.entrySet()) {
                removeFromChunks(entry.getKey(), entry.getValue(), availableChunks);
            }
        }
        if (availableChunks.isEmpty()) {
            return null;
        }
        for (Map.Entry<Long, Long> entry : downloadedChunks.entrySet()) {
            removeFromChunks(entry.getKey(), entry.getValue(), availableChunks);
        }

        return availableChunks;
    }

    private void cleanNodesLastReceiveTime() {
        for (String nodeId : new ArrayList<>(nodesLastReceiveTime.keySet())) {
            if (nodesRequestedChunks.get(nodeId) == null) {
                nodesLastReceiveTime.remove(nodeId);
            }
        }
    }

    private void checkDownloadNotReady(@NonNull Map checkable) {
        if (wantedChunks.isEmpty() && started) {
            completeDownload(false);
            return;
        }
        if (checkable.isEmpty() && ready) {
            started = false;
            ready = false;
            Log.i(TAG, "checkDownloadNotReady, not ready");
            onNotReady.call(this);
        }
    }

    private boolean completeDownload(boolean force) {
        if (finished) return true;
        if (wantedChunks.isEmpty() || force) {
            Log.i(TAG, String.format("completeDownload: force: %s", force));

            stop();
            if (!force) {
                Log.i(TAG, "completeDownload: onFinishing");
                onFinishing.call(this);
                String downloadHash = PatchTool.getFileHash(downloadPath);
                if (Objects.equals(downloadHash, hash) && fileTool.moveFile(
                        downloadPath, path, false)) {
                    // onFinished?
                    Log.i(TAG, "completeDownload: onCompleted");
                    onCompleted.call(this);
                } else {
                    Log.e(TAG, String.format(
                            "completeDownload: download failed, expected hash: %s, actual: %s",
                            hash, downloadHash));
                    downloadedChunks.clear();
                    initWantedChunks();
                    nodesRequestedChunks.clear();
                    nodesLastReceiveTime.clear();
                    nodesDownloadedChunksCount.clear();
                    nodesTimeoutsCount.clear();
                    received = 0;
                    lastReceived = 0;
                    start();
                    return true;
                }
            }
            finished = true;
            return true;
        }
        return false;
    }

    public void onDataReceived(@NonNull Proto.Info info, byte[] data, String nodeId) {
        if (finished) {
            Log.w(TAG, "onDataReceived: task finished");
            return;
        }
        Log.i(TAG, " onDataReceived");

        long now = new Date().getTime();
        Long lastReceivedTime = nodesLastReceiveTime.get(nodeId);
        if (lastReceivedTime == null) {
            lastReceivedTime = 0L;
        }
        nodesLastReceiveTime.put(nodeId, now);
        nodesTimeoutsCount.remove(nodeId);

        Long downloadedChunksCount = nodesDownloadedChunksCount.get(nodeId);
        if (downloadedChunksCount == null) {
            downloadedChunksCount = 0L;
        }
        downloadedChunksCount++;
        nodesDownloadedChunksCount.put(nodeId, downloadedChunksCount);

        Long length = info.getLength();
        Long offset = info.getOffset();

        if (!isChunkAlreadyDownloaded(offset)) {
            if (!onNewChunkDownloaded(offset, length, data)) {
                return;
            }
        }

        TreeMap<Long, Long> nodeRequestedChunks = nodesRequestedChunks.get(nodeId);
        if (nodeRequestedChunks != null) {
            removeFromChunks(offset, length, nodeRequestedChunks);
            if (nodeRequestedChunks.isEmpty()) {
                nodesRequestedChunks.remove(nodeId);
            } else {
                nodesRequestedChunks.put(nodeId, nodeRequestedChunks);
            }

        }
        long requestedCount = nodeRequestedChunks == null ? 0 :
                (CollectionUtils.sum(nodeRequestedChunks.values()) / downloadChunkSize);
        if (downloadedChunksCount * 4 >= requestedCount && requestedCount < maxNodeChunkRequests) {
            downloadNextChunks(nodeId, now - lastReceivedTime);
            cleanNodesLastReceiveTime();
            checkDownloadNotReady(nodesRequestedChunks);
        }
    }

    private boolean isChunkAlreadyDownloaded(Long offset) {
        if (downloadedChunks.isEmpty()) {
            return false;
        }
        Map.Entry chunk = null;
        for (Map.Entry<Long, Long> entry : downloadedChunks.entrySet()) {
            if (entry.getKey() <= offset && entry.getKey() + entry.getValue() > offset) {
                chunk = entry;
                break;
            }
        }
        return chunk != null;
    }

    private boolean onNewChunkDownloaded(Long offset, Long length, byte[] data) {
        if (!writeToFile(data, offset)) {
            return false;
        }

        received += length;

        Log.i(TAG, String.format("onNewChunkDownloaded (%d, %s), received: %d, total: %d",
                offset, length, received, size));

        if ((double) (received - lastReceived) / (double) size > 0.01) {
            lastReceived = received;
            onProgress.call(this);
        }

        Long newOffset = offset;
        Long newLength = length;

        Map.Entry<Long, Long> leftChunk = null;
        for (Map.Entry<Long, Long> entry : downloadedChunks.entrySet()) {
            if (entry.getKey() + entry.getValue() == offset) {
                leftChunk = entry;
                break;
            }
        }
        if (leftChunk != null) {
            newOffset = leftChunk.getKey();
            newLength += leftChunk.getValue();
            downloadedChunks.remove(leftChunk.getKey());
        }

        Long rightChunkLength = downloadedChunks.get(newOffset + newLength);
        if (rightChunkLength != null) {
            downloadedChunks.remove(newOffset + newLength);
            newLength += rightChunkLength;
        }

        downloadedChunks.put(newOffset, newLength);

        removeFromChunks(offset, length, wantedChunks);

        Long partOffset = (offset / downloadPartSize) * downloadPartSize;
        Long partSize = Math.min(downloadPartSize, size - partOffset);
        if (newOffset <= partOffset && newOffset + newLength >= partOffset + partSize) {
            onPartDownloaded.call(type, objectId, partOffset, partSize);
        }

        return !completeDownload(false);
    }

    private void checkTimeouts() {
        if (!started || finished) {
            return;
        }

        Log.i(TAG, "checkTimeouts");

        long now = new Date().getTime();
        HashSet<String> timedOutNodes = new HashSet<>();
        for (Map.Entry<String, Long> entry : nodesLastReceiveTime.entrySet()) {
            if (now - entry.getValue() > timeout) {
                timedOutNodes.add(entry.getKey());
            }
        }

        for (String nodeId : timedOutNodes) {
            Integer timeoutCount = nodesTimeoutsCount.remove(nodeId);
            if (timeoutCount == null) {
                timeoutCount = 0;
            }
            timeoutCount++;
            boolean timeoutLimitExceed;
            if (timeoutCount >= timeoutsLimit) {
                timeoutLimitExceed = true;
            } else {
                timeoutLimitExceed = false;
                nodesTimeoutsCount.put(nodeId, timeoutCount);
            }
            onDisconnect(nodeId, true, timeoutLimitExceed);
        }

        if (started && !finished) {
            handler.postDelayed(checkTimeoutsRunnable, timeoutCheckInterval);
        }
    }

    private boolean writeToFile(byte[] data, long offset) {
        Log.i(TAG, "write to file:" + downloadPath);
        return fileTool.writeToFile(data, offset, downloadPath);
    }

    private void sendAbort(String nodeId) {
        Proto.Message message = Proto.Message.newBuilder()
                .setMagicCookie(0x7a52fa73)
                .setMtype(Proto.Message.MType.DATA_ABORT)
                .setObjId(objectId)
                .setObjType(type == Type.File ?
                        Proto.Message.ObjectType.FILE : Proto.Message.ObjectType.PATCH)
                .build();
        Log.i(TAG, String.format("sendAbort to node %s", nodeId));
        connectivityService.sendMessage(
                message.toByteArray(), nodeId, false);
    }

    private void requestChunks(Long offset, Long length, String nodeId) {
        Proto.Message message = Proto.Message.newBuilder()
                .setMagicCookie(0x7a52fa73)
                .setMtype(Proto.Message.MType.DATA_REQUEST)
                .setObjId(objectId)
                .setObjType(type == Type.File ?
                        Proto.Message.ObjectType.FILE : Proto.Message.ObjectType.PATCH)
                .addInfo(Proto.Info.newBuilder()
                        .setOffset(offset)
                        .setLength(length)
                        .build())
                .build();
        Log.i(TAG, String.format("requestChunks from node: %s", nodeId));
        connectivityService.sendMessage(
                message.toByteArray(), nodeId, false);
    }

    @Override
    public boolean equals(Object other) {
        try {
            DownloadTask otherTask = (DownloadTask) other;
            return objectId.equals(otherTask.objectId) && Objects.equals(fileUuid, otherTask.fileUuid);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return objectId.hashCode();
    }
}
