package net.pvtbox.android.service.transport.Downloads;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.HandlerThread;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.util.Log;

import com.google.protobuf.InvalidProtocolBufferException;

import net.pvtbox.android.R;
import net.pvtbox.android.application.Const;
import net.pvtbox.android.db.DataBaseService;
import net.pvtbox.android.db.model.EventRealm;
import net.pvtbox.android.db.model.FileRealm;
import net.pvtbox.android.service.transport.AvailabilityInfo.AvailabilityInfoConsumer;
import net.pvtbox.android.tools.FileTool;
import net.pvtbox.android.service.transport.Data.DataSupplier;
import net.pvtbox.android.service.transport.AvailabilityInfo.AvailabilityInfoSupplierFile;
import net.pvtbox.android.service.transport.AvailabilityInfo.AvailabilityInfoSupplierPatch;
import net.pvtbox.android.service.transport.Connectivity.ConnectivityService;
import net.pvtbox.android.tools.PatchTool;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.locks.ReentrantLock;

import io.realm.Realm;
import io.realm.RealmChangeListener;
import io.realm.RealmResults;
import io.realm.Sort;
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
public class DownloadManager extends BroadcastReceiver implements RealmChangeListener<RealmResults<FileRealm>> {
    @NonNull
    private String TAG = DownloadManager.class.getSimpleName();

    private final FileTool fileTool;
    @Nullable
    private HandlerThread handlerThread = new HandlerThread(
            "DownloadManagerThread",
            HandlerThread.NORM_PRIORITY + HandlerThread.MIN_PRIORITY);
    private final Handler handler;

    private final ReentrantLock lock = new ReentrantLock();
    @VisibleForTesting
    public DownloadTask currentDownloadTask;

    @VisibleForTesting
    public AvailabilityInfoConsumer availabilityInfoConsumerFile;
    @VisibleForTesting
    public AvailabilityInfoConsumer availabilityInfoConsumerPatch;
    @Nullable
    @VisibleForTesting
    public AvailabilityInfoSupplierPatch availabilityInfoSupplierPatch;
    @Nullable
    @VisibleForTesting
    public AvailabilityInfoSupplierFile availabilityInfoSupplierFile;
    @Nullable
    @VisibleForTesting
    public DataSupplier dataSupplier;
    private final Context context;
    private final ConnectivityService connectivityService;
    private final DataBaseService dataBaseService;
    private final PatchTool patchTool;

    @Nullable
    private Realm realm;
    @Nullable
    private RealmResults<FileRealm> files;
    private boolean initialSyncDone = false;
    private final boolean shareMode;
    private boolean paused;
    @NonNull
    @VisibleForTesting
    public final HashMap<String, DownloadTask> currentFilesDownloads = new HashMap<>();
    @NonNull
    @VisibleForTesting
    private final HashMap<String, Set<DownloadTask>> objIdsDownloadTasks = new HashMap<>();
    @NonNull
    @VisibleForTesting
    public final Queue<DownloadTask> readyDownloadsQueue = new PriorityBlockingQueue<>(
            100, DownloadTask.Comparator);
    @Nullable
    private Runnable checkDownloadsRunnable = null;
    @Nullable
    private Runnable checkReadyAndResubscribeRunnable = null;

    public DownloadManager(
            Context context, ConnectivityService connectivityService, FileTool fileTool,
            DataBaseService dataBaseService, PatchTool patchTool,
            boolean shareMode, boolean paused) {
        this.paused = paused;
        if (shareMode) {
            TAG += "_share";
        }
        this.context = context;
        this.connectivityService = connectivityService;
        this.fileTool = fileTool;
        this.dataBaseService = dataBaseService;
        this.patchTool = patchTool;
        this.shareMode = shareMode;

        Objects.requireNonNull(handlerThread).start();
        handler = new Handler(handlerThread.getLooper());
        handler.post(this::init);
    }

    public void onInitialSyncDone() {
        Log.i(TAG, "onInitialSyncDone");
        initialSyncDone = true;
        scheduleCheck();
    }

    public void cancelAll() {
        for (DownloadTask downloadTask : currentFilesDownloads.values()) {
            downloadTask.cancel();
        }
        readyDownloadsQueue.clear();
        currentFilesDownloads.clear();
        objIdsDownloadTasks.clear();
        currentDownloadTask = null;
    }

    public void pause() {
        handler.post(this::pauseInternal);
    }

    @VisibleForTesting
    public void pauseInternal() {
        paused = true;
        if (currentDownloadTask != null) {
            currentDownloadTask.stop();
            readyDownloadsQueue.add(currentDownloadTask);
        }
        currentDownloadTask = null;
        dataBaseService.setDownloadsStatus(
                R.string.paused_status, currentFilesDownloads.keySet());
    }

