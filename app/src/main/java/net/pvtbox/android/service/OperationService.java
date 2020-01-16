package net.pvtbox.android.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.text.TextUtils;
import android.util.Log;

import net.pvtbox.android.R;
import net.pvtbox.android.api.EventsHttpClient;
import net.pvtbox.android.api.ShareHttpClient;
import net.pvtbox.android.application.Const;
import net.pvtbox.android.db.DataBaseService;
import net.pvtbox.android.db.model.EventRealm;
import net.pvtbox.android.db.model.FileRealm;
import net.pvtbox.android.service.signalserver.SignalServerService;
import net.pvtbox.android.tools.FileTool;
import net.pvtbox.android.tools.JSON;
import net.pvtbox.android.tools.PatchTool;
import net.pvtbox.android.ui.files.Operation;

import org.apache.commons.io.FileUtils;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

import static net.pvtbox.android.tools.Hasher.md5;


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

public class OperationService extends BroadcastReceiver {
    private static final String TAG = OperationService.class.getSimpleName();
    private final Context context;

    @NonNull
    private static final ReentrantLock lock = new ReentrantLock();
    private static boolean isProcessing;
    private final HandlerThread handlerThread = new HandlerThread(
            "operationHandlerThread", HandlerThread.NORM_PRIORITY);
    @NonNull
    private final Handler handler;

    private final DataBaseService dataBaseService;
    private final FileTool fileTool;
    private final EventsHttpClient httpClient;
    private final ShareHttpClient shareHttpClient;
    private SignalServerService signalServerService;

    private int currentOperation;
    private long processed;
    private long total;

    public OperationService(Context context,
                            DataBaseService dataBaseService,
                            FileTool fileTool,
                            EventsHttpClient httpClient,
                            ShareHttpClient shareHttpClient) {
        this.context = context;
        this.dataBaseService = dataBaseService;
        this.fileTool = fileTool;
        this.httpClient = httpClient;
        this.shareHttpClient = shareHttpClient;
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
        handler.post(this::init);
    }

    public void setSignalServerService(SignalServerService signalServerService) {
        this.signalServerService = signalServerService;
    }

    public interface SuccessHandler {
        void call(JSONObject response, String uuid);
    }

    public interface ErrorHandler {
        void call(JSONObject error, String uuid);
    }

    private void init() {
        IntentFilter filter = new IntentFilter(Const.FILE_OPERATION_INTENT);
        LocalBroadcastManager.getInstance(context).registerReceiver(this, filter);
    }

    public void onDestroy() {
        operationDone();
        LocalBroadcastManager.getInstance(context).unregisterReceiver(this);
        handlerThread.quitSafely();
    }


    public static boolean isProcessing() {
        try {
            lock.lock();
            return OperationService.isProcessing;
        } finally {
            lock.unlock();
        }

    }

    private static void setProcessing(boolean isProcessing) {
        try {
            lock.lock();
            OperationService.isProcessing = isProcessing;
        } finally {
            lock.unlock();
        }
    }

