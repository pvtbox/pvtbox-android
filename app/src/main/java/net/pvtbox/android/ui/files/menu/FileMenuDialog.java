package net.pvtbox.android.ui.files.menu;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;

import com.bumptech.glide.signature.ObjectKey;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.appcompat.widget.SwitchCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import net.pvtbox.android.R;
import net.pvtbox.android.db.model.FileRealm;
import net.pvtbox.android.service.OperationService;
import net.pvtbox.android.ui.files.IconTool;
import net.pvtbox.android.ui.files.ModeProvider;
import net.pvtbox.android.ui.files.SelectionProvider;

import java.io.File;
import java.util.Objects;

import static net.pvtbox.android.ui.files.FilesAdapter.TARGET_SIZE;

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

public class FileMenuDialog implements DismissibleDialog, OfflineSwitch {
    private final int colorDisabled;
    private final int colorEnabled;
    private final BottomSheetDialog dialog;

    private View preview;
    private ImageView previewImage;
    private TextView previewName;

    private View share;
    private TextView shareText;

    private View offline;
    private TextView offlineText;
    private SwitchCompat offlineSwitch;
    private boolean offlineSwitchTouched;

    private View download;
    private TextView downloadText;

    private View copy;
    private TextView copyText;

    private View move;
    private TextView moveText;

    private View cancelDownloads;
    private TextView cancelDownloadsText;

    private View rename;
    private TextView renameText;

    private View delete;
    private TextView deleteText;

    private View properties;

    private final Context context;
    private final ModeProvider modeProvider;
    private final SelectionProvider selectionProvider;
    private final OnMenuListener listener;
    private View openWith;
    private boolean active;


    public FileMenuDialog(
            @NonNull Context context,
            ModeProvider modeProvider, SelectionProvider selectionProvider,
            OnMenuListener listener) {
        this.context = context;
        this.modeProvider = modeProvider;
        this.selectionProvider = selectionProvider;
        this.listener = listener;

        LayoutInflater inflater = (LayoutInflater)
                context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View menu = Objects.requireNonNull(inflater).inflate(R.layout.file_menu_dialog, null);

        dialog = new BottomSheetDialog(context);
        dialog.setContentView(menu);

        bindViews(menu);
        setCallbacks();

        colorDisabled = ContextCompat.getColor(context, R.color.monsoon_light);
        colorEnabled = ContextCompat.getColor(context, R.color.black);
    }

    public void show() {
        active = true;
        FileRealm file = selectionProvider.getSelectedItem();
        previewName.setText(file.getName());
        setImage(file);
        boolean isDownloads = modeProvider.isDownloads();
        boolean isRecent = modeProvider.isRecent();
        share.setVisibility(isDownloads ? View.GONE : View.VISIBLE);
        offline.setVisibility(isDownloads ? View.GONE : View.VISIBLE);
        download.setVisibility(isDownloads ? View.GONE : View.VISIBLE);
        copy.setVisibility(isRecent || isDownloads ? View.GONE : View.VISIBLE);
        move.setVisibility(isRecent || isDownloads ? View.GONE : View.VISIBLE);
        rename.setVisibility(isDownloads ? View.GONE : View.VISIBLE);
        cancelDownloads.setVisibility(isDownloads ? View.VISIBLE : View.GONE);
        openWith.setVisibility(isDownloads || file.isFolder() ? View.GONE : View.VISIBLE);

        offlineSwitch.setChecked(
                file.isOffline() || (file.isDownload() && !file.isOnlyDownload()));
        enableOrDisableActions();
        dialog.show();
    }

    private void enableOrDisableActions() {
        if (OperationService.isProcessing()) {
            shareText.setTextColor(colorDisabled);
            offlineText.setTextColor(colorDisabled);
            offlineSwitch.setEnabled(false);
            downloadText.setTextColor(colorDisabled);
            copyText.setTextColor(colorDisabled);
            moveText.setTextColor(colorDisabled);
            renameText.setTextColor(colorDisabled);
            cancelDownloadsText.setTextColor(colorDisabled);
            deleteText.setTextColor(colorDisabled);
        } else {
            shareText.setTextColor(colorEnabled);
            offlineText.setTextColor(colorEnabled);
            offlineSwitch.setEnabled(true);
            downloadText.setTextColor(colorEnabled);
            copyText.setTextColor(colorEnabled);
            moveText.setTextColor(colorEnabled);
            renameText.setTextColor(colorEnabled);
            cancelDownloadsText.setTextColor(colorEnabled);
            deleteText.setTextColor(colorEnabled);
        }
        new Handler().postDelayed(() -> {
            if (!dialog.isShowing() || !active) return;
            enableOrDisableActions();
        }, 50);
    }