    public void resume() {
        handler.post(this::resumeInternal);
    }

    @VisibleForTesting
    public void resumeInternal() {
        paused = false;
        dataBaseService.setDownloadsStatus(
                R.string.starting_download, currentFilesDownloads.keySet());
        startNextTask();
    }

    private void init() {
        availabilityInfoConsumerFile = new AvailabilityInfoConsumer(
                true, connectivityService);
        availabilityInfoConsumerPatch = new AvailabilityInfoConsumer(
                false, connectivityService);

        availabilityInfoSupplierFile = new AvailabilityInfoSupplierFile(
                this, dataBaseService, connectivityService,
                Objects.requireNonNull(handlerThread));
        availabilityInfoSupplierPatch = new AvailabilityInfoSupplierPatch(
                this, dataBaseService, connectivityService,
                handlerThread);
        dataSupplier = new DataSupplier(
                connectivityService, dataBaseService, fileTool, handlerThread);

        connectivityService.incomingNodeConnected = nodeId -> handler.post(
                () -> onIncomingNodeConnected(nodeId));
        connectivityService.incomingNodeDisconnected = nodeId -> handler.post(
                () -> onIncomingNodeDisconnected(nodeId));
        connectivityService.outgoingNodeConnected = nodeId -> handler.post(
                () -> onOutgoingNodeConnected(nodeId));
        connectivityService.outgoingNodeDisconnected = nodeId -> handler.post(
                () -> onOutgoingNodeDisconnected(nodeId));
        connectivityService.messageReceived = (message, nodeId) -> handler.post(
                () -> onDataReceived(message, nodeId));

        IntentFilter filter = new IntentFilter(Const.REMOVE_FAILED_INTENT);
        LocalBroadcastManager.getInstance(context).registerReceiver(this, filter);

        if (!shareMode) subscribe();
    }

    private void onIncomingNodeConnected(String nodeId) {
        Log.i(TAG, String.format("onIncomingNodeConnected: %s", nodeId));
    }

    private void onIncomingNodeDisconnected(String nodeId) {
        Log.i(TAG, String.format("onIncomingNodeDisconnected: %s", nodeId));
        Objects.requireNonNull(availabilityInfoSupplierFile).onNodeDisconnected(nodeId);
        Objects.requireNonNull(availabilityInfoSupplierPatch).onNodeDisconnected(nodeId);
        Objects.requireNonNull(dataSupplier).onNodeDisconnected(nodeId);
    }

    private void onOutgoingNodeConnected(String nodeId) {
        Log.i(TAG, String.format("onOutgoingNodeConnected: %s", nodeId));
        availabilityInfoConsumerFile.onNodeConnected(nodeId);
        availabilityInfoConsumerPatch.onNodeConnected(nodeId);
    }

    private void onOutgoingNodeDisconnected(String nodeId) {
        Log.i(TAG, String.format("onOutgoingNodeDisconnected: %s", nodeId));
        for (DownloadTask task : new ArrayList<>(currentFilesDownloads.values())) {
            task.onNodeDisconnected(nodeId);
        }
    }

    private void onDataReceived(byte[] data, String nodeId) {
        Log.i(TAG, String.format("onDataReceived: onDataReceived fron node: %s", nodeId));
        try {
            Proto.Message message = Proto.Message.parseFrom(data);
            onMessageReceived(message, nodeId);
            return;
        } catch (InvalidProtocolBufferException e) {
            try {
                Proto.Messages messages = Proto.Messages.parseFrom(data);
                onMessagesReceived(messages, nodeId);
                return;
            } catch (InvalidProtocolBufferException e1) {
                Log.e(TAG, "onDataReceived: exception", e1);
            }
        }
        Log.e(TAG, "onDataReceived: cannot parse received data");
    }

    private void onMessagesReceived(@NonNull Proto.Messages messages, String nodeId) {
        Log.i(TAG, String.format("onMessagesReceived: %s", nodeId));
        if (messages.getMsgCount() == 0) return;
        Proto.Message msg = messages.getMsg(0);
        switch (msg.getMtype()) {
            case AVAILABILITY_INFO_REQUEST:
                if (msg.getObjType() == Proto.Message.ObjectType.FILE) {
                    Objects.requireNonNull(availabilityInfoSupplierFile).onRequests(messages, nodeId);
                } else {
                    Objects.requireNonNull(availabilityInfoSupplierPatch).onRequests(messages, nodeId);
                }
                break;
            case AVAILABILITY_INFO_RESPONSE:
                for (Proto.Message message : messages.getMsgList()) {
                    Set<DownloadTask> tasks = objIdsDownloadTasks.get(message.getObjId());
                    if (tasks != null) {
                        for (DownloadTask task : tasks) {
                            task.onAvailabilityInfoReceived(message.getInfoList(), nodeId);
                        }
                    }
                }
                break;
            default:
                Log.i(TAG, "onMessagesReceived: Cant parse as Messages, try parse as Message");
                onMessageReceived(msg, nodeId);
                break;
        }
    }

