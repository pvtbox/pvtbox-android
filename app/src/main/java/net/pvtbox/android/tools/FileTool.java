package net.pvtbox.android.tools;

import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import net.pvtbox.android.application.Const;
import net.pvtbox.android.db.model.FileRealm;

import org.apache.commons.io.FileExistsException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URLConnection;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

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
public class FileTool {
    private final Context context;
    private final static String TAG = FileTool.class.getSimpleName();

    public FileTool(Context context) {
        this.context = context;
    }

    @NonNull
    public List<File> getFileInDirectory(@NonNull String path) {
        File directory = new File(path);
        File[] files = directory.listFiles();
        if (files == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Arrays.asList(files));
    }

    public void createDirectory(@NonNull String path) {
        File folder = new File(path);
        try {
            createParentIfNotExist(folder.getParentFile());
            boolean success = true;
            if (!folder.exists()) {
                Log.d(TAG, "dir not exist");
                success = folder.mkdir();
            }
            Log.d(TAG, "create file:" + success + "path:" + path);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void createParentIfNotExist(@Nullable File file) throws Exception {
        if (file == null || file.exists()) {
            return;
        } else {
            createParentIfNotExist(file.getParentFile());
        }
        if (!file.mkdir()) {
            throw new Exception("cant create folder");
        }
    }

    public boolean move(@NonNull String oldPath, @NonNull String newPath, boolean deleteExisting) {
        File oldFile = new File(oldPath);
        if (!oldFile.exists()) return false;
        if (oldFile.isDirectory()) {
            return moveDirectory(oldPath, newPath);
        } else {
            return moveFile(oldPath, newPath, deleteExisting);
        }
    }

    private boolean moveDirectory(@NonNull String oldPath, @NonNull String newPath) {
        File destDir = new File(newPath);
        File srcDir = new File(oldPath);
        try {
            FileUtils.moveDirectory(srcDir, destDir);
        } catch (FileExistsException e) {
            deleteDirectory(newPath);
            return moveDirectory(oldPath, newPath);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public boolean moveFile(@NonNull String oldPath, @NonNull String newPath, boolean deleteExisting) {
        File oldFile = new File(oldPath);
        File newFile = new File(newPath);
        try {
            FileUtils.moveFile(oldFile, newFile);
        } catch (FileExistsException e) {
            if (deleteExisting) {
                deleteFile(newPath);
                return moveFile(oldPath, newPath, true);
            } else {
                deleteFile(oldPath);
                return true;
            }
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public void deleteFile(@NonNull String path) {
        try {
            if (isExist(path))
                FileUtils.forceDelete(new File(path));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean writeToFile(byte[] buffer, long currentOffset, @NonNull String name) {
        RandomAccessFile randomAccessFile = null;
        try {
            randomAccessFile = new RandomAccessFile(name, "rws");
            randomAccessFile.seek(currentOffset);
            randomAccessFile.write(buffer);
            return true;
        } catch (FileNotFoundException e) {
            String parent = getParentPath(name);
            if (!isExist(parent)) {
                createDirectory(parent);
                return writeToFile(buffer, currentOffset, name);
            }
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            if (randomAccessFile != null) {
                try {
                    randomAccessFile.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public String getRealPhotoPathFromUri(Uri uri) {
        String[] data = {MediaStore.Images.Media.DATA};
        CursorLoader loader = new CursorLoader(context, uri, data, null, null, null);
        Cursor cursor = loader.loadInBackground();
        int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
        cursor.moveToFirst();
        return cursor.getString(column_index);
    }

    public boolean copy(@NonNull String filePath, @NonNull String copyPath, boolean deleteExisting) {
        try {
            File destFile = new File(copyPath);
            File srcFile = new File(filePath);
            if (destFile.exists()) {
                if (deleteExisting) {
                    FileUtils.forceDelete(destFile);
                } else {
                    return true;
                }
            }
            if (srcFile.isDirectory()) {
                FileUtils.copyDirectory(srcFile, destFile, false);
            } else {
                if (srcFile.canRead()) {
                    if (destFile.exists()) {
                        if (deleteExisting) {
                            //noinspection ResultOfMethodCallIgnored
                            destFile.delete();
                        } else {
                            return true;
                        }
                    }
                    FileUtils.copyFile(srcFile, destFile);
                } else {
                    return false;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public void deleteDirectory(@NonNull String path) {
        try {
            if (isExist(path))
                FileUtils.deleteDirectory(new File(path));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @NonNull
    public static String getParentPath(@NonNull String path) {
        String[] slitPath = path.split(File.separator);
        int lengthWithOutNameFile = path.length() - slitPath[slitPath.length - 1].length();
        String folderPath = path.substring(0, lengthWithOutNameFile);
        folderPath = folderPath.isEmpty() ? Const.DEFAULT_PATH : folderPath.substring(0, folderPath.length() - 1);
        return folderPath;
    }


    @NonNull
    public static String buildPath(String parentPath, String name) {
        return parentPath + File.separator + name;
    }

    @NonNull
    public static String buildPath(@Nullable FileRealm parent, String name) {
        String parentPath;
        if (parent == null) {
            parentPath = Const.DEFAULT_PATH;
        } else {
            parentPath = parent.getPath();
        }
        return buildPath(parentPath, name);
    }

    @NonNull
    public static String getNameFromPath(@NonNull String path) {
        return new File(path).getName();
    }

    @NonNull
    private static final AtomicLong recentIndex = new AtomicLong(0);

    @NonNull
    public String buildPathForRecentCopy() {
        long index = recentIndex.incrementAndGet();
        return Const.COPIES_PATH + File.separator + index + ".recent";
    }


    @NonNull
    public static String buildPathForCopyNamedHash(String hash) {
        return Const.COPIES_PATH + File.separator + hash;
    }

    public static boolean isExist(@Nullable String path) {
        if (path == null) {
            return false;
        }
        return new File(path).exists();
    }

    @NonNull
    public byte[] readBytes(String path, long currentOffset, int lengthPart) throws IOException {
        byte[] buffer = new byte[lengthPart];
        RandomAccessFile randomAccessFile = new RandomAccessFile(path, "rw");
        Log.i(TAG, String.format(
                "readBytes: from path: %s, offset: %s, lengthPart: %s, file.length: %s",
                path, currentOffset, lengthPart, randomAccessFile.length()));
        randomAccessFile.seek(currentOffset);
        randomAccessFile.readFully(buffer);
        return buffer;
    }

    public static long getFileSize(@NonNull String path) {
        return new File(path).length();
    }

    public long getFolderSize(@NonNull String path) {
        File file = new File(path);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return size(file.toPath());
        } else {
            return fileSize(file);
        }
    }

    private long fileSize(@Nullable File root) {
        if (root == null) {
            return 0;
        }
        if (root.isFile()) {
            return root.length();
        }
        try {
            if (isSymlink(root)) {
                return 0;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return 0;
        }

        long length = 0;
        File[] files = root.listFiles();
        if (files == null) {
            return 0;
        }
        for (File file : files) {
            length += fileSize(file);
        }

        return length;
    }

    private static boolean isSymlink(@NonNull File file) throws IOException {
        File canon;
        if (file.getParent() == null) {
            canon = file;
        } else {
            File parentFile = file.getParentFile();
            if (parentFile == null) return false;
            File canonDir = parentFile.getCanonicalFile();
            canon = new File(canonDir, file.getName());
        }
        return !canon.getCanonicalFile().equals(canon.getAbsoluteFile());
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private static long size(@NonNull Path path) {

        final AtomicLong size = new AtomicLong(0);

        try {
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @NonNull
                @Override
                public FileVisitResult visitFile(Path file, @NonNull BasicFileAttributes attrs) {
                    size.addAndGet(attrs.size());
                    return FileVisitResult.CONTINUE;
                }

                @NonNull
                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    // Skip folders that can't be traversed
                    return FileVisitResult.CONTINUE;
                }

                @NonNull
                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                    // Ignore errors traversing a folder
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new AssertionError("walkFileTree will not throw IOException if the FileVisitor does not");
        }

        return size.get();
    }

    public void createEmptyFile(@NonNull String path) {
        createDirectory(getParentPath(path));
        try {
            new RandomAccessFile(path, "rw").setLength(0);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void delete(@NonNull String path) {
        File file = new File(path);
        if (file.exists()) {
            if (file.isDirectory()) {
                deleteDirectory(path);
            } else {
                deleteFile(path);
            }
        }
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isExistCopy(String hash) {
        String copyPath = buildPathForCopyNamedHash(hash);
        return isExist(copyPath);
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isFolderEmpty(@NonNull String defaultPath) {
        File file = new File(defaultPath);
        File[] fileList = file.listFiles();
        return !file.exists() || fileList == null || fileList.length == 0;
    }

    public boolean isImage(@Nullable String type) {
        if (type == null) {
            return false;
        }
        if (type.isEmpty()) {
            return false;
        }
        String typeFile = type.toLowerCase();

        return typeFile.equals("jpg") ||
                typeFile.equals("png") ||
                typeFile.equals("jpeg") ||
                typeFile.equals("bmp");
    }


    @Nullable
    private String fileExt(@NonNull String url) {
        if (url.contains("?")) {
            url = url.substring(0, url.indexOf("?"));
        }
        if (url.lastIndexOf(".") == -1) {
            return null;
        } else {
            String ext = url.substring(url.lastIndexOf(".") + 1);
            if (ext.contains("%")) {
                ext = ext.substring(0, ext.indexOf("%"));
            }
            if (ext.contains("/")) {
                ext = ext.substring(0, ext.indexOf("/"));
            }
            return ext.toLowerCase();

        }
    }

    @NonNull
    public static String buildPatchPath(String uuid) {
        return Const.PATCHES_PATH + File.separator + uuid;
    }

    public static boolean isImageOrVideoFile(String path) {
        String mimeType = URLConnection.guessContentTypeFromName(path);
        return mimeType != null && (mimeType.startsWith("image") || mimeType.startsWith("video"));
    }

    @NonNull
    public List<File> getTotalListFile(@NonNull String path) {
        List<File> fileListTotal = new ArrayList<>();
        List<File> fileList = getFileInDirectory(path);
        for (File file : fileList) {
            if (file.getAbsolutePath().contains(Const.CAMERA_PATH) && file.isHidden()) {
                continue;
            } else if (file.isDirectory()) {
                List<File> childList = getTotalListFile(file.getPath());
                fileListTotal.addAll(childList);
            }
            fileListTotal.add(file);
        }
        return fileListTotal;
    }

    public void openFile(@NonNull String name, @NonNull String path) {
        File file = new File(path);
        String fileExt = new File(path).isDirectory() ? "folder" : fileExt(name);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        fileExt = fileExt == null ? "" : fileExt;
        switch (fileExt) {
            case "apk":
                Uri apkUri = Uri.parse("file://" + path);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
                    intent.setData(apkUri);
                } else {
                    intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
                }
                break;
            case "folder":
                Log.d(TAG, String.format("openFile: %s", Uri.parse(path)));
                intent.setDataAndType(Uri.parse(path), "resource/folder");
                break;
            default:
                String mimeType = URLConnection.guessContentTypeFromName(path);
                mimeType = mimeType == null || mimeType.isEmpty() ? "file/*" : mimeType;
                intent.setDataAndType(Uri.parse("file://" + path), mimeType);
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

        try {
            context.startActivity(intent);
        } catch (Exception ex) {
            intent.setDataAndType(Uri.parse("file://" + file), "*/*");
            try {
                context.startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(context, "No handler for this type of file.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    public static String getFileName(String fileName) {
        String name = FilenameUtils.removeExtension(fileName);
        return FilenameUtils.removeExtension(name);
    }

    @NonNull
    public static String getExtension(String fileName) {
        String extension1 = FilenameUtils.getExtension(fileName);
        String name = FilenameUtils.removeExtension(fileName);
        String extension2 = FilenameUtils.getExtension(name);
        String extension = "";
        if (!extension1.isEmpty()) {
            extension = "." + extension1;
        }
        if (!extension2.isEmpty()) {
            extension = "." + extension2 + extension;
        }
        return extension;
    }
}
