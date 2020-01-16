package net.pvtbox.android.tools.diskspace;

import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.os.StatFs;

import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import net.pvtbox.android.R;
import net.pvtbox.android.application.Const;

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
public class DiskSpaceTool {


    public static boolean checkDiskSpace(long size, @NonNull Context context){
        try {
            long freeSpace = getFreeDiskSpace();
            if (freeSpace > size * 2) {
                return true;
            } else {
                sendErrorSpace(context);
                return false;
            }
        } catch (NullPointerException e) {
            return true;
        }

    }

    private static void sendErrorSpace(@NonNull Context context) {
        Intent intent = new Intent(Const.DISK_SPACE_ERROR_INTENT);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    private static long getFreeDiskSpace() {
        StatFs stat = new StatFs(Environment.getExternalStorageDirectory().getPath());
        return stat.getAvailableBytes();
    }

    @NonNull
    public static DiskSpace getDiskQuantity(long diskUserInSpace) {
        float diskUserInSpaceInValue = diskUserInSpace;
        if (diskUserInSpaceInValue < 1024L) {
            return new DiskSpace(diskUserInSpaceInValue, R.string.b);
        }
        for (int i = 0; i <= 4; i++) {
            diskUserInSpaceInValue = diskUserInSpaceInValue / 1024L;

            if (diskUserInSpaceInValue < 1024L) {
                switch (i) {
                    case 0:
                        return new DiskSpace(diskUserInSpaceInValue, R.string.kb);
                    case 1:
                        return new DiskSpace(diskUserInSpaceInValue, R.string.mb);
                    case 2:
                        return new DiskSpace(diskUserInSpaceInValue, R.string.gb);
                    case 3:
                        return new DiskSpace(diskUserInSpaceInValue, R.string.tb);
                }
            }
        }
        return new DiskSpace(0, R.string.kb_in_s);
    }

}