    private void onMessageReceived(@NonNull Proto.Message message, String nodeId) {
        Log.i(TAG, String.format("onMessageReceived: %s", nodeId));
        switch (message.getMtype()) {
            case AVAILABILITY_INFO_REQUEST:
                if (message.getObjType() == Proto.Message.ObjectType.FILE) {
                    handler.postDelayed(() -> Objects.requireNonNull(availabilityInfoSupplierFile).onRequest(message, nodeId), 100);
                } else {
                    handler.postDelayed(() -> Objects.requireNonNull(availabilityInfoSupplierPatch).onRequest(message, nodeId), 100);
                }
                break;
            case AVAILABILITY_INFO_ABORT:
                if (message.getObjType() == Proto.Message.ObjectType.FILE) {
                    Objects.requireNonNull(availabilityInfoSupplierFile).onAbort(message, nodeId);
                } else {
                    Objects.requireNonNull(availabilityInfoSupplierPatch).onAbort(message, nodeId);
                }
                break;
            case DATA_REQUEST:
                Objects.requireNonNull(dataSupplier).onRequest(message, nodeId);
                break;
            case DATA_ABORT:
                Objects.requireNonNull(dataSupplier).onAbort(message, nodeId);
                break;
            case AVAILABILITY_INFO_RESPONSE: {
                Set<DownloadTask> tasks = objIdsDownloadTasks.get(message.getObjId());
                if (tasks != null) {
                    for (DownloadTask task : tasks) {
                        task.onAvailabilityInfoReceived(message.getInfoList(), nodeId);
                    }
                }
                break;
            }
            case DATA_RESPONSE: {
                Set<DownloadTask> tasks = objIdsDownloadTasks.get(message.getObjId());
                if (tasks != null) {
                    for (DownloadTask task : tasks) {
                        task.onDataReceived(
                                message.getInfo(0), message.getData().toByteArray(), nodeId);
                    }
                }
                break;
            }
            case DATA_FAILURE: {
                Set<DownloadTask> tasks = objIdsDownloadTasks.get(message.getObjId());
                if (tasks != null) {
                    for (DownloadTask task : tasks) {
                        task.onNodeDisconnected(nodeId);
                    }
                }
                break;
            }
            case AVAILABILITY_INFO_FAILURE: {
                Set<DownloadTask> tasks = objIdsDownloadTasks.remove(message.getObjId());
                if (tasks != null) {
                    for (DownloadTask task : tasks) {
                        task.cancel();
                        if (task == currentDownloadTask) {
                            currentDownloadTask = null;
                            handler.post(this::startNextTask);
                        }
                        currentFilesDownloads.remove(task.fileUuid);
                        readyDownloadsQueue.remove(task);
                    }
                }
                scheduleCheck();
                break;
            }
            default:
                Log.e(TAG, "onMessageReceived: UNKNOWN MESSAGE");
                break;
        }
    }

    public void onDestroy() {
        if (checkDownloadsRunnable != null) {
            handler.removeCallbacks(checkDownloadsRunnable);
            checkDownloadsRunnable = null;
        }
        if (checkReadyAndResubscribeRunnable != null) {
            handler.removeCallbacks(checkReadyAndResubscribeRunnable);
            checkReadyAndResubscribeRunnable = null;
        }
        handler.removeCallbacksAndMessages(null);
        handler.post(() -> {
            LocalBroadcastManager.getInstance(context).unregisterReceiver(this);
            try {
                lock.lock();
                unsubscribe();
                cancelAll();
            } finally {
                lock.unlock();
            }
            handler.removeCallbacksAndMessages(null);
            Objects.requireNonNull(dataSupplier).onDestroy();
            Objects.requireNonNull(availabilityInfoSupplierFile).onDestroy();
            Objects.requireNonNull(availabilityInfoSupplierPatch).onDestroy();
            availabilityInfoConsumerFile.onDestroy();
            availabilityInfoConsumerPatch.onDestroy();
        });
        Objects.requireNonNull(handlerThread).quitSafely();
        handlerThread = null;
    }

    private void subscribe() {
        realm = Realm.getDefaultInstance();
        files = realm.where(FileRealm.class)
                .equalTo("isProcessing", false)
                .equalTo("isDownload", true)
                .or()
                .isNotNull("downloadPath")
                .findAll();
        files.addChangeListener(this);
    }

    private void unsubscribe() {
        if (files != null) files.removeChangeListener(this);
        if (realm != null) realm.close();
        files = null;
        realm = null;
    }