    @Override
    public void dismiss(int dismissTimeout) {
        active = false;
        if (dismissTimeout > 0) {
            new Handler().postDelayed(dialog::dismiss, dismissTimeout);
        } else {
            dialog.dismiss();
        }
    }

    private void setImage(@NonNull FileRealm file) {
        if (file.isFolder()) {
            previewImage.setImageResource(
                    file.isCollaborated() ? R.drawable.folder_shared : R.drawable.folder);
            return;
        }
        String path = file.getPath();
        boolean fileExist = file.isDownloadActual() || file.isOffline();
        String cameraPath = file.getCameraPath();
        boolean haveCamera = cameraPath != null && !cameraPath.isEmpty();
        boolean canPreview = fileExist || haveCamera;
        if (canPreview) {
            File f = haveCamera ? new File(cameraPath) : new File(path);
            String signature = file.getHashsum();
            if (signature == null) {
                signature = file.getName();
            }
            Glide
                    .with(context)
                    .load(f)
                    .signature(new ObjectKey(signature))
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .override(TARGET_SIZE, TARGET_SIZE)
                    .error(IconTool.getIcon(file))
                    .into(previewImage);
        } else {
            Glide.with(context).clear(previewImage);
            previewImage.setImageResource(IconTool.getIcon(file));
        }
    }

    private void bindViews(@NonNull View menu) {
        preview = menu.findViewById(R.id.file_preview);
        previewImage = menu.findViewById(R.id.file_preview_image);
        previewName = menu.findViewById(R.id.file_preview_name);

        openWith = menu.findViewById(R.id.action_open_with);

        share = menu.findViewById(R.id.action_share);
        shareText = menu.findViewById(R.id.action_share_text);

        offline = menu.findViewById(R.id.action_create_offline_copy);
        offlineText = menu.findViewById(R.id.action_create_offline_copy_text);
        offlineSwitch = menu.findViewById(R.id.action_create_offline_copy_switch);

        download = menu.findViewById(R.id.action_download_to);
        downloadText = menu.findViewById(R.id.action_download_to_text);

        copy = menu.findViewById(R.id.action_copy);
        copyText = menu.findViewById(R.id.action_copy_text);

        move = menu.findViewById(R.id.action_move);
        moveText = menu.findViewById(R.id.action_move_text);

        rename = menu.findViewById(R.id.action_rename);
        renameText = menu.findViewById(R.id.action_rename_text);

        cancelDownloads = menu.findViewById(R.id.action_cancel);
        cancelDownloadsText = menu.findViewById(R.id.action_cancel_text);

        delete = menu.findViewById(R.id.action_delete);
        deleteText = menu.findViewById(R.id.action_delete_text);

        properties = menu.findViewById(R.id.action_properties);
    }

    @SuppressWarnings("SameReturnValue")
    @SuppressLint("ClickableViewAccessibility")
    private void setCallbacks() {
        preview.setOnClickListener(listener);
        openWith.setOnClickListener(listener);
        share.setOnClickListener(listener);
        offline.setOnClickListener(listener);
        offlineSwitch.setOnTouchListener((view, motionEvent) -> {
            offlineSwitchTouched = true;
            return false;
        });
        offlineSwitch.setOnClickListener(view -> {
            if (offlineSwitchTouched) {
                offlineSwitchTouched = false;
                listener.onClick(view);
            }
        });
        offlineSwitch.setOnCheckedChangeListener((compoundButton, b) -> {
            if (offlineSwitchTouched) {
                offlineSwitchTouched = false;
                listener.onClick(compoundButton);
            }
        });
        download.setOnClickListener(listener);
        copy.setOnClickListener(listener);
        move.setOnClickListener(listener);
        rename.setOnClickListener(listener);
        cancelDownloads.setOnClickListener(listener);
        delete.setOnClickListener(listener);
        properties.setOnClickListener(listener);

        dialog.setOnDismissListener(listener);
        dialog.setOnShowListener(listener);
    }

    @Override
    public void setOfflineChecked(boolean checked) {
        offlineSwitch.setChecked(checked);
    }

    @Override
    public boolean isOfflineChecked() {
        return offlineSwitch.isChecked();
    }
}
