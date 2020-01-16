package net.pvtbox.android.service.transport.Connectivity;


import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.pvtbox.android.db.DataBaseService;
import net.pvtbox.android.service.signalserver.SignalServerClient;
import net.pvtbox.android.tools.JSON;
import net.pvtbox.android.tools.SpeedTool;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.NetworkMonitor;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;


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
public class ConnectivityService implements ConnectionListener {
    @NonNull
    private final String TAG;
    private static final int hardConnectionsLimit = 8;
    private static final int bigSendDelay = 250;
    private static final int connectTimeout = 20_000;
    private static final int connectNotificationDelay = 500;
    private static final int reconnectDelay = 1_000;
    @Nullable
    private PeerConnectionFactory factory;
    @NonNull
    private final List<PeerConnection.IceServer> iceServers = new ArrayList<>();

    public interface NodeCallback {
        void call(String nodeId);
    }

    public interface MessageCallback {
        void call(byte[] message, String nodeId);
    }

    private final HandlerThread handlerThread = new HandlerThread(
            "ConnectivityThread",
            HandlerThread.NORM_PRIORITY + HandlerThread.MIN_PRIORITY);
    private final Context context;
    @Nullable
    private Handler handler;

    @NonNull
    private final HashMap<String, Node> nodes = new HashMap<>();

    @NonNull
    private final HashMap<String, Connection> incomingConnections = new HashMap<>();
    @NonNull
    private final HashMap<String, Set<String>> incomingNodesConnections = new HashMap<>();
    @NonNull
    private final Set<String> incomingConnectedNodes = new HashSet<>();

    @NonNull
    private final HashMap<String, Connection> outgoingConnections = new HashMap<>();
    @NonNull
    private final HashMap<String, Set<String>> outgoingNodesConnections = new HashMap<>();
    @NonNull
    private final Set<String> outgoingConnectedNodes = new HashSet<>();

    @Nullable
    private Runnable refreshConnectionsRunnable;

    private final SignalServerClient signalServerService;
    private final DataBaseService dataBaseService;
    private final SpeedTool speedTool;


    @Nullable
    public NodeCallback incomingNodeConnected = null;
    @Nullable
    public NodeCallback incomingNodeDisconnected = null;
    @Nullable
    public NodeCallback outgoingNodeConnected = null;
    @Nullable
    public NodeCallback outgoingNodeDisconnected = null;
    @Nullable
    public MessageCallback messageReceived = null;

    public ConnectivityService(
            Context context, String name, SignalServerClient signalServerService,
            SpeedTool speedTool, DataBaseService dataBaseService) {
        TAG = ConnectivityService.class.getSimpleName() + "_" + name;
        this.context = context;
        this.signalServerService = signalServerService;
        this.dataBaseService = dataBaseService;
        this.speedTool = speedTool;
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
    }

