package net.pvtbox.android.service.transport.Data;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.protobuf.ByteString;

import net.pvtbox.android.application.Const;
import net.pvtbox.android.db.DataBaseService;
import net.pvtbox.android.db.model.EventRealm;
import net.pvtbox.android.tools.FileTool;
import net.pvtbox.android.service.transport.Connectivity.ConnectivityService;

import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Objects;

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
public class DataSupplier {
    private final static String TAG = DataSupplier.class.getSimpleName();
    private static final int MAX_PROCESSING_REQUESTS = 2;
    @NonNull
    private final Handler handler;
    private final ConnectivityService connectivityService;
    private final DataBaseService dataBaseService;
    private final FileTool fileTool;

    @NonNull
    private final ArrayList<DataSupplierRequest> processingDataRequests = new ArrayList<>();
    @NonNull
    private final LinkedList<DataSupplierRequest> queuedDataRequests = new LinkedList<>();

    public DataSupplier(ConnectivityService connectivityService,
                        DataBaseService dataBaseService,
                        FileTool fileTool,
                        @NonNull HandlerThread handlerThread) {
        this.connectivityService = connectivityService;
        this.dataBaseService = dataBaseService;
        this.fileTool = fileTool;
        handler = new Handler(handlerThread.getLooper());
    }

    public void onDestroy() {
        processingDataRequests.clear();
        queuedDataRequests.clear();
    }

    public void onNodeDisconnected(String nodeId) {
        int processingOldSize = processingDataRequests.size();
        for (DataSupplierRequest req : new ArrayList<>(processingDataRequests)) {
            if (Objects.equals(req.nodeId, nodeId)) {
                processingDataRequests.remove(req);
            }
        }
        int queuedOldSize = queuedDataRequests.size();
        for (DataSupplierRequest req : new LinkedList<>(queuedDataRequests)) {
            if (Objects.equals(req.nodeId, nodeId)) {
                queuedDataRequests.remove(req);
            }
        }
        Log.d(TAG, String.format(
                "onDisconnectNode: nodeId: %s, processingRequests: %s-%s, queuedRequests: %s-%s",
                nodeId, processingOldSize, processingDataRequests.size(),
                queuedOldSize, queuedDataRequests.size()));
    }

    public void onAbort(@NonNull Proto.Message message, String nodeId) {
        Long offset = message.getInfoCount() > 0 ? message.getInfo(0).getOffset() : null;

        int processingOldSize = processingDataRequests.size();
        for (DataSupplierRequest req : new ArrayList<>(processingDataRequests)) {
            if (Objects.equals(req.nodeId, nodeId) &&
                    Objects.equals(req.objId, message.getObjId()) &&
                    (offset == null || req.offset == offset)) {
                processingDataRequests.remove(req);
            }
        }
        int queuedOldSize = queuedDataRequests.size();
        for (DataSupplierRequest req : new LinkedList<>(queuedDataRequests)) {
            if (Objects.equals(req.nodeId, nodeId) &&
                    Objects.equals(req.objId, message.getObjId()) &&
                    (offset == null || req.offset == offset)) {
                queuedDataRequests.remove(req);
            }
        }
        Log.d(TAG, String.format(
                "onAbort: nodeId: %s, objId: %s " +
                        "processingRequests: %s-%s, queuedRequests: %s-%s",
                nodeId, message.getObjId(),
                processingOldSize, processingDataRequests.size(),
                queuedOldSize, queuedDataRequests.size()));

    }

    public void onRequest(@NonNull Proto.Message message, String nodeId) {
        DataSupplierRequest request = new DataSupplierRequest(
                message.getObjType() == Proto.Message.ObjectType.FILE,
                nodeId, message.getObjId(), message.getInfo(0).getOffset(),
                message.getInfo(0).getLength());
        Log.d(TAG, String.format(
                "onRequest: nodeId: %s, objId: %s, offset: %s, length: %s, " +
                        "processingRequests: %s, queuedRequests: %s",
                request.nodeId, request.objId, request.offset, request.length,
                processingDataRequests.size(), queuedDataRequests.size()));
        if (processingDataRequests.size() < MAX_PROCESSING_REQUESTS) {
            process(request);
        } else {
            Log.d(TAG, "onRequest: add to queue");
            queuedDataRequests.add(request);
        }

    }

    private void process(@NonNull DataSupplierRequest request) {
        boolean success = false;
        try {
            Log.d(TAG, String.format(
                    "process: nodeId: %s, objId: %s, offset: %s, length: %s",
                    request.nodeId, request.objId, request.offset, request.length));

            Log.d(TAG, "process: added to processing requests");
            processingDataRequests.add(request);

            if (request.isFile) {
                success = processFile(request);
            } else {
                success = processPatch(request);
            }
        } catch (Exception e) {
            Log.w(TAG, "process: exception", e);
        } finally {
            if (!success) {
                sendFailure(request);
                onDataSent(request);
                Log.d(TAG, "process: finished unsuccessfully");
            } else {
                Log.d(TAG, "process: finished successfully");
            }
        }
    }

