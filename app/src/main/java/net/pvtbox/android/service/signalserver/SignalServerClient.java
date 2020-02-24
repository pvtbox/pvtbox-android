package net.pvtbox.android.service.signalserver;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.HandlerThread;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.net.ConnectivityManagerCompat;
import android.util.Log;

import com.datatheorem.android.trustkit.TrustKit;
import com.neovisionaries.ws.client.OpeningHandshakeException;
import com.neovisionaries.ws.client.ThreadType;
import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketException;
import com.neovisionaries.ws.client.WebSocketFactory;
import com.neovisionaries.ws.client.WebSocketFrame;
import com.neovisionaries.ws.client.WebSocketListener;
import com.neovisionaries.ws.client.WebSocketState;

import net.pvtbox.android.R;
import net.pvtbox.android.service.PreferenceService;
import net.pvtbox.android.db.DataBaseService;
import net.pvtbox.android.service.transport.Connectivity.ConnectivityService;
import net.pvtbox.android.tools.JSON;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.net.ssl.SSLSocketFactory;

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
public abstract class SignalServerClient implements WebSocketListener {
    @NonNull
    private final String TAG;
    private static final int CONNECT_TIMEOUT = 10_000;
    private static final int PING_TIMEOUT = 29 * 1000;
    private static final int PING_CHECK_INTERVAL = 30 * 1000;
    private static final long RECONNECT_DELAY = 2 * 1000;

    Context context;

    @Nullable
    String serverUrl = null;

    final PreferenceService preferenceService;
    final DataBaseService dataBaseService;

    private ConnectivityService connectivityService;
    private boolean started = false;
    private long lastPingTime = 0;
    @Nullable
    private WebSocket webSocket = null;
    @NonNull
    private final WebSocketFactory factory;
    private final Lock connectLock = new ReentrantLock();

    private final HandlerThread handlerThread = new HandlerThread(
            "signal_server_client",
            HandlerThread.NORM_PRIORITY - HandlerThread.MIN_PRIORITY);
    @NonNull
    final Handler handler;
    private final HandlerThread processingHandlerThread = new HandlerThread(
            "signal_processing",
            HandlerThread.NORM_PRIORITY - HandlerThread.MIN_PRIORITY);
    @NonNull
    final Handler processingHandler;

    @Nullable
    private Runnable pingCheckWork = null;
    private final Runnable startRunnable = () -> {
        if (started) {
            start();
        }
    };
    private final Runnable checkAndReconnectRunnable = this::checkAndReconnectIfNeeded;

    SignalServerClient(String name,
                       @NonNull Context context,
                       PreferenceService preferenceService,
                       DataBaseService dataBaseService) {
        TAG = SignalServerClient.class.getSimpleName() + "_" + name;
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
        processingHandlerThread.start();
        processingHandler = new Handler(
                processingHandlerThread.getLooper());
        this.context = context;
        this.preferenceService = preferenceService;
        this.dataBaseService = dataBaseService;

        factory = new WebSocketFactory();
        factory.setConnectionTimeout(CONNECT_TIMEOUT);
        if (!preferenceService.isSelfHosted()) {
            TrustKit kit;
            try {
                kit = TrustKit.initializeWithNetworkSecurityConfiguration(context);
            } catch (Exception e) {
                kit = TrustKit.getInstance();
            }
            SSLSocketFactory socketFactory = kit.getSSLSocketFactory("pvtbox.net");
            factory.setSSLSocketFactory(socketFactory);
        }
    }

    void onDestroy() {
        Log.d(TAG, "onDestroy");
        stop(true);
        processingHandler.removeCallbacksAndMessages(null);
        processingHandlerThread.quitSafely();
        handler.removeCallbacksAndMessages(null);
        handlerThread.quitSafely();
    }

    public void setConnectivityService(ConnectivityService connectivityService) {
        this.connectivityService = connectivityService;
    }

    public boolean isStarted() {
        return started;
    }

