package net.pvtbox.android.api;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.pvtbox.android.R;
import net.pvtbox.android.application.Const;
import net.pvtbox.android.service.PreferenceService;
import net.pvtbox.android.tools.JSON;

import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Objects;

import okhttp3.Call;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

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
public class AuthHttpClient extends HttpClient {
    private static final String TAG = AuthHttpClient.class.getSimpleName();

    public AuthHttpClient(@NonNull Context context, PreferenceService preferenceService) {
        super(context, preferenceService);
    }

    public interface ShareSuccess {
        void call();
    }

    public interface ShareWrongPassword {
        void call();
    }

    public interface ShareError {
        void call(String info);
    }

    public void register(
            @NonNull String email, @NonNull String password,
            @Nullable SuccessResposeHandler onSuccess, @Nullable ErrorResponseHandler onError) {
        requestsHandler.post(() -> {
            JSONObject data;
            try {
                data = new JSONObject()
                        .putOpt("node_devicetype", Const.nodeType)
                        .putOpt("node_ostype", Const.nodeOsType)
                        .putOpt("node_osname", Const.nodeOsName)
                        .putOpt("node_name", Const.nodeName)
                        .putOpt("user_email", email)
                        .putOpt("user_password", sha512(password));
            } catch (JSONException e) {
                e.printStackTrace();
                if (onError != null) {
                    onError.call(null);
                }
                return;
            }

            makeRequest(
                    "", "signup", data,
                    onSuccess, onError, null);
        });
    }

    public void login(
            @NonNull String email, @NonNull String password,
            @Nullable SuccessResposeHandler onSuccess, @Nullable ErrorResponseHandler onError) {
        requestsHandler.post(() -> {
            JSONObject data;
            try {
                data = new JSONObject()
                        .putOpt("node_devicetype", Const.nodeType)
                        .putOpt("node_ostype", Const.nodeOsType)
                        .putOpt("node_osname", Const.nodeOsName)
                        .putOpt("node_name", Const.nodeName)
                        .putOpt("user_email", email)
                        .putOpt("user_password", sha512(password));
            } catch (JSONException e) {
                if (onError != null) {
                    onError.call(null);
                }
                e.printStackTrace();
                return;
            }

            makeRequest(
                    "", "login", data,
                    onSuccess, onError, null);
        });
    }

    public void login(
            @NonNull String userHash,
            @Nullable SuccessResposeHandler onSuccess, @Nullable ErrorResponseHandler onError) {
        requestsHandler.post(() -> {
            JSONObject data;
            try {
                data = new JSONObject()
                        .putOpt("node_devicetype", Const.nodeType)
                        .putOpt("node_ostype", Const.nodeOsType)
                        .putOpt("node_osname", Const.nodeOsName)
                        .putOpt("node_name", Const.nodeName)
                        .putOpt("user_hash", userHash);
            } catch (JSONException e) {
                e.printStackTrace();
                if (onError != null) {
                    onError.call(null);
                }
                return;
            }

            makeRequest(
                    "", "login", data,
                    onSuccess, onError, null);
        });
    }

    public void logout(@NonNull String userHash) {
        requestsHandler.post(() -> {
            JSONObject data;
            try {
                data = new JSONObject()
                        .putOpt("user_hash", userHash);
            } catch (JSONException e) {
                e.printStackTrace();
                return;
            }

            makeRequest(
                    "", "logout", data,
                    null, null, null);
        });
    }

    public void remoteActionDone(@NonNull String actionUuid, @NonNull String userHash) {
        requestsHandler.post(() -> {
            JSONObject data;
            try {
                data = new JSONObject()
                        .putOpt("user_hash", userHash)
                        .putOpt("action_uuid", actionUuid);
            } catch (JSONException e) {
                e.printStackTrace();
                return;
            }

            makeRequest(
                    "", "remote_action_done", data,
                    null, null, null);
        });
    }

    public void remindPassword(
            @NonNull String email,
            @Nullable SuccessResposeHandler onSuccess, @Nullable ErrorResponseHandler onError) {
        requestsHandler.post(() -> {
            JSONObject data;
            try {
                data = new JSONObject()
                        .putOpt("user_email", email);
            } catch (JSONException e) {
                if (onError != null) {
                    onError.call(null);
                }
                e.printStackTrace();
                return;
            }

            makeRequest(
                    "", "resetpassword", data,
                    onSuccess, onError, null);
        });
    }

