package net.pvtbox.android.db.model;

import androidx.annotation.Nullable;

import io.realm.RealmObject;
import io.realm.annotations.Index;
import io.realm.annotations.PrimaryKey;

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
public class DeviceRealm extends RealmObject{
    @PrimaryKey
    private String id;
    private String name;
    private String deviceType;
    private boolean online = false;
    private String os;
    private String osType;
    private long diskUsage = 0;
    private double uploadSpeed = 0.0;
    private double downloadSpeed = 0.0;
    private long uploadedSize = 0;
    private long downloadedSize = 0;
    private int connectedNodesCount = 0;
    private int status = 0;
    @Index
    private boolean own = false;

    private long remotesCount = 0;
    private boolean isFetchingChanges = true;
    private boolean isProcessingOperation = false;
    private boolean isDownloadingShare = false;
    private boolean isImportingCamera = false;
    private boolean isInitialSyncing = true;
    private long processingLocalCount = 0;
    private long downloadsCount = 0;
    @Nullable
    private String currentDownloadName = null;
    private long notificationsCount = 0;

    private boolean paused = false;

    private boolean isLogoutInProgress = false;
    private boolean isWipeInProgress = false;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(String deviceType) {
        this.deviceType = deviceType;
    }

    public boolean isOnline() {
        return online;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }

    public String getOs() {
        return os;
    }

    public void setOs(String os) {
        this.os = os;
    }

    public String getOsType() {
        return osType;
    }

    public void setOsType(String osType) {
        this.osType = osType;
    }

    public long getDiskUsage() {
        return diskUsage;
    }

    public void setDiskUsage(long diskUsage) {
        this.diskUsage = diskUsage;
    }

    public double getUploadSpeed() {
        return uploadSpeed;
    }

    public void setUploadSpeed(double uploadSpeed) {
        this.uploadSpeed = uploadSpeed;
    }

    public double getDownloadSpeed() {
        return downloadSpeed;
    }

    public void setDownloadSpeed(double downloadSpeed) {
        this.downloadSpeed = downloadSpeed;
    }

    public long getUploadedSize() { return uploadedSize; }

    public void setUploadedSize(long value) { this.uploadedSize = value; }

    public long getDownloadedSize() { return downloadedSize; }

    public void setDownloadedSize(long value) { this.downloadedSize = value; }

    public int getConnectedNodesCount() { return connectedNodesCount; }

    public void setConnectedNodesCount(int value) { this.connectedNodesCount = value; }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public boolean isOwn() {
        return own;
    }

    public void setOwn(boolean own) {
        this.own = own;
    }

    public boolean isFetchingChanges() {
        return isFetchingChanges;
    }

    public void setFetchingChanges(boolean fetchingChanges) {
        isFetchingChanges = fetchingChanges;
    }

    public boolean isProcessingOperation() {
        return isProcessingOperation;
    }

    public void setProcessingOperation(boolean processingOperation) {
        isProcessingOperation = processingOperation;
    }

    public boolean isDownloadingShare() { return isDownloadingShare; }

    public void setDownloadingShare(boolean value) {
        isDownloadingShare = value;
    }

    public boolean isImportingCamera() {
        return isImportingCamera;
    }

    public void setImportingCamera(boolean importingCamera) {
        isImportingCamera = importingCamera;
    }

    public long getProcessingLocalCount() {
        return processingLocalCount;
    }

    public void setProcessingLocalCount(long processingLocalCount) {
        this.processingLocalCount = processingLocalCount;
    }

    public long getDownloadsCount() {
        return downloadsCount;
    }

    public void setDownloadsCount(long downloadsCount) {
        this.downloadsCount = downloadsCount;
    }

    @Nullable
    public String getCurrentDownloadName() {
        return currentDownloadName;
    }

    public void setCurrentDownloadName(@Nullable String currentDownloadName) {
        this.currentDownloadName = currentDownloadName;
    }

    public boolean isInitialSyncing() {
        return isInitialSyncing;
    }

    public void setInitialSyncing(boolean initialSyncing) {
        isInitialSyncing = initialSyncing;
    }

    public long getRemotesCount() {
        return remotesCount;
    }

    public void setRemotesCount(long remotesCount) {
        this.remotesCount = remotesCount;
    }

    public long getNotificationsCount() {
        return notificationsCount;
    }

    public void setNotificationsCount(long notificationsCount) {
        this.notificationsCount = notificationsCount;
    }

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    public boolean isLogoutInProgress() {
        return isLogoutInProgress;
    }

    public void setLogoutInProgress(boolean logoutInProgress) {
        isLogoutInProgress = logoutInProgress;
    }

    public boolean isWipeInProgress() {
        return isWipeInProgress;
    }

    public void setWipeInProgress(boolean wipeInProgress) {
        isWipeInProgress = wipeInProgress;
    }
}