    public void onDestroy() {
        Log.i(TAG, "onDestroy");
        if (handler == null) return;
        handler.removeCallbacksAndMessages(null);
        handler.post(() -> {
            for (Connection connection : incomingConnections.values()) {
                connection.disconnect();
            }
            for (Connection connection : outgoingConnections.values()) {
                connection.disconnect();
            }
            try {
                NetworkMonitor.getInstance().stopMonitoring();
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (factory != null) {
                factory = null;
            }
            nodes.clear();

            incomingConnections.clear();
            incomingNodesConnections.clear();
            incomingConnectedNodes.clear();

            outgoingConnections.clear();
            outgoingNodesConnections.clear();
            outgoingConnectedNodes.clear();

            if (dataBaseService != null) dataBaseService.updateOwnDeviceConnectedNodesCount(0);
        });
        handlerThread.quitSafely();
        handler = null;
    }

    public void init(@NonNull JSONArray servers) {
        Log.d(TAG, "init");
        if (factory != null) return;
        PeerConnectionFactory.InitializationOptions options =
                PeerConnectionFactory.InitializationOptions.builder(context)
                        /*.setInjectableLogger(
                                (s, severity, s1) -> Log.d(
                                        "Webrtc", String.format(
                                                "%s:%s: %s", s1, severity.toString(), s)),
                                Logging.Severity.LS_INFO)*/
                        .createInitializationOptions();
        PeerConnectionFactory.initialize(options);
        factory = PeerConnectionFactory.builder().createPeerConnectionFactory();
        for (int i = 0; i < servers.length(); ++i) {
            JSONObject server;
            try {
                server = servers.getJSONObject(i);
            } catch (JSONException e) {
                e.printStackTrace();
                continue;
            }
            String uri;
            String login = "";
            String password = "";
            switch (Objects.requireNonNull(JSON.optString(server, "server_type", ""))) {
                case "TURN":
                    uri = "turn:" + JSON.optString(server, "server_url", "");
                    login = JSON.optString(server, "server_login", "");
                    password = JSON.optString(server, "server_password", "");
                    break;
                case "STUN":
                    uri = "stun:" + JSON.optString(server, "server_url", "");
                    break;
                default:
                    continue;

            }
            PeerConnection.IceServer.Builder builder = PeerConnection.IceServer.builder(uri);
            if (!Objects.requireNonNull(login).isEmpty()) {
                builder.setUsername(login);
            }
            if (!Objects.requireNonNull(password).isEmpty()) {
                builder.setPassword(password);
            }
            iceServers.add(builder.createIceServer());
        }
    }

    public void setNodeList(@NonNull JSONArray peers) {
        if (handler == null) return;
        handler.post(() -> {
            for (int i = 0; i < peers.length(); ++i) {
                JSONObject peer = peers.optJSONObject(i);
                if (peer == null) continue;
                if (peer.optBoolean("is_online", false)) {
                    addNode(
                            JSON.optString(peer, "id"),
                            JSON.optString(peer, "type")
                    );
                }
            }

            Log.i(TAG, String.format("setNodeList: online nodes: %s", nodes.keySet()));
            refreshConnections();
        });
    }

    public void onNodeConnected(@NonNull JSONObject peer) {
        if (handler == null) return;
        handler.post(() -> {
            if (peer.optBoolean("is_online", false)) {
                addNode(
                        JSON.optString(peer, "id"),
                        JSON.optString(peer, "type")
                );
            }
        });
    }

    public void onNodeDisconnected(String id) {
        if (handler == null) return;
        handler.post(() -> {
            disconnectConnections(id, true, true);
            nodes.remove(id);
        });
    }

    public void onDisconnectedFromServer() {
        if (handler == null) return;
        handler.post(() -> {
            for (String nodeId : nodes.keySet()) {
                disconnectConnections(nodeId, true, true);
            }
            nodes.clear();
        });
    }

    public void onSdpMessage(@Nullable String message, @Nullable String connectionId, @Nullable String nodeId) {
        if (handler == null || message == null || connectionId == null || nodeId == null) return;
        handler.post(() -> {
            if (factory == null) return;
            Node node = nodes.get(nodeId);
            if (node == null) return;
            Log.d(TAG, String.format("onSdpMessage: %s", nodeId));
            Connection connection;
            connection = incomingConnections.get(connectionId);
            if (connection == null) {
                connection = outgoingConnections.get(connectionId);
            }
            if (connection == null) {
                Set<String> nodeConnections = incomingNodesConnections.get(nodeId);
                if (nodeConnections != null && nodeConnections.size() >= hardConnectionsLimit) {
                    return;
                }
                connection = new Connection(
                        handler, connectionId, nodeId, signalServerService, this);
                incomingConnections.put(connectionId, connection);
                if (nodeConnections == null) {
                    nodeConnections = new HashSet<>();
                }
                nodeConnections.add(connectionId);
                incomingNodesConnections.put(nodeId, nodeConnections);
                createConnection(connection);
                if (handler == null) return;
                handler.postDelayed(
                        () -> checkConnected(connectionId, true), connectTimeout);
            }
            connection.onSdpMessage(message);
        });
    }

    private void createConnection(@NonNull Connection connection) {
        if (factory == null) return;
        PeerConnection.RTCConfiguration config = new PeerConnection.RTCConfiguration(iceServers);
        config.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
        config.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_ONCE;
        config.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
        connection.setConnection(factory.createPeerConnection(config, connection));
    }

    public void sendMessage(
            @NonNull byte[] message, String nodeId, boolean sendThroughIncomingConnection) {
        if (handler == null) return;
        handler.post(() -> {
            Log.i(TAG, String.format("sendMessage: to %s, through incoming connection: %s",
                    nodeId, sendThroughIncomingConnection));
            Set<String> nodeConnections;
            if (sendThroughIncomingConnection) {
                nodeConnections = incomingNodesConnections.get(nodeId);
            } else {
                nodeConnections = outgoingNodesConnections.get(nodeId);
            }
            if (nodeConnections == null) {
                Log.w(TAG, "sendMessage: no connections found");
                return;
            }
            ArrayList<String> nodeConnectionsList = new ArrayList<>(nodeConnections);
            if (nodeConnectionsList.isEmpty()) {
                Log.w(TAG, "sendMessage: node doest not have connections");
                return;
            }
            Collections.shuffle(nodeConnectionsList);
            for (String connectionId : nodeConnectionsList) {
                Connection connection;
                if (sendThroughIncomingConnection) {
                    connection = incomingConnections.get(connectionId);
                } else {
                    connection = outgoingConnections.get(connectionId);
                }
                if (connection != null && connection.isOpen()) {
                    if (!connection.isBufferOverflow()) {
                        Log.i(TAG, "sendMessage: send");
                        connection.send(ByteBuffer.wrap(message));
                        speedTool.addUploadValue(message.length);
                        return;
                    }
                }
            }
        });
    }

    @NonNull
    public Set<String> getOutgoingConnectedNodes() {
        return outgoingConnectedNodes;
    }

    public interface OnSentCallback {
        void call();
    }

    public interface CheckSendCallback {
        @SuppressWarnings("BooleanMethodIsAlwaysInverted")
        boolean call();
    }

    public void sendMessages(
            @NonNull ArrayList<byte[]> messages, String nodeId,
            @NonNull OnSentCallback onSent, @NonNull CheckSendCallback checkSend) {
        if (handler == null) return;
        handler.post(() -> {
            if (!checkSend.call()) {
                onSent.call();
                return;
            }
            Set<String> nodeConnections = incomingNodesConnections.get(nodeId);
            if (nodeConnections == null) {
                onSent.call();
                return;
            }
            ArrayList<String> nodeConnectionsList = new ArrayList<>(nodeConnections);
            Collections.shuffle(nodeConnectionsList);

            while (true) {
                for (String connectionId : nodeConnectionsList) {
                    Connection connection = incomingConnections.get(connectionId);
                    if (connection == null) {
                        onSent.call();
                        return;
                    }
                    if (connection.isOpen() && !connection.isBufferOverflow()) {
                        byte[] message = messages.remove(0);
                        connection.send(ByteBuffer.wrap(message));
                        speedTool.addUploadValue(message.length);

                        if (messages.isEmpty()) {
                            onSent.call();
                            return;
                        }
                    } else {
                        if (handler == null) return;
                        handler.postDelayed(
                                () -> sendMessages(messages, nodeId, onSent, checkSend),
                                bigSendDelay);
                        return;
                    }
                }
            }
        });
    }


    public void reconnect(String nodeId) {
        if (handler == null) return;
        handler.post(() -> {
            Log.i(TAG, String.format("reconnect: %s", nodeId));
            Node node = nodes.get(nodeId);
            if (node == null) {
                return;
            }
            disconnectConnections(nodeId, false, true);
            refreshConnections();
        });
    }

    private void addNode(String id, String type) {
        Node node = new Node(id, type);
        if (nodes.get(id) != null) {
            disconnectConnections(id, true, true);
        }
        nodes.put(node.id, node);
        refreshConnections();
    }

    private void refreshConnections() {
        if (handler == null || refreshConnectionsRunnable != null) return;
        refreshConnectionsRunnable = () -> {
            Log.d(TAG, "refreshConnections");
            refreshConnectionsRunnable = null;
            executeRefreshConnections();
        };
        handler.postDelayed(refreshConnectionsRunnable, reconnectDelay);
    }

    private void executeRefreshConnections() {
        if (!signalServerService.isConnected() || handler == null) return;
        for (Node node : nodes.values()) {
            if (!"node".equals(node.type)) continue;
            Set<String> nodeConnections = outgoingNodesConnections.get(node.id);
            if (nodeConnections == null || nodeConnections.size() < (5 / nodes.size() + 1)) {
                String connectionId = UUID.randomUUID().toString();
                Connection connection = new Connection(
                        handler, connectionId, node.id, signalServerService, this);
                outgoingConnections.put(connectionId, connection);
                if (nodeConnections == null) {
                    nodeConnections = new HashSet<>();
                }
                nodeConnections.add(connectionId);
                outgoingNodesConnections.put(node.id, nodeConnections);

                createConnection(connection);
                connection.initiateConnection();

                handler.postDelayed(() -> checkConnected(connectionId, false), connectTimeout);
            }
        }
    }

    private void disconnectConnections(
            String nodeId, boolean disconnectIncoming, boolean disconnectOutgoing) {
        Log.d(TAG, String.format("disconnectConnections: %s", nodeId));
        Set<String> nodeConnections;
        if (disconnectIncoming) {
            nodeConnections = incomingNodesConnections.remove(nodeId);
            if (nodeConnections != null) {
                for (String connectionId : nodeConnections) {
                    Connection connection = incomingConnections.remove(connectionId);
                    if (connection != null) {
                        connection.disconnect();
                    }
                }
                if (incomingConnectedNodes.remove(nodeId) && incomingNodeDisconnected != null) {
                    incomingNodeDisconnected.call(nodeId);
                }
            }
        }
        if (disconnectOutgoing) {
            nodeConnections = outgoingNodesConnections.remove(nodeId);
            if (nodeConnections != null) {
                for (String connectionId : nodeConnections) {
                    Connection connection = outgoingConnections.remove(connectionId);
                    if (connection != null) {
                        connection.disconnect();
                    }
                }
                if (outgoingConnectedNodes.remove(nodeId) && outgoingNodeDisconnected != null) {
                    outgoingNodeDisconnected.call(nodeId);
                }
            }
        }
        if (dataBaseService != null) dataBaseService.updateOwnDeviceConnectedNodesCount(
                outgoingConnectedNodes.size());
    }

    private void checkConnected(String connectionId, boolean isIncoming) {
        HashMap<String, Connection> connections;
        HashMap<String, Set<String>> nodesConnections;
        if (isIncoming) {
            connections = incomingConnections;
            nodesConnections = incomingNodesConnections;
        } else {
            connections = outgoingConnections;
            nodesConnections = outgoingNodesConnections;
        }
        Connection connection = connections.get(connectionId);
        if (connection == null || connection.isOpen()) return;
        connections.remove(connectionId);
        Set<String> nodeConnections = nodesConnections.get(connection.nodeId);
        assert nodeConnections != null;
        nodeConnections.remove(connectionId);
        if (!nodeConnections.isEmpty()) {
            nodesConnections.put(connection.nodeId, nodeConnections);
        }
        refreshConnections();
    }

    @Override
    public void onConnected(@NonNull Connection connection) {
        if (handler == null) return;
        handler.post(() -> {
            boolean isIncoming = incomingConnections.containsKey(connection.id);
            boolean isOutgoing = outgoingConnections.containsKey(connection.id);
            if (!isIncoming && !isOutgoing) {
                connection.disconnect();
                return;
            }
            Log.d(TAG, String.format("OnConnected: %s", connection.nodeId));
            if (isIncoming && !incomingConnectedNodes.contains(connection.nodeId)) {
                incomingConnectedNodes.add(connection.nodeId);
                if (incomingNodeConnected != null) {
                    incomingNodeConnected.call(connection.nodeId);
                }
            } else if (isOutgoing && !outgoingConnectedNodes.contains(connection.nodeId)) {
                outgoingConnectedNodes.add(connection.nodeId);
                if (dataBaseService != null) dataBaseService.updateOwnDeviceConnectedNodesCount(
                        outgoingConnectedNodes.size());
                handler.postDelayed(() -> {
                    if (outgoingConnectedNodes.contains(connection.nodeId) &&
                            outgoingNodeConnected != null) {
                        outgoingNodeConnected.call(connection.nodeId);
                    }
                }, connectNotificationDelay);
            }
            refreshConnections();
        });
    }

    @Override
    public void onDisconnected(@NonNull Connection connection) {
        if (handler == null) return;
        handler.post(() -> {
            boolean isIncoming = incomingConnections.containsKey(connection.id);
            boolean isOutgoing = outgoingConnections.containsKey(connection.id);
            if (!isIncoming && !isOutgoing) return;

            HashMap<String, Set<String>> nodesConnections;
            if (isIncoming) {
                incomingConnections.remove(connection.id);
                nodesConnections = incomingNodesConnections;
            } else {
                outgoingConnections.remove(connection.id);
                nodesConnections = outgoingNodesConnections;
            }
            Set<String> nodeConnections = nodesConnections.get(connection.nodeId);
            if (nodeConnections != null) {
                nodeConnections.remove(connection.id);
                if (nodeConnections.isEmpty()) {
                    disconnectConnections(connection.nodeId, isIncoming, isOutgoing);
                } else {
                    disconnectConnections(connection.nodeId, false, isOutgoing);
                }
            }
            refreshConnections();
        });
    }

    @Override
    public void onMessage(@NonNull Connection connection, @NonNull byte[] data) {
        Log.d(TAG, String.format("onMessage: from connection: %s", connection));
        if (handler == null) return;
        handler.post(() -> {
            if (messageReceived != null) {
                messageReceived.call(data, connection.nodeId);
            }
            speedTool.addDownloadValue(data.length);
        });
    }
}