    @Override
    public void onChange(@NotNull RealmResults<FileRealm> files) {
        if (!initialSyncDone) return;
        scheduleCheck();
    }

    private void scheduleCheck() {
        Log.i(TAG, "scheduleCheck");
        if (checkDownloadsRunnable != null) return;
        checkDownloadsRunnable = () -> {
            checkDownloadsRunnable = null;
            checkDownloads();
        };
        handler.post(checkDownloadsRunnable);
    }

    private void checkDownloads() {
        if (shareMode) return;

        Log.i(TAG, "checkDownloads");

        HashMap<String, FileRealm> newFilesDownloads = new HashMap<>();
        for (FileRealm file : Objects.requireNonNull(files)) {
            String eventUuid = file.getEventUuid();
            if (eventUuid == null) continue;
            newFilesDownloads.put(file.getUuid(), file);
        }
        HashSet<String> removedFilesDownloads = new HashSet<>(currentFilesDownloads.keySet());
        removedFilesDownloads.removeAll(newFilesDownloads.keySet());

        HashMap<String, FileRealm> addedFilesDownloads = new HashMap<>(newFilesDownloads);
        addedFilesDownloads.keySet().removeAll(currentFilesDownloads.keySet());

        HashMap<String, FileRealm> checkFileDownloads = new HashMap<>(newFilesDownloads);
        checkFileDownloads.keySet().retainAll(currentFilesDownloads.keySet());

        cancelDownloads(removedFilesDownloads);
        startDownloads(addedFilesDownloads);
        updateDownloads(checkFileDownloads);
        dataBaseService.updateOwnDeviceDownloads(
                currentDownloadTask == null ? null : currentDownloadTask.name);
    }

    private void cancelDownloads(@NonNull HashSet<String> removedFilesDownloads) {
        Log.i(TAG, String.format("cancelDownloads: %s", removedFilesDownloads.toString()));
        HashSet<String> toUnsubscribe = new HashSet<>();
        for (String fileUuid : removedFilesDownloads) {
            DownloadTask task = currentFilesDownloads.remove(fileUuid);
            if (task == null) continue;
            String objectId = task.objectId;
            task.cancel();
            readyDownloadsQueue.remove(task);
            if (task == currentDownloadTask) {
                currentDownloadTask = null;
            }
            Set<DownloadTask> tasks = objIdsDownloadTasks.remove(objectId);
            if (tasks == null) continue;
            tasks.remove(task);
            if (tasks.isEmpty()) {
                toUnsubscribe.add(objectId);
            } else {
                objIdsDownloadTasks.put(objectId, tasks);
            }
        }
        availabilityInfoConsumerFile.unsubscribe(toUnsubscribe);
        availabilityInfoConsumerPatch.unsubscribe(toUnsubscribe);
        handler.post(this::startNextTask);
    }

    private void updateDownloads(@NonNull HashMap<String, FileRealm> checkDownloads) {
        Log.i(TAG, String.format("updateDownloads: %s", checkDownloads.keySet().toString()));
        HashSet<String> toSubscribePatches = new HashSet<>();
        HashSet<String> toSubscribeFiles = new HashSet<>();
        for (FileRealm file : checkDownloads.values()) {
            if (!file.isValid()) continue;
            DownloadTask task = currentFilesDownloads.get(file.getUuid());
            if (task != null && !task.started && (
                    !task.eventUuid.equals(file.getEventUuid()) || file.getCameraPath() != null)) {
                task.cancel();
                readyDownloadsQueue.remove(task);
                if (task == currentDownloadTask) {
                    currentDownloadTask = null;
                }
                EventRealm lastEvent = Objects.requireNonNull(realm).where(EventRealm.class)
                        .equalTo("uuid", file.getEventUuid())
                        .findFirst();
                assert lastEvent != null;
                task = updateExistingFile(file, lastEvent);
                if (task == null) continue;
                currentFilesDownloads.put(file.getUuid(), task);
                Set<DownloadTask> objIdDownloadTasks = objIdsDownloadTasks.get(task.objectId);
                if (objIdDownloadTasks == null) {
                    objIdDownloadTasks = new HashSet<>();
                }
                objIdDownloadTasks.add(task);
                objIdsDownloadTasks.put(task.objectId, objIdDownloadTasks);
                if (task.type == DownloadTask.Type.Patch) {
                    toSubscribePatches.add(task.objectId);
                } else {
                    toSubscribeFiles.add(task.objectId);
                }
            } else if (task != null) {
                task.check();
            }
        }
        if (!toSubscribeFiles.isEmpty()) {
            availabilityInfoConsumerFile.subscribe(toSubscribeFiles);
        }
        if (!toSubscribePatches.isEmpty()) {
            availabilityInfoConsumerPatch.subscribe(toSubscribePatches);
        }
        handler.post(this::startNextTask);
        if (checkReadyAndResubscribeRunnable == null) {
            checkReadyAndResubscribe(true);
        }
    }

