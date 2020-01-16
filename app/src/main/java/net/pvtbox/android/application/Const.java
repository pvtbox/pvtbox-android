package net.pvtbox.android.application;

import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;

import androidx.annotation.NonNull;

import java.io.File;
import java.util.Objects;

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
public class Const {

    public static final String SETTINGS_NAME = "PrivateBox";

    public static final String DOWNLOADS_RESUME_OPERATION = "pb_operation.downloads.resume";
    public static final String DOWNLOADS_PAUSE_OPERATION = "pb_operation.downloads.pause";
    public static final String FILE_OPERATION_INTENT = "pb_operation.intent";
    public static final String FILE_OPERATION_TYPE = "pb_operation.type";
    public static final String FILE_OPERATION_ROOT = "pb_operation.root";
    public static final String FILE_OPERATION_NEW_NAME = "pb_operation.new_name";
    public static final String FILE_OPERATION_UUID = "pb_operation.uuid";
    public static final String FILE_OPERATION_UUIDS = "pb_operation.uuids";
    public static final String FILE_OPERATION_PATH = "pb_operation.path";
    public static final String FILE_OPERATION_URI = "pb_operation.uri";
    public static final String FILE_OPERATION_URIS = "pb_operation.uris";
    public static final String FILE_OPERATION_TARGET_OBJECT = "pb_operation.target_object";
    public static final String SHARING_ENABLE = "pb_operation.sharing_enable";

    public static final String OPERATIONS_PROGRESS_INTENT = "pb_operations.progress.intent";
    public static final String OPERATIONS_PROGRESS_MESSAGE = "pb_operations.progress.message";
    public static final String OPERATIONS_PROGRESS_SHOW_AND_DISMISS = "pb_operations.progress.show_and_dismiss";
    public static final String OPERATIONS_PROGRESS_OPEN_ACTION = "pb_operations.progress.open_action";
    public static final String OPERATIONS_PROGRESS_SHOW_SHARE_CANCEL = "pb_operations.progress.show_share_cancel";

    public static final String NETWORK_STATUS = "pb_status.network.intent";
    public static final String NETWORK_STATUS_INFO = "pb_status.network.info";
    public static final String NETWORK_STATUS_INFO_HEADER = "pb_status.network.info_header";
    public static final String NETWORK_STATUS_SIGNAL_CONNECTING = "pb_status.network.signal_connecting";

    public static final String LICENSE_CHANGED = "pb_notifications.license";

    public static final String RESTART_MONITOR_INTENT = "restart.monitor.intent";
    public static final String PAUSE_MONITOR_INTENT = "pause.monitor.intent";
    public static final String UUID = "uuid";
    public static final String DEFAULT_PATH = Environment.getExternalStorageDirectory() + File.separator + "Pvtbox";
    public static final String INTERNAL_PATH = Environment.getExternalStorageDirectory() + File.separator + ".pvtbox";
    public static final String COPIES_PATH = INTERNAL_PATH + File.separator + "copies";
    public static final String UPLOADS_PATH = INTERNAL_PATH + File.separator + "uploads";
    public static final String PATCHES_PATH = INTERNAL_PATH + File.separator + "patches";
    public static final String STOP_SERVICE_INTENT = "pb_service.stop.intent";
    public static final String STOP_SERVICE_WIPE = "pb_service.stop.wipe";
    public static final String REMOVE_FAILED_INTENT = "pb_service.dm.remove_failed";
    public static final String ACTION_EXIT = "net.pvtbox.android.exit";
    public static final String INIT_INTENT = "net.pvtbox.android.init_intent";

    public static final String KEY_SHARE_PATH_DOWNLOAD = "KEY_SHARE_PATH_DOWNLOAD";
    public static final long MAX_LIMIT_SIZE_PREVIEW = 10 * 1024 * 1024;

    public static final String FREE_LICENSE = "FREE_DEFAULT";
    public static final String FREE_TRIAL_LICENSE = "FREE_TRIAL";
    public static final String PAYED_PROFESSIONAL_LICENSE = "PAYED_PROFESSIONAL";
    public static final String PAYED_BUSINESS_LICENSE = "PAYED_BUSINESS_USER";
    public static final String PAYED_BUSINESS_ADMIN_LICENSE = "PAYED_BUSINESS_ADMIN";
    public static final String BASE_URL = "https://pvtbox.net";
    public static final String API = "/api/";
    public static final String KEY_SHARE_HASH = "share_hash";


    public static final String LOGOUT_INTENT = "logout_intent";

    public static final String DISK_SPACE_ERROR_INTENT = "DISK_SPACE_ERROR_INTENT";

    public final static int DEFAULT_CHUNK_SIZE = 60 * 1024;

    public static final String nodeType = Objects.requireNonNull(App.getApplication())
            .getPackageManager()
            .hasSystemFeature(PackageManager.FEATURE_TELEPHONY) ? "phone" : "tablet";
    public static final String nodeOsType = "Android";
    public static final String nodeOsName = "Android " + Build.VERSION.RELEASE;
    public static final String nodeName = (Build.MODEL.startsWith(Build.MANUFACTURER) ?
            Build.MODEL : Build.MANUFACTURER + " " + Build.MODEL)
            .substring(0, Math.min((Build.MODEL.startsWith(Build.MANUFACTURER) ?
                    Build.MODEL : Build.MANUFACTURER + " " + Build.MODEL).length(), 30));
    public static final String Key = "96eb5ef2-94fc-11e9-9efa-ef49bc881390";
    public static final String EMPTY_FILE_HASH = "d41d8cd98f00b204e9800998ecf8427e";
    public static final String DELETE_FILE = "DELETE_FILE";
    @NonNull
    public static final String CAMERA_PATH = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getPath();
    public static final String CAMERA_FOLDER_NAME = "Camera from " + Const.nodeName;
}