    private void sendOperationProcessing(boolean finished, String error) {
        if (!finished && !isProcessing()) return;
        String message;
        if (finished) {
            message = context.getString(currentOperation, error);
        } else {
            message = context.getString(currentOperation,
                    processed < total ? processed + 1 : processed,
                    total);
        }

        Intent intent = new Intent(Const.OPERATIONS_PROGRESS_INTENT);
        intent.putExtra(Const.OPERATIONS_PROGRESS_MESSAGE, message);
        intent.putExtra(Const.OPERATIONS_PROGRESS_SHOW_AND_DISMISS, finished);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    @Override
    public void onReceive(Context context, @NonNull Intent intent) {
        Log.d(TAG, "onReceive");
        handler.post(() -> processOperation(intent));
    }

    private void processOperation(@NonNull Intent intent) {
        Log.d(TAG, "processOperation");
        if (isProcessing()) {
            handler.postDelayed(() -> processOperation(intent), 1000);
            return;
        }

        dataBaseService.updateOwnDeviceProcessingOperation(true);
        setProcessing(true);

        Log.i(TAG, String.format("processOperation extras: %s", intent.getExtras()));

        currentOperation = intent.getIntExtra(Const.FILE_OPERATION_TYPE, Operation.unknown);
        String parentUuid = intent.getStringExtra(Const.FILE_OPERATION_ROOT);
        ArrayList<String> uuids = intent.getStringArrayListExtra(Const.FILE_OPERATION_UUIDS);
        String uuid = intent.getStringExtra(Const.FILE_OPERATION_UUID);
        String newName = intent.getStringExtra(Const.FILE_OPERATION_NEW_NAME);
        String path = intent.getStringExtra(Const.FILE_OPERATION_PATH);
        String uriPath = intent.getStringExtra(Const.FILE_OPERATION_URI);
        Uri uri = null;
        if (uriPath != null) {
            uri = Uri.parse(uriPath);
        }
        ArrayList<Uri> uris = intent.getParcelableArrayListExtra(Const.FILE_OPERATION_URIS);
        boolean share = intent.getBooleanExtra(Const.SHARING_ENABLE, false);
        boolean deleteFile = intent.getBooleanExtra(Const.DELETE_FILE, false);

        processed = 0;
        total = uuids == null ? uris == null ? 1 : uris.size() : uuids.size();

        sendOperationProcessing(false, null);

        switch (currentOperation) {
            case Operation.add_offline_folder:
            case Operation.add_offline_file:
            case Operation.add_offline_multi:
                assert uuids != null;
                processAddOffline(uuids);
                break;
            case Operation.remove_offline_folder:
            case Operation.remove_offline_file:
            case Operation.remove_offline_multi:
                assert uuids != null;
                processRemoveOffline(uuids);
                break;
            case Operation.cancel_download:
            case Operation.cancel_downloads:
                assert uuids != null;
                processCancelDownloads(uuids);
                break;
            default:
                if (!checkOrWaitSignalServerConnected()) {
                    currentOperation = R.string.operation_error;
                    sendOperationProcessing(true, context.getString(R.string.network_error));
                    operationDone();
                }

                switch (currentOperation) {
                    case Operation.create_folder:
                        assert newName != null;
                        processFolderCreate(parentUuid, newName);
                        break;
                    case Operation.rename_folder:
                    case Operation.rename_file:
                        assert uuid != null;
                        assert newName != null;
                        processRename(uuid, newName);
                        break;
                    case Operation.delete_multi:
                    case Operation.delete_folder:
                    case Operation.delete_file:
                        assert uuids != null;
                        processDelete(uuids.iterator());
                        break;
                    case Operation.move_multi:
                    case Operation.move_folder:
                    case Operation.move_file:
                        assert uuids != null;
                        processMove(parentUuid, uuids.iterator());
                        break;
                    case Operation.copy_multi:
                    case Operation.copy_folder:
                    case Operation.copy_file:
                        assert uuids != null;
                        processCopy(parentUuid, uuids.iterator());
                        break;
                    case Operation.create_file:
                        if (path != null) {
                            processFileCreate(parentUuid, newName, path, share, deleteFile);
                        } else {
                            assert newName != null;
                            assert uri != null;
                            processFileCreate(parentUuid, newName, uri, share);
                        }
                        break;
                    case Operation.create_file_multi:
                        assert uris != null;
                        processFileCreateMulti(
                                parentUuid, uris.iterator(), share, new ArrayList<>(uris.size()));
                        break;
                    default:
                        operationDone();
                }
                break;
        }
    }

    private void operationDone() {
        setProcessing(false);
        dataBaseService.updateOwnDeviceProcessingOperation(false);
    }

    private void operationError(@Nullable JSONObject error, @Nullable String fileUuid) {
        if (fileUuid != null) {
            dataBaseService.setProcessing(false, fileUuid);
        }

        String networkError = context.getString(R.string.network_error);
        String err = error == null ? networkError : JSON.optString(error,
                "info", error.optJSONObject("info") == null ? networkError :
                        Objects.requireNonNull(error.optJSONObject("info")).optJSONArray("error_file_name") == null ?
                                networkError :
                                JSON.optString(Objects.requireNonNull(error.optJSONObject("info"))
                                                .optJSONArray("error_file_name")
                                        , 0, networkError));
        currentOperation = R.string.operation_error;
        sendOperationProcessing(true, err);
        operationDone();
    }

    private boolean checkOrWaitSignalServerConnected() {
        int retryCount = 0;
        while (!signalServerService.isConnected()) {
            if (retryCount < 5) {
                retryCount += 1;
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    return false;
                }
            } else {
                return false;
            }
        }
        return true;
    }

