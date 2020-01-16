package net.pvtbox.android.api;

import android.content.Context;

import androidx.annotation.NonNull;

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
public class EventsHttpClient extends HttpClient {
    public EventsHttpClient(@NonNull Context context, PreferenceService preferenceService) {
        super(context, preferenceService);
    }

    public void createFolder(
            String eventUuid, String parentUuid, String name,
            SuccessResposeHandler onSuccess, @NonNull ErrorResponseHandler onError) {
        requestsHandler.post(() -> {
            JSONObject data;
            try {
                data = new JSONObject()
                        .putOpt("event_uuid", eventUuid)
                        .putOpt("parent_folder_uuid", parentUuid)
                        .putOpt("folder_name", name);
            } catch (JSONException e) {
                e.printStackTrace();
                onError.call(null);
                return;
            }

            makeRequest(
                    "events", "folder_event_create", data,
                    onSuccess, onError, null);
        });
    }

    public void copyFolder(
            String eventUuid, String uuid, String parentUuid, String name,
            long lastEventId, SuccessResposeHandler onSuccess, @NonNull ErrorResponseHandler onError) {
        requestsHandler.post(() -> {
            JSONObject data;
            try {
                data = new JSONObject()
                        .putOpt("event_uuid", eventUuid)
                        .putOpt("target_parent_folder_uuid", parentUuid)
                        .putOpt("target_folder_name", name)
                        .putOpt("source_folder_uuid", uuid)
                        .putOpt("last_event_id", lastEventId);
            } catch (JSONException e) {
                e.printStackTrace();
                onError.call(null);
                return;
            }

            makeRequest(
                    "events", "folder_event_copy", data,
                    onSuccess, onError, null);
        });
    }

    public void moveFolder(
            String eventUuid, String uuid, String parentUuid, String name,
            long lastEventId, SuccessResposeHandler onSuccess, @NonNull ErrorResponseHandler onError) {
        requestsHandler.post(() -> {
            JSONObject data;
            try {
                data = new JSONObject()
                        .putOpt("event_uuid", eventUuid)
                        .putOpt("folder_uuid", uuid)
                        .putOpt("new_folder_name", name)
                        .putOpt("new_parent_folder_uuid", parentUuid)
                        .putOpt("last_event_id", lastEventId);
            } catch (JSONException e) {
                e.printStackTrace();
                onError.call(null);
                return;
            }

            makeRequest(
                    "events", "folder_event_move", data,
                    onSuccess, onError, null);
        });
    }

    public void deleteFolder(
            String eventUuid, String uuid, long lastEventId,
            SuccessResposeHandler onSuccess, @NonNull ErrorResponseHandler onError) {
        requestsHandler.post(() -> {
            JSONObject data;
            try {
                data = new JSONObject()
                        .putOpt("event_uuid", eventUuid)
                        .putOpt("folder_uuid", uuid)
                        .putOpt("last_event_id", lastEventId);
            } catch (JSONException e) {
                e.printStackTrace();
                onError.call(null);
                return;
            }

            makeRequest(
                    "events", "folder_event_delete", data,
                    onSuccess, onError, null);
        });
    }

    public void createFile(
            String eventUuid, String parentUuid, String name, long size,
            String hash, SuccessResposeHandler onSuccess, @NonNull ErrorResponseHandler onError) {
        requestsHandler.post(() -> {
            JSONObject data;
            try {
                data = new JSONObject()
                        .putOpt("event_uuid", eventUuid)
                        .putOpt("folder_uuid", parentUuid)
                        .putOpt("file_name", name)
                        .putOpt("file_size", size)
                        .putOpt("hash", hash)
                        .putOpt("diff_file_size", 0);
            } catch (JSONException e) {
                e.printStackTrace();
                onError.call(null);
                return;
            }

            makeRequest(
                    "events", "file_event_create", data,
                    onSuccess, onError, null);
        });
    }

    public void updateFile(
            String eventUuid, String uuid, long size, String hash,
            long lastEventId, SuccessResposeHandler onSuccess, @NonNull ErrorResponseHandler onError) {
        requestsHandler.post(() -> {
            JSONObject data;
            try {
                data = new JSONObject()
                        .putOpt("event_uuid", eventUuid)
                        .putOpt("file_uuid", uuid)
                        .putOpt("file_size", size)
                        .putOpt("hash", hash)
                        .putOpt("last_event_id", lastEventId)
                        .putOpt("diff_file_size", 0)
                        .putOpt("rev_diff_file_size", 0);
            } catch (JSONException e) {
                e.printStackTrace();
                onError.call(null);
                return;
            }

            makeRequest(
                    "events", "file_event_update", data,
                    onSuccess, onError, null);
        });
    }

    public void moveFile(
            String eventUuid, String uuid, String parentUuid, String name,
            long lastEventId, SuccessResposeHandler onSuccess, @NonNull ErrorResponseHandler onError) {
        requestsHandler.post(() -> {
            JSONObject data;
            try {
                data = new JSONObject()
                        .putOpt("event_uuid", eventUuid)
                        .putOpt("file_uuid", uuid)
                        .putOpt("new_file_name", name)
                        .putOpt("new_folder_uuid", parentUuid)
                        .putOpt("last_event_id", lastEventId);
            } catch (JSONException e) {
                e.printStackTrace();
                onError.call(null);
                return;
            }

            makeRequest(
                    "events", "file_event_move", data,
                    onSuccess, onError, null);
        });
    }

    public void deleteFile(
            String eventUuid, String uuid, long lastEventId,
            SuccessResposeHandler onSuccess, @NonNull ErrorResponseHandler onError) {
        requestsHandler.post(() -> {
            JSONObject data;
            try {
                data = new JSONObject()
                        .putOpt("event_uuid", eventUuid)
                        .putOpt("file_uuid", uuid)
                        .putOpt("last_event_id", lastEventId);
            } catch (JSONException e) {
                e.printStackTrace();
                onError.call(null);
                return;
            }

            makeRequest(
                    "events", "file_event_delete", data,
                    onSuccess, onError, null);
        });
    }

    public void patchReady(
            String patchUuid, long patchSize) {
        requestsHandler.post(() -> {
            JSONObject data;
            try {
                data = new JSONObject()
                        .putOpt("diff_uuid", patchUuid)
                        .putOpt("diff_size", patchSize);
            } catch (JSONException e) {
                e.printStackTrace();
                return;
            }

            makeRequest(
                    "events", "patch_ready", data,
                    response -> {}, error -> requestsHandler.postDelayed(
                            () ->patchReady(patchUuid, patchSize), 5000), null);
        });
    }
}
