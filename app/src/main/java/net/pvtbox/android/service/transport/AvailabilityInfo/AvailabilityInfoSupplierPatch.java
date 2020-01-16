package net.pvtbox.android.service.transport.AvailabilityInfo;

import android.os.HandlerThread;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.pvtbox.android.db.DataBaseService;
import net.pvtbox.android.tools.FileTool;
import net.pvtbox.android.service.transport.Connectivity.ConnectivityService;
import net.pvtbox.android.service.transport.Downloads.DownloadManager;

import java.util.Set;

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
public class AvailabilityInfoSupplierPatch extends AvailabilityInfoSupplierBase {


    public AvailabilityInfoSupplierPatch(DownloadManager downloadManager,
                                         DataBaseService dataBaseService,
                                         ConnectivityService connectivityService,
                                         @NonNull HandlerThread handlerThread) {
        super(dataBaseService, connectivityService, downloadManager, handlerThread);
    }

    @Nullable
    @Override
    protected Proto.Message onRequest(@NonNull Proto.Message message, String nodeId, boolean aggregate) {
        String patchUuid = message.getObjId();
        String patchPath = FileTool.buildPatchPath(patchUuid);
        Proto.Message res;
        if (FileTool.isExist(patchPath)) {
            res = buildAvailabilityInfoResponse(
                    patchUuid, 0, FileTool.getFileSize(patchPath), false);
            if (!aggregate) sendAvailabilityInfoResponse(nodeId, res.toByteArray());
        } else {
            res = sendAlreadyDownloadedChunksIfAny(nodeId, patchUuid, false, aggregate);
            addSubscriber(patchUuid, nodeId);
        }
        return res;
    }

    public void onNewAvailabilityInfo(String objectId, long offset, long length) {
        Set<String> nodeSet = subscriptions.get(objectId);
        if (nodeSet == null) {
            return;
        }
        byte[] res = buildAvailabilityInfoResponse(
                objectId, offset, length, false).toByteArray();
        for (String nodeId : nodeSet) {
            sendAvailabilityInfoResponse(nodeId, res);
        }
    }
}
