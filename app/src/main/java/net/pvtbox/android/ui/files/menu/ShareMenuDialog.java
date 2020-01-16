package net.pvtbox.android.ui.files.menu;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import net.pvtbox.android.R;
import net.pvtbox.android.application.Const;
import net.pvtbox.android.service.PreferenceService;
import net.pvtbox.android.ui.files.CurrentFolderProvider;

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

public class ShareMenuDialog implements DismissibleDialog {
    private static final String TAG = ShareMenuDialog.class.getSimpleName();

    private final PreferenceService preferenceService;
    @NonNull
    private final BottomSheetDialog dialog;
    private final CurrentFolderProvider currentFolderProvider;
    private final OnMenuListener listener;
    private final int colorDisabled;
    private final int colorEnabled;

    private TextView collaborateText;
    private TextView getLinkText;

    public ShareMenuDialog(
            @NonNull Context context, PreferenceService preferenceService,
            CurrentFolderProvider currentFolderProvider, OnMenuListener listener) {
        this.preferenceService = preferenceService;
        this.currentFolderProvider = currentFolderProvider;
        this.listener = listener;

        LayoutInflater inflater = (LayoutInflater)
                context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        assert inflater != null;
        View menu = Objects.requireNonNull(inflater).inflate(R.layout.share_menu_dialog, null);

        dialog = new BottomSheetDialog(context);
        dialog.setContentView(menu);

        bindViews(menu);
        setCallbacks(menu);

        colorDisabled = ContextCompat.getColor(context, R.color.monsoon_light);
        colorEnabled = ContextCompat.getColor(context, R.color.black);
    }

    public void show() {
        Log.d(TAG, "show");
        collaborateText.setTextColor(
                currentFolderProvider.getCurrentFolder() == null && !Objects.equals(
                        preferenceService.getLicenseType(), Const.FREE_LICENSE) ?
                            colorEnabled : colorDisabled);
        getLinkText.setTextColor(
                Objects.equals(preferenceService.getLicenseType(), Const.FREE_LICENSE) ?
                        colorDisabled : colorEnabled);
        dialog.show();
    }

    private void bindViews(@NonNull View menu) {
        collaborateText = menu.findViewById(R.id.action_collaborate_text);
        getLinkText = menu.findViewById(R.id.action_get_link_text);
    }

    private void setCallbacks(@NonNull View menu) {
        menu.findViewById(R.id.action_get_link)
                .setOnClickListener(listener);
        menu.findViewById(R.id.action_collaborate)
                .setOnClickListener(listener);
        dialog.setOnDismissListener(listener);
    }

    @Override
    public void dismiss(int dismissTimeout) {
        dialog.dismiss();
    }
}