    public void start() {
        handler.post(() -> {
            started = true;
            String url = getUrl();
            if (url == null) {
                broadcastConnectingToServer(R.string.connecting_to_server,
                        R.string.wait_while_connecting);
                handler.removeCallbacks(startRunnable);
                handler.postDelayed(startRunnable, RECONNECT_DELAY);
                return;
            }
            stop(false);

            ConnectivityManager connManager = (ConnectivityManager) context.getSystemService(
                    Context.CONNECTIVITY_SERVICE);
            if (connManager != null &&
                    ConnectivityManagerCompat.isActiveNetworkMetered(connManager)) {
                if (!preferenceService.canUseCellular()) {
                    broadcastConnectingToServer(R.string.connectivity_problem,
                            R.string.connectivity_problem_cellular);
                    handler.removeCallbacks(startRunnable);
                    handler.postDelayed(startRunnable, RECONNECT_DELAY);
                    return;
                } else {
                    NetworkInfo networkInfo = connManager.getActiveNetworkInfo();
                    boolean roamingEnabled = networkInfo != null && networkInfo.isRoaming();
                    if (roamingEnabled && !preferenceService.canUseRoaming()) {
                        broadcastConnectingToServer(R.string.connectivity_problem,
                                R.string.connectivity_problem_roaming);
                        handler.removeCallbacks(startRunnable);
                        handler.postDelayed(startRunnable, RECONNECT_DELAY);
                        return;
                    }
                }
            }

            try {
                connectLock.lock();
                webSocket = factory.createSocket(url, CONNECT_TIMEOUT);
                webSocket.getConnectedSocket().setSoTimeout(CONNECT_TIMEOUT);
                webSocket.getConnectedSocket().setReuseAddress(true);
                webSocket.getConnectedSocket().setTcpNoDelay(true);
            } catch (Exception e) {
                Log.w(TAG, "start: failed", e);
                handler.removeCallbacks(startRunnable);
                handler.postDelayed(startRunnable, RECONNECT_DELAY);
                broadcastConnectingToServer(R.string.connectivity_problem,
                        R.string.check_internet_connection);
                return;
            } finally {
                connectLock.unlock();
            }
            webSocket.addListener(this);
            handler.removeCallbacks(checkAndReconnectRunnable);
            handler.postDelayed(checkAndReconnectRunnable, 10_000);
            Log.d(TAG, "connect");
            webSocket.connectAsynchronously();
        });
    }

    abstract protected void onAccessDenied();

    @Nullable
    abstract protected String getUrl();

    void broadcastConnectingToServer(int header, int text) {
    }

    void stop(boolean quit) {
        try {
            connectLock.lock();
            handler.removeCallbacks(checkAndReconnectRunnable);
            started = !quit;
            if (webSocket != null) {
                webSocket.clearListeners();
                webSocket.disconnect();
                webSocket = null;
                connectivityService.onDisconnectedFromServer();
            }
        } finally {
            connectLock.unlock();
        }
    }

    private void checkAndReconnectIfNeeded() {
        Log.d(TAG, "checkAndReconnectIfNeeded");
        try {
            connectLock.lock();
            if (started && (webSocket == null || !webSocket.isOpen())) {
                Log.d(TAG, "checkAndReconnectIfNeeded: not connected after timeout, reconnect");
                handler.removeCallbacks(startRunnable);
                handler.post(startRunnable);
            }
        } finally {
            connectLock.unlock();
        }
    }

    private void pingCheck() {
        if (pingCheckWork == null) {
            pingCheckWork = () -> {
                pingCheckWork = null;
                Log.d(TAG, "pingCheck");
                if (webSocket == null || !webSocket.isOpen()) return;
                if (new Date().getTime() - lastPingTime > PING_TIMEOUT) {
                    Log.d(TAG, "reconnect by lose ping");
                    handler.removeCallbacks(startRunnable);
                    handler.post(startRunnable);
                } else {
                    pingCheck();
                }
            };
            handler.postDelayed(pingCheckWork, PING_CHECK_INTERVAL);
        }
    }

    private void handleSdp(JSONObject json) {
        JSONObject data = json.optJSONObject("data");
        connectivityService.onSdpMessage(
                JSON.optString(data, "message"),
                JSON.optString(data, "conn_uuid"),
                JSON.optString(json, "node_id"));
    }

    void handlePeerList(JSONObject json) {
        JSONArray data = json.optJSONArray("data");
        if (data == null || data.length() == 0) {
            return;
        }
        connectivityService.setNodeList(data);
    }

    void handlePeerConnect(JSONObject json) {
        JSONObject peer = json.optJSONObject("data");
        if (peer == null) return;
        connectivityService.onNodeConnected(peer);
    }