    private void startDownloads(@NonNull HashMap<String, FileRealm> addedFilesDownloads) {
        Log.i(TAG, String.format("startDownloads: %s", addedFilesDownloads.keySet().toString()));
        HashSet<String> toSubscribePatches = new HashSet<>();
        HashSet<String> toSubscribeFiles = new HashSet<>();
        HashSet<String> toUpdateStatus = new HashSet<>();
        for (FileRealm file : addedFilesDownloads.values()) {
            if (!file.isValid()) continue;
            DownloadTask task;
            EventRealm lastEvent = Objects.requireNonNull(realm).where(EventRealm.class)
                    .equalTo("uuid", file.getEventUuid())
                    .findFirst();
            if (lastEvent != null) {
                lastEvent = realm.copyFromRealm(lastEvent);
            } else {
                continue;
            }
            assert lastEvent.getHashsum() != null;

            if (file.getHashsum() != null
                    && file.getCameraPath() == null) {
                task = updateExistingFile(file, lastEvent);
            } else {
                task = downloadFile(file, lastEvent);
            }
            if (task == null) continue;
            currentFilesDownloads.put(file.getUuid(), task);
            toUpdateStatus.add(file.getUuid());
            Set<DownloadTask> objIdDownloadTasks = objIdsDownloadTasks.get(task.objectId);
            if (objIdDownloadTasks == null) {
                objIdDownloadTasks = new HashSet<>();
            }
            objIdDownloadTasks.add(task);
            objIdsDownloadTasks.put(task.objectId, objIdDownloadTasks);
            if (task.type == DownloadTask.Type.Patch) {
                toSubscribePatches.add(task.objectId);
            } else {
                toSubscribeFiles.add(task.objectId);
            }
        }
        if (!toUpdateStatus.isEmpty()) {
            dataBaseService.setDownloadsStatus(
                    paused ? R.string.paused_status : R.string.starting_download,
                    toUpdateStatus);
        }
        if (!toSubscribeFiles.isEmpty()) {
            availabilityInfoConsumerFile.subscribe(toSubscribeFiles);
        }
        if (!toSubscribePatches.isEmpty()) {
            availabilityInfoConsumerPatch.subscribe(toSubscribePatches);
        }
        if (checkReadyAndResubscribeRunnable == null) {
            checkReadyAndResubscribe(true);
        }
    }

    public void checkReadyAndResubscribe(boolean delayed) {
        checkReadyAndResubscribeRunnable = () -> {
            checkReadyAndResubscribeRunnable = null;
            if (readyDownloadsQueue.size() + (currentDownloadTask == null ? 0 : 1) <
                    currentFilesDownloads.size()) {
                Log.i(TAG, "checkReadyAndResubscribe");
                if (shareMode) {
                    HashSet<String> toSubscribe = new HashSet<>();
                    for (DownloadTask task : currentFilesDownloads.values()) {
                        toSubscribe.add(task.objectId);
                    }
                    availabilityInfoConsumerFile.subscribe(toSubscribe);
                } else {
                    if (currentDownloadTask == null && !paused) {
                        Log.i(TAG, String.format(
                                "checkReadyAndResubscribe, set waiting for nodes for %s",
                                currentFilesDownloads.keySet().toString()));
                        dataBaseService.setDownloadsStatus(
                                R.string.waiting_for_nodes,
                                currentFilesDownloads.keySet());
                    }
                    availabilityInfoConsumerPatch.resubscribe();
                    availabilityInfoConsumerFile.resubscribe();
                }
            }
        };
        if (delayed) {
            handler.postDelayed(checkReadyAndResubscribeRunnable, 10_000);
        } else {
            handler.post(checkReadyAndResubscribeRunnable);
        }
    }