    private void processAddOffline(@NonNull ArrayList<String> uuids) {
        for (String uuid : uuids) {
            dataBaseService.addOffline(uuid);
            processed += 1;
            sendOperationProcessing(false, null);
        }
        currentOperation = R.string.added_offline_successfully;
        sendOperationProcessing(true, null);
        operationDone();
    }

    private void processRemoveOffline(@NonNull ArrayList<String> uuids) {
        for (String uuid : uuids) {
            dataBaseService.removeOffline(uuid);
            processed += 1;
            sendOperationProcessing(false, null);
        }
        currentOperation = R.string.removed_offline_successfully;
        sendOperationProcessing(true, null);
        operationDone();
    }

    private void processCancelDownloads(@NonNull ArrayList<String> uuids) {
        dataBaseService.cancelDownloads(uuids);
        currentOperation = uuids.size() > 1 ?
                R.string.cancelled_downloads_successfully :
                R.string.cancelled_download_successfully;
        sendOperationProcessing(true, null);
        operationDone();
    }

    private void processFolderCreate(String parentUuid, String name) {
        createFolder(parentUuid, name, false,
                (response, uuid) -> createFolderSuccess(), this::operationError);
    }

    public void createFolder(
            @Nullable String parentUuid, String name, boolean generateUniqName,
            @NonNull SuccessHandler onSuccess, @NonNull ErrorHandler onError) {
        String uuid = UUID.randomUUID().toString();
        String eventUuid = md5(uuid);
        FileRealm file = new FileRealm();
        file.setUuid(uuid);
        file.setEventUuid(eventUuid);
        file.setName(name);
        file.setFolder(true);
        long now = new Date().getTime();
        file.setDateCreated(now);
        file.setDateModified(now);
        file.setParentUuid(parentUuid);
        file.setProcessing(true);
        file = dataBaseService.addFile(file, generateUniqName, "");
        httpClient.createFolder(
                eventUuid, parentUuid == null ? "" : parentUuid, file.getName(),
                response -> handler.post(() -> {
                    JSONObject data = response.optJSONObject("data");
                    if (data == null) {
                        dataBaseService.deleteFileByUuid(uuid);
                        onError.call(response, null);
                        return;
                    }
                    EventRealm event = new EventRealm();
                    event.setId(data.optLong("event_id"));
                    event.setUuid(JSON.optString(data, "event_uuid"));
                    String folderUuid = JSON.optString(data, "folder_uuid");
                    event.setFileUuid(folderUuid);
                    long date = (long) (data.optDouble("timestamp") * 1000.0);

                    dataBaseService.updateFileWithEvent(
                            uuid, event, date, null, null,
                            null);
                    onSuccess.call(response, folderUuid);
                }),
                error -> {
                    dataBaseService.deleteFileByUuid(uuid);

                    if (error != null && "WRONG_DATA".equals(JSON.optString(error, "errcode"))) {
                        JSONObject errorData = error.optJSONObject("error_data");
                        if (errorData != null) {
                            String varFileName = JSON.optString(errorData, "var_file_name");
                            if (!Objects.requireNonNull(varFileName).isEmpty()) {
                                createFolder(
                                        parentUuid, varFileName, true,
                                        onSuccess, onError);
                                return;
                            }
                        }
                    }

                    onError.call(error, null);
                });
    }

