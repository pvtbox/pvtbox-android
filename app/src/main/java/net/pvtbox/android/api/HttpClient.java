package net.pvtbox.android.api;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.datatheorem.android.trustkit.TrustKit;

import net.pvtbox.android.application.Const;
import net.pvtbox.android.service.PreferenceService;
import net.pvtbox.android.tools.JSON;

import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;

import static net.pvtbox.android.tools.Hasher.sha512;

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
public class HttpClient {
    private final static String TAG = HttpClient.class.getSimpleName();

    @NonNull
    private static final HashSet<String> updateNodeHashErrcodes = new HashSet<String>() {{
        add("USER_NODE_MISMATCH");
        add("NODEHASH_EXIST");
        add("NODE_EXIST");
        add("BAD_NODE_STATUS");
    }};
    // make private after removing stun interceptor
    @NonNull
    private static final HashSet<String> updateNodeSignErrcodes = new HashSet<String>() {{
        add("SIGNATURE_INVALID");
        add("FLST");
        add("NODE_SIGN_NOT_FOUND");
        add("USER_NODE_MISMATCH");
        add("NODEHASH_EXIST");
        add("NODE_EXIST");
    }};
    @NonNull
    private static final HashSet<String> expectedErrcodes = new HashSet<String>() {{
        add("FS_SYNC");
        add("FS_SYNC_NOT_FOUND");
        add("FS_SYNC_PARENT_NOT_FOUND");
        add("EMAIL_EXIST");
        add("USER_NOT_FOUND");
        add("WRONG_DATA");
        add("OPERATION_DENIED");
        add("LOCKED_CAUSE_TOO_MANY_BAD_LOGIN");
        add("LICENSE_LIMIT");
        add("NODE_LOGOUT_EXIST");
        add("NODE_WIPED");
        add("FAILED_SEND_EMAIL");
        add("ERROR_COLLABORATION_DATA");
    }};
    @NonNull
    final Context context;

    public interface SuccessResposeHandler {
        void call(JSONObject response);
    }

    public interface ErrorResponseHandler {
        void call(JSONObject error);
    }

    public interface DataResponseHandler {
        void call(BufferedSource source);
    }

    private interface Callback {
        void call(String data);
    }

    private static HandlerThread requestsThread;
    Handler requestsHandler;

    static private TrustKit kit;
    static OkHttpClient pvtboxClient;
    static OkHttpClient client;

    final PreferenceService preferenceService;

    private static final ReentrantLock lock = new ReentrantLock();
    private static int instanceCount = 0;

    HttpClient(@NonNull Context context, PreferenceService preferenceService) {
        this.preferenceService = preferenceService;
        this.context = context;
        try {
            lock.lock();
            if (instanceCount == 0) {
                requestsThread = new HandlerThread(
                        "HttpClient", HandlerThread.NORM_PRIORITY);
                requestsThread.start();
                try {
                    kit = TrustKit.initializeWithNetworkSecurityConfiguration(context);
                } catch (IllegalStateException e) {
                    kit = TrustKit.getInstance();
                } catch (Exception e) {
                    Log.w(TAG, "HttpClient: ", e);
                    throw e;
                }
            } else {
                kit = TrustKit.getInstance();
            }
            requestsHandler = new Handler(requestsThread.getLooper());

            pvtboxClient = new OkHttpClient.Builder()
                    .sslSocketFactory(
                            kit.getSSLSocketFactory("pvtbox.net"),
                            kit.getTrustManager("pvtbox.net"))
                    .callTimeout(20, TimeUnit.SECONDS)
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .build();
            client = new OkHttpClient.Builder()
                    .callTimeout(20, TimeUnit.SECONDS)
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .build();

            instanceCount++;
        } finally {
            lock.unlock();
        }
    }

    public void onDestroy() {
        try {
            lock.lock();
            instanceCount--;
            if (instanceCount == 0) {
                requestsHandler.removeCallbacksAndMessages(null);
                requestsThread.quitSafely();
            }
        } finally {
            lock.unlock();
        }
    }

    void makeRequest(
            String api, String action, @NonNull JSONObject data,
            @Nullable SuccessResposeHandler onSuccess, @Nullable ErrorResponseHandler onError,
            @Nullable DataResponseHandler onData) {
        makeRequest(api, action, data,
                false, false, 0,
                onSuccess, onError, onData);
    }