    void handleDisconnectPeer(JSONObject json) {
        String nodeId = JSON.optString(json, "node_id");
        if (nodeId == null) return;
        connectivityService.onNodeDisconnected(nodeId);
    }

    public void sendMessage(String nodeId, String connectionId, String message) {
        JSONObject data;
        try {
            data = new JSONObject()
                    .putOpt("operation", "sdp")
                    .putOpt("node_id", nodeId)
                    .putOpt("data", new JSONObject()
                            .putOpt("conn_uuid", connectionId)
                            .putOpt("message", message));
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }

        sendString(data.toString());
    }

    public boolean isConnected() {
        return webSocket != null && webSocket.isOpen();
    }

    public void sendString(String json) {
        handler.post(() -> {
            Log.i(TAG, String.format("sendString %s", json));
            if (webSocket != null && webSocket.isOpen()) {
                webSocket.sendText(json);
            }
        });
    }

    @Override
    public void onConnected(WebSocket websocket, Map<String, List<String>> headers) {
        handler.post(() -> {
            Log.i(TAG, "onConnected");
            handler.removeCallbacks(checkAndReconnectRunnable);
            lastPingTime = new Date().getTime();
            pingCheck();
            broadcastConnectedToServer();
        });
    }

    void broadcastConnectedToServer() {
    }

    @Override
    public void onPingFrame(WebSocket websocket, WebSocketFrame frame) {
        handler.post(() -> lastPingTime = new Date().getTime());
    }

    @Override
    public void onPongFrame(WebSocket websocket, WebSocketFrame frame) {
        handler.post(() -> lastPingTime = new Date().getTime());
    }

    @Override
    public void onConnectError(WebSocket websocket, WebSocketException cause) {
        handler.post(() -> {
            Log.d(TAG, "onConnectError");
            if (cause instanceof OpeningHandshakeException) {
                OpeningHandshakeException c = (OpeningHandshakeException) cause;
                if (c.getStatusLine().getStatusCode() == 403) {
                    onAccessDenied();
                } else {
                    broadcastConnectingToServer(R.string.connectivity_problem,
                            R.string.service_unavailable);
                    handler.removeCallbacks(startRunnable);
                    handler.postDelayed(startRunnable, RECONNECT_DELAY);
                }
            } else {
                Log.w(TAG, "start: WebSocket error", cause);
                broadcastConnectingToServer(R.string.connectivity_problem,
                        R.string.check_internet_connection);
                handler.removeCallbacks(startRunnable);
                handler.postDelayed(startRunnable, RECONNECT_DELAY);
            }
        });
    }

    @Override
    public void onDisconnected(WebSocket websocket, WebSocketFrame serverCloseFrame, WebSocketFrame clientCloseFrame, boolean closedByServer) {
        Log.d(TAG, "onDisconnected");
        if (connectivityService != null) {
            connectivityService.onDisconnectedFromServer();
        }
        handler.removeCallbacks(startRunnable);
        handler.post(startRunnable);
    }

    @Override
    public void onTextMessage(WebSocket websocket, @NonNull byte[] data) {
        try {
            String text = new String(data);
            onTextMessage(websocket, text);
        } catch (Exception e) {
            Log.e(TAG, "onTextMessage: failed byte to string conversation", e);
        }
    }

    @Override
    public void onTextMessage(WebSocket websocket, @NonNull String text) {
        handler.post(() -> lastPingTime = new Date().getTime());
        processingHandler.post(() -> {
            try {
                handleMessage(text);
            } catch (Exception e) {
                Log.e(TAG, String.format("onTextMessage: handling failure: %s", text), e);
                e.printStackTrace();
            }
        });
    }

    private void handleMessage(@NonNull String text) {
        JSONObject json;
        try {
            json = new JSONObject(text);
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }
        Log.i(TAG, text);
        switch (Objects.requireNonNull(JSON.optString(json, "operation", ""))) {
            case "sdp":
                handleSdp(json);
                break;
            case "upload_add":
                handleUploadAdd(json);
                break;
            case "upload_cancel":
                handleUploadCancel(json);
                break;
            case "remote_action":
                handleRemoteAction(json);
                break;
            case "sharing_enable":
                handleSharingEnable(json);
                break;
            case "sharing_disable":
                handleDisableShare(json);
                break;
            case "node_status":
                handelNodeStatus(json);
                break;
            case "license_type":
                handleLicenceType(json);
                break;
            case "peer_list":
                handlePeerList(json);
                break;
            case "peer_disconnect":
                handleDisconnectPeer(json);
                break;
            case "peer_connect":
                handlePeerConnect(json);
                break;
            case "sharing_list":
                handleSharingList(json);
                break;
            case "share_info":
                handleShareInfo(json);
                break;
            case "file_events":
                handleFileEvents(json);
                break;
            case "patches_info":
                handlePatchesInfo(json);
                break;
            case "collaborated_folders":
                handleCollaboratedFolders(json);
                break;
            case "min_stored_event":
                handleMinStoredEvent(json);
                break;
            case "new_notifications_count":
                handleNewNotificationsCount(json);
        }
    }

