package net.pvtbox.android.service.signalserver;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.util.Log;

import net.pvtbox.android.R;
import net.pvtbox.android.application.Const;
import net.pvtbox.android.service.PreferenceService;
import net.pvtbox.android.db.DataBaseService;
import net.pvtbox.android.db.model.FileRealm;
import net.pvtbox.android.service.OperationService;
import net.pvtbox.android.tools.FileTool;
import net.pvtbox.android.tools.JSON;
import net.pvtbox.android.tools.diskspace.DiskSpace;
import net.pvtbox.android.tools.diskspace.DiskSpaceTool;
import net.pvtbox.android.service.transport.Downloads.DownloadManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
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
public class ShareSignalServerService extends SignalServerClient {

    private final static String TAG = ShareSignalServerService.class.getSimpleName();

    private final FileTool fileTool;
    private String pathDownload;

    private final OperationService operationService;
    private DownloadManager downloadManager;
    @Nullable
    private String shareHash;

    @NonNull
    private final Set<String> shareTasks = new HashSet<>();
    private boolean shareProcessed = true;
    private boolean receivedShareInfo = false;
    @NonNull
    private final ReentrantLock shareLock = new ReentrantLock();

    @Nullable
    private static SignalServerClient instance = null;

    public ShareSignalServerService(@NonNull Context context,
                                    PreferenceService preferenceService,
                                    OperationService operationService,
                                    DataBaseService dataBaseService,
                                    FileTool fileTool) {
        super("share", context, preferenceService, dataBaseService);
        instance = this;
        this.operationService = operationService;
        this.fileTool = fileTool;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
    }

    public void setDownloadManager(DownloadManager downloadManager) {
        this.downloadManager = downloadManager;
    }

    @Nullable
    @Override
    protected String getUrl() {
        return buildUrlForDirectDownload(shareHash);
    }

    @Override
    protected void onAccessDenied() {
        shareHash = null;
        stop(true);
        dataBaseService.updateOwnDeviceDownloadingShare(false);
        Intent i = new Intent(Const.OPERATIONS_PROGRESS_INTENT);
        String message = context.getString(
                R.string.share_download_unavailable);
        i.putExtra(Const.OPERATIONS_PROGRESS_MESSAGE, message);
        i.putExtra(Const.OPERATIONS_PROGRESS_SHOW_AND_DISMISS, true);
        LocalBroadcastManager.getInstance(context).sendBroadcast(i);
    }

    public void downloadDirectLink(String hash, String pathDownload) {
        Log.i(TAG, String.format("downloadDirectLink: hash: %s, pathDownload: %s",
                hash, pathDownload));
        this.pathDownload = pathDownload;
        this.shareHash = hash;
        receivedShareInfo = false;
        dataBaseService.updateOwnDeviceDownloadingShare(true);
        start();
    }

    @Nullable
    private String buildUrlForDirectDownload(String hash) {
        if (serverUrl != null && !serverUrl.isEmpty()) {
            String url = "wss://" + serverUrl + "/ws/webshare/" + hash;
            Log.d(TAG, "url:" + url);
            return url;
        }
        return null;
    }

    @Override
    protected void handleShareInfo(JSONObject json) {
        if (shareHash == null || receivedShareInfo) {
            return;
        }
        JSONObject data = json.optJSONObject("data");
        if (data == null) return;
        receivedShareInfo = true;
        processShareInfo(data, pathDownload, true);
    }

