package net.pvtbox.android.service.transport.Connectivity;

import android.os.Handler;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.pvtbox.android.service.signalserver.SignalServerClient;
import net.pvtbox.android.tools.JSON;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.RtpReceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

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
class Connection implements PeerConnection.Observer, DataChannel.Observer, SdpObserver {
    private static final int MAX_BUFFER_CAPACITY = 16 * 1024 * 1024;
    @NonNull
    private final String TAG;
    public final String id;
    public final String nodeId;
    @NonNull
    private final AtomicBoolean isOpen = new AtomicBoolean(false);
    private final Handler handler;
    @Nullable
    private PeerConnection peerConnection;
    private final SignalServerClient signalServerService;
    private final ConnectionListener listener;
    @NonNull
    private final List<DataChannel> dataChannels = new ArrayList<>();
    @Nullable
    private List<IceCandidate> queuedRemoteCandidates = new ArrayList<>();
    @NonNull
    private final AtomicInteger channelSelector = new AtomicInteger(0);

    Connection(Handler handler, String id, String nodeId,
               SignalServerClient signalServerService, ConnectionListener listener) {
        this.handler = handler;
        this.id = id;
        this.nodeId = nodeId;
        this.signalServerService = signalServerService;
        this.listener = listener;
        TAG = Connection.class.getSimpleName() + '_' + id;
    }

    void setConnection(PeerConnection conn) {
        peerConnection = conn;
    }

    void initiateConnection() {
        DataChannel.Init init = new DataChannel.Init();
        init.ordered = false;
        init.negotiated = false;
        if (peerConnection == null) return;
        DataChannel channel = peerConnection.createDataChannel(id + "_1", init);
        channel.registerObserver(this);
        dataChannels.add(channel);
        //channel = peerConnection.createDataChannel(id + "_2", init);
        //channel.registerObserver(this);
        //dataChannels.add(channel);
        peerConnection.createOffer(this, new MediaConstraints());
    }