    void handleUploadAdd(JSONObject json) {
    }

    void handleUploadCancel(JSONObject json) {
    }

    void handleRemoteAction(JSONObject json) {
    }

    void handleSharingEnable(JSONObject json) {
    }

    void handleDisableShare(JSONObject json) {
    }

    void handelNodeStatus(JSONObject json) {
    }

    void handleLicenceType(JSONObject json) {
    }

    void handleSharingList(JSONObject json) {
    }

    void handleShareInfo(JSONObject json) {
    }

    void handleFileEvents(JSONObject json) {
    }

    void handlePatchesInfo(JSONObject json) {
    }

    void handleCollaboratedFolders(JSONObject json) {
    }

    void handleMinStoredEvent(JSONObject json) {
    }

    void handleNewNotificationsCount(JSONObject json) {
    }

    @Override
    public void onStateChanged(WebSocket websocket, WebSocketState newState) {
        Log.i(TAG, String.format("onStateChanged: %s", newState));
    }

    @Override
    public void onFrame(WebSocket websocket, WebSocketFrame frame) {

    }

    @Override
    public void onContinuationFrame(WebSocket websocket, WebSocketFrame frame) {

    }

    @Override
    public void onTextFrame(WebSocket websocket, WebSocketFrame frame) {

    }

    @Override
    public void onBinaryFrame(WebSocket websocket, WebSocketFrame frame) {

    }

    @Override
    public void onCloseFrame(WebSocket websocket, WebSocketFrame frame) {

    }

    @Override
    public void onBinaryMessage(WebSocket websocket, byte[] binary) {

    }

    @Override
    public void onSendingFrame(WebSocket websocket, WebSocketFrame frame) {

    }

    @Override
    public void onFrameSent(WebSocket websocket, WebSocketFrame frame) {

    }

    @Override
    public void onFrameUnsent(WebSocket websocket, WebSocketFrame frame) {

    }

    @Override
    public void onThreadCreated(WebSocket websocket, ThreadType threadType, Thread thread) {

    }

    @Override
    public void onThreadStarted(WebSocket websocket, ThreadType threadType, Thread thread) {

    }

    @Override
    public void onThreadStopping(WebSocket websocket, ThreadType threadType, Thread thread) {

    }

    @Override
    public void onError(WebSocket websocket, WebSocketException cause) {
        Log.w(TAG, "onError");
    }

    @Override
    public void onFrameError(WebSocket websocket, WebSocketException cause, WebSocketFrame frame) {

    }

    @Override
    public void onMessageError(WebSocket websocket, WebSocketException cause, List<WebSocketFrame> frames) {

    }

    @Override
    public void onMessageDecompressionError(WebSocket websocket, WebSocketException cause, byte[] compressed) {

    }

    @Override
    public void onTextMessageError(WebSocket websocket, WebSocketException cause, byte[] data) {

    }

    @Override
    public void onSendError(WebSocket websocket, WebSocketException cause, WebSocketFrame frame) {

    }

    @Override
    public void onUnexpectedError(WebSocket websocket, WebSocketException cause) {

    }

    @Override
    public void handleCallbackError(WebSocket websocket, Throwable cause) {

    }

    @Override
    public void onSendingHandshake(WebSocket websocket, String requestLine, List<String[]> headers) {

    }

    public void setServers(@NonNull JSONArray servers) {
        for (int i = 0; i < servers.length(); ++i) {
            JSONObject server = servers.optJSONObject(i);
            if ("SIGN".equals(JSON.optString(server, "server_type"))) {
                serverUrl = JSON.optString(server, "server_url");
                return;
            }
        }
    }
}