    private void processShareInfo(@NonNull JSONObject data, String pathDownload, boolean root) {
        String name = JSON.optString(data, "name", "");
        String path = FileTool.buildPath(pathDownload, name);
        Log.i(TAG, String.format("processShareInfo: %s", path));
        if (root) {
            Log.i(TAG, String.format("processShareInfo: start processing root %s", path));
            try {
                shareLock.lock();
                if (shareHash == null) return;
                shareProcessed = false;
            } finally {
                shareLock.unlock();
            }
        }

        if (shareHash == null) return;

        JSONArray childs = data.optJSONArray("childs");
        if (childs == null) {
            Log.i(TAG, String.format("processShareInfo: schedule processing %s", path));
            try {
                shareLock.lock();
                if (shareHash == null) return;
                shareTasks.add(path);
                Intent i = new Intent(Const.OPERATIONS_PROGRESS_INTENT);
                String message = context.getString(
                        R.string.share_downloading, shareTasks.size());
                i.putExtra(Const.OPERATIONS_PROGRESS_MESSAGE, message);
                i.putExtra(Const.OPERATIONS_PROGRESS_SHOW_AND_DISMISS, false);
                i.putExtra(Const.OPERATIONS_PROGRESS_SHOW_SHARE_CANCEL, true);
                LocalBroadcastManager.getInstance(context).sendBroadcast(i);
            } finally {
                shareLock.unlock();
            }
            addShareToDownload(data, path);
        } else {
            Log.i(TAG, String.format("processShareInfo: create directory %s", path));
            if (shareHash == null) return;
            if (path.contains(Const.DEFAULT_PATH)) {
                registerFolder(path);
            }
            if (childs.length() > 0) {
                addChilds(childs, path);
            }
        }

        if (root) {
            downloadManager.checkReadyAndResubscribe(false);
            try {
                shareLock.lock();
                shareProcessed = true;
                if (shareTasks.isEmpty()) {
                    sendShareDownloaded();
                    shareHash = null;
                    handler.post(() -> stop(true));
                    dataBaseService.updateOwnDeviceDownloadingShare(false);
                    Intent i = new Intent(Const.OPERATIONS_PROGRESS_INTENT);
                    String message = context.getString(
                            R.string.share_downloaded_successfully);
                    i.putExtra(Const.OPERATIONS_PROGRESS_MESSAGE, message);
                    i.putExtra(Const.OPERATIONS_PROGRESS_SHOW_AND_DISMISS, true);
                    LocalBroadcastManager.getInstance(context).sendBroadcast(i);
                }
            } finally {
                shareLock.unlock();
            }
            Log.i(TAG, String.format("processShareInfo: processed root %s", path));
        }
    }

    private void addChilds(@NonNull JSONArray childs, String path) {
        Log.i(TAG, String.format("addChilds: %s", path));
        for (int i = 0; i < childs.length(); ++i) {
            JSONObject child = childs.optJSONObject(i);
            if (child == null) continue;
            processShareInfo(child, path, false);
        }
    }

    private void addShareToDownload(@NonNull JSONObject data, @NonNull String path) {
        if (shareHash == null) return;
        Log.i(TAG, String.format("addShareToDownload: %s", path));
        long fileSize = data.optLong("file_size",
                Integer.valueOf(Objects.requireNonNull(Objects.requireNonNull(JSON.optString(data, "file_size", "0")))));
        String eventUuid = JSON.optString(data, "event_uuid");
        String name = JSON.optString(data, "name");
        String hash = JSON.optString(data, "file_hash");
        String copyPath = FileTool.buildPathForCopyNamedHash(hash);

        if (FileTool.isExist(copyPath)) {
            Log.w(TAG, String.format("addShareToDownload: copy already exist %s", path));
            onComplete(path, fileSize, name, copyPath);
            return;
        }

        if (fileSize == 0) {
            Log.i(TAG, String.format("addShareToDownload: file is empty %s", path));
            fileTool.createEmptyFile(copyPath);
            onComplete(path, fileSize, name, copyPath);
            return;
        }

        downloadManager.addFileDownload(
                UUID.randomUUID().toString(), eventUuid, name, fileSize, hash, copyPath,
                task -> processingHandler.post(() -> onComplete(path, fileSize, name, copyPath)),
                task -> processingHandler.post(() -> sendProgress(task.name, task.received, task.size)));
    }