    @Nullable
    private DownloadTask updateExistingFile(@NonNull FileRealm file, @NonNull EventRealm lastEvent) {
        String fileUuid = file.getUuid();
        Log.i(TAG, String.format("updateExistingFile %s, name: %s", fileUuid, file.getName()));

        if (Objects.equals(lastEvent.getHashsum(), file.getHashsum())) {
            Log.i(TAG, String.format(
                    "updateExistingFile: %s, file hash equals event hash", fileUuid));
            dataBaseService.downloadCompleted(
                    file.getUuid(), lastEvent.getUuid(), lastEvent.getHashsum());
            return null;
        }
        if (FileTool.isExist(FileTool.buildPathForCopyNamedHash(lastEvent.getHashsum()))) {
            Log.i(TAG, String.format("updateExistingFile: %s, last event copy exists", fileUuid));
            return downloadFile(file, lastEvent);
        }
        EventRealm handledEvent = Objects.requireNonNull(realm).where(EventRealm.class)
                .equalTo("fileUuid", fileUuid)
                .equalTo("hashsum", file.getHashsum())
                .sort("id", Sort.DESCENDING)
                .findFirst();
        if (handledEvent == null) {
            Log.w(TAG, String.format("updateExistingFile: %s, handledEvent not found", fileUuid));
            return downloadFile(file, lastEvent);
        }
        EventRealm notHandledEvent = realm.where(EventRealm.class)
                .equalTo("fileUuid", fileUuid)
                .isNotNull("diffFileUuid")
                .greaterThan("id", handledEvent.getId())
                .findFirst();
        if (notHandledEvent == null) {
            Log.w(TAG, String.format(
                    "updateExistingFile: %s, notHandledEvent event not found", fileUuid));
            return downloadFile(file, lastEvent);
        }
        if (!FileTool.isExist(FileTool.buildPatchPath(notHandledEvent.getDiffFileUuid()))) {
            Number patchSizesSum = realm.where(EventRealm.class)
                    .equalTo("fileUuid", fileUuid)
                    .isNotNull("diffFileUuid")
                    .greaterThan("id", handledEvent.getId())
                    .sum("diffFileSize");
            if (patchSizesSum != null && patchSizesSum.longValue() >= file.getSize()) {
                Log.i(TAG, String.format(
                        "updateExistingFile: %s, file size < patches size", fileUuid));
                return downloadFile(file, lastEvent);
            }
            if (notHandledEvent.getDiffFileSize() == 0) {
                long now = new Date().getTime();
                if (notHandledEvent.getTimestamp() + 1000 * 60 > now) {
                    Log.i(TAG, String.format(
                            "updateExistingFile: %s, patch size unknown, " +
                                    "event timestamp: %d, now: %d, wait",
                            fileUuid, notHandledEvent.getTimestamp(), now));
                    handler.postDelayed(this::scheduleCheck, 5000);
                    return null;
                } else {
                    Log.i(TAG, String.format(
                            "updateExistingFile: %s, patch size unknown, download file", fileUuid));
                    return downloadFile(file, lastEvent);
                }
            }
        }
        return downloadPatch(file, notHandledEvent);
    }

    @Nullable
    private DownloadTask downloadFile(@NonNull FileRealm file, @NonNull EventRealm lastEvent) {
        Log.i(TAG, String.format("downloadFile %s, name: %s", file.getUuid(), file.getName()));
        if (file.getSize() == 0) {
            Log.i(TAG, String.format("downloadFile: %s empty file", file.getUuid()));
            dataBaseService.downloadCompleted(file.getUuid(), lastEvent.getUuid(), lastEvent.getHashsum());
            return null;
        }
        String copyPath = FileTool.buildPathForCopyNamedHash(lastEvent.getHashsum());
        if (FileTool.isExist(copyPath)) {
            Log.i(TAG, String.format("downloadFile: %s copy exists", file.getUuid()));
            dataBaseService.downloadCompleted(file.getUuid(), lastEvent.getUuid(), lastEvent.getHashsum());
            return null;
        }
        if (file.getCameraPath() != null) {
            if (copyFromCamera(file, lastEvent)) {
                return null;
            } else {
                dataBaseService.dropCameraPath(file.getCameraPath());
            }
        }
        dataBaseService.setDownloadedSize(file.getUuid(), 0);

        return addFileDownload(
                file.getUuid(), file.getEventUuid(), file.getName(), lastEvent.getSize(),
                lastEvent.getHashsum(), FileTool.buildPathForCopyNamedHash(lastEvent.getHashsum()),
                task -> dataBaseService.downloadCompleted(task.fileUuid, task.objectId, task.hash),
                this::onDownloadTaskProgress);

    }

    @Nullable
    public DownloadTask addFileDownload(
            String uuid, String eventUuid, String name, long size, String hashsum,
            String downloadPath, @NonNull DownloadTask.Callback onCompleted,
            DownloadTask.Callback onProgress) {
        Log.i(TAG, String.format("addFileDownload: %s", uuid));
        DownloadTask downloadTask = new DownloadTask(
                DownloadTask.Type.File, 1000, uuid, eventUuid, eventUuid,
                name, size, hashsum, downloadPath,
                context, connectivityService, Objects.requireNonNull(handlerThread), fileTool,
                task -> handler.post(() -> onDownloadTaskReady(task)),
                task -> handler.post(() -> {
                    onCompleted.call(task);
                    onDownloadTaskCompleted(task);
                }),
                task -> handler.post(() -> onDownloadTaskNotReady(task)),
                onProgress,
                this::onDownloadTaskFinishing,
                (type, objId, offset, length) -> handler.post(() -> onPartDownloaded(
                        type, objId, offset, length)));
        if (shareMode) {
            currentFilesDownloads.put(uuid, downloadTask);
            Set<DownloadTask> tasks = objIdsDownloadTasks.get(eventUuid);
            if (tasks == null) {
                tasks = new HashSet<>();
            }
            tasks.add(downloadTask);
            objIdsDownloadTasks.put(eventUuid, tasks);
        }
        return downloadTask;
    }