    private void createFolderSuccess() {
        currentOperation = R.string.added_folder_successfully;
        sendOperationProcessing(true, null);
        operationDone();
    }

    private void processRename(String uuid, String newName) {
        FileRealm file = dataBaseService.getFileByUuid(uuid);
        if (file == null) {
            sendOperationProcessing(true, context.getString(R.string.object_was_deleted));
            operationDone();
            return;
        }
        String eventUuid = md5(UUID.randomUUID().toString());
        dataBaseService.setProcessing(true, uuid);
        String parentUuid = file.getParentUuid();
        if (parentUuid == null) parentUuid = "";
        if (file.isFolder()) {
            httpClient.moveFolder(
                    eventUuid, uuid, parentUuid, newName, file.getEventId(),
                    response -> handler.post(() -> renameSuccess(response, uuid, newName)),
                    error -> handler.post(() -> operationError(error, uuid)));
        } else {
            httpClient.moveFile(
                    eventUuid, uuid, parentUuid, newName, file.getEventId(),
                    response -> handler.post(() -> renameSuccess(response, uuid, newName)),
                    error -> handler.post(() -> operationError(error, uuid)));
        }
    }

    private void renameSuccess(@NonNull JSONObject response, String uuid, String newName) {
        JSONObject data = response.optJSONObject("data");
        if (data == null) {
            operationError(response, uuid);
            return;
        }
        EventRealm event = new EventRealm();
        event.setId(data.optLong("event_id"));
        event.setUuid(JSON.optString(data, "event_uuid"));
        event.setHashsum(JSON.optString(data, "file_hash", null));
        event.setSize(data.optLong("file_size_after_event", 0));
        event.setFileUuid(uuid);

        dataBaseService.updateFileWithEvent(
                uuid, event, 0, newName, null, null);

        currentOperation = R.string.renamed_successfully;
        sendOperationProcessing(true, null);
        operationDone();
    }

    private void processDelete(@NonNull Iterator<String> iterator) {
        if (!iterator.hasNext()) {
            operationError(null, null);
            return;
        }
        String uuid = iterator.next();
        FileRealm file = dataBaseService.getFileByUuid(uuid);
        if (file == null) {
            deleteSuccess(null, null, iterator);
            return;
        }
        String eventUuid = md5(UUID.randomUUID().toString());
        dataBaseService.setProcessing(true, uuid);
        if (file.isFolder()) {
            httpClient.deleteFolder(
                    eventUuid, uuid, file.getEventId(),
                    response -> handler.post(() -> deleteSuccess(response, uuid, iterator)),
                    error -> handler.post(() -> {
                        if (error != null && (
                                "FS_SYNC_NOT_FOUND".equals(
                                        JSON.optString(error, "errcode")) ||
                                        "FS_SYNC_PARENT_NOT_FOUND".equals(
                                                JSON.optString(error, "errcode")))) {
                            deleteSuccess(error, uuid, iterator);
                        } else {
                            operationError(error, uuid);
                        }
                    }));
        } else {
            httpClient.deleteFile(
                    eventUuid, uuid, file.getEventId(),
                    response -> handler.post(() -> deleteSuccess(response, uuid, iterator)),
                    error -> handler.post(() -> {
                        if (error != null && (
                                "FS_SYNC_NOT_FOUND".equals(
                                        JSON.optString(error, "errcode")) ||
                                        "FS_SYNC_PARENT_NOT_FOUND".equals(
                                                JSON.optString(error, "errcode")))) {
                            deleteSuccess(error, uuid, iterator);
                        } else {
                            operationError(error, uuid);
                        }
                    }));
        }
    }

