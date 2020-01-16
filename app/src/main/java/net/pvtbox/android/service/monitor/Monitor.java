package net.pvtbox.android.service.monitor;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.FileObserver;
import android.os.Handler;
import android.os.HandlerThread;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.util.Log;

import net.pvtbox.android.R;
import net.pvtbox.android.api.EventsHttpClient;
import net.pvtbox.android.application.Const;
import net.pvtbox.android.db.model.EventRealm;
import net.pvtbox.android.db.model.FileRealm;
import net.pvtbox.android.service.OperationService;
import net.pvtbox.android.service.PreferenceService;
import net.pvtbox.android.db.DataBaseService;
import net.pvtbox.android.tools.FileTool;
import net.pvtbox.android.tools.JSON;
import net.pvtbox.android.tools.PatchTool;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static net.pvtbox.android.tools.Hasher.md5;


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
public class Monitor {
    private final String TAG = Monitor.class.getSimpleName();

    private final HandlerThread handlerThread = new HandlerThread(
            "MonitorThread",
            HandlerThread.NORM_PRIORITY - HandlerThread.MIN_PRIORITY * 2);
    @Nullable
    private Handler handler;
    private final HandlerThread cameraHandlerThread = new HandlerThread(
            "MonitorCameraThread", HandlerThread.NORM_PRIORITY);
    @Nullable
    private Handler cameraHandler;
    private final Context context;
    private final FileTool fileTool;

    private final DataBaseService dataBaseService;
    private final PreferenceService preferenceService;
    private final PatchTool patchTool;

    private BroadcastReceiver restartMonitor;
    private BroadcastReceiver pauseMonitor;
    private boolean importingCamera = true;
    private boolean working = true;

    @Nullable
    private Runnable updateFileStructWork = null;
    @Nullable
    private Runnable updateCameraWork = null;

    private boolean havePendingFileStructUpdate = false;


    private static final int mask = FileObserver.CLOSE_WRITE
            | FileObserver.MOVED_FROM | FileObserver.MOVED_TO | FileObserver.DELETE | FileObserver.CREATE
            | FileObserver.DELETE_SELF | FileObserver.MOVE_SELF;

    @NonNull
    private final ConcurrentHashMap<String, FileObserver> observers = new ConcurrentHashMap<>();
    @NonNull
    private final ConcurrentHashMap<String, FileObserver> cameraObservers = new ConcurrentHashMap<>();

    private final OperationService operationService;
    private final EventsHttpClient httpClient;
    private long localProcessingCount = 0;

    public Monitor(Context context,
                   OperationService operationService,
                   FileTool fileTool,
                   DataBaseService dataBaseService,
                   PreferenceService preferenceService,
                   EventsHttpClient httpClient,
                   PatchTool patchTool) {
        this.context = context;
        this.operationService = operationService;
        this.httpClient = httpClient;
        this.fileTool = fileTool;
        this.dataBaseService = dataBaseService;
        this.preferenceService = preferenceService;
        this.patchTool = patchTool;
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
        cameraHandlerThread.start();
        cameraHandler = new Handler(cameraHandlerThread.getLooper());
    }

    public void onInitialSyncDone() {
        importingCamera = false;

        updateCameraWork = this::updateCamera;
        if (cameraHandler != null) {
            cameraHandler.post(updateCameraWork);
        }

        updateFileStructWork = this::updateFileStruct;
        if (handler != null) {
            handler.post(updateFileStructWork);
            handler.post(this::registerBroadcasts);
        }
    }

    public void onDestroy() {
        working = false;
        LocalBroadcastManager.getInstance(context).unregisterReceiver(restartMonitor);
        LocalBroadcastManager.getInstance(context).unregisterReceiver(pauseMonitor);
        stopWatchingCamera();
        stopWatching();
        if (updateFileStructWork != null && handler != null) {
            handler.removeCallbacks(updateFileStructWork);
        }
        handler = null;
        handlerThread.quitSafely();
        if (updateCameraWork != null && cameraHandler != null) {
            cameraHandler.removeCallbacks(updateCameraWork);
        }
        cameraHandler = null;
        cameraHandlerThread.quitSafely();
    }

