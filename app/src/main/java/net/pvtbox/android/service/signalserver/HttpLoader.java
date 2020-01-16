package net.pvtbox.android.service.signalserver;


import android.content.Context;
import android.content.Intent;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.util.Log;

import net.pvtbox.android.R;
import net.pvtbox.android.api.ShareHttpClient;
import net.pvtbox.android.application.Const;
import net.pvtbox.android.db.DataBaseService;
import net.pvtbox.android.service.OperationService;
import net.pvtbox.android.tools.Hasher;
import net.pvtbox.android.tools.JSON;
import net.pvtbox.android.tools.SpeedTool;
import net.pvtbox.android.tools.FileTool;
import net.pvtbox.android.tools.diskspace.DiskSpace;
import net.pvtbox.android.tools.diskspace.DiskSpaceTool;

import org.json.JSONObject;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import okio.Buffer;
import okio.BufferedSource;

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
public class HttpLoader {

    private static final int BYTE_COUNT = 64 * 1024;
    @NonNull
    private static final String TAG = HttpLoader.class.getSimpleName();

    private final DataBaseService dataBaseService;
    private SignalServerService signalServerService;
    private final FileTool fileTool;
    private final SpeedTool speedTool;
    private final Context context;

    private final Handler handler = new Handler();

    private final Set<String> downloads = new HashSet<>();
    private final ShareHttpClient httpClient;
    private final OperationService operationService;

    public HttpLoader(FileTool fileTool,
                      DataBaseService dataBaseService, ShareHttpClient httpClient,
                      OperationService operationService, SpeedTool speedTool,
                      Context context) {
        this.dataBaseService = dataBaseService;
        this.fileTool = fileTool;
        this.httpClient = httpClient;
        this.operationService = operationService;
        this.speedTool = speedTool;
        this.context = context;
    }

    void setSignalServerService(SignalServerService signalServerService) {
        this.signalServerService = signalServerService;
    }

    public void load(@NonNull JSONObject data) {
        Log.i(TAG, "load");
        handler.post(() -> {
            String uploadId = JSON.optString(data, "upload_id");
            Log.i(TAG, String.format("load: %s", uploadId));
            if (downloads.contains(uploadId)) return;

            Log.i(TAG, "load: add download");
            downloads.add(uploadId);
            if (!signalServerService.isConnected()) {
                onError(null, data, true);
                return;
            }
            httpClient.download(
                    uploadId,
                    error -> onError(error, data, true),
                    bufferedSource -> onData(data, bufferedSource));
        });
    }

    public void cancel(String uploadId) {
        handler.post(() -> downloads.remove(uploadId));
    }

    private void onError(@Nullable JSONObject error, JSONObject data, boolean abort) {
        String uploadId = JSON.optString(data, "upload_id");
        Log.i(TAG, String.format("onError: %s", uploadId));
        if (error != null && error.optInt("errcode", 0) == 404) {
            signalServerService.sendUploadFailed(uploadId);
        } else {
            downloads.remove(uploadId);
            if (abort) {
                signalServerService.sendUploadFailed(uploadId);
            } else {
                handler.postDelayed(() -> load(data), 1000);
            }
        }
    }