    private void deleteSuccess(@Nullable JSONObject response, @Nullable String uuid, @NonNull Iterator<String> iterator) {
        if (response == null) {
            if (iterator.hasNext()) {
                processed += 1;
                sendOperationProcessing(false, null);
                processDelete(iterator);
            } else {
                currentOperation = R.string.delete_successfully;
                sendOperationProcessing(true, null);
                operationDone();
            }
            return;
        }
        JSONObject data = response.optJSONObject("data");
        if (uuid != null) {
            dataBaseService.deleteFileByUuid(uuid);
        }
        if (data != null) {
            EventRealm event = new EventRealm();
            event.setId(data.optLong("event_id"));
            event.setUuid(JSON.optString(data, "event_uuid"));
            event.setHashsum(JSON.optString(data, "file_hash_before_event", null));
            event.setSize(data.optLong("file_size_before_event", 0));
            dataBaseService.saveEvent(event);
        }

        if (iterator.hasNext()) {
            processed += 1;
            sendOperationProcessing(false, null);
            processDelete(iterator);
        } else {
            currentOperation = R.string.delete_successfully;
            sendOperationProcessing(true, null);
            operationDone();
        }
    }

    private void processMove(@Nullable String parentUuid, @NonNull Iterator<String> iterator) {
        if (!iterator.hasNext()) {
            operationError(null, null);
            return;
        }
        String uuid = iterator.next();
        FileRealm file = dataBaseService.getFileByUuid(uuid);
        if (file == null) {
            sendOperationProcessing(true, context.getString(R.string.object_was_deleted));
            operationDone();
            return;
        }
        String eventUuid = md5(UUID.randomUUID().toString());
        dataBaseService.setProcessing(true, uuid);
        if (file.isFolder()) {
            httpClient.moveFolder(
                    eventUuid, uuid, parentUuid == null ? "" : parentUuid,
                    file.getName(), file.getEventId(),
                    response -> handler.post(() -> moveSuccess(response, uuid, parentUuid, iterator)),
                    error -> handler.post(() -> operationError(error, uuid)));
        } else {
            httpClient.moveFile(
                    eventUuid, uuid, parentUuid == null ? "" : parentUuid,
                    file.getName(), file.getEventId(),
                    response -> handler.post(() -> moveSuccess(response, uuid, parentUuid, iterator)),
                    error -> handler.post(() -> operationError(error, uuid)));
        }
    }

    private void moveSuccess(
            @NonNull JSONObject response, String uuid, @Nullable String parentUuid, @NonNull Iterator<String> iterator) {
        JSONObject data = response.optJSONObject("data");
        if (data == null) {
            operationError(response, uuid);
            return;
        }
        EventRealm event = new EventRealm();
        event.setId(data.optLong("event_id"));
        event.setUuid(JSON.optString(data, "event_uuid"));
        event.setHashsum(JSON.optString(data, "file_hash", null));
        event.setSize(data.optLong("file_size_after_event", 0));
        event.setFileUuid(uuid);

        dataBaseService.updateFileWithEvent(uuid, event, 0, null,
                parentUuid == null ? "" : parentUuid, null);

        if (iterator.hasNext()) {
            processed += 1;
            sendOperationProcessing(false, null);
            processMove(parentUuid, iterator);
        } else {
            currentOperation = R.string.moved_successfully;
            sendOperationProcessing(true, null);
            operationDone();
        }
    }

