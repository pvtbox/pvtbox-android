package net.pvtbox.android.ui.files;

import android.app.Activity;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import net.pvtbox.android.R;
import net.pvtbox.android.application.Const;
import net.pvtbox.android.db.DataBaseService;
import net.pvtbox.android.db.model.FileRealm;
import net.pvtbox.android.service.PreferenceService;
import net.pvtbox.android.tools.FileTool;
import net.pvtbox.android.ui.ShowFragmentActivity;
import net.pvtbox.android.ui.collaboration.CollaborationFragment;
import net.pvtbox.android.ui.files.dialog.OnDialogListener;
import net.pvtbox.android.ui.files.dialog.RenameDialog;
import net.pvtbox.android.ui.files.menu.OnMenuListener;
import net.pvtbox.android.ui.files.menu.ShareMenuDialog;
import net.pvtbox.android.ui.getlink.GetLinkFragment;
import net.pvtbox.android.ui.imageviewer.PropertyViewerActivity;
import net.pvtbox.android.ui.settings.PvtboxFragment;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Objects;

import static net.pvtbox.android.tools.ShareActivityHelper.getFileNameFromUri;
import static net.pvtbox.android.tools.ShareActivityHelper.isUriVirtual;
import static net.pvtbox.android.tools.ShareActivityHelper.openUriInputStream;


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
public class FilesActionsHandler implements OnMenuListener, OnDialogListener {
    private static final String TAG = FilesActionsHandler.class.getSimpleName();
    @NonNull
    private final Activity activity;
    @NonNull
    private final ShareMenuDialog shareDialog;
    private final PreferenceService preferenceService;
    private final SelectionProvider selectionProvider;
    private final CurrentFolderProvider currentFolderProvider;
    private final FileTool fileTool;
    private final DataBaseService dataBaseService;
    private final SelectionChangeListenerProvider selectionChangeListenerProvider;
    @NonNull
    private final RenameDialog renameDialog;

    public FilesActionsHandler(
            @NonNull Activity activity, PreferenceService preferenceService,
            SelectionProvider selectionProvider, CurrentFolderProvider currentFolderProvider,
            SelectionChangeListenerProvider selectionChangeListenerProvider,
            FileTool fileTool, DataBaseService dataBaseService) {
        this.activity = activity;
        this.selectionProvider = selectionProvider;
        this.currentFolderProvider = currentFolderProvider;
        this.selectionChangeListenerProvider = selectionChangeListenerProvider;
        this.fileTool = fileTool;
        this.dataBaseService = dataBaseService;
        this.preferenceService = preferenceService;

        this.shareDialog = new ShareMenuDialog(activity, preferenceService,
                currentFolderProvider, this);
        this.renameDialog = new RenameDialog(
                activity, this, selectionProvider, this,
                dataBaseService);
    }

    void onDeleteAction() {
        ArrayList<FileRealm> items = selectionProvider.getSelectedItems();
        Log.d(TAG, String.format("onDeleteAction: %s", items));
        AlertDialog.Builder builder = new AlertDialog.Builder(
                new ContextThemeWrapper(activity.getWindow().getContext(), R.style.AppTheme));
        String message = activity.getString(R.string.do_you_want_to_del);
        int operation;
        if (items.size() > 1) {
            operation = Operation.delete_multi;
            message = String.format(message,
                    String.format(activity.getString(R.string.selected_items), items.size()));
        } else {
            FileRealm item = items.get(0);
            if (item.isFolder()) {
                operation = Operation.delete_folder;
                message = String.format(message,
                        String.format(activity.getString(R.string.folder_with_name),
                                item.getName()));
            } else {
                operation = Operation.delete_file;
                message = String.format(message,
                        String.format(activity.getString(R.string.file_with_name),
                                item.getName()));
            }
        }
        builder.setMessage(message)
                .setCancelable(true)
                .setPositiveButton(R.string.delete, (dialog, id) -> sendOperationBroadcast(
                        operation, null, items, null, null,
                        null, null, false, false))
                .setNegativeButton(R.string.cancel, (dialog, id) -> {

                });
        AlertDialog alert = builder.create();
        alert.show();
    }

    void onShareAction() {
        FileRealm fileItem = selectionProvider.getSelectedItem();
        Log.d(TAG, String.format("onShareAction: %s", fileItem.getName()));
        if (fileItem.isFolder()) {
            shareDialog.show();
        } else {
            Intent intent = new Intent(activity, ShowFragmentActivity.class);
            intent.putExtra(ShowFragmentActivity.CLASS, GetLinkFragment.class);
            intent.putExtra(PvtboxFragment.FILE_ITEM, fileItem.getUuid());
            activity.startActivity(intent);
            Objects.requireNonNull(selectionChangeListenerProvider.getSelectionChangeListener()).onCancelSelection();
        }
    }