    private void onCanceled(String path) {
        try {
            Log.i(TAG, String.format("onCanceled: %s", path));
            shareLock.lock();
            shareTasks.remove(path);
            if (shareTasks.isEmpty() && shareProcessed) {
                shareHash = null;
                stop(true);
                dataBaseService.updateOwnDeviceDownloadingShare(false);
            }
            Intent i = new Intent(Const.OPERATIONS_PROGRESS_INTENT);
            String message = context.getString(
                    R.string.share_download_cancelled);
            i.putExtra(Const.OPERATIONS_PROGRESS_MESSAGE, message);
            i.putExtra(Const.OPERATIONS_PROGRESS_SHOW_AND_DISMISS, true);
            LocalBroadcastManager.getInstance(context).sendBroadcast(i);
        } finally {
            shareLock.unlock();
        }
    }

    private void onComplete(@NonNull String path, long fileSize, String name, @NonNull String copyPath) {
        Log.i(TAG, String.format("onComplete: %s", path));
        if (!path.contains(Const.DEFAULT_PATH) && !fileTool.copy(copyPath, path, true)) {
            Log.e(TAG, String.format("onComplete: failed to copy file %s", path));
            onCanceled(path);
            return;
        }

        if (path.contains(Const.DEFAULT_PATH) && dataBaseService.getFileByPath(path) == null) {
            Log.i(TAG, String.format("onComplete: register file %s", path));
            registerFile(path, fileSize, name, copyPath);
        }
        try {
            shareLock.lock();
            shareTasks.remove(path);
            if (shareTasks.isEmpty() && shareProcessed) {
                sendShareDownloaded();
                shareHash = null;
                handler.post(() -> stop(true));
                dataBaseService.updateOwnDeviceDownloadingShare(false);
                Intent i = new Intent(Const.OPERATIONS_PROGRESS_INTENT);
                String message = context.getString(
                        R.string.share_downloaded_successfully);
                i.putExtra(Const.OPERATIONS_PROGRESS_MESSAGE, message);
                i.putExtra(Const.OPERATIONS_PROGRESS_SHOW_AND_DISMISS, true);
                LocalBroadcastManager.getInstance(context).sendBroadcast(i);
            } else {
                try {
                    shareLock.lock();
                    if (shareTasks.isEmpty()) return;
                    Intent i = new Intent(Const.OPERATIONS_PROGRESS_INTENT);
                    String message = context.getString(
                            R.string.share_downloading, shareTasks.size());
                    i.putExtra(Const.OPERATIONS_PROGRESS_MESSAGE, message);
                    i.putExtra(Const.OPERATIONS_PROGRESS_SHOW_AND_DISMISS, false);
                    i.putExtra(Const.OPERATIONS_PROGRESS_SHOW_SHARE_CANCEL, true);
                    LocalBroadcastManager.getInstance(context).sendBroadcast(i);
                } finally {
                    shareLock.unlock();
                }
            }
        } finally {
            shareLock.unlock();
        }
    }

    private void sendShareDownloaded() {
        if (shareHash == null) return;
        String hash = shareHash.split("\\?")[0];
        JSONObject data;
        try {
            data = new JSONObject()
                    .putOpt("operation", "share_downloaded")
                    .putOpt("data", new JSONObject()
                            .putOpt("share_hash", hash));
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }

        sendString(data.toString());
    }

    private void registerFile(@NonNull String path, long fileSize, String name, @NonNull String copyPath) {
        FileRealm firstExistingParent = null;

        ArrayDeque<String> folderNames = new ArrayDeque<>();
        String parentPath = FileTool.getParentPath(path);
        while (!Const.DEFAULT_PATH.equals(parentPath)) {
            firstExistingParent = dataBaseService.getFileByPath(parentPath);
            if (firstExistingParent == null) {
                folderNames.addFirst(FileTool.getNameFromPath(parentPath));
                parentPath = FileTool.getParentPath(parentPath);
            } else {
                break;
            }
        }
        if (firstExistingParent != null && firstExistingParent.isProcessing()) {
            processingHandler.postDelayed(
                    () -> registerFile(path, fileSize, name, copyPath), 500);
            return;
        }

        OperationService.ErrorHandler cb = (error, uuid) -> processingHandler.post(
                () -> registerFile(path, fileSize, name, copyPath));
        if (folderNames.isEmpty()) {
            operationService.createFile(
                    firstExistingParent == null ? null : firstExistingParent.getUuid(),
                    name, copyPath, null, false, false,
                    (response, uuid) -> {
                    },
                    cb);
        } else {
            String folderName = folderNames.getFirst();
            operationService.createFolder(
                    firstExistingParent == null ? null : firstExistingParent.getUuid(),
                    folderName, false,
                    (response, uuid) -> processingHandler.post(
                            () -> registerFile(path, fileSize, name, copyPath)),
                    cb);
        }
    }

