package net.pvtbox.android.db.model;

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
@SuppressWarnings({"unused", "FieldCanBeLocal"})
public class EventRealm extends RealmObject{
    @PrimaryKey
    private String uuid;
    private boolean checked;
    @Index
    private long id;
    @Index
    private String fileUuid;

    private String diffFileUuid;
    private long diffFileSize;
    private String revDiffFileUuid;
    private long revDiffFileSize;

    private String hashsum;
    private long size;

    private String cameraPath;

    private long timestamp;


    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public void setChecked(boolean checked) {
        this.checked = checked;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getFileUuid() {
        return fileUuid;
    }

    public void setFileUuid(String fileUuid) {
        this.fileUuid = fileUuid;
    }

    public String getDiffFileUuid() {
        return diffFileUuid;
    }

    public void setDiffFileUuid(String diffFileUuid) {
        this.diffFileUuid = diffFileUuid;
    }

    public long getDiffFileSize() {
        return diffFileSize;
    }

    public void setDiffFileSize(long diffFileSize) {
        this.diffFileSize = diffFileSize;
    }

    public String getHashsum() {
        return hashsum;
    }

    public void setHashsum(String hashsum) {
        this.hashsum = hashsum;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getCameraPath() {
        return cameraPath;
    }

    public void setCameraPath(String cameraPath) {
        this.cameraPath = cameraPath;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getRevDiffFileUuid() {
        return revDiffFileUuid;
    }

    public void setRevDiffFileUuid(String revDiffFileUuid) {
        this.revDiffFileUuid = revDiffFileUuid;
    }

    public void setRevDiffFileSize(long revDiffFileSize) {
        this.revDiffFileSize = revDiffFileSize;
    }
}