    private boolean processFile(@NonNull DataSupplierRequest request) throws IOException {
        EventRealm event = dataBaseService.getEventByUuid(request.objId);
        if (event == null) {
            Log.d(TAG, "processFile: fileEvent not found");
            return false;
        }

        String copyFile = FileTool.buildPathForCopyNamedHash(event.getHashsum());
        String cameraPath = event.getCameraPath();
        String filePath = null;
        if (FileTool.isExist(copyFile)) {
            Log.d(TAG, "supply normal file");
            filePath = copyFile;
        } else if (FileTool.isExist(cameraPath)) {
            filePath = cameraPath;
        } else {
            if (cameraPath != null) {
                dataBaseService.dropCameraPath(cameraPath);
            }
            String pathFileDownload = copyFile + ".download";
            if (FileTool.isExist(pathFileDownload)) {
                Log.d(TAG, "supply normal .download file");
                filePath = pathFileDownload;
            }
        }
        return filePath != null && sendResponse(request, filePath);
    }

    private boolean processPatch(@NonNull DataSupplierRequest request) throws IOException {
        String patchPath = FileTool.buildPatchPath(request.objId);
        String filePath = null;
        if (FileTool.isExist(patchPath)) {
            filePath = patchPath;
        } else {
            String patchPathDownload = patchPath + ".download";
            if (FileTool.isExist(patchPathDownload)) {
                filePath = patchPathDownload;
            }
        }
        return filePath != null && sendResponse(request, filePath);
    }

    @SuppressWarnings("SameReturnValue")
    private boolean sendResponse(@NonNull DataSupplierRequest request, String filePath) throws IOException {
        long currentOffset = request.offset;
        ArrayList<byte[]> responses = new ArrayList<>();
        while (currentOffset < request.offset + request.length) {
            long length = Math.min(
                    Const.DEFAULT_CHUNK_SIZE, request.offset + request.length - currentOffset);

            byte[] buffer;
            int retry = 0;
            while (true) {
                try {
                    buffer = fileTool.readBytes(filePath, currentOffset, (int) length);
                    break;
                } catch (EOFException e) {
                    Log.w(TAG, "sendResponse: read bytes exception", e);
                    retry++;
                    if (retry >= 3) throw e;
                }
            }
            Log.d(TAG, String.format(
                    "sendResponse: objId: %s, offset: %s, length: %s",
                    request.objId, currentOffset, length));

            responses.add(buildDataResponse(
                    request.objId, currentOffset, length, buffer, request.isFile));
            currentOffset += length;
        }
        sendDataResponseMessages(request, responses);
        return true;
    }

    private byte[] buildDataResponse(String objId, long offset, long length, @NonNull byte[] data, boolean isFile) {
        return Proto.Message.newBuilder()
                .setMagicCookie(0x7a52fa73)
                .setMtype(Proto.Message.MType.DATA_RESPONSE)
                .setObjId(objId)
                .setObjType(isFile ?
                        Proto.Message.ObjectType.FILE : Proto.Message.ObjectType.PATCH)
                .addInfo(Proto.Info.newBuilder()
                        .setOffset(offset)
                        .setLength(length)
                        .build())
                .setData(ByteString.copyFrom(data))
                .build()
                .toByteArray();
    }

    private void sendDataResponseMessages(
            @NonNull DataSupplierRequest request, @NonNull ArrayList<byte[]> responses) {
        connectivityService.sendMessages(
                responses, request.nodeId,
                () -> handler.post(() -> onDataSent(request)),
                () -> processingDataRequests.contains(request));
    }

    private void onDataSent(@NonNull DataSupplierRequest request) {
        Log.d(TAG, String.format(
                "onDataSent: nodeId: %s, objId: %s, offset: %s, length: %s, " +
                        "processingRequests: %s, queuedRequests: %s",
                request.nodeId, request.objId, request.offset, request.length,
                processingDataRequests.size(), queuedDataRequests.size()));
        processingDataRequests.remove(request);
        if (processingDataRequests.size() < MAX_PROCESSING_REQUESTS) {
            DataSupplierRequest req = queuedDataRequests.poll();
            if (req != null) {
                process(req);
            }
        }
    }

    private void sendFailure(@NonNull DataSupplierRequest request) {
        Log.d(TAG, String.format(
                "sendFailure: nodeId: %s, objId: %s, offset: %s",
                request.nodeId, request.objId, request.offset));
    }
}
