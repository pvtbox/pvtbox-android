package net.pvtbox.android.ui.files.dialog;

import android.content.Context;
import android.content.DialogInterface;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;

import net.pvtbox.android.R;
import net.pvtbox.android.db.DataBaseService;
import net.pvtbox.android.db.model.FileRealm;
import net.pvtbox.android.tools.FileTool;
import net.pvtbox.android.ui.files.FilesActionsHandler;
import net.pvtbox.android.ui.files.SelectionProvider;

import java.nio.charset.StandardCharsets;
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

public class RenameDialog {
    @NonNull
    private final AlertDialog dialog;
    private final EditText input;
    private final Context context;
    private final FilesActionsHandler filesActionsHandler;
    private final SelectionProvider selectionProvider;
    private final DataBaseService dataBaseService;

    public RenameDialog(Context context, FilesActionsHandler filesActionsHandler,
                        SelectionProvider selectionProvider, OnDialogListener dialogListener,
                        DataBaseService dataBaseService) {
        this.context = context;
        this.filesActionsHandler = filesActionsHandler;
        this.selectionProvider = selectionProvider;
        this.dataBaseService = dataBaseService;
        AlertDialog.Builder builder = new AlertDialog.Builder(
                new ContextThemeWrapper(context, R.style.AppTheme));
        builder.setTitle(R.string.rename);
        View viewDialog = LayoutInflater.from(context).inflate(R.layout.rename_dialog, null);
        builder.setView(viewDialog);
        builder.setPositiveButton(R.string.ok, null);
        builder.setNegativeButton(R.string.cancel,
                (DialogInterface dialog, int which) -> dialog.cancel());

        input = viewDialog.findViewById(R.id.name_file_edit_text);

        dialog = builder.create();
        Objects.requireNonNull(dialog.getWindow()).setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        dialog.setOnDismissListener(dialogListener);
    }

    public void show(@NonNull String originalName) {
        input.setError(null);
        input.setText(originalName);
        int dotPosition = originalName.indexOf('.');
        input.setSelection(0, dotPosition == -1 ? originalName.length() : dotPosition);
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String newName = input.getText().toString().trim();
            if (newName.isEmpty()) {
                input.setError(context.getString(R.string.empty_name_error));
                return;
            }
            if (newName.getBytes(StandardCharsets.UTF_8).length > 255) {
                input.setError(context.getString(R.string.filename_too_long));
                return;
            }
            FileRealm file = selectionProvider.getSelectedItem();
            String newPath = FileTool.getParentPath(file.getPath());
            newPath = FileTool.buildPath(newPath, newName);
            FileRealm existingFileItem = dataBaseService.getFileByPath(newPath);
            if (existingFileItem != null) {
                if (existingFileItem.isFolder()) {
                    input.setError(String.format(
                            context.getString(R.string.folder_with_name_already_exist),
                            newName));
                } else {
                    input.setError(String.format(
                            context.getString(R.string.file_with_name_already_exist),
                            newName));
                }

                return;
            }
            filesActionsHandler.onRename(newName);
            dialog.cancel();

        });
    }
}
