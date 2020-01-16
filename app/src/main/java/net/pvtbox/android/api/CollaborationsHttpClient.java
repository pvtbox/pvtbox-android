package net.pvtbox.android.api;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.pvtbox.android.service.PreferenceService;

import org.json.JSONException;
import org.json.JSONObject;

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
public class CollaborationsHttpClient extends HttpClient {
    public CollaborationsHttpClient(@NonNull Context context, PreferenceService preferenceService) {
        super(context, preferenceService);
    }

    public void info(
            String uuid,
            SuccessResposeHandler onSuccess, @Nullable ErrorResponseHandler onError) {
        requestsHandler.post(() -> {
            JSONObject data;
            try {
                data = new JSONObject()
                        .putOpt("user_hash", preferenceService.getUserHash())
                        .putOpt("uuid", uuid);
            } catch (JSONException e) {
                e.printStackTrace();
                if (onError != null) {
                    onError.call(null);
                }
                return;
            }

            makeRequest(
                    "sharing", "collaboration_info", data,
                    onSuccess, onError, null);
        });
    }

    public void add(
            String uuid, String email, String permission,
            @Nullable SuccessResposeHandler onSuccess, @Nullable ErrorResponseHandler onError) {
        requestsHandler.post(() -> {
            JSONObject data;
            try {
                data = new JSONObject()
                        .putOpt("user_hash", preferenceService.getUserHash())
                        .putOpt("uuid", uuid)
                        .putOpt("colleague_email", email)
                        .putOpt("access_type", permission);
            } catch (JSONException e) {
                e.printStackTrace();
                if (onError != null) {
                    onError.call(null);
                }
                return;
            }

            makeRequest(
                    "sharing", "colleague_add", data,
                    onSuccess, onError, null);
        });
    }

    public void delete(
            String uuid, String id,
            @Nullable SuccessResposeHandler onSuccess, @Nullable ErrorResponseHandler onError) {
        requestsHandler.post(() -> {
            JSONObject data;
            try {
                data = new JSONObject()
                        .putOpt("user_hash", preferenceService.getUserHash())
                        .putOpt("uuid", uuid)
                        .putOpt("colleague_id", id);
            } catch (JSONException e) {
                e.printStackTrace();
                if (onError != null) {
                    onError.call(null);
                }
                return;
            }

            makeRequest(
                    "sharing", "colleague_delete", data,
                    onSuccess, onError, null);
        });
    }

    public void edit(
            String uuid, String id, String permission,
            @Nullable SuccessResposeHandler onSuccess, @Nullable ErrorResponseHandler onError) {
        requestsHandler.post(() -> {
            JSONObject data;
            try {
                data = new JSONObject()
                        .putOpt("user_hash", preferenceService.getUserHash())
                        .putOpt("uuid", uuid)
                        .putOpt("colleague_id", id)
                        .putOpt("access_type", permission);
            } catch (JSONException e) {
                e.printStackTrace();
                if (onError != null) {
                    onError.call(null);
                }
                return;
            }

            makeRequest(
                    "sharing", "colleague_edit", data,
                    onSuccess, onError, null);
        });
    }

    public void cancel(
            String uuid,
            @Nullable SuccessResposeHandler onSuccess, @Nullable ErrorResponseHandler onError) {
        requestsHandler.post(() -> {
            JSONObject data;
            try {
                data = new JSONObject()
                        .putOpt("user_hash", preferenceService.getUserHash())
                        .putOpt("uuid", uuid);
            } catch (JSONException e) {
                e.printStackTrace();
                if (onError != null) {
                    onError.call(null);
                }
                return;
            }

            makeRequest(
                    "sharing", "collaboration_cancel", data,
                    onSuccess, onError, null);
        });
    }

    public void leave(
            String uuid,
            @Nullable SuccessResposeHandler onSuccess, @Nullable ErrorResponseHandler onError) {
        requestsHandler.post(() -> {
            JSONObject data;
            try {
                data = new JSONObject()
                        .putOpt("user_hash", preferenceService.getUserHash())
                        .putOpt("uuid", uuid);
            } catch (JSONException e) {
                e.printStackTrace();
                if (onError != null) {
                    onError.call(null);
                }
                return;
            }

            makeRequest(
                    "sharing", "collaboration_leave", data,
                    onSuccess, onError, null);
        });
    }

    public void join(
            int colleagueId,
            SuccessResposeHandler onSuccess, @Nullable ErrorResponseHandler onError) {
        requestsHandler.post(() -> {
            JSONObject data;
            try {
                data = new JSONObject()
                        .putOpt("user_hash", preferenceService.getUserHash())
                        .putOpt("colleague_id", colleagueId);
            } catch (JSONException e) {
                e.printStackTrace();
                if (onError != null) {
                    onError.call(null);
                }
                return;
            }

            makeRequest(
                    "sharing", "collaboration_join", data,
                    onSuccess, onError, null);
        });
    }

    public void getNotifications(
            long from, long limit,
            SuccessResposeHandler onSuccess, @Nullable ErrorResponseHandler onError) {
        requestsHandler.post(() -> {
            JSONObject data;
            try {
                data = new JSONObject()
                        .putOpt("user_hash", preferenceService.getUserHash())
                        .putOpt("from", from)
                        .putOpt("limit", limit);
            } catch (JSONException e) {
                e.printStackTrace();
                if (onError != null) {
                    onError.call(null);
                }
                return;
            }

            makeRequest(
                    "", "getNotifications", data,
                    onSuccess, onError, null);
        });
    }

}