    void onSdpMessage(@NonNull String message) {
        try {
            JSONObject json = new JSONObject(message);
            if (json.has("candidate")) {
                IceCandidate candidate = new IceCandidate(
                        JSON.optString(json, "sdpMid"),
                        json.optInt("sdpMLineIndex", 0),
                        JSON.optString(json, "candidate"));
                addCandidate(candidate);
            } else {
                SessionDescription sdp = new SessionDescription(
                        SessionDescription.Type.fromCanonicalForm(
                                Objects.requireNonNull(JSON.optString(json, "type"))),
                        JSON.optString(json, "sdp"));
                Log.d(TAG, String.format("onSdpMessage: setRemoteDescription: %s", sdp.type.canonicalForm()));
                if (peerConnection != null) {
                    peerConnection.setRemoteDescription(this, sdp);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void addCandidate(@NonNull IceCandidate candidate) {
        Log.d(TAG, String.format("onSdpMessage: addIceCandidate: %s", candidate.toString()));
        if (queuedRemoteCandidates != null) {
            queuedRemoteCandidates.add(candidate);
        } else if (peerConnection != null) {
            peerConnection.addIceCandidate(candidate);
        }
    }

    private void drainCandidates() {
        if (queuedRemoteCandidates == null) return;
        for (IceCandidate candidate : queuedRemoteCandidates) {
            if (peerConnection != null) {
                peerConnection.addIceCandidate(candidate);
            }
        }
        queuedRemoteCandidates = null;
    }

    public void send(ByteBuffer data) {
        if (dataChannels.isEmpty()) {
            Log.e(TAG, "send called without opened datachannel");
            return;
        }

        for (int i = 0; i <= dataChannels.size(); ++i) {
            int c = channelSelector.incrementAndGet();
            if (c >= dataChannels.size()) {
                channelSelector.set(0);
                c = 0;
            }
            DataChannel channel = dataChannels.get(c);
            if (channel.state() == DataChannel.State.OPEN &&
                    channel.bufferedAmount() < MAX_BUFFER_CAPACITY - 1024 * 1024) {
                channel.send(new DataChannel.Buffer(data, true));
                return;
            }
        }
    }

    void disconnect() {
        isOpen.set(false);
        for (DataChannel channel : dataChannels) {
            channel.unregisterObserver();
            channel.close();
        }
        dataChannels.clear();

        if (peerConnection != null) {
            peerConnection.close();
            peerConnection = null;
        }
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Connection that = (Connection) o;

        return Objects.equals(id, that.id);
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    boolean isBufferOverflow() {
        for (DataChannel channel : dataChannels) {
            if (channel.bufferedAmount() < MAX_BUFFER_CAPACITY / 2) {
                return false;
            }
        }
        return true;
    }

    boolean isOpen() {
        return isOpen.get();
    }

    // PeerConnection.Observer
    @Override
    public void onConnectionChange(@NonNull PeerConnection.PeerConnectionState newState) {
        Log.d(TAG, String.format("onConnectionChange: %s", newState.toString()));
    }

    public void onSignalingChange(@NonNull PeerConnection.SignalingState signalingState) {
        Log.d(TAG, String.format("onSignalingChange: %s", signalingState.toString()));
    }

    @Override
    public void onIceConnectionChange(@NonNull PeerConnection.IceConnectionState iceConnectionState) {
        Log.d(TAG, String.format("onIceConnectionChange: %s", iceConnectionState.toString()));
    }

    @Override
    public void onIceConnectionReceivingChange(boolean b) {
        Log.d(TAG, "onIceConnectionReceivingChange: ");
    }

    @Override
    public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
        Log.d(TAG, "onIceGatheringChange: ");
    }

    @Override
    public void onIceCandidate(@NonNull IceCandidate iceCandidate) {
        Log.d(TAG, "onIceCandidate: ");
        JSONObject json = new JSONObject();
        try {
            json.put("sdpMLineIndex", iceCandidate.sdpMLineIndex);
            json.put("sdpMid", iceCandidate.sdpMid);
            json.put("candidate", iceCandidate.sdp);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        signalServerService.sendMessage(nodeId, id, json.toString());
    }

    @Override
    public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {
        Log.d(TAG, "onIceCandidatesRemoved: ");
    }

    @Override
    public void onAddStream(MediaStream mediaStream) {
        Log.d(TAG, "onAddStream: ");
    }

    @Override
    public void onRemoveStream(MediaStream mediaStream) {
        Log.d(TAG, "onRemoveStream: ");
    }

    @Override
    public void onDataChannel(@NonNull DataChannel dataChannel) {
        Log.d(TAG, "onDataChannel: ");
        dataChannel.registerObserver(this);
        handler.post(() -> dataChannels.add(dataChannel));
    }

    @Override
    public void onRenegotiationNeeded() {
        Log.d(TAG, "onRenegotiationNeeded: ");
    }

    @Override
    public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {
        Log.d(TAG, "onAddTrack: ");
    }

    // DataChannel.Observer
    @Override
    public void onBufferedAmountChange(long l) {
        Log.d(TAG, String.format("onBufferedAmountChange: %d", l));
    }

    @Override
    public void onStateChange() {
        handler.post(() -> {
            if (dataChannels.isEmpty()) return;
            for (DataChannel channel : dataChannels) {
                Log.i(TAG, String.format("onStateChange: %s, wasOpen: %s, %s",
                        channel.state().toString(), isOpen.get(), nodeId));
                if (channel.state() == DataChannel.State.OPEN && isOpen.compareAndSet(false, true)) {
                    Log.i(TAG, String.format("onStateChange: update open to true, isOpen: %s",
                            isOpen.get()));
                    listener.onConnected(this);
                } else if ((channel.state() == DataChannel.State.CLOSING ||
                        channel.state() == DataChannel.State.CLOSED) && isOpen.compareAndSet(true, false)) {
                    Log.i(TAG, String.format("onStateChange: update open to false, isOpen: %s",
                            isOpen.get()));
                    listener.onDisconnected(this);
                    disconnect();
                    return;
                }
            }
        });
    }

    @Override
    public void onMessage(@NonNull DataChannel.Buffer buffer) {
        Log.d(TAG, "onMessage: ");
        byte[] data = new byte[buffer.data.remaining()];
        buffer.data.get(data);
        handler.post(() -> listener.onMessage(this, data));
    }

    // SdpObserver
    @Override
    public void onCreateSuccess(@NonNull SessionDescription sessionDescription) {
        handler.post(() -> {
            if (peerConnection == null) return;
            Log.d(TAG, String.format("onCreateSuccess: %s", sessionDescription.type.canonicalForm()));
            peerConnection.setLocalDescription(this, sessionDescription);
            JSONObject json = new JSONObject();
            try {
                json.put("type", sessionDescription.type.canonicalForm());
                json.put("sdp", sessionDescription.description);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            signalServerService.sendMessage(nodeId, id, json.toString());
        });
    }

    @Override
    public void onSetSuccess() {
        Log.d(TAG, "onSetSuccess: ");
        handler.post(() -> {
            if (peerConnection == null) return;
            if (peerConnection.getLocalDescription() != null &&
                    peerConnection.getRemoteDescription() != null) {
                drainCandidates();
            } else if (peerConnection.getRemoteDescription() != null) {
                peerConnection.createAnswer(this, new MediaConstraints());
            }
        });
    }

    @Override
    public void onCreateFailure(String s) {
        Log.d(TAG, String.format("onCreateFailure: %s", s));
    }

    @Override
    public void onSetFailure(String s) {
        Log.d(TAG, String.format("onSetFailure: %s", s));
    }
}