    private void registerFolder(@NonNull String path) {
        Log.d(TAG, String.format("registerFolder: %s", path));
        FileRealm file = dataBaseService.getFileByPath(path);
        if (file != null) return;

        FileRealm firstExistingParent = null;
        ArrayDeque<String> folderNames = new ArrayDeque<>();
        String parentPath = FileTool.getParentPath(path);
        while (!Const.DEFAULT_PATH.equals(parentPath)) {
            firstExistingParent = dataBaseService.getFileByPath(parentPath);
            if (firstExistingParent == null) {
                folderNames.addFirst(FileTool.getNameFromPath(parentPath));
                parentPath = FileTool.getParentPath(parentPath);
            } else {
                break;
            }
        }
        if (firstExistingParent != null && firstExistingParent.isProcessing()) {
            processingHandler.postDelayed(
                    () -> registerFolder(path), 500);
            return;
        }

        String folderName;
        OperationService.SuccessHandler successHandler = (response, uuid) ->
                processingHandler.post(() -> registerFolder(path));
        OperationService.ErrorHandler errorHandler = (response, uuid) ->
                processingHandler.post(() -> registerFolder(path));
        if (folderNames.isEmpty()) {
            folderName = FileTool.getNameFromPath(path);
            successHandler = (response, uuid) -> {};
            errorHandler = (response, uuid) -> {};
        } else {
            folderName = folderNames.getFirst();
        }
        operationService.createFolder(
                firstExistingParent == null ? null : firstExistingParent.getUuid(),
                folderName, false,
                successHandler,
                errorHandler);
    }

    private void cancelShareDownloads() {
        Log.i(TAG, "cancelShareDownloads");
        try {
            shareLock.lock();
            shareTasks.clear();
            downloadManager.cancelAll();
            shareHash = null;
            stop(true);
            dataBaseService.updateOwnDeviceDownloadingShare(false);
            Intent i = new Intent(Const.OPERATIONS_PROGRESS_INTENT);
            String message = context.getString(
                    R.string.share_download_cancelled);
            i.putExtra(Const.OPERATIONS_PROGRESS_MESSAGE, message);
            i.putExtra(Const.OPERATIONS_PROGRESS_SHOW_AND_DISMISS, true);
            LocalBroadcastManager.getInstance(context).sendBroadcast(i);
        } finally {
            shareLock.unlock();
        }
    }

    private void sendProgress(String name, long received, long size) {
        String message;
        Intent intent = new Intent(Const.OPERATIONS_PROGRESS_INTENT);

        DiskSpace diskSpace = DiskSpaceTool.getDiskQuantity(size);
        String sizeText = context.getString(
                R.string.folder_size,
                diskSpace.getFormatStringValue(),
                context.getString(diskSpace.getIdQuantity()));
        int progress = (int) ((float) received / (float) size * 100);
        message = context.getString(
                R.string.share_download_progress, name, progress, sizeText, shareTasks.size());
        intent.putExtra(Const.OPERATIONS_PROGRESS_SHOW_SHARE_CANCEL, true);

        intent.putExtra(Const.OPERATIONS_PROGRESS_MESSAGE, message);
        intent.putExtra(Const.OPERATIONS_PROGRESS_SHOW_AND_DISMISS, false);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }


    public static void CancelDownloads() {
        if (instance != null) {
            ((ShareSignalServerService) instance).cancelShareDownloads();
        }
    }
}