    private void makeRequest(
            String api, String action, @Nullable JSONObject data,
            boolean forceUpdateNodeHash, boolean forceUpdateNodeSign, int retryCount,
            @Nullable SuccessResposeHandler onSuccess, @Nullable ErrorResponseHandler onError,
            @Nullable DataResponseHandler onData) {
        if (data == null) {
            if (onError != null) {
                onError.call(null);
            }
            return;
        }
        if ("events".equals(api)) {
            if (preferenceService.getUserHash() == null) return;
            try {
                data.putOpt("user_hash", preferenceService.getUserHash());
            } catch (JSONException e) {
                e.printStackTrace();
                if (onError != null) {
                    onError.call(null);
                }
                return;
            }
        }
        try {
            data.putOpt("node_hash", getNodeHash(forceUpdateNodeHash));
        } catch (JSONException e) {
            e.printStackTrace();
            if (onError != null) {
                onError.call(null);
            }
            return;
        }
        getNodeSign(forceUpdateNodeSign, nodeSign -> {
            if (nodeSign == null) {
                if (onError != null) {
                    onError.call(null);
                }
                return;
            }
            try {
                data.putOpt("node_sign", nodeSign);
            } catch (JSONException e) {
                e.printStackTrace();
                if (onError != null) {
                    onError.call(null);
                }
                return;
            }
            JSONObject req;
            try {
                req = new JSONObject()
                        .putOpt("action", action)
                        .putOpt("data", data);
            } catch (JSONException e) {
                e.printStackTrace();
                if (onError != null) {
                    onError.call(null);
                }
                return;
            }
            executeRequest(api, req, retryCount, onSuccess, onError, onData);
        });
    }

    @Nullable
    private String getNodeHash(boolean forceUpdate) {
        String nodeHash;
        if (forceUpdate) {
            nodeHash = generateNodeHash();
        } else {
            nodeHash = preferenceService.getNodeHash();
            if (nodeHash == null) {
                nodeHash = generateNodeHash();
            }
        }
        return nodeHash;
    }

    @Nullable
    private String generateNodeHash() {
        String nodeHash = sha512(UUID.randomUUID().toString());
        preferenceService.setNodeHash(nodeHash);
        return nodeHash;
    }

    private void getNodeSign(boolean forceUpdate, @NonNull Callback completion) {
        if (forceUpdate) {
            generateNodeSign(completion);
        } else {
            String nodeSign = preferenceService.getNodeSign();
            if (nodeSign == null) {
                generateNodeSign(completion);
            } else {
                completion.call(nodeSign);
            }
        }
    }

    private void generateNodeSign(@NonNull Callback completion) {
        getIp(ip -> {
            if (ip == null) {
                completion.call(null);
            } else {
                String nodeSign = sha512(getNodeHash(false) + ip);
                preferenceService.setNodeSign(nodeSign);
                completion.call(nodeSign);
            }
        });
    }

    private void getIp(@NonNull Callback completion) {
        JSONObject req;
        try {
            req = new JSONObject()
                    .putOpt("action", "stun")
                    .putOpt("data", new JSONObject()
                            .putOpt("get", "candidate"));
        } catch (JSONException e) {
            e.printStackTrace();
            completion.call(null);
            return;
        }
        executeRequest(
                "", req, 0,
                response -> {
                    String ip = String.valueOf(response.optLong("info", 0));
                    completion.call(ip);
                },
                error -> completion.call(null), null);
    }