    private void registerBroadcasts() {
        IntentFilter filter = new IntentFilter(Const.RESTART_MONITOR_INTENT);
        restartMonitor = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (cameraHandler == null || handler == null) return;
                working = true;
                cameraHandler.post(() -> {
                    Log.d(TAG, "onReceive: restart monitor intent");
                    if (updateCameraWork == null) {
                        updateCameraWork = Monitor.this::updateCamera;
                        cameraHandler.post(updateCameraWork);
                    }
                });
                handler.post(() -> {
                    if (updateFileStructWork == null) {
                        updateFileStructWork = Monitor.this::updateFileStruct;
                        handler.post(updateFileStructWork);
                    } else {
                        havePendingFileStructUpdate = true;
                    }
                });
            }
        };
        LocalBroadcastManager.getInstance(context).registerReceiver(restartMonitor, filter);

        filter = new IntentFilter(Const.PAUSE_MONITOR_INTENT);
        pauseMonitor = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                working = false;
            }
        };
        LocalBroadcastManager.getInstance(context).registerReceiver(pauseMonitor, filter);

    }


    private void addWatcher(@NonNull String path) {
        //noinspection SuspiciousMethodCalls
        if (!observers.contains(path)) {
            FileObserver observer = new FileObserver(path, mask) {
                @Override
                public void onEvent(int event, @Nullable String path) {
                    if (path == null) return;
                    Log.d(TAG, String.format("onEvent: %d, %s", event, path));
                    if (updateFileStructWork == null && handler != null) {
                        updateFileStructWork = Monitor.this::updateFileStruct;
                        handler.post(updateFileStructWork);
                    } else {
                        havePendingFileStructUpdate = true;
                    }
                }
            };
            observers.put(path, observer);
            observer.startWatching();
        }
    }

    private void addCameraWatcher(@NonNull String path) {
        //noinspection SuspiciousMethodCalls
        if (!cameraObservers.contains(path)) {
            FileObserver observer = new FileObserver(path, mask) {
                @Override
                public void onEvent(int event, @Nullable String path) {
                    if (path == null) return;
                    Log.d(TAG, String.format("onEvent: %d, %s", event, path));
                    if (updateCameraWork == null && cameraHandler != null) {
                        updateCameraWork = Monitor.this::updateCamera;
                        cameraHandler.post(updateCameraWork);
                    }
                }
            };
            cameraObservers.put(path, observer);
            observer.startWatching();
        }
    }

    private void stopWatching() {
        for (FileObserver observer : observers.values()) {
            observer.stopWatching();
        }
        observers.clear();
    }

    private void stopWatchingCamera() {
        for (FileObserver observer : cameraObservers.values()) {
            observer.stopWatching();
        }
        cameraObservers.clear();
    }

    private void updateFileStruct() {
        if (!working) return;
        Log.i(TAG, "updateFileStruct");

        TreeSet<String> existingFilesPaths = new TreeSet<>();
        TreeSet<String> existingFoldersPaths = new TreeSet<>();
        List<File> existingFiles = fileTool.getTotalListFile(Const.DEFAULT_PATH);
        stopWatching();
        addWatcher(Const.DEFAULT_PATH);
        for (File file : existingFiles) {
            if (file.isDirectory()) {
                addWatcher(file.getPath());
                existingFoldersPaths.add(file.getAbsolutePath());
            } else {
                existingFilesPaths.add(file.getAbsolutePath());
            }
        }
        HashMap<String, ArrayList<Long>> knownFiles = dataBaseService.getOfflineAndActualFiles();
        HashSet<String> offlineFolders = dataBaseService.getOfflineFolders();
        HashSet<String> allFolders = dataBaseService.getAllFolders();

        TreeSet<String> addedFoldersPaths = new TreeSet<>(existingFoldersPaths);
        addedFoldersPaths.removeAll(allFolders);
        updateLocalProcessingCount(addedFoldersPaths.size());

        TreeSet<String> addedFilesPaths = new TreeSet<>(existingFilesPaths);
        addedFilesPaths.removeAll(knownFiles.keySet());
        updateLocalProcessingCount(addedFilesPaths.size());

        TreeSet<String> deletedFoldersPaths = new TreeSet<>(offlineFolders);
        deletedFoldersPaths.removeAll(existingFoldersPaths);
        updateLocalProcessingCount(deletedFoldersPaths.size());

        TreeSet<String> deletedFilesPaths = new TreeSet<>(knownFiles.keySet());
        deletedFilesPaths.removeAll(existingFilesPaths);
        updateLocalProcessingCount(deletedFilesPaths.size());

        onCreateFolder(null, addedFoldersPaths.iterator());
        onCreateFile(null, addedFilesPaths.iterator());
        onDeleteFolder(deletedFoldersPaths.iterator());
        onDeleteFile(deletedFilesPaths.iterator());

        TreeSet<String> modifiedFilesPaths = new TreeSet<>(knownFiles.keySet());
        modifiedFilesPaths.removeAll(deletedFilesPaths);

        TreeSet<String> modifiedFiles = new TreeSet<>();

        for (String modifiedPath : modifiedFilesPaths) {
            if (!working) return;
            File file = new File(modifiedPath);
            ArrayList<Long> info = knownFiles.get(modifiedPath);
            if (info == null) return;
            if (file.lastModified() != info.get(0) || file.length() != info.get(1)) {
                modifiedFiles.add(modifiedPath);
            }
        }
        if (!modifiedFiles.isEmpty()) {
            updateLocalProcessingCount(modifiedFiles.size());
            onModifyFile(modifiedFiles.iterator());
        }
    }

    private void onCreateFolder(@Nullable String path, @NonNull Iterator<String> iterator) {
        if (path == null) {
            if (!iterator.hasNext()) return;
            path = iterator.next();
        }
        Log.i(TAG, String.format("onCreateFolder: %s", path));
        if (!FileTool.isExist(path)) {
            if (handler != null) {
                handler.post(() -> {
                    updateLocalProcessingCount(-1);
                    onCreateFolder(null, iterator);
                });
            }
            return;
        }
        FileRealm file = dataBaseService.getFileByPath(path);
        if (file != null) {
            if (handler != null) {
                handler.post(() -> {
                    updateLocalProcessingCount(-1);
                    onCreateFolder(null, iterator);
                });
            }
            return;
        }
        String parentPath = FileTool.getParentPath(path);
        FileRealm parent = null;
        if (!Const.DEFAULT_PATH.equals(parentPath)) {
            parent = dataBaseService.getFileByPath(parentPath);
            if (parent == null || parent.isProcessing()) {
                String finalPath = path;
                if (handler != null) {
                    handler.postDelayed(() -> onCreateFolder(finalPath, iterator), 500);
                }
                return;
            }
        }
        operationService.createFolder(
                parent == null ? null : parent.getUuid(), FileTool.getNameFromPath(path),
                false,
                (response, uuid) -> {
                    if (handler != null) {
                        handler.post(() -> {
                            updateLocalProcessingCount(-1);
                            onCreateFolder(null, iterator);
                        });
                    }
                },
                (error, uuid) -> {
                    if (handler != null) {
                        handler.post(() -> {
                            updateLocalProcessingCount(-1);
                            onCreateFolder(null, iterator);
                        });
                    }
                });
    }

    private void onDeleteFolder(@NonNull Iterator<String> iterator) {
        if (!iterator.hasNext()) return;
        String path = iterator.next();
        Log.i(TAG, String.format("onDeleteFolder: %s", path));
        if (FileTool.isExist(path)) {
            if (handler != null) {
                handler.post(() -> {
                    updateLocalProcessingCount(-1);
                    onDeleteFolder(iterator);
                });
            }
            return;
        }
        FileRealm file = dataBaseService.getFileByPath(path);
        if (file == null || file.isProcessing() || !file.isOffline()) {
            if (handler != null) {
                handler.post(() -> {
                    updateLocalProcessingCount(-1);
                    onDeleteFolder(iterator);
                });
            }
            return;
        }

        String uuid = file.getUuid();
        String eventUuid = md5(UUID.randomUUID().toString());
        dataBaseService.setProcessing(true, uuid);
        httpClient.deleteFolder(
                eventUuid, uuid, file.getEventId(),
                response -> {
                    if (handler != null) {
                        handler.post(() -> {
                            dataBaseService.deleteFileByUuid(uuid);
                            updateLocalProcessingCount(-1);
                            onDeleteFolder(iterator);
                        });
                    }
                },
                error -> {
                    if (handler != null) {
                        handler.post(() -> {
                            dataBaseService.setProcessing(false, uuid);
                            updateLocalProcessingCount(-1);
                            onDeleteFolder(iterator);
                        });
                    }
                });
    }

    private void onCreateFile(@Nullable String path, @NonNull Iterator<String> iterator) {
        if (path == null) {
            if (!iterator.hasNext()) {
                return;
            }
            path = iterator.next();
        }
        Log.i(TAG, String.format("onCreateFile: %s", path));
        if (!FileTool.isExist(path)) {
            if (handler != null) {
                handler.post(() -> {
                    updateLocalProcessingCount(-1);
                    onCreateFile(null, iterator);
                });
            }
            return;
        }
        File f = new File(path);
        long mtime = f.lastModified();
        long size = f.length();
        FileRealm file = dataBaseService.getFileByPath(path);
        if (file != null) {
            String tempPath = fileTool.buildPathForRecentCopy();
            if (!fileTool.copy(path, tempPath, true) ||
                    FileTool.getFileSize(tempPath) != size) {
                fileTool.deleteFile(tempPath);
                if (handler != null) {
                    handler.post(() -> {
                        updateLocalProcessingCount(-1);
                        onCreateFile(null, iterator);
                    });
                }
                return;
            }
            String hashsum = PatchTool.getFileHash(tempPath);
            String copyPath = FileTool.buildPathForCopyNamedHash(hashsum);
            if (!fileTool.move(tempPath, copyPath, false) ||
                    FileTool.getFileSize(copyPath) != size) {
                fileTool.deleteFile(tempPath);
                fileTool.deleteFile(copyPath);
                if (handler != null) {
                    handler.post(() -> {
                        updateLocalProcessingCount(-1);
                        onCreateFile(null, iterator);
                    });
                }
                return;
            }
            if (file.isDownload()) {
                dataBaseService.setFileMTime(file.getUuid(), mtime);
            } else {
                EventRealm event = dataBaseService.getEventByUuid(file.getEventUuid());
                if (event != null && Objects.equals(event.getHashsum(), hashsum)) {
                    dataBaseService.onFileFound(file.getUuid(), file.getEventUuid(), hashsum);
                }
            }
            if (handler != null) {
                handler.post(() -> {
                    updateLocalProcessingCount(-1);
                    onCreateFile(null, iterator);
                });
            }
            return;
        }
        String finalPath = path;
        String parentPath = FileTool.getParentPath(path);
        FileRealm parent = null;
        if (!Const.DEFAULT_PATH.equals(parentPath)) {
            parent = dataBaseService.getFileByPath(parentPath);
            if (parent == null || parent.isProcessing()) {
                if (handler != null) {
                    handler.postDelayed(() -> onCreateFile(finalPath, iterator), 500);
                }
                return;
            }
        }
        FileRealm finalParent = parent;
        if (handler != null) {
            handler.postDelayed(() -> registerNewFile(finalPath, finalParent, mtime, size, iterator),
                    1000);
        }
    }

    private void registerNewFile(
            @NonNull String path, @Nullable FileRealm parent, long mtime, long size, @NonNull Iterator<String> iterator) {
        File f = new File(path);
        long newMtime = f.lastModified();
        long newSize = f.length();
        if (newMtime == 0) {
            if (handler != null) {
                handler.post(() -> {
                    updateLocalProcessingCount(-1);
                    onCreateFile(null, iterator);
                });
            }
            return;
        }
        if (!FileTool.isExist(path)) {
            if (handler != null) {
                handler.post(() -> {
                    updateLocalProcessingCount(-1);
                    onCreateFile(null, iterator);
                });
            }
            return;
        }
        FileRealm file = dataBaseService.getFileByPath(path);
        String tempPath = fileTool.buildPathForRecentCopy();
        Runnable runnable = () -> registerNewFile(path, parent, newMtime, newSize, iterator);
        if (!fileTool.copy(path, tempPath, true) ||
                FileTool.getFileSize(tempPath) != newSize) {
            fileTool.deleteFile(tempPath);
            if (handler != null) {
                handler.postDelayed(runnable, 2000);
            }
            return;
        }
        String hashsum = PatchTool.getFileHash(tempPath);
        String copyPath = FileTool.buildPathForCopyNamedHash(hashsum);
        if (!fileTool.move(tempPath, copyPath, false) ||
                FileTool.getFileSize(copyPath) != newSize) {
            fileTool.deleteFile(tempPath);
            fileTool.deleteFile(copyPath);
            if (handler != null) {
                handler.postDelayed(runnable, 2000);
            }
            return;
        }

        if (file != null) {
            if (file.isDownload()) {
                dataBaseService.setFileMTime(file.getUuid(), newMtime);
            }
            if (handler != null) {
                handler.post(() -> {
                    updateLocalProcessingCount(-1);
                    onCreateFile(null, iterator);
                });
            }
            return;
        }
        if (mtime != newMtime || size != newSize) {
            if (handler != null) {
                handler.postDelayed(runnable,
                        2000);
            }
            return;
        }
        operationService.createFile(
                parent == null ? null : parent.getUuid(),
                FileTool.getNameFromPath(path), copyPath, hashsum, false, false,
                (response, uuid) -> {
                    if (handler != null) {
                        handler.post(() -> {
                            dataBaseService.setFileMTime(uuid, mtime);
                            updateLocalProcessingCount(-1);
                            onCreateFile(null, iterator);
                        });
                    }
                },
                (error, uuid) -> {
                    if (handler != null) {
                        handler.post(() -> {
                            updateLocalProcessingCount(-1);
                            onCreateFile(null, iterator);
                        });
                    }
                });
    }

    private void onModifyFile(@NonNull Iterator<String> iterator) {
        if (!iterator.hasNext()) {
            return;
        }
        String path = iterator.next();
        Log.i(TAG, String.format("onModifyFile: %s", path));
        if (!FileTool.isExist(path)) {
            if (handler != null) {
                handler.post(() -> {
                    updateLocalProcessingCount(-1);
                    onModifyFile(iterator);
                });
            }
            return;
        }
        FileRealm file = dataBaseService.getFileByPath(path);
        if (file == null || file.isProcessing()) {
            if (handler != null) {
                handler.post(() -> {
                    updateLocalProcessingCount(-1);
                    onModifyFile(iterator);
                });
            }
            return;
        }
        String uuid = file.getUuid();
        File f = new File(path);
        long size = f.length();
        long mtime = f.lastModified();
        if (file.isOffline() || file.isDownloadActual() && !file.isDownload()) {
            String tempPath = fileTool.buildPathForRecentCopy();
            if (!fileTool.copy(path, tempPath, true) ||
                    FileTool.getFileSize(tempPath) != size) {
                fileTool.delete(tempPath);
                if (handler != null) {
                    handler.post(() -> {
                        updateLocalProcessingCount(-1);
                        onModifyFile(iterator);
                    });
                }
                return;
            }

            String hashsum = PatchTool.getFileHash(tempPath);
            if (Objects.equals(hashsum, file.getHashsum())) {
                fileTool.delete(tempPath);
                dataBaseService.setFileMTime(uuid, mtime);
                if (handler != null) {
                    handler.post(() -> {
                        updateLocalProcessingCount(-1);
                        onModifyFile(iterator);
                    });
                }
                return;
            }
            String copyPath = FileTool.buildPathForCopyNamedHash(hashsum);
            if (!fileTool.move(tempPath, copyPath, false) ||
                    FileTool.getFileSize(copyPath) != size) {
                fileTool.deleteFile(copyPath);
                fileTool.delete(tempPath);
                if (handler != null) {
                    handler.post(() -> onModifyFile(iterator));
                }
                return;
            }
            String oldHashsum = file.getHashsum();
            String eventUuid = md5(UUID.randomUUID().toString());
            EventRealm event = new EventRealm();
            event.setUuid(eventUuid);
            event.setSize(size);
            event.setHashsum(hashsum);
            event.setFileUuid(uuid);
            dataBaseService.addEvent(event);
            httpClient.updateFile(
                    eventUuid, uuid, size, hashsum, file.getEventId(),
                    response -> {
                        if (handler != null) {
                            handler.post(() -> onFileUpdated(
                                    response, uuid, eventUuid, mtime, oldHashsum, iterator));
                        }
                    },
                    error -> {
                        if (handler != null) {
                            handler.post(
                                    () -> {
                                        dataBaseService.deleteEventByUuid(eventUuid);
                                        dataBaseService.setProcessing(false, uuid);
                                        updateLocalProcessingCount(-1);
                                        onModifyFile(iterator);
                                    });
                        }
                    });
        } else {
            if (file.isDownload()) {
                String tempPath = fileTool.buildPathForRecentCopy();
                if (fileTool.copy(path, tempPath, true) ||
                        FileTool.getFileSize(tempPath) != size) {
                    fileTool.delete(tempPath);
                    if (handler != null) {
                        handler.post(() -> onModifyFile(iterator));
                    }
                    return;
                }

                String hashsum = PatchTool.getFileHash(tempPath);
                String copyPath = FileTool.buildPathForCopyNamedHash(hashsum);
                if (fileTool.move(tempPath, copyPath, false) ||
                        FileTool.getFileSize(copyPath) != size) {
                    fileTool.deleteFile(copyPath);
                    fileTool.delete(tempPath);
                    if (handler != null) {
                        handler.post(() -> onModifyFile(iterator));
                    }
                    return;
                }
            }
            dataBaseService.setFileMTime(uuid, mtime);
            if (handler != null) {
                handler.post(() -> onModifyFile(iterator));
            }
        }
    }

    private void onFileUpdated(
            @NonNull JSONObject response, String uuid, String eventUuid,
            long mtime, String oldHashsum, @NonNull Iterator<String> iterator) {
        JSONObject data = response.optJSONObject("data");
        if (data == null) {
            dataBaseService.deleteEventByUuid(eventUuid);
            dataBaseService.setProcessing(false, uuid);
            if (handler != null) {
                handler.post(() -> {
                    updateLocalProcessingCount(-1);
                    onModifyFile(iterator);
                });
            }
            return;
        }
        EventRealm event = new EventRealm();
        event.setId(data.optLong("event_id"));
        event.setUuid(JSON.optString(data, "event_uuid"));
        String newHashsum = JSON.optString(data, "file_hash", null);
        event.setHashsum(newHashsum);
        long size = data.optLong("file_size_after_event", 0);
        event.setSize(size);
        event.setFileUuid(uuid);
        long date = (long) (data.optDouble("timestamp") * 1000.0);
        String patchUuid = JSON.optString(data, "diff_file_uuid");
        String revPatchUuid = JSON.optString(data, "rev_diff_file_uuid");

        event.setDiffFileUuid(patchUuid);
        event.setRevDiffFileUuid(revPatchUuid);

        dataBaseService.updateFileWithEvent(
                uuid, event, date, null, null, newHashsum);
        dataBaseService.setFileMTime(uuid, mtime);
        createPatch(newHashsum, oldHashsum, revPatchUuid);
        createPatch(oldHashsum, newHashsum, patchUuid);
        if (handler != null) {
            handler.post(() -> updateLocalProcessingCount(-1));
            handler.post(() -> onModifyFile(iterator));
        }
    }

    private void createPatch(String oldHashsum, String newHashsum, String patchUuid) {
        String patchFile = FileTool.buildPatchPath(patchUuid);
        if (patchTool.createPatchFile(
                FileTool.buildPathForCopyNamedHash(newHashsum), patchFile,
                newHashsum, oldHashsum)) {
            long patchSize = FileTool.getFileSize(patchFile);
            dataBaseService.setPatchSize(patchUuid, patchSize);
            httpClient.patchReady(patchUuid, patchSize);
        }
    }

    private void onDeleteFile(@NonNull Iterator<String> iterator) {
        if (!iterator.hasNext()) {
            return;
        }
        String path = iterator.next();
        if (FileTool.isExist(path)) {
            if (handler != null) {
                handler.post(() -> {
                    updateLocalProcessingCount(-1);
                    onDeleteFile(iterator);
                });
            }
            return;
        }
        FileRealm file = dataBaseService.getFileByPath(path);
        if (file == null || file.isProcessing() || file.isDownload() || FileTool.isExist(path)) {
            if (handler != null) {
                handler.post(() -> {
                    updateLocalProcessingCount(-1);
                    onDeleteFile(iterator);
                });
            }
            return;
        }

        Log.i(TAG, String.format("onDeleteFile: %s", path));

        String uuid = file.getUuid();
        if (file.isOffline()) {
            String eventUuid = md5(UUID.randomUUID().toString());
            dataBaseService.setProcessing(true, uuid);
            httpClient.deleteFile(
                    eventUuid, uuid, file.getEventId(),
                    response -> {
                        if (handler != null) {
                            handler.post(() -> {
                                dataBaseService.deleteFileByUuid(uuid);
                                updateLocalProcessingCount(-1);
                                onDeleteFile(iterator);
                            });
                        }
                    },
                    error -> {
                        if (handler != null) {
                            handler.post(() -> {
                                dataBaseService.setProcessing(false, uuid);
                                updateLocalProcessingCount(-1);
                                onDeleteFile(iterator);
                            });
                        }
                    });
        } else {
            dataBaseService.onFileDeleted(uuid);
            if (handler != null) {
                handler.post(() -> {
                    updateLocalProcessingCount(-1);
                    onDeleteFile(iterator);
                });
            }
        }
    }

    private void updateLocalProcessingCount(int i) {
        localProcessingCount += i;
        if (localProcessingCount == 0) {
            updateFileStructWork = null;
            if (havePendingFileStructUpdate) {
                havePendingFileStructUpdate = false;
                updateFileStructWork = this::updateFileStruct;
                if (handler != null) {
                    handler.post(updateFileStructWork);
                }
            }
        }
        dataBaseService.updateOwnDeviceLocalProcessingCount(localProcessingCount);
    }

    private void updateCamera() {
        if (importingCamera || !working || !preferenceService.getAutoCameraUpdate()) {
            updateCameraWork = null;
            return;
        }
        importingCamera = true;
        Log.d(TAG, "updateCamera");
        String cameraFolderUuid = preferenceService.getCameraFolderUuid();
        if (cameraFolderUuid == null) {
            createCameraFolder();
        } else {
            importCamera(cameraFolderUuid, false);
        }
    }

    private void createCameraFolder() {
        Log.d(TAG, "createCameraFolder");
        if (!preferenceService.getAutoCameraUpdate()) {
            Log.d(TAG, "createCameraFolder: import cancelled");
            String message = context.getString(R.string.cancel_import_camera);
            Intent intent = new Intent(Const.OPERATIONS_PROGRESS_INTENT);
            intent.putExtra(Const.OPERATIONS_PROGRESS_MESSAGE, message);
            intent.putExtra(Const.OPERATIONS_PROGRESS_SHOW_AND_DISMISS, true);
            LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
            importingCamera = false;
            updateCameraWork = null;
            return;
        }
        String message = context.getString(R.string.start_import_camera);
        Intent intent = new Intent(Const.OPERATIONS_PROGRESS_INTENT);
        intent.putExtra(Const.OPERATIONS_PROGRESS_MESSAGE, message);
        intent.putExtra(Const.OPERATIONS_PROGRESS_SHOW_AND_DISMISS, false);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);

        operationService.createFolder(
                null, Const.CAMERA_FOLDER_NAME, true,
                (response, uuid) -> {
                    if (cameraHandler != null) {
                        cameraHandler.post(() -> {
                            preferenceService.setCameraFolderUuid(uuid);
                            importCamera(uuid, true);
                        });
                    }
                },
                (error, uuid) -> {
                    if (cameraHandler != null) {
                        cameraHandler.postDelayed(this::createCameraFolder, 500);
                    }
                });
    }

    private void importCamera(String folder, boolean firstTime) {
        Log.i(TAG, "importCamera");
        List<File> existingFiles = fileTool.getTotalListFile(Const.CAMERA_PATH);
        HashMap<String, File> newPhotos = getPhotoList(existingFiles);
        HashMap<String, Long> knownPhotos = dataBaseService.getCameraFiles(folder);

        newPhotos.keySet().removeAll(knownPhotos.keySet());

        stopWatchingCamera();
        addCameraWatcher(Const.CAMERA_PATH);
        for (File existingFile : existingFiles) {
            if (existingFile.isDirectory() && !existingFile.isHidden()) {
                addCameraWatcher(existingFile.getPath());
            }
        }

        if (newPhotos.isEmpty()) {
            if (firstTime) {
                String message = context.getString(R.string.imported_camera);
                Intent intent = new Intent(Const.OPERATIONS_PROGRESS_INTENT);
                intent.putExtra(Const.OPERATIONS_PROGRESS_MESSAGE, message);
                intent.putExtra(Const.OPERATIONS_PROGRESS_SHOW_AND_DISMISS, true);
                LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
            }
            Log.d(TAG, "importCamera: 0 new photos, finished");
            dataBaseService.updateOwnDeviceImportingCamera(false);
            importingCamera = false;
            updateCameraWork = null;
            return;
        }
        long photosCount = newPhotos.size();
        long processedCount = 0;
        dataBaseService.updateOwnDeviceImportingCamera(true);
        addNewPhoto(newPhotos.entrySet().iterator(),
                folder, photosCount, processedCount);
    }

    private void addNewPhoto(
            @NonNull Iterator<Map.Entry<String, File>> iterator,
            String folder, long photosCount, long processedCount) {
        if (!working) return;
        if (!preferenceService.getAutoCameraUpdate()) {
            Log.d(TAG, "addNewPhoto: import cancelled");
            String message = context.getString(R.string.cancel_import_camera);
            Intent intent = new Intent(Const.OPERATIONS_PROGRESS_INTENT);
            intent.putExtra(Const.OPERATIONS_PROGRESS_MESSAGE, message);
            intent.putExtra(Const.OPERATIONS_PROGRESS_SHOW_AND_DISMISS, true);
            LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
            importingCamera = false;
            updateCameraWork = null;
            dataBaseService.updateOwnDeviceImportingCamera(false);
            return;
        }
        if (!iterator.hasNext()) {
            dataBaseService.updateOwnDeviceImportingCamera(false);
            importCamera(folder, true);
            Log.d(TAG, String.format("addNewPhoto, imported %d photos, finished", processedCount));
            return;
        }
        createNewPhoto(
                folder, iterator.next().getKey(), photosCount, processedCount + 1,
                iterator);
    }

    private void createNewPhoto(
            String folder, @NonNull String path, long photosCount, long processedCount,
            @NonNull Iterator<Map.Entry<String, File>> iterator) {
        if (!working) return;
        if (!preferenceService.getAutoCameraUpdate()) {
            Log.d(TAG, "createNewPhoto: import cancelled");
            String message = context.getString(R.string.cancel_import_camera);
            Intent intent = new Intent(Const.OPERATIONS_PROGRESS_INTENT);
            intent.putExtra(Const.OPERATIONS_PROGRESS_MESSAGE, message);
            intent.putExtra(Const.OPERATIONS_PROGRESS_SHOW_AND_DISMISS, true);
            LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
            dataBaseService.updateOwnDeviceImportingCamera(false);
            importingCamera = false;
            updateCameraWork = null;
            return;
        }
        Log.d(TAG, String.format("createNewPhoto %s, %d/%d", path, processedCount, photosCount));

        Intent intent = new Intent(Const.OPERATIONS_PROGRESS_INTENT);
        intent.putExtra(Const.OPERATIONS_PROGRESS_MESSAGE, String.format(
                context.getString(R.string.progress_import_camera),
                processedCount, photosCount));
        intent.putExtra(Const.OPERATIONS_PROGRESS_SHOW_AND_DISMISS, false);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);

        File f = new File(path);
        long mtime = f.lastModified();
        long size = f.length();
        long delay = size > 0 ? 100 : 1000;
        Log.i(TAG, String.format("createNewPhoto: %s, size: %d, mtime: %d", path, size, mtime));
        if (cameraHandler != null) {
            cameraHandler.postDelayed(() -> registerPhoto(
                    folder, path, mtime, size, iterator, photosCount, processedCount),
                    delay);
        }
    }

    private void registerPhoto(
            String folder, @NonNull String path, long mtime, long size,
            @NonNull Iterator<Map.Entry<String, File>> iterator, long photosCount, long processedCount) {
        File f = new File(path);
        long newMtime = f.lastModified();
        long newSize = f.length();
        Log.d(TAG, String.format("registerPhoto: %s, size: %d, mtime: %d", path, newSize, newMtime));
        if (newMtime == 0) {
            if (cameraHandler != null) {
                cameraHandler.post(() -> addNewPhoto(
                        iterator, folder, photosCount, processedCount));
            }
            return;
        }
        if (mtime != newMtime || size != newSize) {
            Log.d(TAG, "registerPhoto: mtime or size differs, delay reg");
            if (cameraHandler != null) {
                cameraHandler.postDelayed(() -> registerPhoto(
                        folder, path, newMtime, newSize, iterator, photosCount, processedCount),
                        1000);
            }
            return;
        }
        operationService.createFile(
                folder, null, path, null, true, false,
                (response, uuid) -> {
                    if (cameraHandler != null) {
                        cameraHandler.post(() -> addNewPhoto(
                                iterator, folder, photosCount, processedCount));
                    }
                },
                (error, uuid) -> {
                    if (cameraHandler != null) {
                        cameraHandler.post(() -> createNewPhoto(
                                folder, path, photosCount, processedCount, iterator));
                    }
                }
        );

    }

    @NonNull
    private HashMap<String, File> getPhotoList(@NonNull List<File> totalListFile) {
        Log.d(TAG, "getPhotoList");
        HashMap<String, File> photos = new HashMap<>();
        for (File file : totalListFile) {
            if (!file.isHidden() && FileTool.isImageOrVideoFile(file.getPath())) {
                photos.put(file.getAbsolutePath(), file);
            }
        }
        return photos;
    }
}