    private void onPartDownloaded(@NonNull DownloadTask.Type type, String objId, long offset, long length) {
        if (shareMode) return;
        Log.d(TAG, String.format("onPartDownloaded %s", objId));
        switch (type) {
            case File:
                Objects.requireNonNull(availabilityInfoSupplierFile).onNewAvailabilityInfo(objId, offset, length);
                break;
            case Patch:
                Objects.requireNonNull(availabilityInfoSupplierPatch).onNewAvailabilityInfo(objId, offset, length);
                break;
        }
    }

    @VisibleForTesting
    public void onDownloadTaskReady(@NonNull DownloadTask task) {
        Log.d(TAG, String.format(
                "onDownloadTaskReady %s, objId: %s", task.fileUuid, task.objectId));
        if (!currentFilesDownloads.containsKey(task.fileUuid)) { return; }
        if (readyDownloadsQueue.contains(task)) { return; }
        readyDownloadsQueue.add(task);
        if (currentDownloadTask == null) {
            handler.post(this::startNextTask);
        }
    }

    @VisibleForTesting
    public void onDownloadTaskCompleted(@NonNull DownloadTask task) {
        Log.d(TAG, String.format(
                "onDownloadTaskCompleted %s, objId: %s", task.fileUuid, task.objectId));
        availabilityInfoConsumerFile.unsubscribe(task.objectId);

        if (Objects.equals(currentFilesDownloads.get(task.fileUuid), task)) {
            currentFilesDownloads.remove(task.fileUuid);
        }
        Set<DownloadTask> tasks = objIdsDownloadTasks.remove(task.objectId);
        if (tasks != null) {
            tasks.remove(task);
            if (!tasks.isEmpty()) {
                objIdsDownloadTasks.put(task.objectId, tasks);
            }
        }

        task.cancel();
        readyDownloadsQueue.remove(task);
        if (task.equals(currentDownloadTask)) {
            currentDownloadTask = null;
            handler.post(this::startNextTask);
        }
        scheduleCheck();
    }

    @VisibleForTesting
    public void onDownloadTaskNotReady(@NonNull DownloadTask task) {
        Log.d(TAG, String.format(
                "onDownloadTaskNotReady %s, objId: %s", task.fileUuid, task.objectId));
        readyDownloadsQueue.remove(task);
        if (currentDownloadTask != null &&
                Objects.equals(currentDownloadTask.objectId, task.objectId)) {
            currentDownloadTask = null;
            handler.post(this::startNextTask);
        }
    }

    private void onDownloadTaskProgress(@NonNull DownloadTask task) {
        dataBaseService.setDownloadedSize(task.fileUuid, task.received);
    }

    private void onDownloadTaskFinishing(@NonNull DownloadTask task) {
        if (shareMode) return;
        dataBaseService.setDownloadStatusAndDownloadedSize(R.string.finishing_download, task.size, task.fileUuid);
    }

    @VisibleForTesting
    public void startNextTask() {
        if (currentDownloadTask != null || paused) return;
        DownloadTask task = readyDownloadsQueue.poll();
        if (!shareMode) {
            dataBaseService.updateOwnDeviceDownloads(
                    task == null ? null : task.name);
        }
        if (task == null) {
            if (!currentFilesDownloads.isEmpty() && !shareMode) {
                Log.i(TAG, String.format(
                        "startNextTask: set waiting for nodes: %s",
                        currentFilesDownloads.keySet().toString()));
                dataBaseService.setDownloadsStatus(
                        R.string.waiting_for_nodes,
                        currentFilesDownloads.keySet());
            }
            if (checkReadyAndResubscribeRunnable == null) {
                checkReadyAndResubscribe(true);
            }
            return;
        }
        currentDownloadTask = task;
        Log.i(TAG, String.format("startNextTask %s, objId: %s", task.fileUuid, task.objectId));
        if (!task.start()) {
            if (task.equals(currentDownloadTask)) {
                currentDownloadTask = null;
                handler.post(this::startNextTask);
            }
            return;
        }
        if (!shareMode) {
            dataBaseService.setDownloadStatus(
                    R.string.downloading, task.fileUuid);
        }
        if (currentFilesDownloads.size() > 1 && !shareMode) {
            HashSet<String> tasks = new HashSet<>(currentFilesDownloads.keySet());
            tasks.remove(task.fileUuid);
            dataBaseService.setDownloadsStatus(
                    R.string.waiting_other_downloads,
                    tasks);
        }
    }

