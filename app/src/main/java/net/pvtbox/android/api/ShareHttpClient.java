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
public class ShareHttpClient extends HttpClient {
    public ShareHttpClient(@NonNull Context context, PreferenceService preferenceService) {
        super(context, preferenceService);
    }

    public void sharingEnable(
            String uuid, int ttl, String password, boolean keepPassword,
            SuccessResposeHandler onSuccess, @Nullable ErrorResponseHandler onError) {
        requestsHandler.post(() -> {
            JSONObject data;
            try {
                data = new JSONObject()
                        .putOpt("user_hash", preferenceService.getUserHash())
                        .putOpt("uuid", uuid)
                        .putOpt("share_ttl", ttl)
                        .putOpt("share_password", password == null ? "null" : password)
                        .putOpt("share_keep_password", keepPassword);
            } catch (JSONException e) {
                e.printStackTrace();
                if (onError != null) {
                    onError.call(null);
                }
                return;
            }

            makeRequest(
                    "sharing", "sharing_enable", data,
                    onSuccess, onError, null);
        });
    }

    public void sharingDisable(
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
                    "sharing", "sharing_disable", data,
                    onSuccess, onError, null);
        });
    }

    public void download(
            String id,
            @Nullable ErrorResponseHandler onError, @NonNull DataResponseHandler onData) {
        requestsHandler.post(() -> {
            JSONObject data;
            try {
                data = new JSONObject()
                        .putOpt("upload_id", id);
            } catch (JSONException e) {
                e.printStackTrace();
                if (onError != null) {
                    onError.call(null);
                }
                return;
            }

            makeRequest(
                    "events", "download", data,
                    null, onError, onData);
        });
    }
}
