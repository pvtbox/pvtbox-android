package net.pvtbox.android.tools;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.util.Log;

import net.pvtbox.android.application.Const;
import net.pvtbox.patchlib.Patch;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.TreeMap;


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
public class PatchTool {
    @NonNull
    private final String TAG = PatchTool.class.getSimpleName();

    private final FileTool fileTool;

    public PatchTool(FileTool fileTool) {
        this.fileTool = fileTool;
    }

    public boolean createPatchFile(@NonNull String filePath, String patchFilePath, String hash, @Nullable String oldHash) {
        if (oldHash == null) {
            Log.e(TAG, "old hash null:" + filePath);
        }
        try {
            TreeMap<Long, String> oldBlockHash = oldHash == null ? null : readSignature(oldHash);
            if (oldHash != null && oldBlockHash == null) {
                if (!fileTool.isExistCopy(oldHash)) {
                    return false;
                }
                oldBlockHash = Patch.blocksHashes(
                        FileTool.buildPathForCopyNamedHash(oldHash), Patch.defaultBlockSize);
                saveSignature(oldBlockHash, oldHash);
            }
            TreeMap<Long, String> blockHash = readSignature(hash);
            if (blockHash == null) {
                if (!fileTool.isExistCopy(hash)) {
                    return false;
                }
                blockHash = Patch.blocksHashes(
                        FileTool.buildPathForCopyNamedHash(hash), Patch.defaultBlockSize);
                saveSignature(blockHash, hash);
            }
            Patch.createPatch(
                    filePath, patchFilePath, hash, blockHash, oldHash, oldBlockHash,
                    Patch.defaultBlockSize);
        } catch (@NonNull IOException | NoSuchAlgorithmException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    private void saveSignature(@Nullable Map<Long, String> blockHash, String hash) throws IOException {
        if (blockHash == null) {
            return;
        }

        String hashBlockFilePath = getNameHashBlockFile(hash);
        if (FileTool.isExist(hashBlockFilePath)) {
            return;
        }

        Properties properties = new Properties();
        for (Map.Entry<Long, String> entry : blockHash.entrySet()) {
            properties.put(Long.toString(entry.getKey()), entry.getValue());
        }

        Log.d(TAG, "save block hash:" + hash);
        FileOutputStream out = new FileOutputStream(hashBlockFilePath);
        properties.store(out, null);
    }

    @NonNull
    private static String getNameHashBlockFile(String hash) {
        return Const.PATCHES_PATH + File.separator + hash + ".hb";
    }

    @Nullable
    private TreeMap<Long, String> readSignature(String hash) {
        TreeMap<Long, String> blockHash = new TreeMap<>();
        Properties properties = new Properties();
        try {
            properties.load(new FileInputStream(getNameHashBlockFile(hash)));
            for (String key : properties.stringPropertyNames()) {
                blockHash.put(Long.parseLong(key), Objects.requireNonNull(properties.get(key)).toString());
            }
            return blockHash;
        } catch (IOException e) {
            //   e.printStackTrace();
        }
        Log.e(TAG, "block hash file not found " + getNameHashBlockFile(hash));
        return null;

    }

    @Nullable
    public static String getFileHash(@Nullable String filePath) {
        if (filePath == null) {
            return null;
        }
        File file = new File(filePath);
        boolean isDirectory = file.isDirectory();
        boolean isExist = file.exists();
        if (isDirectory || !isExist) {
            return null;
        }
        try {
            TreeMap<Long, String> blockHash;
            if (new File(filePath).exists()) {
                blockHash = Patch.blocksHashes(filePath, Patch.defaultBlockSize);
                return Patch.hashFromBlocksHashes(blockHash);
            } else {
                return null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


    public void acceptPath(@NonNull String oldFilePath, @NonNull String path, String pathFilePatch, String hash)
            throws IOException {
        Log.d(TAG, "path:" + path + "hash:" + hash);
        String tempPath = path + ".temp";
        Patch.acceptPatch(oldFilePath, tempPath, pathFilePatch, hash);
        if (FileTool.isExist(tempPath)) {
            fileTool.move(tempPath, path, true);
        }
    }
}