    public void hideNode(
            @NonNull String id,
            @Nullable SuccessResposeHandler onSuccess, @Nullable ErrorResponseHandler onError) {
        requestsHandler.post(() -> {
            JSONObject data;
            try {
                data = new JSONObject()
                        .putOpt("node_id", id)
                        .putOpt("user_hash", preferenceService.getUserHash());
            } catch (JSONException e) {
                if (onError != null) {
                    onError.call(null);
                }
                e.printStackTrace();
                return;
            }

            makeRequest(
                    "", "hideNode", data,
                    onSuccess, onError, null);
        });
    }

    public void logoutNode(
            @NonNull String id,
            @Nullable SuccessResposeHandler onSuccess, @Nullable ErrorResponseHandler onError) {
        requestsHandler.post(() -> {
            JSONObject data;
            try {
                data = new JSONObject()
                        .putOpt("action_type", "logout")
                        .putOpt("target_node_id", id)
                        .putOpt("user_hash", preferenceService.getUserHash());
            } catch (JSONException e) {
                if (onError != null) {
                    onError.call(null);
                }
                e.printStackTrace();
                return;
            }

            makeRequest(
                    "", "execute_remote_action", data,
                    onSuccess, onError, null);
        });
    }

    public void wipeNode(
            @NonNull String id,
            @Nullable SuccessResposeHandler onSuccess, @Nullable ErrorResponseHandler onError) {
        requestsHandler.post(() -> {
            JSONObject data;
            try {
                data = new JSONObject()
                        .putOpt("action_type", "wipe")
                        .putOpt("target_node_id", id)
                        .putOpt("user_hash", preferenceService.getUserHash());
            } catch (JSONException e) {
                if (onError != null) {
                    onError.call(null);
                }
                e.printStackTrace();
                return;
            }

            makeRequest(
                    "", "execute_remote_action", data,
                    onSuccess, onError, null);
        });
    }

    public void support(
            @NonNull String text, @NonNull String subject,
            @Nullable SuccessResposeHandler onSuccess, @Nullable ErrorResponseHandler onError) {
        requestsHandler.post(() -> {
            JSONObject data;
            try {
                data = new JSONObject()
                        .putOpt("user_hash", preferenceService.getUserHash())
                        .putOpt("subject", subject)
                        .putOpt("body", text);
            } catch (JSONException e) {
                if (onError != null) {
                    onError.call(null);
                }
                e.printStackTrace();
                return;
            }

            makeRequest(
                    "", "support", data,
                    onSuccess, onError, null);
        });
    }

    public void checkSharePassword(
            @NonNull String url, @NonNull ShareSuccess onSuccess,
            @NonNull ShareWrongPassword onWrongPassword, @NonNull ShareError onError) {
        requestsHandler.post(() -> {
            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();
            client.newCall(request).enqueue(new okhttp3.Callback() {
                @Override
                public void onFailure(@NotNull Call call, @NotNull IOException e) {
                    onError.call(context.getString(R.string.network_error));
                }

                @Override
                public void onResponse(@NotNull Call call, @NonNull Response response) {
                    Log.i(TAG, String.format("onResponse: %s", response.toString()));
                    if (response.code() == 400) {
                        onSuccess.call();
                    } else {
                        try {
                            ResponseBody body = response.body();
                            if (body == null) {
                                onError.call(context.getString(R.string.network_error));
                                return;
                            }
                            JSONObject res = new JSONObject(body.string());
                            String errcode = JSON.optString(res, "errcode");
                            if (Objects.equals(errcode, "SHARE_WRONG_PASSWORD")) {
                                onWrongPassword.call();
                            } else if (Objects.equals(
                                    errcode, "LOCKED_CAUSE_TOO_MANY_BAD_LOGIN")) {
                                onError.call(context.getString(
                                        R.string.locked_after_too_many_incorrect_attempts));
                            } else {
                                onError.call(JSON.optString(res, "info"));
                            }
                        } catch (Exception e) {
                            onError.call(context.getString(R.string.network_error));
                        }
                    }
                }
            });
        });
    }
}
