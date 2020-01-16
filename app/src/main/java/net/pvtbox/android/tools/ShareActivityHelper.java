package net.pvtbox.android.tools;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
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
public class ShareActivityHelper {
    static public void handleShareIntent(Intent intent, Activity activity) {
        String action = intent.getAction();
        if (Intent.ACTION_SEND.equals(action)) {
            Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (uri != null) {
                intent.putExtra(Intent.EXTRA_STREAM, processUri(
                        uri, intent, activity));
            }
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            ArrayList<Uri> uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (uris != null) {
                ArrayList<Uri> result = new ArrayList<>();
                for (Uri uri : uris) {
                    result.add(processUri(uri, intent, activity));
                }
                intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, result);
            }
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static Uri processUri(Uri uri, Intent intent, Activity activity) {
        if ("file".equals(uri.getScheme())) {
            return uri;
        }
        boolean isVirtual = isUriVirtual(uri, activity);
        String name = getFileNameFromUri(uri, activity);
        if (name == null || name.isEmpty()) {
            name = "imported_file";
        }
        if (isVirtual) {
            name += ".pdf";
        }
        File tempFile;
        try {
            tempFile = File.createTempFile(name, ".tmp");
        } catch (IOException e) {
            return uri;
        }
        try (InputStream istream = openUriInputStream(uri, isVirtual, activity)) {
            if (istream == null) {
                tempFile.delete();
                return uri;
            }
            FileUtils.copyInputStreamToFile(istream, tempFile);
        } catch (Exception e) {
            tempFile.delete();
            return uri;
        }
        return Uri.fromParts("share", tempFile.getAbsolutePath(), name);
    }

    public static InputStream openUriInputStream(
            @NonNull Uri uri, boolean isVirtual, Activity activity) throws IOException {
        if (isVirtual) {
            return getInputStreamForVirtualUri(uri, activity);
        } else {
            return activity.getContentResolver().openInputStream(uri);
        }
    }

    @Nullable
    public static String getFileNameFromUri(@NonNull Uri uri, Activity activity) {
        String name = null;
        try (Cursor cursor = activity.getContentResolver()
                .query(uri, null,
                        null, null, null)) {
            Objects.requireNonNull(cursor).moveToFirst();
            for (int i = 0; i < cursor.getColumnCount(); ++i) {
                String column = cursor.getColumnName(i);
                if ("file_name".equals(column) || OpenableColumns.DISPLAY_NAME.equals(column)) {
                    name = cursor.getString(i);
                    if (name != null && !name.isEmpty()) {
                        break;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return name;
    }

    public static boolean isUriVirtual(@NonNull Uri uri, Activity activity) {
        boolean isVirtual = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (!DocumentsContract.isDocumentUri(activity, uri)) {
                return false;
            }
            try (Cursor cursor = activity.getContentResolver()
                    .query(uri,
                            new String[]{DocumentsContract.Document.COLUMN_FLAGS},
                            null, null, null)) {
                Objects.requireNonNull(cursor).moveToFirst();
                int flags = cursor.getInt(0);
                isVirtual = (flags & DocumentsContract.Document.FLAG_VIRTUAL_DOCUMENT) != 0;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return isVirtual;
    }
    private static InputStream getInputStreamForVirtualUri(
            Uri uri, Activity activity) throws IOException {
        ContentResolver resolver = activity.getContentResolver();
        String[] openableMimeTypes = resolver.getStreamTypes(uri, "*/*");
        if (openableMimeTypes == null ||
                openableMimeTypes.length < 1) {
            throw new FileNotFoundException();
        }

        AssetFileDescriptor descriptor = resolver
                .openTypedAssetFileDescriptor(uri, openableMimeTypes[0], null);
        if (descriptor == null) {
            throw new FileNotFoundException();
        }

        return descriptor
                .createInputStream();
    }
}
