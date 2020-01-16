package net.pvtbox.android.db;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.util.Log;

import net.pvtbox.android.BuildConfig;
import net.pvtbox.android.R;
import net.pvtbox.android.application.Const;
import net.pvtbox.android.service.PreferenceService;
import net.pvtbox.android.db.model.EventRealm;
import net.pvtbox.android.db.model.FileRealm;
import net.pvtbox.android.db.model.DeviceRealm;
import net.pvtbox.android.tools.FileTool;
import net.pvtbox.android.tools.JSON;
import net.pvtbox.android.ui.files.ModeProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import io.realm.Case;
import io.realm.Realm;
import io.realm.RealmQuery;
import io.realm.RealmResults;
import io.realm.Sort;


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
public class DataBaseService {
    @NonNull
    private final String TAG = DataBaseService.class.getSimpleName();

    private final PreferenceService preferenceService;
    private final Context context;
    private final FileTool fileTool;
    @Nullable
    private Runnable onSyncedCallback = null;
    @Nullable
    private Runnable onSyncingCallback = null;
    @Nullable
    private Runnable onPausedCallback = null;

    public DataBaseService(Context context,
                           PreferenceService preferenceService,
                           FileTool fileTool) {
        this.context = context;
        this.preferenceService = preferenceService;
        this.fileTool = fileTool;
    }

    public void clearDb() {
        try (Realm realmInstance = Realm.getDefaultInstance()) {
            realmInstance.executeTransaction((realm) -> {
                realm.delete(FileRealm.class);
                realm.where(DeviceRealm.class)
                        .notEqualTo("id", "own")
                        .findAll().deleteAllFromRealm();
                realm.delete(EventRealm.class);
            });
        }
    }

    public void cancelDownloads(@NonNull ArrayList<String> uuids) {
        try (Realm realmInstance = Realm.getDefaultInstance()) {
            realmInstance.executeTransaction((realm) -> {
                RealmResults<FileRealm> files = realm.where(FileRealm.class)
                        .equalTo("isDownload", true)
                        .in("uuid", uuids.toArray(new String[0]))
                        .findAll();
                for (FileRealm file : files) {
                    file.setDownload(false);
                    file.setDownloadPath(null);
                }
            });
        }
    }

    public void setAllEventsUnchecked() {
        try (Realm realmInstance = Realm.getDefaultInstance()) {
            realmInstance.executeTransaction((realm) -> {
                RealmResults<EventRealm> events = realm.where(EventRealm.class)
                        .equalTo("checked", true)
                        .findAll();
                for (EventRealm event : events) {
                    event.setChecked(false);
                }
            });
        }
    }

    public void clearProcessingFiles() {
        Log.i(TAG, "clearProcessingFiles");
        try (Realm realmInstance = Realm.getDefaultInstance()) {
            realmInstance.executeTransaction((realm) -> {
                RealmResults<FileRealm> files = realm.where(FileRealm.class)
                        .equalTo("isProcessing", true)
                        .findAll();
                for (FileRealm file : files) {
                    EventRealm event = realm.where(EventRealm.class)
                            .equalTo("uuid", file.getEventUuid())
                            .findFirst();
                    if (event == null) {
                        file.deleteFromRealm();
                        continue;
                    }
                    if (event.getId() == 0) {
                        event.deleteFromRealm();
                    }
                    EventRealm prevEvent = realm.where(EventRealm.class)
                            .equalTo("fileUuid", file.getUuid())
                            .sort("id", Sort.DESCENDING)
                            .findFirst();
                    if (prevEvent == null) {
                        file.deleteFromRealm();
                        continue;
                    }
                    file.setEventUuid(prevEvent.getUuid());
                    file.setEventId(prevEvent.getId());
                    file.setProcessing(false);
                }
            });
        }
    }

    // MONITOR

    @NonNull
    public HashMap<String, Long> getCameraFiles(String folderUuid) {
        try (Realm realm = Realm.getDefaultInstance()) {
            List<FileRealm> fileRealmList = realm.where(FileRealm.class)
                    .equalTo("isFolder", false)
                    .equalTo("parentUuid", folderUuid)
                    .isNotNull("cameraPath")
                    .findAll();
            HashMap<String, Long> result = new HashMap<>();

            for (FileRealm fileRealm : fileRealmList) {
                result.put(fileRealm.getCameraPath(), fileRealm.getMtime());
            }
            return result;
        }
    }

    @NonNull
    public HashMap<String, ArrayList<Long>> getOfflineAndActualFiles() {
        try (Realm realm = Realm.getDefaultInstance()) {
            List<FileRealm> fileRealmList = realm.where(FileRealm.class)
                    .equalTo("isFolder", false)
                    .beginGroup()
                    .equalTo("isOffline", true)
                    .or()
                    .equalTo("isDownloadActual", true)
                    .endGroup()
                    .findAll();
            HashMap<String, ArrayList<Long>> result = new HashMap<>();

            for (FileRealm fileRealm : fileRealmList) {
                result.put(fileRealm.getPath(), new ArrayList<Long>() {{
                    add(fileRealm.getMtime());
                    add(fileRealm.getSize());
                }});
            }
            return result;
        }
    }

    @NonNull
    public HashSet<String> getOfflineFolders() {
        try (Realm realm = Realm.getDefaultInstance()) {
            List<FileRealm> fileRealmList = realm.where(FileRealm.class)
                    .equalTo("isFolder", true)
                    .equalTo("isOffline", true)
                    .findAll();
            HashSet<String> result = new HashSet<>();

            for (FileRealm fileRealm : fileRealmList) {
                result.add(fileRealm.getPath());
            }
            return result;
        }
    }

    @NonNull
    public HashSet<String> getAllFolders() {
        try (Realm realm = Realm.getDefaultInstance()) {
            List<FileRealm> fileRealmList = realm.where(FileRealm.class)
                    .equalTo("isFolder", true)
                    .findAll();
            HashSet<String> result = new HashSet<>();

            for (FileRealm fileRealm : fileRealmList) {
                result.add(fileRealm.getPath());
            }
            return result;
        }
    }


    public void setFileMTime(String uuid, long value) {
        try (Realm realmInstance = Realm.getDefaultInstance()) {
            realmInstance.executeTransaction(realm -> {
                FileRealm file = realm.where(FileRealm.class)
                        .equalTo("uuid", uuid)
                        .findFirst();
                if (file == null) {
                    return;
                }
                file.setMtime(value);
            });
        }
    }

    public void onFileDeleted(String uuid) {
        try (Realm realmInstance = Realm.getDefaultInstance()) {
            realmInstance.executeTransaction(realm -> {
                FileRealm file = realm.where(FileRealm.class)
                        .equalTo("uuid", uuid)
                        .findFirst();
                if (file == null) return;
                file.setDownloadActual(false);
                file.setHashsum(null);
                if (file.getDownloadedSize() > 0) {
                    changeDownloadedSize(file, -file.getDownloadedSize(), realm);
                }
            });
        }
    }

    public void onFileFound(String uuid, String eventUuid, String hashsum) {
        try (Realm realmInstance = Realm.getDefaultInstance()) {
            realmInstance.executeTransaction(realm -> {
                FileRealm file = realm.where(FileRealm.class)
                        .equalTo("uuid", uuid)
                        .equalTo("eventUuid", eventUuid)
                        .findFirst();
                if (file == null) return;
                setFileDownloaded(file, hashsum, eventUuid, true, realm);
            });
        }
    }

    // FILES & EVENTS

    @NonNull
    public ArrayList<Long> getEventsCheckParams() {
        ArrayList<Long> result = new ArrayList<>();
        try (Realm realmInstance = Realm.getDefaultInstance()) {
            realmInstance.executeTransaction(realm -> {
                long lastEventId = 0;
                Number lastEvent = realm.where(EventRealm.class)
                        .notEqualTo("id", 0)
                        .max("id");
                if (lastEvent != null) {
                    lastEventId = lastEvent.longValue();
                }
                long lastCheckedEventId = 0;
                Number lastCheckedEvent = realm.where(EventRealm.class)
                        .notEqualTo("id", 0)
                        .equalTo("checked", true)
                        .max("id");
                if (lastCheckedEvent != null) {
                    lastCheckedEventId = lastCheckedEvent.longValue();
                }
                long eventsCount = realm.where(EventRealm.class)
                        .notEqualTo("id", 0)
                        .greaterThan("id", lastCheckedEventId)
                        .lessThan("id", lastEventId)
                        .count();
                long firstUnknownPatch = 0;
                Number firstUnknownPatchEvent = realm.where(EventRealm.class)
                        .notEqualTo("id", 0)
                        .isNotNull("diffFileUuid")
                        .equalTo("diffFileSize", 0)
                        .min("id");
                if (firstUnknownPatchEvent != null) {
                    firstUnknownPatch = firstUnknownPatchEvent.longValue();
                }

                result.add(lastEventId);
                result.add(lastCheckedEventId);
                result.add(eventsCount);
                result.add(firstUnknownPatch);
            });
        }
        return result;
    }

    public void savePatchesInfo(@NonNull JSONArray infos) {
        try (Realm realmInstance = Realm.getDefaultInstance()) {
            realmInstance.executeTransaction(realm -> {
                for (int i = 0; i < infos.length(); ++i) {
                    JSONObject info = infos.optJSONObject(i);
                    RealmResults<EventRealm> events = realm.where(EventRealm.class)
                            .isNotNull("diffFileUuid")
                            .equalTo("diffFileUuid", JSON.optString(
                                    info, "diff_uuid", ""))
                            .equalTo("diffFileSize", 0)
                            .findAll();
                    events.setLong("diffFileSize", info.optLong(
                            "diff_size", 0));
                }
            });
        }
    }

