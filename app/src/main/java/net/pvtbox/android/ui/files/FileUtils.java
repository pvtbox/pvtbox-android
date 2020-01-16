package net.pvtbox.android.ui.files;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.pvtbox.android.R;
import net.pvtbox.android.db.model.FileRealm;
import net.pvtbox.android.tools.diskspace.DiskSpace;
import net.pvtbox.android.tools.diskspace.DiskSpaceTool;

import java.util.Calendar;
import java.util.Date;

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
class FileUtils {

    private static final int MIN = 60;
    private static final int HOUR = 60 * MIN;
    private static final int DAY = 24 * HOUR;
    private static final int YEAR = 365 * DAY;
    private static final int MONTH = 30 * DAY;

    public static String getSizeAndDateString(@NonNull FileRealm file, @NonNull Context context) {
        String sizeAndDateString;
        if (file.isFolder()) {
            sizeAndDateString = buildFolderSizeAndTimeString(
                    context, file.getDownloadedSize(), file.getSize(),
                    file.getFilesCount(), file.getDateCreated());
        } else {
            long addedTime = file.getDateCreated();
            long modifiedTime = file.getDateModified();
            boolean isModify = addedTime != modifiedTime;
            sizeAndDateString = buildFileSizeAndTimeString(
                    context, file.getDownloadedSize(), file.getSize(),
                    isModify ? modifiedTime : addedTime, isModify);
        }
        return sizeAndDateString;
    }

    @NonNull
    private static String buildFileSizeAndTimeString(
            @NonNull Context context, long downloadedSize, long size, long time, boolean isModify) {
        String dateText = buildDataCaption(context, new Date(time), isModify);
        DiskSpace downloadedSizeSpace = DiskSpaceTool.getDiskQuantity(downloadedSize);
        DiskSpace sizeSpace = DiskSpaceTool.getDiskQuantity(size);
        return context.getString(R.string.file_date_and_size,
                dateText,
                context.getString(R.string.file_size,
                        downloadedSizeSpace.getFormatStringValue(),
                        context.getString(downloadedSizeSpace.getIdQuantity()),
                        sizeSpace.getFormatStringValue(),
                        context.getString(sizeSpace.getIdQuantity())
                ));
    }

    @NonNull
    private static String buildFolderSizeAndTimeString(
            @NonNull Context context, long downloaded, long size, long files, long time) {
        String dateText = buildDataCaption(context, new Date(time), false);
        DiskSpace diskSpace = DiskSpaceTool.getDiskQuantity(size);
        DiskSpace downloadedSizeSpace = DiskSpaceTool.getDiskQuantity(downloaded);
        return context.getString(R.string.folder_date_and_size,
                dateText,
                context.getString(R.string.file_size,
                        downloadedSizeSpace.getFormatStringValue(),
                        context.getString(downloadedSizeSpace.getIdQuantity()),
                        diskSpace.getFormatStringValue(),
                        context.getString(diskSpace.getIdQuantity())
                ),
                files);
    }

    @NonNull
    private static String buildDataCaption(@NonNull Context context, @Nullable Date lastChanges, boolean isModify) {
        if (lastChanges == null) {
            return "empty date";
        }
        Calendar now = Calendar.getInstance();
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(lastChanges);

        long diff = (now.getTimeInMillis() - calendar.getTimeInMillis()) / 1000;

        if (diff > YEAR) {
            return String.format(
                    context.getString(R.string.date_format),
                    isModify ? context.getString(R.string.Modified) : context.getString(R.string.Added),
                    diff / YEAR,
                    context.getString(diff / YEAR > 1 ? R.string.years : R.string.year),
                    context.getString(R.string.ago));
        }
        if (diff > MONTH) {
            return String.format(
                    context.getString(R.string.date_format),
                    isModify ? context.getString(R.string.Modified) : context.getString(R.string.Added),
                    diff / MONTH,
                    context.getString(diff / MONTH > 1 ? R.string.months : R.string.month),
                    context.getString(R.string.ago));
        }
        if (diff > DAY) {
            return String.format(
                    context.getString(R.string.date_format),
                    isModify ? context.getString(R.string.Modified) : context.getString(R.string.Added),
                    diff / DAY,
                    context.getString(diff / DAY > 1 ? R.string.days : R.string.day),
                    context.getString(R.string.ago));
        }
        if (diff > HOUR) {
            return String.format(
                    context.getString(R.string.date_format),
                    isModify ? context.getString(R.string.Modified) : context.getString(R.string.Added),
                    diff / HOUR,
                    context.getString(diff / HOUR > 1 ? R.string.hours : R.string.hour),
                    context.getString(R.string.ago));
        }
        if (diff > MIN) {
            return String.format(
                    context.getString(R.string.date_format),
                    isModify ? context.getString(R.string.Modified) : context.getString(R.string.Added),
                    diff / MIN,
                    context.getString(diff / MIN > 1 ? R.string.minutes : R.string.minute),
                    context.getString(R.string.ago));
        }
        return isModify ? context.getString(R.string.recently_modified) : context.getString(R.string.recently_added);
    }
}