    void onSwitchOfflineAction(boolean checked) {
        ArrayList<FileRealm> items = selectionProvider.getSelectedItems();
        Log.d(TAG, String.format("onSwitchOfflineAction: %s, %s", items, checked));
        if (checked) {
            int operation = items.size() > 1 ? Operation.add_offline_multi :
                    items.get(0).isFolder() ? Operation.add_offline_folder :
                            Operation.add_offline_file;
            sendOperationBroadcast(
                    operation, null, items, null, null,
                    null, null, false, false);
        } else {
            int operation = items.size() > 1 ? Operation.remove_offline_multi :
                    items.get(0).isFolder() ? Operation.remove_offline_folder :
                            Operation.remove_offline_file;
            sendOperationBroadcast(
                    operation, null, items, null, null,
                    null, null, false, false);
        }
    }

    void onDownloadAction() {
        onDownloadTo(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS).getPath());
    }

    private void onDownloadTo(String path) {
        ArrayList<FileRealm> selectedItems = selectionProvider.getSelectedItems();
        Objects.requireNonNull(selectionChangeListenerProvider.getSelectionChangeListener()).onCancelSelection();
        Log.d(TAG, String.format("onDownloadAction: %s", selectedItems));
        ArrayList<String> uuids = new ArrayList<>(selectedItems.size());
        for (FileRealm file : selectedItems) {
            uuids.add(file.getUuid());
        }


        Intent intent = new Intent(Const.OPERATIONS_PROGRESS_INTENT);
        intent.putExtra(Const.OPERATIONS_PROGRESS_MESSAGE, activity.getString(
                R.string.will_download_to, path));
        intent.putExtra(Const.OPERATIONS_PROGRESS_SHOW_AND_DISMISS, true);
        LocalBroadcastManager.getInstance(activity).sendBroadcast(intent);
        dataBaseService.downloadFilesTo(uuids, path);
    }

    void onOpenWithAction() {
        FileRealm file = selectionProvider.getSelectedItem();
        if (file.isDownload()) {
            showToast(activity.getString(R.string.wait_while_downloading));
            return;
        } else {
            String path = file.getPath();
            if (!FileTool.isExist(path)) {
                path = file.getCameraPath();
                if (path != null && !path.isEmpty()) {
                    if (!FileTool.isExist(path)) {
                        path = null;
                    }
                }

                if (path == null) {
                    showToast(activity.getString(R.string.download_firstly));
                    return;
                }
            }
        }
        Log.d(TAG, String.format("onOpenWithAction: %s", file.getName()));
        CustomAppChooser customAppChooser = CustomAppChooser.newInstance(file);
        customAppChooser.setStyle(DialogFragment.STYLE_NORMAL, R.style.DialogWhite);
        customAppChooser.show(activity.getFragmentManager(), TAG);
    }

    public void onCopyAction() {
        ArrayList<FileRealm> items = selectionProvider.getSelectedItems();
        Log.d(TAG, String.format("onCopyAction: %s", items));
        FileRealm currentFolder = currentFolderProvider.getCurrentFolder();
        int operation = items.size() > 1 ? Operation.copy_multi :
                items.get(0).isFolder() ? Operation.copy_folder :
                        Operation.copy_file;
        sendOperationBroadcast(
                operation, currentFolder, items, null, null,
                null, null, false, false);
    }

    public boolean onMoveAction() {
        ArrayList<FileRealm> items = selectionProvider.getSelectedItems();
        Log.d(TAG, String.format("onMoveAction: %s", items));
        FileRealm currentFolder = currentFolderProvider.getCurrentFolder();
        String currentFolderUuid = currentFolder == null ? null : currentFolder.getUuid();
        for (FileRealm item : items) {
            if (Objects.equals(item.getParentUuid(), currentFolderUuid)) {
                if (item.isFolder()) {
                    Toast.makeText(
                            activity,
                            String.format(activity.getString(
                                    R.string.can_not_move_folder_in_same_folder),
                                    item.getName()),
                            Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(
                            activity,
                            String.format(activity.getString(
                                    R.string.can_not_move_file_in_same_folder),
                                    item.getName()),
                            Toast.LENGTH_LONG).show();
                }
                return false;
            }
            String newPath = FileTool.buildPath(currentFolder, item.getName());
            FileRealm existingItem = dataBaseService.getFileByPath(newPath);
            if (existingItem != null) {
                if (existingItem.isFolder()) {
                    Toast.makeText(
                            activity,
                            String.format(activity.getString(
                                    R.string.folder_with_name_already_exist),
                                    existingItem.getName()),
                            Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(
                            activity,
                            String.format(activity.getString(
                                    R.string.file_with_name_already_exist),
                                    existingItem.getName()),
                            Toast.LENGTH_LONG).show();
                }
                return false;
            }
        }

        int operation = items.size() > 1 ? Operation.move_multi :
                items.get(0).isFolder() ? Operation.move_folder :
                        Operation.move_file;

        sendOperationBroadcast(
                operation, currentFolder, items, null, null,
                null, null, false, false);
        return true;
    }

    void onRenameAction() {
        FileRealm fileItem = selectionProvider.getSelectedItem();
        Log.d(TAG, String.format("onRenameAction: %s", fileItem.getName()));
        renameDialog.show(fileItem.getName());
    }

    public void onRename(String newName) {
        FileRealm file = selectionProvider.getSelectedItem();
        FileRealm currentFolder = currentFolderProvider.getCurrentFolder();

        sendOperationBroadcast(
                file.isFolder() ? Operation.rename_folder : Operation.rename_file,
                currentFolder, null, file, newName, null, null, false, false);
    }

    void onCancelDownloadsAction() {
        ArrayList<FileRealm> files = selectionProvider.getSelectedItems();

        sendOperationBroadcast(
                files.size() > 1 ? Operation.cancel_downloads : Operation.cancel_download,
                null, files, null, null, null, null, false, false);
    }

    void onPropertiesAction() {
        FileRealm file = selectionProvider.getSelectedItem();
        Log.d(TAG, String.format("onPropertiesAction: %s", file.getName()));
        Intent intent = new Intent(activity, PropertyViewerActivity.class);
        intent.putExtra(Const.FILE_OPERATION_TARGET_OBJECT, file.getUuid());
        activity.startActivity(intent);
    }

    public void onAddPhoto(@NonNull Uri uri) {
        Log.i(TAG, String.format("onAddPhoto: uri: %s", uri));
        String path = fileTool.getRealPhotoPathFromUri(uri);
        if (path == null || path.isEmpty()) {
            onAddFile(uri, false);
            return;
        }
        File file = new File(path);
        if (!file.exists()) {
            Log.e(TAG, "onAddPhoto: file does not exist");
            showToast(String.format(
                    activity.getString(R.string.can_not_import_file), uri.getLastPathSegment(),
                    activity.getString(R.string.file_does_not_exist)));
            return;
        }
        FileRealm currentFolder = currentFolderProvider.getCurrentFolder();

        sendOperationBroadcast(
                Operation.create_file, currentFolder, null, null, null,
                path, null, false, false);
    }

    public void onAddFile(@NonNull String path, boolean share) {
        Log.i(TAG, String.format("onAddFile: path: %s", path));
        File file = new File(path);
        if (!file.exists()) {
            Log.e(TAG, "onAddFile: file does not exist");
            showToast(String.format(
                    activity.getString(R.string.can_not_import_file), path,
                    activity.getString(R.string.file_does_not_exist)));
            return;
        }

        FileRealm currentFolder = currentFolderProvider.getCurrentFolder();

        sendOperationBroadcast(
                Operation.create_file, currentFolder, null, null, null,
                path, null, share, false);
    }

    public void onAddFile(@NonNull Uri uri, boolean share) {
        Log.i(TAG, String.format("onAddFile: uri: %s", uri));

        if ("file".equals(uri.getScheme())) {
            onAddFile(Objects.requireNonNull(uri.getPath()), share);
            return;
        } else if ("share".equals(uri.getScheme())) {
            onAddShareFile(uri, share);
            return;
        }
        boolean isVirtual = isUriVirtual(uri, activity);
        String name = getFileNameFromUri(uri, activity);
        if (name == null || name.isEmpty()) {
            Log.e(TAG, "onAddFile: cant get file name");
            name = "imported_file";
        }
        if (isVirtual) {
            name += ".pdf";
        }
        File tempFile;
        try {
            tempFile = File.createTempFile(name, ".tmp");
        } catch (IOException e) {
            showToast(activity.getString(R.string.can_not_import_file, name,
                            activity.getString(R.string.unsupported_file)));
            e.printStackTrace();
            return;
        }
        try (InputStream istream = openUriInputStream(uri, isVirtual, activity)) {
            if (istream == null) {
                return;
            }
            FileUtils.copyInputStreamToFile(istream, tempFile);
        } catch (Exception e) {
            Log.e(TAG, "onAddFile: open stream error:", e);
            showToast(activity.getString(R.string.can_not_import_file, name,
                    activity.getString(R.string.unsupported_file)));
            return;
        }

        FileRealm currentFolder = currentFolderProvider.getCurrentFolder();

        sendOperationBroadcast(
                Operation.create_file, currentFolder, null, null, name,
                tempFile.getPath(), null, share, true);
    }

    private void onAddShareFile(Uri uri, boolean share) {
        String path = uri.getSchemeSpecificPart();
        String name = uri.getFragment();
        FileRealm currentFolder = currentFolderProvider.getCurrentFolder();

        sendOperationBroadcast(
                Operation.create_file, currentFolder, null, null, name,
                path, null, share, true);
    }

    public void onAddShareFiles(ArrayList<Uri> uris, boolean share) {
        if (uris.size() == 1) {
            onAddShareFile(uris.get(0), share);
            return;
        }
        FileRealm currentFolder = currentFolderProvider.getCurrentFolder();

        sendOperationBroadcast(
                Operation.create_file_multi, currentFolder, null, null, null,
                null, uris, share, true);
    }

    public void onAddFolder(String name) {
        Log.d(TAG, String.format("onAddFolder: %s", name));
        FileRealm currentFolder = currentFolderProvider.getCurrentFolder();

        sendOperationBroadcast(
                Operation.create_folder, currentFolder, null, null, name,
                null, null, false, false);
    }

    @Override
    public void onClick(@NonNull View view) {
        Log.d(TAG, String.format("onClick view: %s", view));
        switch (view.getId()) {
            case R.id.action_collaborate:
                if (Objects.equals(preferenceService.getLicenseType(), Const.FREE_LICENSE)) {
                    showToast(activity.getString(R.string.not_available_for_free_license));
                } else if (currentFolderProvider.getCurrentFolder() == null) {
                    onCollaborateAction();
                } else {
                    showToast(activity.getString(R.string.only_root_can_collaborate));
                }
                break;
            case R.id.action_get_link:
                onGetLinkAction();
                break;
        }
        shareDialog.dismiss(0);
    }

    @Override
    public void onDismiss(DialogInterface dialogInterface) {
        Log.d(TAG, "onDismiss");
        Objects.requireNonNull(selectionChangeListenerProvider.getSelectionChangeListener()).onCancelSelection();
    }

    @Override
    public void onShow(DialogInterface dialogInterface) {

    }

    private void onGetLinkAction() {
        FileRealm file = selectionProvider.getSelectedItem();
        if (file.isFolder() &&
                Objects.equals(preferenceService.getLicenseType(), Const.FREE_LICENSE)) {
            showToast(activity.getString(R.string.not_available_for_free_license));
            return;

        }
        Log.d(TAG, String.format("onGetLinkAction: %s", file.getName()));
        Intent intent = new Intent(activity, ShowFragmentActivity.class);
        intent.putExtra(ShowFragmentActivity.CLASS, GetLinkFragment.class);
        intent.putExtra(PvtboxFragment.FILE_ITEM, file.getUuid());
        activity.startActivity(intent);
    }

    private void onCollaborateAction() {
        FileRealm file = selectionProvider.getSelectedItem();
        Log.d(TAG, String.format("onCollaborateAction: %s", file.getName()));
        Intent intent = new Intent(activity, ShowFragmentActivity.class);
        intent.putExtra(ShowFragmentActivity.CLASS, CollaborationFragment.class);
        intent.putExtra(PvtboxFragment.FILE_ITEM, file.getUuid());
        activity.startActivity(intent);
    }

    private void sendOperationBroadcast(
            int operation, @Nullable FileRealm parent,
            @Nullable ArrayList<FileRealm> files, @Nullable FileRealm file,
            @Nullable String newName, @Nullable String path, @Nullable ArrayList<Uri> uris,
            boolean share, boolean deleteFile) {

        Intent intent = new Intent(Const.FILE_OPERATION_INTENT);
        intent.putExtra(Const.FILE_OPERATION_TYPE, operation);
        if (parent != null) {
            intent.putExtra(Const.FILE_OPERATION_ROOT, parent.getUuid());
        }
        if (files != null) {
            ArrayList<String> uuids = new ArrayList<>();
            for (FileRealm f : files) {
                uuids.add(f.getUuid());
            }
            intent.putStringArrayListExtra(Const.FILE_OPERATION_UUIDS, uuids);
        }
        if (file != null) {
            intent.putExtra(Const.FILE_OPERATION_UUID, file.getUuid());
        }
        if (newName != null) {
            intent.putExtra(Const.FILE_OPERATION_NEW_NAME, newName);
        }
        if (path != null) {
            intent.putExtra(Const.FILE_OPERATION_PATH, path);
        }
        if (uris != null) {
            intent.putExtra(Const.FILE_OPERATION_URIS, uris);
        }
        intent.putExtra(Const.SHARING_ENABLE, share);
        intent.putExtra(Const.DELETE_FILE, deleteFile);

        LocalBroadcastManager.getInstance(activity).sendBroadcast(intent);
    }

    private void showToast(String message) {
        Toast toast = Toast.makeText(activity, message, Toast.LENGTH_LONG);
        toast.show();
    }
}