    public void saveFileEvents(
            @NonNull JSONArray events, boolean markChecked, boolean isInitialSync) {
        Log.i(TAG, "saveFileEvents");
        try (Realm realmInstance = Realm.getDefaultInstance()) {
            realmInstance.executeTransaction(realm -> {
                for (int i = 0; i < events.length(); ++i) {
                    JSONObject event = events.optJSONObject(i);
                    if (event == null) {
                        continue;
                    }
                    String eventUuid = JSON.optString(event, "event_uuid");
                    String fileUuid = JSON.optString(event, "uuid");
                    String eventType = JSON.optString(event, "event_type");
                    long diffFileSize = event.optLong("diff_file_size", 0);
                    EventRealm existingEvent = getExistingEvent(
                            eventUuid, Objects.equals(eventType, "delete") ? null : fileUuid,
                            realm);
                    if (existingEvent == null) {
                        onFileEvent(event, isInitialSync, markChecked, realm);
                    } else if (markChecked) {
                        existingEvent.setChecked(true);
                        existingEvent.setDiffFileSize(diffFileSize);
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "saveFileEvents error:", e);
        }
    }

    @Nullable
    private EventRealm getExistingEvent(String uuid, @Nullable String fileUuid, @NonNull Realm realm) {
        RealmQuery<EventRealm> result = realm.where(EventRealm.class)
                .equalTo("uuid", uuid);
        if (fileUuid != null) {
            result = result.equalTo("fileUuid", fileUuid);
        }
        return result.findFirst();
    }

    private void onFileEvent(@NonNull JSONObject event, boolean isInitialSync, boolean markChecked, @NonNull Realm realm) {
        String eventType = JSON.optString(event, "event_type");
        if ("restore".equals(eventType)) {
            eventType = "create";
        }

        String parentUuid = JSON.optString(event, "parent_folder_uuid");
        FileRealm parent = null;
        if (parentUuid != null) {
            parent = getFileByUuid(parentUuid, realm);
        }
        long timestamp = (long) (event.optDouble("timestamp") * 1000.0);
        long eventId = event.optLong("event_id");
        String eventUuid = JSON.optString(event, "event_uuid");
        String fileUuid = JSON.optString(event, "uuid");
        String hashsum = JSON.optString(event, "file_hash_after_event",
                JSON.optString(event, "file_hash_before_event"));
        long size = event.optLong("file_size_after_event",
                event.optLong("file_size_before_event",
                        event.optLong("file_size", 0)));
        String diffFileUuid = JSON.optString(event, "diff_file_uuid");
        long diffFileSize = event.optLong("diff_file_size", 0);
        String revDiffFileUuid = JSON.optString(event, "rev_diff_file_uuid");
        long revDiffFileSize = event.optLong("rev_diff_file_size", 0);
        FileRealm file = getFileByUuid(fileUuid, realm);
        if (file == null) {
            switch (Objects.requireNonNull(eventType)) {
                case "create":
                case "update":
                case "move":
                    file = onNewFileEvent(eventUuid, eventId, fileUuid, parentUuid,
                            parent, hashsum, size, event, isInitialSync, realm);
                    break;
                case "delete":
                    return;
            }
        } else {
            file = onExistingFileEvent(eventUuid, eventId, eventType, file, parent, hashsum, size,
                    event, isInitialSync, realm);
        }

        EventRealm eventRealm = new EventRealm();
        eventRealm.setUuid(eventUuid);
        eventRealm.setFileUuid(fileUuid);
        eventRealm.setId(eventId);
        eventRealm.setHashsum(hashsum);
        eventRealm.setSize(size);
        eventRealm.setChecked(markChecked);
        eventRealm.setDiffFileUuid(diffFileUuid);
        eventRealm.setDiffFileSize(diffFileSize);
        eventRealm.setRevDiffFileUuid(revDiffFileUuid);
        eventRealm.setRevDiffFileSize(revDiffFileSize);
        eventRealm.setTimestamp(timestamp);
        eventRealm.setCameraPath(file == null ? null : file.getCameraPath());
        realm.insertOrUpdate(eventRealm);
    }

    @Nullable
    public FileRealm getFileByName(String name, String folderUuid) {
        try (Realm realmInstance = Realm.getDefaultInstance()) {
            FileRealm file = realmInstance.where(FileRealm.class)
                    .equalTo("name", name)
                    .equalTo("parentUuid", folderUuid)
                    .findFirst();
            if (file == null) return null;
            return realmInstance.copyFromRealm(file);
        }
    }

    @Nullable
    public FileRealm getFileByUuid(String uuid) {
        try (Realm realmInstance = Realm.getDefaultInstance()) {
            FileRealm file = getFileByUuid(uuid, realmInstance);
            if (file == null) return null;
            return realmInstance.copyFromRealm(file);
        }
    }

    @Nullable
    private FileRealm getFileByUuid(String uuid, @NonNull Realm realm) {
        return realm.where(FileRealm.class)
                .equalTo("uuid", uuid)
                .findFirst();
    }

    @Nullable
    public FileRealm getFileByPath(String path) {
        try (Realm realmInstance = Realm.getDefaultInstance()) {
            FileRealm file = getFileByPath(path, true, realmInstance);
            if (file == null) return null;
            return realmInstance.copyFromRealm(file);
        }
    }

    @Nullable
    private FileRealm getFileByPath(String path, boolean checkParents, @NonNull Realm realm) {
        FileRealm file = realm.where(FileRealm.class)
                .equalTo("path", path)
                .findFirst();
        if (file == null || (checkParents && !checkAllParentsExist(file.getParentUuid(), realm))) {
            return null;
        } else {
            return file;
        }
    }

    private boolean checkAllParentsExist(@Nullable String uuid, @NonNull Realm realm) {
        if (uuid == null) return true;
        FileRealm file = getFileByUuid(uuid, realm);
        if (file == null) {
            return false;
        } else {
            return checkAllParentsExist(file.getParentUuid(), realm);
        }
    }

    @Nullable
    public FileRealm getRootParent(@NonNull FileRealm file) {
        try (Realm realmInstance = Realm.getDefaultInstance()) {
            if (file.getParentUuid() == null) return file;
            FileRealm parent = getRootParent(file.getParentUuid(), realmInstance);
            if (parent == null) return null;
            return realmInstance.copyFromRealm(parent);
        }
    }

    @Nullable
    private FileRealm getRootParent(String uuid, @NonNull Realm realm) {
        FileRealm file = realm.where(FileRealm.class)
                .equalTo("uuid", uuid)
                .findFirst();
        if (file == null) return null;
        if (file.getParentUuid() == null) return file;
        return getRootParent(file.getParentUuid(), realm);
    }

    private FileRealm onNewFileEvent(
            String eventUuid, long eventId, String fileUuid,
            String parentUuid, @Nullable FileRealm parent, String hashsum, long size,
            @NonNull JSONObject event, boolean isInitialSync, @NonNull Realm realm) {
        String fileName = getFileNameFromEvent(event);
        String path = FileTool.buildPath(parent, fileName);
        String cameraPath = null;
        FileRealm existingFile = getFileByPath(path, false, realm);
        if (existingFile != null && Objects.equals(existingFile.getParentUuid(), parentUuid)) {
            cameraPath = existingFile.getCameraPath();
            if (existingFile.getParentUuid() != null) {
                FileRealm oldParent = getFileByUuid(existingFile.getParentUuid(), realm);
                onFileSwitchedParent(false, existingFile, oldParent, realm);
            }
            existingFile.deleteFromRealm();
        }
        FileRealm file = realm.createObject(FileRealm.class, fileUuid);
        file.setParentUuid(parentUuid);
        file.setEventUuid(eventUuid);
        file.setEventId(eventId);
        file.setName(fileName);
        file.setPath(path);
        boolean isFolder = event.optBoolean(
                "is_folder", event.optInt("is_folder", 0) == 1);
        file.setFolder(isFolder);
        double timestamp = event.optDouble("timestamp");
        double fileCreated = event.optDouble("file_created", timestamp);
        file.setDateCreated((long) (fileCreated * 1000.0));
        file.setDateModified((long) ((isFolder ? fileCreated : timestamp) * 1000.0));
        if (isFolder) {
            if (parent != null && parent.isOffline()) {
                fileTool.createDirectory(file.getPath());
                file.setOffline(true);
            } else {
                file.setOffline(false);
            }
            handleMissingParentCase(file, realm);
        } else {
            file.setSize(size);
            changeFilesCount(parent, 1, realm);
            if (size > 0) {
                changeSize(parent, size, realm);
            }
            if (cameraPath == null) {
                FileRealm fileWithCameraPath = realm.where(FileRealm.class)
                        .equalTo("isFolder", false)
                        .isNotNull("cameraPath")
                        .equalTo("hashsum", hashsum)
                        .findFirst();
                if (fileWithCameraPath != null) {
                    cameraPath = fileWithCameraPath.getCameraPath();
                }
            }
            file.setCameraPath(cameraPath);
            file.setOnlyDownload(!(parent != null && parent.isOffline()));
            boolean isDownload = (parent != null && parent.isOffline()) ||
                    (cameraPath == null && checkNeedDownload(fileName, size));
            file.setDownload(isDownload);
            if (isDownload && isInitialSync) {
                file.setDownloadStatus(R.string.waitingInitialSync);
            }
        }
        return file;
    }

    private void handleMissingParentCase(@NonNull FileRealm parent, @NonNull Realm realm) {
        RealmResults<FileRealm> childs = realm.where(FileRealm.class)
                .equalTo("parentUuid", parent.getUuid())
                .findAll();
        for (FileRealm child : childs) {
            onFileSwitchedParent(true, child, parent, realm);
            updatePath(child, parent, realm);
        }
    }

    private void updatePath(@NonNull FileRealm file, FileRealm parent, @NonNull Realm realm) {
        file.setPath(FileTool.buildPath(parent, file.getName()));
        if (file.isFolder()) {
            RealmResults<FileRealm> childs = realm.where(FileRealm.class)
                    .equalTo("parentUuid", file.getUuid())
                    .findAll();
            for (FileRealm child : childs) {
                updatePath(child, file, realm);
            }
        }
    }

    @Nullable
    private FileRealm onExistingFileEvent(
            String eventUuid, long eventId, String eventType, @NonNull FileRealm file,
            @Nullable FileRealm parent, String hashsum, long size, @NonNull JSONObject event,
            boolean isInitialSync, @NonNull Realm realm) {
        if ("delete".equals(eventType)) {
            onDeleteFileEvent(file, parent, realm);
            return null;
        }
        file.setEventUuid(eventUuid);
        file.setEventId(eventId);
        String fileName = getFileNameFromEvent(event);
        String path = FileTool.buildPath(parent, fileName);

        if (!Objects.equals(path, file.getPath())) {
            onMoveFileEvent(file, parent, path, fileName, realm);
        }

        boolean isFolder = file.isFolder();
        long timestamp = (long) (event.optDouble("timestamp") * 1000.0);
        if (!"move".equals(eventType)) {
            file.setDateModified(timestamp);
        }
        if (isFolder) {
            if (file.isOffline() || parent != null && parent.isOffline()) {
                fileTool.createDirectory(file.getPath());
                file.setOffline(true);
            } else {
                file.setOffline(false);
            }
        } else {
            long oldSize = file.getSize();
            String oldHash = file.getHashsum();
            if (!Objects.equals(oldHash, hashsum) || oldSize != size) {
                onUpdateFileEvent(file, parent, hashsum, size, isInitialSync, realm);
            }
            if (Objects.equals(oldHash, hashsum)) {
                if (file.getCameraPath() == null) {
                    file.setDownloadActual(true);
                }
            } else {
                file.setCameraPath(null);
            }
        }
        return file;
    }

    private void onUpdateFileEvent(
            @NonNull FileRealm file, @Nullable FileRealm parent, String newHash, long newSize,
            boolean isInitialSync, @NonNull Realm realm) {
        long oldSize = file.getSize();
        long oldDownloadedSize = file.getDownloadedSize();
        if (oldDownloadedSize > 0) {
            changeDownloadedSize(parent, -oldDownloadedSize, realm);
            file.setDownloadedSize(0);
        }
        if (oldSize != newSize) {
            changeSize(parent, newSize - oldSize, realm);
            file.setSize(newSize);
        }
        if (!Objects.equals(file.getHashsum(), newHash)) {
            file.setCameraPath(null);
        }
        file.setDownloadActual(false);
        file.setOnlyDownload(!(file.isOffline() || parent != null && parent.isOffline()));
        file.setDownload(file.isOffline() || (parent != null && parent.isOffline()) ||
                (file.getCameraPath() == null && checkNeedDownload(file.getName(), newSize)));
        if (!file.isFolder() && file.isDownload() && isInitialSync) {
            file.setDownloadStatus(R.string.waitingInitialSync);
        }
    }

    private void onDeleteFileEvent(@NonNull FileRealm file, FileRealm parent, @NonNull Realm realm) {
        if (file.isFolder() && preferenceService.getCameraFolderUuid() != null) {
            checkCameraFolderDeleted(file, realm);
        }
        onFileSwitchedParent(false, file, parent, realm);
        if (file.isFolder()) {
            deleteChilds(file.getUuid(), realm);
        }
        fileTool.delete(file.getPath());

        realm.where(EventRealm.class)
                .equalTo("fileUuid", file.getUuid())
                .findAll()
                .deleteAllFromRealm();
        file.deleteFromRealm();
    }

    private boolean checkCameraFolderDeleted(@NonNull FileRealm file, @NonNull Realm realm) {
        if (Objects.equals(file.getUuid(), preferenceService.getCameraFolderUuid())) {
            if (preferenceService.getAutoCameraUpdate()) {
                String message = context.getString(R.string.cancel_import_camera);
                Intent intent = new Intent(Const.OPERATIONS_PROGRESS_INTENT);
                intent.putExtra(Const.OPERATIONS_PROGRESS_MESSAGE, message);
                intent.putExtra(Const.OPERATIONS_PROGRESS_SHOW_AND_DISMISS, true);
                LocalBroadcastManager.getInstance(context).sendBroadcast(intent);

                Log.i(TAG, "show camera folder deleted notification");
                String channelId = BuildConfig.APPLICATION_ID;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    NotificationManager manager = (NotificationManager) context.getSystemService(
                            Context.NOTIFICATION_SERVICE);
                    if (manager != null) {
                        NotificationChannel channel = new NotificationChannel(
                                channelId, context.getString(R.string.app_name),
                                NotificationManager.IMPORTANCE_HIGH);
                        manager.createNotificationChannel(channel);
                    }
                }

                Notification notification =
                        new NotificationCompat.Builder(context, channelId)
                                .setContentTitle(context.getText(R.string.camera_folder_deleted_title))
                                .setStyle(new NotificationCompat.BigTextStyle()
                                        .bigText(context.getText(R.string.camera_folder_deleted_message)))
                                .setSmallIcon(R.drawable.notification_icon)
                                .setPriority(NotificationCompat.PRIORITY_HIGH)
                                .build();
                NotificationManagerCompat notificationManager = NotificationManagerCompat.from(
                        context);

                notificationManager.notify(1122, notification);
            }
            preferenceService.setCameraFolderUuid(null);
            preferenceService.setAutoCameraUpdate(false);
            return true;
        }
        RealmResults<FileRealm> subFolders = realm.where(FileRealm.class)
                .equalTo("parentUuid", file.getUuid())
                .equalTo("isFolder", true)
                .findAll();
        for (FileRealm subFolder : subFolders) {
            if (checkCameraFolderDeleted(subFolder, realm)) {
                return true;
            }
        }
        return false;
    }

    private void onFileSwitchedParent(boolean added, @NonNull FileRealm file, @Nullable FileRealm parent, @NonNull Realm realm) {
        if (parent == null) return;
        int multiplier = added ? 1 : -1;
        long fileSize = file.getSize();
        if (fileSize > 0) {
            changeSize(parent, fileSize * multiplier, realm);
        }
        long downloadedSize = file.getDownloadedSize();
        if (downloadedSize > 0) {
            changeDownloadedSize(parent, downloadedSize * multiplier, realm);
        }
        if (file.isFolder()) {
            long filesCount = file.getFilesCount();
            if (filesCount > 0) {
                changeFilesCount(parent, filesCount * multiplier, realm);
            }
            long offlineFilesCount = file.getOfflineFilesCount();
            if (offlineFilesCount > 0) {
                changeOfflineFilesCount(parent, offlineFilesCount * multiplier, realm);
            }
        } else {
            changeFilesCount(parent, multiplier, realm);
            if (file.isOffline()) {
                changeOfflineFilesCount(parent, multiplier, realm);
            }
        }
    }

    private void deleteChilds(String fileUuid, @NonNull Realm realm) {
        RealmResults<FileRealm> childs = realm.where(FileRealm.class)
                .equalTo("parentUuid", fileUuid)
                .findAll();
        for (FileRealm child : childs) {
            if (child.isFolder()) {
                deleteChilds(child.getUuid(), realm);
            }
            realm.where(EventRealm.class)
                    .equalTo("fileUuid", child.getUuid())
                    .findAll()
                    .deleteAllFromRealm();
            child.deleteFromRealm();
        }
    }

    private void onMoveFileEvent(
            @NonNull FileRealm file, @Nullable FileRealm parent,
            @NonNull String newPath, String newName, @NonNull Realm realm) {
        String parentUuid = parent == null ? null : parent.getUuid();
        if (!Objects.equals(file.getParentUuid(), parentUuid)) {
            if (file.getParentUuid() != null) {
                FileRealm oldParent = getFileByUuid(file.getParentUuid(), realm);
                onFileSwitchedParent(false, file, oldParent, realm);
            }
            if (parent != null) {
                onFileSwitchedParent(true, file, parent, realm);
            }
        }
        String oldPath = file.getPath();
        file.setName(newName);
        file.setPath(newPath);
        file.setParentUuid(parentUuid);
        boolean setOffline = !file.isOffline() && parent != null && parent.isOffline();
        moveChilds(file.getUuid(), newPath, setOffline, realm);
        fileTool.move(oldPath, newPath, true);
        if (setOffline && !file.isFolder()) {
            file.setDownload(true);
            file.setOnlyDownload(false);
        }
    }

    private void moveChilds(String fileUuid, String newPath, boolean setOffline, @NonNull Realm realm) {
        RealmResults<FileRealm> childs = realm.where(FileRealm.class)
                .equalTo("parentUuid", fileUuid)
                .findAll();
        for (FileRealm child : childs) {
            String newChildPath = FileTool.buildPath(newPath, child.getName());
            child.setPath(newChildPath);
            if (child.isFolder()) {
                if (setOffline) {
                    fileTool.createDirectory(child.getPath());
                    child.setOffline(true);
                }
                moveChilds(child.getUuid(), newChildPath, setOffline, realm);
            } else if (setOffline) {
                if (!child.isDownload()) {
                    child.setDownload(true);
                }
                child.setOnlyDownload(false);
            }
        }
    }

    private void changeFilesCount(@Nullable FileRealm file, long count, @NonNull Realm realm) {
        if (file == null) return;
        file.setFilesCount(file.getFilesCount() + count);
        String parentUuid = file.getParentUuid();
        if (parentUuid == null) return;
        FileRealm parent = getFileByUuid(parentUuid, realm);
        if (parent != null) changeFilesCount(parent, count, realm);
    }

    private void changeOfflineFilesCount(@Nullable FileRealm file, long count, @NonNull Realm realm) {
        if (file == null) return;
        if (file.isFolder()) {
            file.setOfflineFilesCount(file.getOfflineFilesCount() + count);
        }
        String parentUuid = file.getParentUuid();
        if (parentUuid == null) return;
        FileRealm parent = getFileByUuid(parentUuid, realm);
        if (parent != null) changeOfflineFilesCount(parent, count, realm);
    }

    private void changeSize(@Nullable FileRealm file, long size, @NonNull Realm realm) {
        if (file == null) return;
        file.setSize(file.getSize() + size);
        String parentUuid = file.getParentUuid();
        if (parentUuid == null) return;
        FileRealm parent = getFileByUuid(parentUuid, realm);
        if (parent != null) changeSize(parent, size, realm);
    }

    private void changeDownloadedSize(@Nullable FileRealm file, long size, @NonNull Realm realm) {
        if (file == null) return;
        file.setDownloadedSize(file.getDownloadedSize() + size);
        String parentUuid = file.getParentUuid();
        if (parentUuid == null) return;
        FileRealm parent = getFileByUuid(parentUuid, realm);
        if (parent != null) changeDownloadedSize(parent, size, realm);
    }

    @NonNull
    private String getFileNameFromEvent(JSONObject event) {
        //noinspection ConstantConditions
        return JSON.optString(event, "file_name_after_event",
                JSON.optString(event, "file_name"));
    }

    private boolean checkNeedDownload(String name, long size) {
        return preferenceService.isMediaDownloadEnabled() &&
                FileTool.isImageOrVideoFile(name) &&
                size < Const.MAX_LIMIT_SIZE_PREVIEW;
    }

    public FileRealm addFile(@NonNull FileRealm file, boolean generateUniqName, String suffix) {
        try (Realm realmInstance = Realm.getDefaultInstance()) {
            AtomicReference<FileRealm> result = new AtomicReference<>();
            realmInstance.executeTransaction(realm -> {
                FileRealm fileRealm = realm.copyToRealmOrUpdate(file);
                FileRealm parent = null;
                String parentUuid = fileRealm.getParentUuid();
                FileRealm existingParent = realm.where(FileRealm.class)
                        .equalTo("uuid", parentUuid)
                        .findFirst();
                if (existingParent != null) {
                    parent = existingParent;
                    onFileSwitchedParent(true, fileRealm, existingParent, realm);
                }
                if (generateUniqName) {
                    fileRealm.setName(this.generateUniqName(
                            fileRealm.getUuid(), parent, fileRealm.getName(), fileRealm.isFolder(),
                            suffix, realm));
                }
                String path = FileTool.buildPath(parent, fileRealm.getName());
                FileRealm existingFile = getFileByPath(path, false, realm);
                if (existingFile != null && !checkAllParentsExist(
                        existingFile.getParentUuid(), realm)) {
                    deleteFileByUuid(existingFile.getUuid(), realm);
                }
                if (fileRealm.getPath() == null || generateUniqName) {
                    fileRealm.setPath(path);
                }
                result.set(realm.copyFromRealm(fileRealm));
            });
            return result.get();
        }
    }

    public void deleteFileByUuid(String uuid) {
        try (Realm realmInstance = Realm.getDefaultInstance()) {
            realmInstance.executeTransaction(realm -> deleteFileByUuid(uuid, realm));
        }
    }

    private void deleteFileByUuid(String uuid, @NonNull Realm realm) {
        FileRealm file = realm.where(FileRealm.class)
                .equalTo("uuid", uuid)
                .findFirst();
        if (file == null) return;
        fileTool.delete(file.getPath());
        String parentUuid = file.getParentUuid();
        if (parentUuid != null) {
            FileRealm parent = realm.where(FileRealm.class)
                    .equalTo("uuid", parentUuid)
                    .findFirst();
            if (parent != null) onFileSwitchedParent(false, file, parent, realm);
        }
        if (file.isFolder()) {
            if (preferenceService.getCameraFolderUuid() != null) {
                checkCameraFolderDeleted(file, realm);
            }

            deleteChilds(file.getUuid(), realm);
        }
        realm.where(EventRealm.class)
                .equalTo("fileUuid", file.getUuid())
                .findAll()
                .deleteAllFromRealm();
        file.deleteFromRealm();
    }

    public void deleteEventByUuid(String uuid) {
        try (Realm realmInstance = Realm.getDefaultInstance()) {
            realmInstance.executeTransaction(realm -> {
                EventRealm event = realm.where(EventRealm.class)
                        .equalTo("uuid", uuid)
                        .findFirst();
                if (event != null) event.deleteFromRealm();
            });
        }
    }

    public void updateFileWithEvent(
            String fileUuid, @NonNull EventRealm event, long timestamp,
            @Nullable String newName, @Nullable String newParentUuid, @Nullable String newHashsum) {
        try (Realm realmInstance = Realm.getDefaultInstance()) {
            realmInstance.executeTransaction(realm -> {
                FileRealm file = realm.where(FileRealm.class)
                        .equalTo("uuid", fileUuid)
                        .findFirst();
                if (file == null) return;
                event.setCameraPath(file.getCameraPath());
                EventRealm eventRealm = realm.copyToRealmOrUpdate(event);
                if (!Objects.equals(file.getUuid(), eventRealm.getFileUuid())) {
                    FileRealm newFile = realm.copyFromRealm(file);
                    newFile.setUuid(eventRealm.getFileUuid());
                    newFile.setDateCreated(timestamp);
                    newFile = realm.copyToRealmOrUpdate(newFile);
                    file.deleteFromRealm();
                    file = newFile;
                }

                if (timestamp != 0) {
                    file.setDateModified(timestamp);
                }

                if (newName != null) {
                    file.setName(newName);
                    String newPath = FileTool.buildPath(
                            FileTool.getParentPath(file.getPath()), newName);
                    fileTool.move(file.getPath(), newPath, true);
                    file.setPath(newPath);
                    if (file.isFolder()) {
                        RealmResults<FileRealm> childs = realm.where(FileRealm.class)
                                .equalTo("parentUuid", file.getUuid())
                                .findAll();
                        for (FileRealm child : childs) {
                            updatePath(child, file, realm);
                        }
                    }
                }

                if (newParentUuid != null) {
                    if (file.getParentUuid() != null) {
                        FileRealm oldParent = getFileByUuid(file.getParentUuid(), realm);
                        onFileSwitchedParent(false, file, oldParent, realm);
                    }
                    if (newParentUuid.isEmpty()) {
                        file.setParentUuid(null);
                        String newPath = FileTool.buildPath(
                                (FileRealm) null, file.getName());
                        fileTool.move(file.getPath(), newPath, true);
                        file.setPath(newPath);
                        if (file.isFolder()) {
                            RealmResults<FileRealm> childs = realm.where(FileRealm.class)
                                    .equalTo("parentUuid", file.getUuid())
                                    .findAll();
                            for (FileRealm child : childs) {
                                updatePath(child, file, realm);
                            }
                        }
                    } else {
                        file.setParentUuid(newParentUuid);
                        FileRealm newParent = getFileByUuid(newParentUuid, realm);
                        String newPath = FileTool.buildPath(Objects.requireNonNull(newParent).getPath(), file.getName());
                        fileTool.move(file.getPath(), newPath, true);
                        file.setPath(newPath);
                        onFileSwitchedParent(true, file, newParent, realm);
                        if (file.isFolder()) {
                            moveChilds(
                                    file.getUuid(), newPath,
                                    newParent.isOffline() && ! file.isOffline(), realm);
                        } else if (newParent.isOffline() && ! file.isOffline()) {
                            file.setDownload(true);
                            file.setOnlyDownload(false);
                        }
                    }
                }

                file.setEventUuid(eventRealm.getUuid());
                file.setEventId(eventRealm.getId());

                if (newHashsum != null) {
                    file.setHashsum(newHashsum);
                }

                if (!file.isFolder() && event.getSize() != file.getSize()) {
                    changeSize(file, event.getSize() - file.getSize(), realm);
                    changeDownloadedSize(
                            file, event.getSize() - file.getDownloadedSize(), realm);
                }

                file.setProcessing(false);
                FileRealm parent = file.getParentUuid() == null ? null :
                        getFileByUuid(file.getParentUuid(), realm);
                if (file.isFolder()) {
                    if (file.isOffline() || (parent != null && parent.isOffline())) {
                        fileTool.createDirectory(file.getPath());
                        file.setOffline(true);
                    } else {
                        file.setOffline(false);
                    }
                } else {
                    file.setOnlyDownload(
                            !(parent != null && parent.isOffline()) || file.isOnlyDownload());
                    file.setDownload(file.isDownload() ||
                            (parent != null && parent.isOffline()) ||
                            (file.getCameraPath() == null &&
                                    checkNeedDownload(file.getName(), file.getSize())));
                    String cameraPath = file.getCameraPath();
                    if (cameraPath != null) {
                        RealmResults<EventRealm> eventsWithSameHases = realm.where(EventRealm.class)
                                .isNull("cameraPath")
                                .equalTo("hashsum", eventRealm.getHashsum())
                                .findAll();
                        if (eventsWithSameHases.isEmpty()) {
                            return;
                        }
                        ArrayList<String> filesUuids = new ArrayList<>();
                        for (EventRealm e : eventsWithSameHases) {
                            e.setCameraPath(cameraPath);
                            filesUuids.add(e.getFileUuid());
                        }
                        RealmResults<FileRealm> files = realm.where(FileRealm.class)
                                .equalTo("isFolder", false)
                                .isNull("cameraPath")
                                .in("uuid", filesUuids.toArray(new String[0]))
                                .findAll();
                        for (FileRealm f : files) {
                            f.setCameraPath(cameraPath);
                            if (f.isDownload() && f.isOnlyDownload()) {
                                f.setDownload(false);
                            }
                        }
                    }
                }
            });
        }
    }

    public void saveEvent(@NonNull EventRealm event) {
        try (Realm realmInstance = Realm.getDefaultInstance()) {
            realmInstance.executeTransaction(realm -> realm.copyToRealmOrUpdate(event));
        }
    }

    public void setPatchSize(String patchUuid, long patchSize) {
        try (Realm realmInstance = Realm.getDefaultInstance()) {
            realmInstance.executeTransaction(realm -> {
                realm.where(EventRealm.class)
                        .equalTo("diffFileUuid", patchUuid)
                        .findAll().setLong("diffFileSize", patchSize);
                realm.where(EventRealm.class)
                        .equalTo("revDiffFileUuid", patchUuid)
                        .findAll().setLong("revDiffFileSize", patchSize);
            });
        }
    }

    public void setProcessing(boolean processing, String uuid) {
        try (Realm realmInstance = Realm.getDefaultInstance()) {
            realmInstance.executeTransaction(realm -> {
                FileRealm file = realm.where(FileRealm.class)
                        .equalTo("uuid", uuid)
                        .findFirst();
                if (file == null) return;
                file.setProcessing(processing);
            });
        }
    }

    public void addOffline(String uuid) {
        try (Realm realmInstance = Realm.getDefaultInstance()) {
            realmInstance.executeTransaction(realm -> {
                FileRealm file = realm.where(FileRealm.class)
                        .equalTo("uuid", uuid)
                        .findFirst();
                if (file == null) return;
                addOffline(file, realm);
            });
        }
    }

    private void addOffline(@NonNull FileRealm file, @NonNull Realm realm) {
        if (file.isFolder()) {
            fileTool.createDirectory(file.getPath());
            file.setOffline(true);
            RealmResults<FileRealm> childs = realm.where(FileRealm.class)
                    .equalTo("parentUuid", file.getUuid())
                    .findAll();
            for (FileRealm child : childs) {
                addOffline(child, realm);
            }
        } else if (!file.isOffline()) {
            file.setDownload(true);
            file.setOnlyDownload(false);
        }
    }

    public void removeOffline(String uuid) {
        try (Realm realmInstance = Realm.getDefaultInstance()) {
            realmInstance.executeTransaction(realm -> {
                FileRealm file = realm.where(FileRealm.class)
                        .equalTo("uuid", uuid)
                        .findFirst();
                if (file == null) return;
                removeOffline(file, realm, true);
            });
        }
    }

    private void removeOffline(@NonNull FileRealm file, @NonNull Realm realm, boolean isRoot) {
        if (isRoot) {
            changeOfflineFilesCount(
                    file, file.isFolder() ? -file.getOfflineFilesCount() : -1, realm);
        }
        file.setOffline(false);
        file.setDownload(false);
        if (file.isFolder()) {
            RealmResults<FileRealm> childs = realm.where(FileRealm.class)
                    .equalTo("parentUuid", file.getUuid())
                    .findAll();
            for (FileRealm child : childs) {
                removeOffline(child, realm, false);
            }
        } else {
            file.setOnlyDownload(true);
        }
    }

    private String generateUniqName(
            String uuid, @Nullable FileRealm parent, String baseName, boolean isFolder,
            String suffix, @NonNull Realm realm) {
        String name = baseName;
        String namePrefix = isFolder ? name : FileTool.getFileName(name);
        String nameSuffix = isFolder ? "" : FileTool.getExtension(name);

        int count = 0;

        while (realm.where(FileRealm.class)
                .notEqualTo("uuid", uuid)
                .equalTo("parentUuid", parent == null ? null : parent.getUuid())
                .equalTo("name", name)
                .findFirst() != null) {
            name = String.format("%s%s%s", namePrefix, suffix, count > 0 ?
                    String.format(" %d%s", count, nameSuffix) : nameSuffix);
            name = name.trim();
            try {
                byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
                if (nameBytes.length >= 255) {
                    if (namePrefix.length() >= nameSuffix.length()) {
                        namePrefix = namePrefix.substring(
                                0, namePrefix.length() - (nameBytes.length - 255) - 1);
                    } else {
                        nameSuffix = "." + nameSuffix.substring((nameBytes.length - 255) + 2);
                    }
                    name = String.format("%s%s%s", namePrefix, suffix, count > 0 ?
                            String.format(" %d%s", count, nameSuffix) : nameSuffix);
                    name = name.trim();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            count += 1;
        }
        return name;
    }

    public void addEvent(@NonNull EventRealm event) {
        try (Realm realmInstance = Realm.getDefaultInstance()) {
            realmInstance.executeTransaction(realm -> realm.copyToRealmOrUpdate(event));
        }
    }

    @Nullable
    public EventRealm getEventByUuid(String uuid) {
        try (Realm realmInstance = Realm.getDefaultInstance()) {
            EventRealm event = realmInstance.where(EventRealm.class)
                    .equalTo("uuid", uuid)
                    .findFirst();
            if (event == null) return null;
            return realmInstance.copyFromRealm(event);
        }
    }

    public boolean cleanup() {
        Log.d(TAG, "cleanup");
        try (Realm realmInstance = Realm.getDefaultInstance()) {
            {
                if (realmInstance.where(DeviceRealm.class)
                        .equalTo("own", true)
                        .equalTo("status", R.string.synced_status)
                        .findFirst() == null) {
                    return false;
                }
                RealmResults<FileRealm> files = realmInstance.where(FileRealm.class)
                        .findAll();
                if (files.where()
                        .equalTo("isDownload", true)
                        .or()
                        .equalTo("isProcessing", true)
                        .or()
                        .isNotNull("downloadPath")
                        .findFirst() != null) {
                    return false;
                }

                HashSet<String> filesHashes = new HashSet<>(files.size());
                ArrayList<String> filesEventsUuids = new ArrayList<>(files.size());
                for (FileRealm file : files) {
                    filesHashes.add(file.getHashsum());
                    filesEventsUuids.add(file.getEventUuid());
                }
                List<File> copyFiles = fileTool.getFileInDirectory(Const.COPIES_PATH);
                HashMap<String, File> copies = new HashMap<>(copyFiles.size());
                for (File f : copyFiles) {
                    copies.put(f.getName(), f);
                }
                copies.keySet().remove(Const.EMPTY_FILE_HASH);
                copies.keySet().removeAll(filesHashes);
                for (File f : copies.values()) {
                    if (!f.delete()) {
                        f.deleteOnExit();
                    }
                }

                String minEventUuid = preferenceService.getLastEventUuid();
                if (minEventUuid != null) {
                    realmInstance.executeTransaction(realm -> {
                        EventRealm minEvent = realm.where(EventRealm.class)
                                .equalTo("uuid", minEventUuid)
                                .findFirst();
                        if (minEvent != null) {
                            realm.where(EventRealm.class)
                                    .lessThan("id", minEvent.getId())
                                    .not()
                                    .in("uuid", filesEventsUuids.toArray(new String[0]))
                                    .findAll()
                                    .deleteAllFromRealm();
                        }
                    });
                }
            }

            {
                RealmResults<EventRealm> events = realmInstance.where(EventRealm.class)
                        .isNotNull("diffFileUuid")
                        .or()
                        .isNotNull("revDiffFileUuid")
                        .findAll();
                HashSet<String> patchesUuids = new HashSet<>();
                for (EventRealm event : events) {
                    String diffUuid = event.getDiffFileUuid();
                    if (diffUuid != null) {
                        patchesUuids.add(diffUuid);
                    }
                    diffUuid = event.getRevDiffFileUuid();
                    if (diffUuid != null) {
                        patchesUuids.add(diffUuid);
                    }
                }
                List<File> patchFiles = fileTool.getFileInDirectory(Const.PATCHES_PATH);
                HashMap<String, File> patches = new HashMap<>(patchFiles.size());
                for (File f : patchFiles) {
                    patches.put(f.getName(), f);
                }
                patches.keySet().removeAll(patchesUuids);
                for (File f : patches.values()) {
                    if (!f.delete()) {
                        f.deleteOnExit();
                    }
                }
            }

            updateOwnDeviceDiskUsage(realmInstance);
        }
        return true;
    }

    // DOWNLOADS

    public void downloadCompleted(String uuid, String eventUuid, String hashsum) {
        Log.i(TAG, String.format("downloadCompleted: %s, hashsum: %s", uuid, hashsum));
        try (Realm realmInstance = Realm.getDefaultInstance()) {
            realmInstance.executeTransaction(realm -> {
                FileRealm file = realm.where(FileRealm.class)
                        .equalTo("uuid", uuid)
                        .beginGroup()
                        .equalTo("isDownload", true)
                        .or()
                        .isNotNull("downloadPath")
                        .endGroup()
                        .findFirst();
                if (file == null) return;
                setFileDownloaded(file, hashsum, eventUuid, false, realm);

                RealmResults<EventRealm> eventsWithSameHashes = realm.where(EventRealm.class)
                        .equalTo("hashsum", hashsum)
                        .notEqualTo("uuid", eventUuid)
                        .findAll();
                if (eventsWithSameHashes.isEmpty()) {
                    return;
                }
                ArrayList<String> eventsUuids = new ArrayList<>();
                for (EventRealm evenWithSameHash : eventsWithSameHashes) {
                    eventsUuids.add(evenWithSameHash.getUuid());
                }
                RealmResults<FileRealm> files = realm.where(FileRealm.class)
                        .beginGroup()
                        .equalTo("isDownload", true)
                        .or()
                        .isNotNull("downloadPath")
                        .endGroup()
                        .in("eventUuid", eventsUuids.toArray(new String[0]))
                        .findAll();
                for (FileRealm f : files) {
                    setFileDownloaded(f, hashsum, f.getEventUuid(), false, realm);
                }
            });
        }
    }

    private void setFileDownloaded(
            @NonNull FileRealm file, String hashsum, String eventUuid, boolean force, @NonNull Realm realm) {
        file.setDownloadStatus(R.string.processing_download);
        if (file.isDownload() || force) {
            file.setHashsum(hashsum);
            if (Objects.equals(eventUuid, file.getEventUuid())) {
                file.setDownload(false);
                file.setDownloadStatus(R.string.processing_download);
                long diff = file.getSize() - file.getDownloadedSize();
                if (diff != 0) {
                    changeDownloadedSize(file, diff, realm);
                }
                if (!file.isOnlyDownload() && !file.isOffline()) {
                    file.setOffline(true);
                    changeOfflineFilesCount(file, 1, realm);
                }
                file.setDownloadActual(true);
            }
            String tempFile = fileTool.buildPathForRecentCopy();
            fileTool.copy(FileTool.buildPathForCopyNamedHash(hashsum), tempFile, true);
            fileTool.move(tempFile, file.getPath(), true);
        }
        if (file.getDownloadPath() != null) {
            fileTool.copy(FileTool.buildPathForCopyNamedHash(hashsum),
                    FileTool.buildPath(file.getDownloadPath(), file.getName()),
                    true);
            file.setDownloadPath(null);
        }
    }

    public void setDownloadedSize(String uuid, long size) {
        try (Realm realmInstance = Realm.getDefaultInstance()) {
            FileRealm file = realmInstance.where(FileRealm.class)
                    .equalTo("uuid", uuid)
                    .lessThan("downloadedSize", size)
                    .findFirst();
            if (file == null) return;
            realmInstance.executeTransaction(realm -> {
                if (!file.isValid()) return;
                long diff = size - file.getDownloadedSize();
                changeDownloadedSize(file, diff, realm);
            });
        }
    }

    public void setDownloadStatus(int status, String uuid) {
        try (Realm realmInstance = Realm.getDefaultInstance()) {
            FileRealm file = realmInstance.where(FileRealm.class)
                    .equalTo("uuid", uuid)
                    .notEqualTo("downloadStatus", status)
                    .findFirst();
            if (file == null) return;
            realmInstance.executeTransaction(realm -> {
                if (!file.isValid()) return;
                file.setDownloadStatus(status);
            });
        }
    }

    public void setDownloadStatusAndDownloadedSize(int status, long size, String uuid) {
        try (Realm realmInstance = Realm.getDefaultInstance()) {
            FileRealm file = realmInstance.where(FileRealm.class)
                    .equalTo("uuid", uuid)
                    .notEqualTo("downloadStatus", status)
                    .findFirst();
            if (file == null) return;
            realmInstance.executeTransaction(realm -> {
                if (!file.isValid()) return;
                file.setDownloadStatus(status);
                long diff = size - file.getDownloadedSize();
                changeDownloadedSize(file, diff, realm);
            });
        }
    }

    public void setDownloadsStatus(int status, @NonNull Set<String> uuids) {
        if (uuids.isEmpty()) return;
        try (Realm realmInstance = Realm.getDefaultInstance()) {
            RealmResults<FileRealm> files = realmInstance.where(FileRealm.class)
                    .in("uuid", uuids.toArray(new String[0]))
                    .notEqualTo("downloadStatus", status)
                    .findAll();
            if (files == null || files.isEmpty()) return;
            realmInstance.executeTransaction(realm -> {
                for (FileRealm file : files) {
                    if (!file.isValid()) return;
                    file.setDownloadStatus(status);
                }
            });
        }
    }

    public void dropCameraPath(String cameraPath) {
        try (Realm realmInstance = Realm.getDefaultInstance()) {
            realmInstance.executeTransaction(realm -> {
                RealmResults<FileRealm> files = realm.where(FileRealm.class)
                        .equalTo("cameraPath", cameraPath)
                        .findAll();
                for (FileRealm file : files) {
                    file.setCameraPath(null);
                    file.setHashsum(null);
                }
                RealmResults<EventRealm> events = realm.where(EventRealm.class)
                        .equalTo("cameraPath", cameraPath)
                        .findAll();
                for (EventRealm event : events) {
                    event.setCameraPath(null);
                }
            });
        }
    }

    public void downloadFile(String uuid) {
        try (Realm realmInstance = Realm.getDefaultInstance()) {
            FileRealm file = realmInstance.where(FileRealm.class)
                    .equalTo("uuid", uuid)
                    .equalTo("isDownload", false)
                    .findFirst();
            if (file == null) return;
            realmInstance.executeTransaction(realm -> {
                if (file.isValid()) {
                    file.setDownload(true);
                    file.setDownloadStatus(R.string.starting_download);
                }
            });
        }
    }

    public void downloadFilesTo(@NonNull ArrayList<String> uuids, String path) {
        if (uuids.isEmpty()) return;
        try (Realm realmInstance = Realm.getDefaultInstance()) {
            RealmResults<FileRealm> files = realmInstance.where(FileRealm.class)
                    .in("uuid", uuids.toArray(new String[0]))
                    .findAll();
            if (files.isEmpty()) return;
            realmInstance.executeTransaction(realm -> {
                for (FileRealm file : files) {
                    if (file.isValid()) setDownloadPath(path, file, realm);
                }
            });
        }
    }

    private void setDownloadPath(String path, @NonNull FileRealm file, @NonNull Realm realm) {
        if (file.isFolder()) {
            RealmResults<FileRealm> childs = realm.where(FileRealm.class)
                    .equalTo("parentUuid", file.getUuid())
                    .findAll();
            path = FileTool.buildPath(path, file.getName());
            for (FileRealm child : childs) {
                setDownloadPath(path, child, realm);
            }
        } else {
            file.setDownloadPath(path);
        }
    }

    // FILES UI


    public RealmResults<FileRealm> getFilesListResult(
            @Nullable String parentUuid, @NonNull ModeProvider.Sorting sorting, boolean filterOffline, @Nullable String query,
            @NonNull Realm realm) {
        RealmQuery<FileRealm> realmQuery = realm.where(FileRealm.class).isNotNull("name");

        if (parentUuid != null && !parentUuid.isEmpty()) {
            realmQuery = realmQuery.equalTo("parentUuid", parentUuid);
        } else {
            realmQuery = realmQuery
                    .beginGroup()
                    .isNull("parentUuid")
                    .or()
                    .isEmpty("parentUuid")
                    .endGroup();
        }

        if (filterOffline) {
            realmQuery = realmQuery
                    .beginGroup()
                    .equalTo("isOffline", true)
                    .or()
                    .greaterThan("offlineFilesCount", 0)
                    .endGroup();
        }

        if (query != null && !query.isEmpty()) {
            realmQuery = realmQuery.beginsWith("name", query, Case.INSENSITIVE);
        }

        switch (sorting) {
            case name:
                realmQuery = realmQuery.sort(
                        "isFolder", Sort.DESCENDING,
                        "name", Sort.ASCENDING);
                break;
            case date:
                realmQuery = realmQuery.sort(
                        "isFolder", Sort.DESCENDING,
                        "dateModified", Sort.DESCENDING);
                break;
            default:
                break;
        }
        return realmQuery.findAll();
    }

    public RealmResults<FileRealm> getRecentFileListResult(@Nullable String query, long limit, @NonNull Realm realm) {
        RealmQuery<FileRealm> realmQuery = realm.where(FileRealm.class).equalTo("isFolder", false);
        if (query != null && !query.isEmpty()) {
            realmQuery = realmQuery.beginsWith("name", query, Case.INSENSITIVE);
        }
        return realmQuery.sort("dateModified", Sort.DESCENDING).limit(limit).findAll();
    }

    public RealmResults<FileRealm> getDownloadsFileListResult(
            @Nullable String query, @NonNull Realm realm) {
        RealmQuery<FileRealm> realmQuery = realm.where(FileRealm.class)
                .equalTo("isFolder", false)
                .equalTo("isDownload", true);
        if (query != null && !query.isEmpty()) {
            realmQuery = realmQuery.beginsWith("name", query, Case.INSENSITIVE);
        }
        realmQuery.sort(
                "downloadStatus", Sort.ASCENDING,
                "size", Sort.ASCENDING);
        return realmQuery.findAll();
    }

    // SHARE

    public void disableShare(String uuid) {
        try (Realm realmInstance = Realm.getDefaultInstance()) {
            realmInstance.executeTransaction((realm) -> {
                FileRealm fileRealm = realm.where(FileRealm.class)
                        .equalTo("uuid", uuid)
                        .findFirst();
                if (fileRealm != null) {
                    fileRealm.setShareSecured(false);
                    fileRealm.setShareExpire(0);
                    fileRealm.setShareLink(null);
                    fileRealm.setShared(false);
                }
            });
        }
    }

    public void saveShareLink(@NonNull JSONObject info) {
        try (Realm realmInstance = Realm.getDefaultInstance()) {
            realmInstance.executeTransaction((realm) -> {
                FileRealm fileRealm = realm.where(FileRealm.class)
                        .equalTo("uuid", JSON.optString(info,"uuid", ""))
                        .findFirst();
                if (fileRealm != null) {
                    fileRealm.setShared(true);
                    fileRealm.setShareSecured(info.optBoolean("share_password"));
                    fileRealm.setShareExpire(info.optInt("share_ttl_info"));
                    fileRealm.setShareLink(JSON.optString(info, "share_link"));
                }
            });
        }
    }

    public void updateShareList(@NonNull Map<String, JSONObject> shareInfo) {
        String[] uuidsArray = shareInfo.keySet().toArray(new String[0]);
        try (Realm realmInstance = Realm.getDefaultInstance()) {
            realmInstance.executeTransaction((realm) -> {
                RealmResults<FileRealm> removeShare = realm.where(FileRealm.class)
                        .equalTo("isShared", true)
                        .findAll();
                for (FileRealm file : removeShare) {
                    file.setShared(false);
                    file.setShareSecured(false);
                    file.setShareExpire(0);
                    file.setShareLink(null);
                }
                if (uuidsArray.length == 0) return;
                RealmResults<FileRealm> addShare = realm.where(FileRealm.class)
                        .in("uuid", uuidsArray)
                        .findAll();
                for (FileRealm file : addShare) {
                    JSONObject info = shareInfo.get(file.getUuid());
                    if (info == null) continue;
                    file.setShared(true);
                    file.setShareSecured(info.optBoolean("share_password"));
                    file.setShareExpire(info.optInt("share_ttl_info"));
                    file.setShareLink(JSON.optString(info, "share_link"));
                }
            });
        }
    }

    // COLLABORATIONS

    public void updateCollaboratedFolders(@NonNull ArrayList<String> collaboratedFolders) {
        Log.d(TAG, String.format("updateCollaboratedFolders: %s", collaboratedFolders));
        try (Realm realmInstance = Realm.getDefaultInstance()) {
            realmInstance.executeTransaction((realm) -> {
                String[] collaboratedFoldersArray = collaboratedFolders
                        .toArray(new String[0]);
                List<FileRealm> list = realm.where(FileRealm.class)
                        .equalTo("isCollaborated", true)
                        .not().in("uuid", collaboratedFoldersArray)
                        .findAll();
                for (FileRealm file : list) {
                    file.setCollaborated(false);
                }
                if (!collaboratedFolders.isEmpty()) {
                    list = realm.where(FileRealm.class)
                            .equalTo("isCollaborated", false)
                            .in("uuid", collaboratedFoldersArray)
                            .findAll();
                    for (FileRealm file : list) {
                        file.setCollaborated(true);
                    }
                }
            });
        }
        Log.d(TAG, String.format("updateCollaboratedFolders: %s done", collaboratedFolders));
    }

    // DEVICES

    public void savePeerList(@NonNull JSONArray peers) {
        try (Realm realmInstance = Realm.getDefaultInstance()) {
            realmInstance.executeTransaction((realm) -> {
                realm.where(DeviceRealm.class)
                        .notEqualTo("own", true)
                        .findAll().deleteAllFromRealm();
                for (int i = 0; i < peers.length(); ++i) {
                    JSONObject peer = peers.optJSONObject(i);
                    if (peer == null) return;
                    addPeerInternal(realm, peer);
                }
            });
        }
    }

    public void peerConnect(@NonNull JSONObject peer) {
        try (Realm realmInstance = Realm.getDefaultInstance()) {
            realmInstance.executeTransaction((realm) -> addPeerInternal(realm, peer));
        }
    }

    private void addPeerInternal(@NonNull Realm realm, @NonNull JSONObject peer) {

        String id = JSON.optString(peer, "id");
        String type = JSON.optString(peer, "type");
        boolean own = peer.optBoolean("own", false);
        int syncStatus = peer.optInt(
                "node_status",
                Integer.valueOf(peer.optString("node_status", "7")));


        if (!"node".equals(type) || !own) return;

        DeviceRealm device = realm.where(DeviceRealm.class)
                .equalTo("id", id)
                .findFirst();
        if (device == null) {
            device = new DeviceRealm();
            device.setId(id);
        } else if (syncStatus == 0) {
            device.deleteFromRealm();
            return;
        }
        device.setName(JSON.optString(peer, "node_name"));
        device.setOnline(peer.optBoolean("is_online", false));
        device.setDeviceType(JSON.optString(peer,"node_devicetype"));
        device.setOs(JSON.optString(peer, "node_osname"));
        device.setOsType(JSON.optString(peer, "node_ostype"));
        device.setDownloadSpeed(0);
        device.setUploadSpeed(0);

        int status = convertDeviceStatus(syncStatus);
        if (status != device.getStatus()) {
            device.setStatus(status);
            device.setLogoutInProgress(false);
            device.setWipeInProgress(false);
        }

        device.setDiskUsage(peer.optLong("disk_usage"));

        realm.copyToRealmOrUpdate(device);
    }

    public void disconnectNode(String nodeId) {
        try (Realm realmInstance = Realm.getDefaultInstance()) {
            realmInstance.executeTransaction((realm) -> {
                DeviceRealm deviceRealm = realm.where(DeviceRealm.class)
                        .equalTo("id", nodeId)
                        .findFirst();
                if (deviceRealm != null) {
                    deviceRealm.setOnline(false);
                    deviceRealm.setDownloadSpeed(0);
                    deviceRealm.setUploadSpeed(0);
                    if (deviceRealm.getStatus() != R.string.logged_out_status && deviceRealm.getStatus() != R.string.wiped_status) {
                        deviceRealm.setStatus(R.string.power_off_status);
                    }
                    deviceRealm.setLogoutInProgress(false);
                    deviceRealm.setWipeInProgress(false);
                }
            });
        }
    }

    public void saveNodeStatus(String nodeId, JSONObject json) {
        try (Realm realmInstance = Realm.getDefaultInstance()) {
            realmInstance.executeTransaction((realm) -> {
                DeviceRealm deviceRealm = realm.where(DeviceRealm.class).equalTo("id", nodeId).findFirst();
                if (deviceRealm != null) {
                    int syncStatus = json.optInt(
                            "node_status",
                            Integer.valueOf(json.optString("node_status", "7")));
                    if (syncStatus == 0) {
                        deviceRealm.deleteFromRealm();
                    } else {
                        int status = convertDeviceStatus(syncStatus);
                        if (status != deviceRealm.getStatus()) {
                            deviceRealm.setStatus(status);
                            deviceRealm.setLogoutInProgress(false);
                            deviceRealm.setWipeInProgress(false);
                        }

                        deviceRealm.setDownloadSpeed(json.optDouble("download_speed"));
                        deviceRealm.setUploadSpeed(json.optDouble("upload_speed"));
                        deviceRealm.setDiskUsage(json.optLong("disk_usage"));
                    }
                }
            });
        }
    }

    public void deleteDevice(String nodeId) {
        try (Realm realmInstance = Realm.getDefaultInstance()) {
            realmInstance.executeTransaction((realm) -> {
                DeviceRealm deviceRealm = realm.where(DeviceRealm.class).equalTo("id", nodeId).findFirst();
                if (deviceRealm != null) {
                    deviceRealm.deleteFromRealm();
                }
            });
        }
    }

    private int convertDeviceStatus(int status) {
        switch (status) {
            case 4:
                return R.string.synced_status;
            case 5:
                return R.string.logged_out_status;
            case 6:
                return R.string.wiped_status;
            case 7:
                return R.string.power_off_status;
            case 8:
                return R.string.paused_status;
            case 9:
                return R.string.indexing_status;
            default:
                return R.string.syncing_status;

        }
    }

    public boolean isNodeOnline() {
        try (Realm realm = Realm.getDefaultInstance()) {
            return realm.where(DeviceRealm.class)
                    .notEqualTo("id", "own")
                    .equalTo("online", true)
                    .count() > 0;
        }
    }

    public void setupOwnDevice() {
        try (Realm realmInstance = Realm.getDefaultInstance()) {
            realmInstance.executeTransaction((realm) -> {
                DeviceRealm device = realm.where(DeviceRealm.class)
                        .equalTo("own", true)
                        .findFirst();
                if (device == null) {
                    device = new DeviceRealm();
                    device.setId("own");
                    device.setOwn(true);
                }
                device.setStatus(R.string.connecting_status);
                device.setOnline(false);
                device.setName(Const.nodeName);
                device.setOs(Const.nodeOsName);
                device.setOsType(Const.nodeOsType);
                device.setDeviceType(Const.nodeType);
                device.setFetchingChanges(true);
                device.setUploadSpeed(0);
                device.setDownloadSpeed(0);
                device.setUploadedSize(0);
                device.setDownloadedSize(0);
                device.setConnectedNodesCount(0);
                device.setDiskUsage(fileTool.getFolderSize(Const.DEFAULT_PATH) +
                        fileTool.getFolderSize(Const.INTERNAL_PATH));
                device.setDownloadsCount(
                        realm.where(FileRealm.class)
                                .equalTo("isDownload", true)
                                .count());
                device.setRemotesCount(0);
                device.setProcessingOperation(false);
                device.setDownloadingShare(false);
                device.setProcessingLocalCount(0);
                device.setImportingCamera(false);
                device.setCurrentDownloadName(null);
                device.setInitialSyncing(true);
                device.setPaused(false);
                realm.copyToRealmOrUpdate(device);
            });
        }
    }

    @Nullable
    public DeviceRealm getOwnDevice() {
        try (Realm realm = Realm.getDefaultInstance()) {
            DeviceRealm device = realm.where(DeviceRealm.class)
                    .equalTo("own", true)
                    .findFirst();
            if (device == null) return null;
            return realm.copyFromRealm(device);
        }
    }

    public void updateOwnDevicePaused(boolean paused) {
        try (Realm realmInstance = Realm.getDefaultInstance()) {
            realmInstance.executeTransaction((realm) -> {
                DeviceRealm device = realm.where(DeviceRealm.class)
                        .equalTo("own", true)
                        .findFirst();
                if (device == null) return;
                device.setPaused(paused);
                if (paused) {
                    if (onPausedCallback != null) {
                        onPausedCallback.run();
                    }
                } else {
                    if (device.getStatus() == R.string.synced_status) {
                        if (onSyncedCallback != null) {
                            onSyncedCallback.run();
                        }
                    } else {
                        if (onSyncingCallback != null) {
                            onSyncingCallback.run();
                        }
                    }
                }
            });
        }
    }

    public void updateOwnDeviceSpeedAndSize(
            double downloadSpeed, double uploadSpeed, long downloaded, long uploaded) {
        try (Realm realmInstance = Realm.getDefaultInstance()) {
            realmInstance.executeTransaction((realm) -> {
                DeviceRealm device = realm.where(DeviceRealm.class)
                        .equalTo("own", true)
                        .findFirst();
                if (device == null) return;
                device.setDownloadSpeed(downloadSpeed);
                device.setUploadSpeed(uploadSpeed);
                device.setDownloadedSize(downloaded);
                device.setUploadedSize(uploaded);
            });
        }
    }

    public void updateOwnDeviceConnectedNodesCount(int value) {
        try (Realm realmInstance = Realm.getDefaultInstance()) {
            realmInstance.executeTransaction((realm) -> {
                DeviceRealm device = realm.where(DeviceRealm.class)
                        .equalTo("own", true)
                        .findFirst();
                if (device == null) return;
                device.setConnectedNodesCount(value);
            });
        }
    }

    public void updateOwnDeviceNewNotificationsCount(long value) {
        try (Realm realmInstance = Realm.getDefaultInstance()) {
            realmInstance.executeTransaction((realm) -> {
                DeviceRealm device = realm.where(DeviceRealm.class)
                        .equalTo("own", true)
                        .findFirst();
                if (device == null) return;
                device.setNotificationsCount(value);
            });
        }
    }

    public void updateOwnDeviceDownloads(String name) {
        try (Realm realmInstance = Realm.getDefaultInstance()) {
            realmInstance.executeTransaction((realm) -> {
                DeviceRealm device = realm.where(DeviceRealm.class)
                        .equalTo("own", true)
                        .findFirst();
                if (device == null) return;
                device.setDownloadsCount(
                        realm.where(FileRealm.class)
                                .equalTo("isDownload", true)
                                .count());
                device.setCurrentDownloadName(name);
                updateOwnDeviceStatus(device);
            });
        }
    }

    public void updateOwnDeviceInitialSyncing(boolean value) {
        try (Realm realmInstance = Realm.getDefaultInstance()) {
            realmInstance.executeTransaction((realm) -> {
                DeviceRealm device = realm.where(DeviceRealm.class)
                        .equalTo("own", true)
                        .findFirst();
                if (device == null) return;
                device.setInitialSyncing(value);
                updateOwnDeviceStatus(device);
            });
        }
    }

    public void updateOwnDeviceImportingCamera(boolean value) {
        try (Realm realmInstance = Realm.getDefaultInstance()) {
            realmInstance.executeTransaction((realm) -> {
                DeviceRealm device = realm.where(DeviceRealm.class)
                        .equalTo("own", true)
                        .findFirst();
                if (device == null) return;
                device.setImportingCamera(value);
                updateOwnDeviceStatus(device);
            });
        }
    }

    public void updateOwnDeviceLocalProcessingCount(long value) {
        Log.i(TAG, String.format("updateOwnDeviceLocalProcessingCount: %d", value));
        try (Realm realmInstance = Realm.getDefaultInstance()) {
            realmInstance.executeTransaction((realm) -> {
                DeviceRealm device = realm.where(DeviceRealm.class)
                        .equalTo("own", true)
                        .findFirst();
                if (device == null) return;
                device.setProcessingLocalCount(value);
                updateOwnDeviceStatus(device);
            });
        }
    }

    public void updateOwnDeviceRemotes(boolean fetchingChanges, long remotesCount) {
        try (Realm realmInstance = Realm.getDefaultInstance()) {
            realmInstance.executeTransaction((realm) -> {
                DeviceRealm device = realm.where(DeviceRealm.class)
                        .equalTo("own", true)
                        .findFirst();
                if (device == null) return;
                device.setFetchingChanges(fetchingChanges);
                device.setRemotesCount(remotesCount);
                updateOwnDeviceStatus(device);
            });
        }
    }

    public void updateOwnDeviceProcessingOperation(boolean value) {
        try (Realm realmInstance = Realm.getDefaultInstance()) {
            realmInstance.executeTransaction((realm) -> {
                DeviceRealm device = realm.where(DeviceRealm.class)
                        .equalTo("own", true)
                        .findFirst();
                if (device == null) return;
                device.setProcessingOperation(value);
                updateOwnDeviceStatus(device);
            });
        }
    }

    public void updateOwnDeviceDownloadingShare(boolean value) {
        try (Realm realmInstance = Realm.getDefaultInstance()) {
            realmInstance.executeTransaction((realm) -> {
                DeviceRealm device = realm.where(DeviceRealm.class)
                        .equalTo("own", true)
                        .findFirst();
                if (device == null) return;
                device.setDownloadingShare(value);
                updateOwnDeviceStatus(device);
            });
        }
    }

    public void updateOwnDeviceOnline(boolean value) {
        try (Realm realmInstance = Realm.getDefaultInstance()) {
            realmInstance.executeTransaction((realm) -> {
                DeviceRealm device = realm.where(DeviceRealm.class)
                        .equalTo("own", true)
                        .findFirst();
                if (device == null) return;
                if (!device.isOnline() && value) {
                    device.setFetchingChanges(true);
                }
                device.setOnline(value);
                updateOwnDeviceStatus(device);
            });
        }
    }

    private void updateOwnDeviceDiskUsage(@NonNull Realm realmInstance) {
        long diskUsage = fileTool.getFolderSize(Const.DEFAULT_PATH) +
                fileTool.getFolderSize(Const.INTERNAL_PATH);
        realmInstance.executeTransaction(realm -> {
            DeviceRealm device = realm.where(DeviceRealm.class)
                    .equalTo("own", true)
                    .findFirst();
            if (device == null) return;
            device.setDiskUsage(diskUsage);
        });
    }

    private void updateOwnDeviceStatus(@NonNull DeviceRealm device) {
        int status;
        if (!device.isOnline()) {
            status = R.string.connecting_status;
        } else if (
                device.isInitialSyncing() || device.isFetchingChanges()
                        || device.getRemotesCount() > 0
                        || device.isImportingCamera() || device.getProcessingLocalCount() > 0
                        || device.isProcessingOperation() || device.getDownloadsCount() > 0
                        || device.isDownloadingShare()) {
            status = R.string.syncing_status;
        } else {
            status = R.string.synced_status;
        }

        if (status != device.getStatus()) {
            device.setStatus(status);
            if (status == R.string.synced_status) {
                if (device.isPaused() && onPausedCallback != null) {
                    onPausedCallback.run();
                } else if (onSyncedCallback != null) {
                    onSyncedCallback.run();
                }
            } else if (status == R.string.syncing_status && onSyncingCallback != null) {
                onSyncingCallback.run();
            }
        }
    }

    public void updateDeviceLogoutInProgress(String id) {
        try (Realm realmInstance = Realm.getDefaultInstance()) {
            realmInstance.executeTransaction((realm) -> {
                DeviceRealm device = realm.where(DeviceRealm.class)
                        .equalTo("id", id)
                        .findFirst();
                if (device == null) return;
                device.setLogoutInProgress(true);
            });
        }
    }

    public void updateDeviceWipeInProgress(String id) {
        try (Realm realmInstance = Realm.getDefaultInstance()) {
            realmInstance.executeTransaction((realm) -> {
                DeviceRealm device = realm.where(DeviceRealm.class)
                        .equalTo("id", id)
                        .findFirst();
                if (device == null) return;
                device.setWipeInProgress(true);
            });
        }
    }

    public void setOnSyncedCallback(@Nullable Runnable callback) {
        onSyncedCallback = callback;
    }

    public void setOnSyncingCallback(@Nullable Runnable callback) {
        onSyncingCallback = callback;
    }

    public void setOnPausedCallback(@Nullable Runnable callback) {
        onPausedCallback = callback;
    }
}
