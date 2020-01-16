package net.pvtbox.android.ui.files.menu;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;

import net.pvtbox.android.R;

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

public class AddMenuDialog implements DismissibleDialog {
    private final BottomSheetDialog dialog;
    private final OnMenuListener listener;

    private final SwitchCompat cameraImportSwitch;
    private boolean cameraImportSwitchTouched;

    public AddMenuDialog(@NonNull Context context, OnMenuListener listener) {
        this.listener = listener;

        LayoutInflater inflater = (LayoutInflater)
                context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        assert inflater != null;
        View menu = inflater.inflate(R.layout.add_menu_dialog, null);

        dialog = new BottomSheetDialog(context);
        dialog.setContentView(menu);
        cameraImportSwitch = menu.findViewById(R.id.action_import_camera_switch);


        setCallbacks(menu);
        if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
            menu.findViewById(R.id.action_take_photo).setVisibility(View.GONE);
        }
    }

    public void switchCameraImport() {
        cameraImportSwitch.setChecked(!cameraImportSwitch.isChecked());
    }

    public void show(boolean importCameraEnabled) {
        cameraImportSwitch.setChecked(importCameraEnabled);
        dialog.show();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setCallbacks(@NonNull View menu) {
        menu.findViewById(R.id.action_new_folder)
                .setOnClickListener(listener);
        menu.findViewById(R.id.action_add_photo)
                .setOnClickListener(listener);
        menu.findViewById(R.id.action_take_photo)
                .setOnClickListener(listener);
        menu.findViewById(R.id.action_add_files)
                .setOnClickListener(listener);
        menu.findViewById(R.id.action_send_files)
                .setOnClickListener(listener);
        menu.findViewById(R.id.action_import_camera)
                .setOnClickListener(listener);
        cameraImportSwitch.setOnTouchListener((view, motionEvent) -> {
            cameraImportSwitchTouched = true;
            return false;
        });
        cameraImportSwitch.setOnClickListener(v -> {
            if (cameraImportSwitchTouched) {
                cameraImportSwitchTouched = false;
                listener.onClick(v);
            }
        });
        cameraImportSwitch.setOnCheckedChangeListener((compoundButton, b) -> {
            if (cameraImportSwitchTouched) {
                cameraImportSwitchTouched = false;
                listener.onClick(compoundButton);
            }
        });
        menu.findViewById(R.id.action_insert_link)
                .setOnClickListener(listener);
    }

    @Override
    public void dismiss(int dismissTimeout) {
        if (dismissTimeout > 0) {
            new Handler().postDelayed(dialog::dismiss, dismissTimeout);
        } else {
            dialog.dismiss();
        }
    }

    public boolean isShowing() {
        return dialog.isShowing();
    }
}