    private boolean copyFromCamera(@NonNull FileRealm file, @NonNull EventRealm lastEvent) {
        if (!FileTool.isExist(file.getCameraPath())) return false;
        Log.i(TAG, String.format("copyFromCamera: %s", file.getUuid()));
        String copyPath = FileTool.buildPathForCopyNamedHash(lastEvent.getHashsum());
        String tempFile = copyPath + ".download";
        if (fileTool.copy(file.getCameraPath(), tempFile, false)) {
            if (lastEvent.getHashsum().equals(PatchTool.getFileHash(tempFile))) {
                fileTool.move(tempFile, copyPath, false);
                dataBaseService.downloadCompleted(
                        file.getUuid(), lastEvent.getUuid(), lastEvent.getHashsum());
                return true;
            }
        }
        return false;
    }

    @Nullable
    private DownloadTask downloadPatch(@NonNull FileRealm file, @NonNull EventRealm event) {
        Log.i(TAG, String.format(
                "downloadPatch %s, diffUuid: %s", file.getUuid(), event.getDiffFileUuid()));
        String newCopyPath = FileTool.buildPathForCopyNamedHash(event.getHashsum());
        String oldCopyPath = FileTool.buildPathForCopyNamedHash(file.getHashsum());
        if (FileTool.isExist(newCopyPath)) {
            dataBaseService.downloadCompleted(file.getUuid(), event.getUuid(), event.getHashsum());
            return null;
        }

        String patchPath = FileTool.buildPatchPath(event.getDiffFileUuid());
        if (FileTool.isExist(patchPath)) {
            onPatchDownloaded(
                    patchPath, oldCopyPath, newCopyPath, event.getFileUuid(),
                    event.getUuid(), file.getHashsum(), event.getHashsum());
            return null;
        }

        dataBaseService.setDownloadedSize(file.getUuid(), 0);
        return new DownloadTask(
                DownloadTask.Type.Patch, 500,
                file.getUuid(), event.getDiffFileUuid(), event.getUuid(),
                file.getName(), event.getDiffFileSize(), null, patchPath,
                context, connectivityService, Objects.requireNonNull(handlerThread), fileTool,
                task -> handler.post(() -> onDownloadTaskReady(task)),
                task -> handler.post(() -> {
                    availabilityInfoConsumerPatch.unsubscribe(task.objectId);
                    onPatchDownloaded(
                            patchPath, oldCopyPath, newCopyPath, task.fileUuid,
                            event.getUuid(), file.getHashsum(), event.getHashsum());
                    if (Objects.equals(currentFilesDownloads.get(task.fileUuid), task)) {
                        currentFilesDownloads.remove(task.fileUuid);
                    }
                    Set<DownloadTask> tasks = objIdsDownloadTasks.remove(task.objectId);
                    if (tasks != null) {
                        tasks.remove(task);
                        if (!tasks.isEmpty()) {
                            objIdsDownloadTasks.put(task.objectId, tasks);
                        }
                    }
                    task.cancel();
                    readyDownloadsQueue.remove(task);
                    if (task.equals(currentDownloadTask)) {
                        currentDownloadTask = null;
                        startNextTask();
                        scheduleCheck();
                    }
                }),
                task -> handler.post(() -> onDownloadTaskNotReady(task)),
                this::onDownloadTaskProgress,
                this::onDownloadTaskFinishing,
                (type, objId, offset, length) -> handler.post(() -> onPartDownloaded(
                        type, objId, offset, length)));
    }

    private void onPatchDownloaded(
            @NonNull String patchPath, @NonNull String oldCopyPath, @NonNull String newCopyPath, String fileUuid,
            String eventUuid, String oldHashsum, String newHashsum) {
        Log.i(TAG, String.format("onPatchDownloaded: %s", fileUuid));
        try {
            patchTool.acceptPath(oldCopyPath, newCopyPath, patchPath, oldHashsum);
        } catch (Exception e) {
            Log.e(TAG,
                    String.format("onPatchDownloaded %s, failed to accept patch", fileUuid),
                    e);
            // todo download file
            fileTool.delete(patchPath);
            scheduleCheck();
            return;
        }
        dataBaseService.downloadCompleted(fileUuid, eventUuid, newHashsum);
    }

    @Nullable
    public SortedMap<Long, Long> getAlreadyDownloadedBlocks(String objId) {
        Set<DownloadTask> tasks = objIdsDownloadTasks.get(objId);
        if (tasks == null || tasks.isEmpty()) return null;
        return tasks.iterator().next().getDownloadedChunks();
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        handler.post(() -> {

        });
    }
}