    private void executeRequest(
            String api, @NonNull JSONObject data, int rc,
            @Nullable SuccessResposeHandler onSuccess, @Nullable ErrorResponseHandler onError,
            @Nullable DataResponseHandler onData) {
        String dataString = data.toString();

        String url = preferenceService.getHost() + Const.API + api;
        Log.i(TAG, String.format("executeRequest: %s, to %s", dataString, url));
        final int retryCount = rc + 1;

        Request request;
        try {
            request = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(
                            dataString, MediaType.parse("application/json; charset=utf-8")))
                    .build();
        } catch (Exception e) {
            if (onError != null) {
                onError.call(null);
            }
            return;
        }
        if (onData == null) {
            executeClientRequest(api, data, onSuccess, onError, retryCount, request);
        } else {
            executeStreamClientRequest(api, data, onError, onData, retryCount, request);
        }

    }

    private void executeClientRequest(
            String api, @NonNull JSONObject data,
            @Nullable SuccessResposeHandler onSuccess,
            @Nullable ErrorResponseHandler onError,
            int retryCount, Request request) {
        OkHttpClient cli = request.url().host().contains("pvtbox.net") ? pvtboxClient : client;
        cli.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                if (retryCount > 3) {
                    if (onError != null) {
                        onError.call(null);
                    }
                } else {
                    requestsHandler.postDelayed(
                            () -> executeRequest(api, data, retryCount,
                                    onSuccess, onError, null),
                            1000);
                }
            }

            @Override
            public void onResponse(@NotNull Call call, @NonNull Response response) {
                JSONObject res;
                ResponseBody body = response.body();
                if (body == null) {
                    if (onError != null) {
                        onError.call(null);
                    }
                    return;
                }
                try {
                    res = new JSONObject(body.string());
                } catch (Exception e) {
                    if (retryCount > 3) {
                        if (onError != null) {
                            onError.call(null);
                        }
                    } else {
                        requestsHandler.postDelayed(
                                () -> executeRequest(api, data,
                                        retryCount, onSuccess, onError, null),
                                1000);
                    }
                    return;
                }

                Log.i(TAG, String.format("onSuccess: %s", res));

                String result = JSON.optString(res, "result", "");
                assert result != null;
                switch (result) {
                    case "success":
                    case "queued":
                        if (onSuccess != null) {
                            onSuccess.call(res);
                        }
                        break;
                    default:
                        processError(
                                api, res, data, retryCount,
                                onSuccess, onError);
                }
            }
        });
    }

    private void executeStreamClientRequest(
            String api, @NonNull JSONObject data,
            @Nullable ErrorResponseHandler onError,
            @NonNull DataResponseHandler onData,
            int retryCount, Request request) {
        OkHttpClient.Builder streamClientBuilder = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS);
        if (request.url().host().contains("pvtbox.net")) {
                streamClientBuilder.sslSocketFactory(
                    kit.getSSLSocketFactory("pvtbox.net"),
                    kit.getTrustManager("pvtbox.net"));
        }
        streamClientBuilder.build().newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                if (retryCount > 3) {
                    if (onError != null) {
                        onError.call(null);
                    }
                } else {
                    requestsHandler.postDelayed(
                            () -> executeRequest(api, data, retryCount,
                                    null, onError, onData),
                            1000);
                }
            }

            @Override
            public void onResponse(@NotNull Call call, @NonNull Response response) {
                if (response.code() == 404) {
                    JSONObject res = null;
                    try {
                        res = new JSONObject()
                                .putOpt("errcode", 404);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    if (onError != null) {
                        onError.call(res);
                    }
                    return;
                } else if (!response.isSuccessful()) {
                    if (onError != null) {
                        onError.call(null);
                    }
                    return;
                }

                ResponseBody body = response.body();
                if (body == null) {
                    if (onError != null) {
                        onError.call(null);
                    }
                    return;
                }
                BufferedSource source = body.source();
                onData.call(source);
            }
        });
    }

    private void processError(
            String api, JSONObject res, @NonNull JSONObject data,
            int retryCount, SuccessResposeHandler onSuccess, @Nullable ErrorResponseHandler onError) {
        String errcode = JSON.optString(res, "errcode", null);
        Runnable retryRequestRunnable = () -> executeRequest(api, data, retryCount,
                onSuccess, onError, null);
        if ("FS_TRY_LATER".equals(errcode)) {
            requestsHandler.postDelayed(
                    retryRequestRunnable,
                    1000);
            return;
        }
        if (retryCount > 3) {
            if (onError != null) {
                onError.call(res);
            }
            return;
        }
        if (errcode == null) {
            requestsHandler.postDelayed(
                    retryRequestRunnable,
                    1000);
            return;
        }
        if (expectedErrcodes.contains(errcode)) {
            if (onError != null) {
                onError.call(res);
            }
            return;
        }
        boolean updateNodeHash = updateNodeHashErrcodes.contains(errcode);
        boolean updateNodeSign = updateNodeSignErrcodes.contains(errcode);

        if (updateNodeHash || updateNodeSign) {
            makeRequest(
                    api, JSON.optString(data, "action"),
                    data.optJSONObject("data"),
                    updateNodeHash, updateNodeSign, retryCount, onSuccess, onError, null);
            return;
        }

        requestsHandler.postDelayed(
                retryRequestRunnable,
                1000);

    }
}