    private void onData(JSONObject data, BufferedSource source) {
        Log.i(TAG, "onData: ");

        String md5 = JSON.optString(data, "upload_md5");
        String uploadId = JSON.optString(data, "upload_id");
        String name = JSON.optString(data, "upload_name");
        String folderUuid = JSON.optString(data, "folder_uuid");
        long size = data.optLong("upload_size");
        DiskSpace diskSpace = DiskSpaceTool.getDiskQuantity(size);
        String sizeFormat = diskSpace.getFormatStringValue();
        String sizeStr = context.getString(diskSpace.getIdQuantity());
        assert md5 != null;
        String downloadPath = FileTool.buildPath(Const.UPLOADS_PATH, md5);
        if (!FileTool.isExist(Const.UPLOADS_PATH)) {
            fileTool.createDirectory(Const.UPLOADS_PATH);
        }
        fileTool.createEmptyFile(downloadPath);
        long lastDownloadedTime = 0;
        int downloaded = 0;
        Intent intent;
        try (FileOutputStream output = new FileOutputStream(downloadPath, true)) {
            Buffer buffer = new Buffer();
            while (!source.exhausted()) {
                if (!downloads.contains(uploadId)) {
                    handler.post(() -> {
                        Intent i = new Intent(Const.OPERATIONS_PROGRESS_INTENT);
                        i.putExtra(Const.OPERATIONS_PROGRESS_MESSAGE, context.getString(
                                R.string.http_download_cancel));
                        i.putExtra(Const.OPERATIONS_PROGRESS_SHOW_AND_DISMISS, true);
                        LocalBroadcastManager.getInstance(context).sendBroadcast(i);

                        fileTool.deleteFile(downloadPath);
                    });
                    return;
                }
                source.read(buffer, BYTE_COUNT);

                byte[] byteArray = buffer.readByteArray();

                output.write(byteArray);
                speedTool.addDownloadValue(byteArray.length);
                downloaded += byteArray.length;
                long date = new Date().getTime();
                if (date - lastDownloadedTime >= 1000) {
                    lastDownloadedTime = date;
                    int progress = (int) ((double) downloaded / (double) size * 100.);

                    intent = new Intent(Const.OPERATIONS_PROGRESS_INTENT);
                    intent.putExtra(Const.OPERATIONS_PROGRESS_MESSAGE,
                            context.getString(
                                    R.string.http_download_progress,
                                    name,
                                    progress,
                                    sizeFormat,
                                    sizeStr));
                    intent.putExtra(Const.OPERATIONS_PROGRESS_SHOW_AND_DISMISS, false);
                    LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            fileTool.delete(downloadPath);
            intent = new Intent(Const.OPERATIONS_PROGRESS_INTENT);
            intent.putExtra(Const.OPERATIONS_PROGRESS_MESSAGE,
                    context.getString(R.string.download_network_error));
            intent.putExtra(Const.OPERATIONS_PROGRESS_SHOW_AND_DISMISS, true);
            LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
            onError(null, data, false);
            return;
        }

        intent = new Intent(Const.OPERATIONS_PROGRESS_INTENT);
        intent.putExtra(Const.OPERATIONS_PROGRESS_MESSAGE,
                context.getString(
                        R.string.http_download_progress,
                        name,
                        100,
                        sizeFormat,
                        sizeStr));
        intent.putExtra(Const.OPERATIONS_PROGRESS_SHOW_AND_DISMISS, false);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);

        String fileMd5 = Hasher.fileMd5(downloadPath);
        if (!Objects.equals(fileMd5, md5)) {
            fileTool.delete(downloadPath);
            intent = new Intent(Const.OPERATIONS_PROGRESS_INTENT);
            intent.putExtra(Const.OPERATIONS_PROGRESS_MESSAGE,
                    context.getString(R.string.download_network_error));
            intent.putExtra(Const.OPERATIONS_PROGRESS_SHOW_AND_DISMISS, true);
            LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
            onError(null, data, true);
            return;
        }

        signalServerService.sendUploadCompleted(uploadId);

        registerFile(folderUuid, name, downloadPath, uploadId);
    }

    private void registerFile(String folderUuid, String name, String downloadPath, String uploadId) {
        operationService.createFile(
                folderUuid, name, downloadPath,
                null, false, false,
                (response, uuid) -> {
                    Intent intent = new Intent(Const.OPERATIONS_PROGRESS_INTENT);
                    intent.putExtra(Const.OPERATIONS_PROGRESS_MESSAGE,
                            context.getString(R.string.file_downloaded_successfully, name));
                    intent.putExtra(Const.OPERATIONS_PROGRESS_SHOW_AND_DISMISS, true);
                    LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
                    downloads.remove(uploadId);
                    fileTool.delete(downloadPath);
                },
                (error, uuid) -> {
                    if (folderUuid != null &&
                            dataBaseService.getFileByUuid(folderUuid) == null) {
                        fileTool.delete(downloadPath);
                        return;
                    }
                    registerFile(folderUuid, name, downloadPath, uploadId);
                });
    }
}