    private void processCopy(@Nullable String parentUuid, @NonNull Iterator<String> iterator) {
        if (!iterator.hasNext()) {
            operationError(null, null);
            return;
        }
        String uuid = iterator.next();
        FileRealm file = dataBaseService.getFileByUuid(uuid);
        if (file == null) {
            sendOperationProcessing(true, context.getString(R.string.object_was_deleted));
            operationDone();
            return;
        }
        long eventId = file.getEventId();
        String eventUuid = file.getEventUuid();
        String copyUuid = UUID.randomUUID().toString();
        String copyEventUuid = md5(copyUuid);
        file.setUuid(copyUuid);
        file.setEventUuid(copyEventUuid);
        file.setEventId(0);
        file.setParentUuid(parentUuid);
        file.setOffline(false);
        file.setShared(false);
        file.setShareLink(null);
        file.setShareSecured(false);
        file.setShareExpire(0);
        file.setDownload(false);
        file.setOnlyDownload(false);
        file.setDownloadedSize(0);
        file.setDownloadPath(null);
        file.setOfflineFilesCount(0);
        file.setDownloadActual(false);
        file.setHashsum(null);
        long now = new Date().getTime();
        file.setDateCreated(now);
        file.setDateModified(now);
        file.setProcessing(true);
        file = dataBaseService.addFile(file, true, " copy");
        if (file.isFolder()) {
            httpClient.copyFolder(
                    copyEventUuid, uuid, parentUuid == null ? "" : parentUuid, file.getName(), eventId,
                    response -> handler.post(() -> copySuccess(
                            response, copyUuid, true, parentUuid, iterator)),
                    error -> handler.post(() -> {
                        dataBaseService.deleteFileByUuid(copyUuid);
                        operationError(error, null);
                    }));
        } else {
            EventRealm event = dataBaseService.getEventByUuid(eventUuid);
            httpClient.createFile(
                    copyEventUuid, parentUuid == null ? "" : parentUuid, file.getName(),
                    Objects.requireNonNull(event).getSize(), event.getHashsum(),
                    response -> handler.post(() -> copySuccess(
                            response, copyUuid, false, parentUuid, iterator)),
                    error -> handler.post(() -> {
                        dataBaseService.deleteFileByUuid(copyUuid);
                        operationError(error, null);
                    }));
        }
    }

    private void copySuccess(
            @NonNull JSONObject response, String copyUuid, boolean isFolder, String parentUuid,
            @NonNull Iterator<String> iterator) {
        if (!isFolder) {
            JSONObject data = response.optJSONObject("data");
            if (data == null) {
                dataBaseService.deleteFileByUuid(copyUuid);
                operationError(response, null);
                return;
            }
            EventRealm event = new EventRealm();
            event.setId(data.optLong("event_id"));
            event.setUuid(JSON.optString(data, "event_uuid"));
            event.setFileUuid(JSON.optString(data, "file_uuid"));
            event.setHashsum(JSON.optString(data, "file_hash"));
            event.setSize(data.optLong("file_size_after_event", 0));
            long date = (long) (data.optDouble("timestamp") * 1000.0);

            dataBaseService.updateFileWithEvent(
                    copyUuid, event, date, null, null, null);
        }

        if (iterator.hasNext()) {
            processed += 1;
            sendOperationProcessing(false, null);
            processCopy(parentUuid, iterator);
        } else {
            currentOperation = R.string.copied_successfully;
            sendOperationProcessing(true, null);
            operationDone();
        }
    }

    private void processFileCreate(
            String parentUuid, @NonNull String name, @NonNull Uri uri, boolean share) {
        File tempFile;
        try {
            tempFile = File.createTempFile(name, ".tmp");
        } catch (IOException e) {
            e.printStackTrace();
            operationError(null, null);
            return;
        }
        try (InputStream istream = context.getContentResolver().openInputStream(uri)) {
            if (istream == null) {
                operationError(null, null);
                return;
            }
            FileUtils.copyInputStreamToFile(istream, tempFile);
        } catch (Exception e) {
            e.printStackTrace();
            operationError(null, null);
            return;
        }
        processFileCreate(parentUuid, name, tempFile.getAbsolutePath(), share, true);
    }

