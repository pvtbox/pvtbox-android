package net.pvtbox.android.db.model;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.pvtbox.android.R;

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
public class FileRealm extends RealmObject {
    @PrimaryKey
    private String uuid;
    @Nullable
    @Index
    private String parentUuid = null;
    @Nullable
    private String eventUuid = null;
    private long eventId = 0;

    private String path;
    private String name;
    @Nullable
    @Index
    private String hashsum = null;

    private long dateCreated = 0;
    private long dateModified = 0;

    private long size = 0;
    private long downloadedSize = 0;

    private long filesCount = 0;
    private long offlineFilesCount = 0;

    private boolean isDownload = false;
    private boolean isOnlyDownload = true;
    private boolean isOffline = false;
    private boolean isDownloadActual = false;

    private boolean isProcessing = false;

    private int downloadStatus = R.string.processing_download;

    private boolean isFolder = false;
    private boolean isCollaborated = false;
    private boolean isShared = false;

    @Nullable
    private String shareLink = null;
    private boolean shareSecured = false;
    private int shareExpire = 0;

    @Nullable
    private String cameraPath = null;

    private long mtime = -1;

    @Nullable
    private String downloadPath = null;

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    @Nullable
    public String getParentUuid() {
        return parentUuid;
    }

    public void setParentUuid(@Nullable String parentUuid) {
        this.parentUuid = parentUuid;
    }

    @Nullable
    public String getEventUuid() {
        return eventUuid;
    }

    public void setEventUuid(@Nullable String eventUuid) {
        this.eventUuid = eventUuid;
    }

    public long getEventId() {
        return eventId;
    }

    public void setEventId(long eventId) {
        this.eventId = eventId;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Nullable
    public String getHashsum() {
        return hashsum;
    }

    public void setHashsum(@Nullable String hashsum) {
        this.hashsum = hashsum;
    }

    public long getDateCreated() {
        return dateCreated;
    }

    public void setDateCreated(long dateCreated) {
        this.dateCreated = dateCreated;
    }

    public long getDateModified() {
        return dateModified;
    }

    public void setDateModified(long dateModified) {
        this.dateModified = dateModified;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
        if (size == 0) {
            Log.d("", "setSize: 0");
        }
    }

    public long getDownloadedSize() {
        return downloadedSize;
    }

    public void setDownloadedSize(long downloadedSize) {
        this.downloadedSize = downloadedSize;
    }

    public long getFilesCount() {
        return filesCount;
    }

    public void setFilesCount(long filesCount) {
        this.filesCount = filesCount;
    }

    public long getOfflineFilesCount() {
        return offlineFilesCount;
    }

    public void setOfflineFilesCount(long offlineFilesCount) {
        this.offlineFilesCount = offlineFilesCount;
    }

    public boolean isDownload() {
        return isDownload;
    }

    public void setDownload(boolean download) {
        isDownload = download;
    }

    public boolean isOnlyDownload() {
        return isOnlyDownload;
    }

    public void setOnlyDownload(boolean onlyDownload) {
        isOnlyDownload = onlyDownload;
    }

    public boolean isOffline() {
        return isOffline;
    }

    public void setOffline(boolean offline) {
        isOffline = offline;
    }

    public boolean isDownloadActual() {
        return isDownloadActual;
    }

    public void setDownloadActual(boolean downloadActual) {
        isDownloadActual = downloadActual;
    }

    public boolean isProcessing() {
        return isProcessing;
    }

    public void setProcessing(boolean processing) {
        isProcessing = processing;
    }

    public int getDownloadStatus() {
        return downloadStatus;
    }

    public void setDownloadStatus(int downloadStatus) {
        this.downloadStatus = downloadStatus;
    }

    public boolean isFolder() {
        return isFolder;
    }

    public void setFolder(boolean folder) {
        isFolder = folder;
    }

    public boolean isCollaborated() {
        return isCollaborated;
    }

    public void setCollaborated(boolean collaborated) {
        isCollaborated = collaborated;
    }

    public boolean isShared() {
        return isShared;
    }

    public void setShared(boolean shared) {
        isShared = shared;
    }

    @Nullable
    public String getShareLink() {
        return shareLink;
    }

    public void setShareLink(@Nullable String shareLink) {
        this.shareLink = shareLink;
    }

    public boolean isShareSecured() {
        return shareSecured;
    }

    public void setShareSecured(boolean shareSecured) {
        this.shareSecured = shareSecured;
    }

    public int getShareExpire() {
        return shareExpire;
    }

    public void setShareExpire(int shareExpire) {
        this.shareExpire = shareExpire;
    }

    @Nullable
    public String getCameraPath() {
        return cameraPath;
    }

    public void setCameraPath(@Nullable String cameraPath) {
        this.cameraPath = cameraPath;
    }

    public long getMtime() {
        return mtime;
    }

    public void setMtime(long mtime) {
        this.mtime = mtime;
    }

    @Nullable
    public String getDownloadPath() {
        return downloadPath;
    }

    public void setDownloadPath(@Nullable String downloadPath) {
        this.downloadPath = downloadPath;
    }


    @NonNull
    public String getType() {
        if (isFolder()) {
            return "dir";
        }
        String[] split = getName().split("\\.");
        String fileType = split.length == 0 ? "txt" : split[split.length - 1];
        return fileType.toLowerCase();
    }
}
