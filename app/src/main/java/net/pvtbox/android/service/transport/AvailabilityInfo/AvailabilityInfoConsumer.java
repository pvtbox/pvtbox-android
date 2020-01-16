package net.pvtbox.android.service.transport.AvailabilityInfo;


import androidx.annotation.NonNull;

import net.pvtbox.android.service.transport.Connectivity.ConnectivityService;

import java.util.ArrayList;
import java.util.HashSet;
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
public class AvailabilityInfoConsumer {

    @NonNull
    private final HashSet<String> subscriptions = new HashSet<>();
    private final ConnectivityService connectivityService;
    private final boolean isFile;


    public AvailabilityInfoConsumer(
            boolean isFile, ConnectivityService connectivity) {
        this.isFile = isFile;
        connectivityService = connectivity;
    }

    public void onDestroy() {
        subscriptions.clear();
    }

    public void subscribe(@NonNull HashSet<String> objIds) {
        subscriptions.addAll(objIds);
        Set<String> connectedNodes = connectivityService.getOutgoingConnectedNodes();
        if (connectedNodes.isEmpty()) return;
        ArrayList<byte[]> requests = generateRequests(objIds);
        for (String nodeId : connectedNodes) {
            for (byte[] request : requests) {
                connectivityService.sendMessage(request, nodeId, false);
            }
        }
    }

    public void unsubscribe(@NonNull HashSet<String> objIds) {
        subscriptions.removeAll(objIds);
    }

    public void unsubscribe(String objId) {
        subscriptions.remove(objId);
    }

    public void resubscribe() {
        Set<String> connectedNodes = connectivityService.getOutgoingConnectedNodes();
        if (connectedNodes.isEmpty()) return;
        ArrayList<byte[]> requests = generateRequests(subscriptions);
        for (String nodeId : connectedNodes) {
            for (byte[] request : requests) {
                connectivityService.sendMessage(request, nodeId, false);
            }
        }
    }

    public void onNodeConnected(String nodeId) {
       if (subscriptions.isEmpty()) return;
        ArrayList<byte[]> requests = generateRequests(subscriptions);
        for (byte[] request : requests) {
            connectivityService.sendMessage(request, nodeId, false);
        }
    }

    @NonNull
    private ArrayList<byte[]> generateRequests(@NonNull HashSet<String> objIds) {
        ArrayList<byte[]> requests = new ArrayList<>();
        Proto.Messages.Builder builder = Proto.Messages.newBuilder();
        int count = 0;
        for (String objId : objIds) {
            builder.addMsg(Proto.Message.newBuilder()
                    .setMagicCookie(0x7a52fa73)
                    .setMtype(Proto.Message.MType.AVAILABILITY_INFO_REQUEST)
                    .setObjId(objId)
                    .setObjType(isFile ?
                            Proto.Message.ObjectType.FILE : Proto.Message.ObjectType.PATCH)
                    .build()
            );
            count += 1;
            if (count >= 99) {
                requests.add(builder.build().toByteArray());
                builder = Proto.Messages.newBuilder();
                count = 0;
            }
        }
        requests.add(builder.build().toByteArray());

        return requests;
    }
}