    private void processFileCreateMulti(
            String parentUuid, Iterator<Uri> iterator, boolean share, ArrayList<String> fileUuids) {
        if (!iterator.hasNext()) {
            operationError(null, null);
            return;
        }
        Uri uri = iterator.next();
        String path = uri.getSchemeSpecificPart();
        String name = uri.getFragment();
        createFile(
                parentUuid, name, path, null, false, true,
                (response, uuid) -> createFileMultiSuccess(
                        response, parentUuid, iterator, share, fileUuids),
                (error, uuid) -> operationError(error, null));
    }

    private void createFileMultiSuccess(
            JSONObject response, String parentUuid, Iterator<Uri> iterator,
            boolean share, ArrayList<String> fileUuids) {
        JSONObject data = response.optJSONObject("data");
        fileUuids.add(JSON.optString(data, "file_uuid"));
        if (iterator.hasNext()) {
            processed += 1;
            sendOperationProcessing(false, null);
            processFileCreateMulti(parentUuid, iterator, share, fileUuids);
        } else {
            if (share) {
                processed = 0;
                currentOperation = R.string.create_link_progress_multi;
                sendOperationProcessing(false, null);
                createLinks(fileUuids.iterator(), new ArrayList<>(fileUuids.size()));
            } else {
                currentOperation = R.string.added_file_successfully;
                sendOperationProcessing(true, null);
            }
        }
    }

    private void createLinks(Iterator<String> iterator, ArrayList<String> links) {
        if (!iterator.hasNext()) {
            operationError(null, null);
            return;
        }
        String uuid = iterator.next();
        shareHttpClient.sharingEnable(
                uuid, 0, "", false,
                response -> onLinkCreated(response, iterator, links),
                error -> operationError(error, null));
    }

    private void onLinkCreated(
            @NonNull JSONObject response, Iterator<String> iterator, ArrayList<String> links) {
        String link = JSON.optString(response.optJSONObject("data"), "share_link");
        links.add(link);
        if (iterator.hasNext()) {
            processed += 1;
            sendOperationProcessing(false, null);
            createLinks(iterator, links);
        } else {
            currentOperation = R.string.links_created_successfully;
            sendOperationProcessing(true, null);
            operationDone();
            String linksStr = TextUtils.join("\r\n", links);
            Intent sendIntent = new Intent();
            sendIntent.setAction(Intent.ACTION_SEND);
            sendIntent.putExtra(
                    Intent.EXTRA_TEXT,
                    linksStr);
            sendIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            sendIntent.setType("text/plain");
            context.startActivity(sendIntent);
        }
    }

    private void processFileCreate(
            String parentUuid, String name, @NonNull String path, boolean share, boolean deleteFile) {
        createFile(
                parentUuid, name, path, null, false, deleteFile,
                (response, uuid) -> createFileSuccess(response, share),
                (error, uuid) -> operationError(error, null));
    }

    private void createFileSuccess(@NonNull JSONObject response, boolean share) {
        currentOperation = R.string.added_file_successfully;
        sendOperationProcessing(!share, null);
        if (share) {
            JSONObject data = response.optJSONObject("data");
            createLink(JSON.optString(data, "file_uuid"));
        } else {
            operationDone();
        }
    }

    private void createLink(String uuid) {
        currentOperation = R.string.create_link_progress;
        sendOperationProcessing(false, null);
        shareHttpClient.sharingEnable(
                uuid, 0, "", false,
                response -> {
                    currentOperation = R.string.link_created_successfully;
                    sendOperationProcessing(true, null);
                    operationDone();
                    Intent sendIntent = new Intent();
                    sendIntent.setAction(Intent.ACTION_SEND);
                    sendIntent.putExtra(
                            Intent.EXTRA_TEXT,
                            JSON.optString(response.optJSONObject("data"), "share_link"));
                    sendIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    sendIntent.setType("text/plain");
                    context.startActivity(sendIntent);
                },
                error -> operationError(error, null));
    }

