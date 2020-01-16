package net.pvtbox.android.ui.files;


import androidx.annotation.NonNull;

import net.pvtbox.android.R;
import net.pvtbox.android.db.model.FileRealm;

import java.net.URLConnection;

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
public class IconTool {

    public static int getIcon(@NonNull FileRealm fileItem) {
        int icon = R.drawable.all_files_menu_icon;
        switch (fileItem.getType()) {
            case "dir":
                if (fileItem.isCollaborated()) {
                    icon = R.drawable.folder_shared;
                } else {
                    icon = R.drawable.folder;
                }
                break;
            case "doc":
            case "docx":
                icon = R.drawable.word;
                break;
            case "xls":
            case "xlt":
            case "xlm":
                icon = R.drawable.excel;
                break;
            case "zip":
            case "xz":
            case "7z":
            case "gzip":
            case "gz":
            case "tar":
            case "bz":
            case "bzip":
            case "rar":
                icon = R.drawable.archive;
                break;
            default:
                String mimeType = URLConnection.guessContentTypeFromName(fileItem.getPath());
                if (mimeType == null || mimeType.isEmpty()) break;
                if (mimeType.startsWith("video")) {
                    icon = R.drawable.video;
                } else if (mimeType.startsWith("audio")) {
                    icon = R.drawable.music;
                } else if (mimeType.startsWith("image")) {
                    icon = R.drawable.photo;
                }
                break;
        }

        return icon;
    }
}