    public void createFile(
            @Nullable String parentUuid, @Nullable String name, @NonNull String path, @Nullable String hashsum, boolean fromCamera,
            boolean deleteFile, @NonNull SuccessHandler onSuccess, @NonNull ErrorHandler onError) {
        if (name == null) {
            name = FileTool.getNameFromPath(path);
        }

        if (hashsum == null) {
            hashsum = PatchTool.getFileHash(path);
        }
        if (hashsum == null) {
            onError.call(null, null);
            return;
        }

        String uuid = UUID.randomUUID().toString();
        String eventUuid = md5(uuid);
        FileRealm file = new FileRealm();
        file.setName(name);
        file.setUuid(uuid);
        file.setEventUuid(eventUuid);
        file.setParentUuid(parentUuid);
        file.setOffline(false);
        file.setDownload(!fromCamera);
        file.setOnlyDownload(false);
        long now = new Date().getTime();
        file.setDateCreated(now);
        file.setDateModified(now);
        file.setProcessing(true);
        long size = FileTool.getFileSize(path);
        file.setSize(size);
        file.setHashsum(hashsum);
        String copyPath = FileTool.buildPathForCopyNamedHash(hashsum);
        if (fromCamera) {
            file.setCameraPath(path);
        } else if (!path.equals(copyPath)) {
            if (!fileTool.copy(path, copyPath, false)) {
                onError.call(null, null);
                return;
            }
        }
        EventRealm event = new EventRealm();
        event.setUuid(eventUuid);
        event.setSize(size);
        event.setHashsum(hashsum);
        if (fromCamera) {
            event.setCameraPath(path);
        }
        dataBaseService.addEvent(event);
        file = dataBaseService.addFile(file, true, "");
        httpClient.createFile(
                eventUuid, parentUuid == null ? "" : parentUuid, file.getName(), size, hashsum,
                response -> handler.post(() -> {
                    if (path.contains(FileTool.buildPath(
                            context.getFilesDir().getAbsolutePath(), "Pictures")) || deleteFile) {
                        fileTool.delete(path);
                    }

                    JSONObject data = response.optJSONObject("data");
                    if (data == null) {
                        dataBaseService.deleteFileByUuid(uuid);
                        dataBaseService.deleteEventByUuid(eventUuid);
                        onError.call(response, uuid);
                        return;
                    }
                    EventRealm ev = new EventRealm();
                    ev.setId(data.optLong("event_id"));
                    ev.setUuid(JSON.optString(data, "event_uuid"));
                    ev.setHashsum(JSON.optString(data, "file_hash", null));
                    ev.setSize(data.optLong("file_size_after_event", 0));
                    ev.setFileUuid(JSON.optString(data, "file_uuid"));
                    long date = (long) (data.optDouble("timestamp") * 1000.0);

                    dataBaseService.updateFileWithEvent(
                            uuid, ev, date, null, null, null);
                    onSuccess.call(response, uuid);
                }),
                error -> handler.post(() -> {
                    dataBaseService.deleteFileByUuid(uuid);
                    dataBaseService.deleteEventByUuid(eventUuid);
                    if (error != null && "WRONG_DATA".equals(JSON.optString(error, "errcode"))) {
                        JSONObject errorData = error.optJSONObject("error_data");
                        if (errorData != null) {
                            String varFileName = JSON.optString(errorData, "var_file_name", "");
                            if (!Objects.requireNonNull(varFileName).isEmpty()) {
                                createFile(
                                        parentUuid, varFileName, path, null, fromCamera,
                                        deleteFile, onSuccess, onError);
                                return;
                            }
                        }
                    }

                    if (path.contains(FileTool.buildPath(
                            context.getFilesDir().getAbsolutePath(), "Pictures"))) {
                        fileTool.delete(path);
                    }

                    onError.call(error, null);
                }));
    }
}
