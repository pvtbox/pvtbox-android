package net.pvtbox.android.ui.files.menu;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.appcompat.widget.SwitchCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import net.pvtbox.android.R;
import net.pvtbox.android.service.OperationService;
import net.pvtbox.android.ui.files.ModeProvider;
import net.pvtbox.android.ui.files.SelectionProvider;

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
public class MultiSelectMenuDialog implements DismissibleDialog, OfflineSwitch {
    private final int colorEnabled;
    private final int colorDisabled;
    private final PopupWindow dialog;

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

    private TextView deleteText;

    private final View anchor;
    private final ModeProvider modeProvider;
    private final SelectionProvider selectionProvider;
    private final OnMenuListener listener;
    private boolean active;


    public MultiSelectMenuDialog(@NonNull Context context,
                                 View anchor,
                                 ModeProvider modeProvider,
                                 SelectionProvider selectionProvider,
                                 OnMenuListener listener) {
        this.anchor = anchor;
        this.modeProvider = modeProvider;
        this.selectionProvider = selectionProvider;
        this.listener = listener;

        LayoutInflater inflater = (LayoutInflater)
                context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View menu = Objects.requireNonNull(inflater).inflate(R.layout.multi_select_menu_dialog, null);

        dialog = new PopupWindow(
                menu,
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                true);
        dialog.setOutsideTouchable(true);
        dialog.setTouchable(true);
        dialog.setBackgroundDrawable(new ColorDrawable(
                ContextCompat.getColor(context, R.color.monsoon_transparent)));
        dialog.setElevation(10);

        bindViews(menu);
        setCallbacks(menu);

        colorDisabled = ContextCompat.getColor(context, R.color.monsoon_light);
        colorEnabled = ContextCompat.getColor(context, R.color.black);
    }

    public void show() {
        active = true;
        boolean isRecent = modeProvider.isRecent();
        boolean isDowloads = modeProvider.isDownloads();

        offline.setVisibility(isDowloads ? View.GONE : View.VISIBLE);
        copy.setVisibility(isRecent || isDowloads ? View.GONE : View.VISIBLE);
        move.setVisibility(isRecent || isDowloads ? View.GONE : View.VISIBLE);
        download.setVisibility(isDowloads ? View.GONE : View.VISIBLE);
        cancelDownloads.setVisibility(isDowloads ? View.VISIBLE : View.GONE);

        enableOrDisableActions();

        dialog.showAsDropDown(anchor);
    }

    private void enableOrDisableActions() {
        boolean isAllSelectedOffline = selectionProvider.isAllSelectedOffline();
        boolean isAllSelectedNotOffline = selectionProvider.isAllSelectedNotOffline();

        if (selectionProvider.getSelectedCount() == 0 || OperationService.isProcessing()) {
            offlineText.setTextColor(colorDisabled);
            downloadText.setTextColor(colorDisabled);
            copyText.setTextColor(colorDisabled);
            moveText.setTextColor(colorDisabled);
            cancelDownloadsText.setTextColor(colorDisabled);
            deleteText.setTextColor(colorDisabled);
            offlineSwitch.setChecked(false);
            offlineSwitch.setEnabled(false);
        } else {
            downloadText.setTextColor(colorEnabled);
            copyText.setTextColor(colorEnabled);
            moveText.setTextColor(colorEnabled);
            cancelDownloadsText.setTextColor(colorEnabled);
            deleteText.setTextColor(colorEnabled);
            offlineSwitch.setChecked(isAllSelectedOffline);
            offlineSwitch.setEnabled(
                    isAllSelectedOffline || isAllSelectedNotOffline);
            offlineText.setTextColor(
                    isAllSelectedOffline || isAllSelectedNotOffline ? colorEnabled : colorDisabled);
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

    private void bindViews(@NonNull View menu) {
        offline = menu.findViewById(R.id.action_create_offline_copy);
        offlineSwitch = menu.findViewById(R.id.action_create_offline_copy_switch);
        offlineText = menu.findViewById(R.id.action_create_offline_copy_text);

        download = menu.findViewById(R.id.action_download_to);
        downloadText = menu.findViewById(R.id.action_download_to_text);

        copy = menu.findViewById(R.id.action_copy);
        copyText = menu.findViewById(R.id.action_copy_text);

        move = menu.findViewById(R.id.action_move);
        moveText = menu.findViewById(R.id.action_move_text);

        cancelDownloads = menu.findViewById(R.id.action_cancel);
        cancelDownloadsText = menu.findViewById(R.id.action_cancel_text);

        deleteText = menu.findViewById(R.id.action_delete_text);
    }

    @SuppressWarnings("SameReturnValue")
    @SuppressLint("ClickableViewAccessibility")
    private void setCallbacks(@NonNull View menu) {
        menu.findViewById(R.id.action_select_all)
                .setOnClickListener(listener);
        menu.findViewById(R.id.action_cancel_selection)
                .setOnClickListener(listener);
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
        cancelDownloads.setOnClickListener(listener);
        menu.findViewById(R.id.action_delete)
                .setOnClickListener(listener);
    }

    // implements OfflineSwitch

    public boolean isOfflineChecked() {
        return offlineSwitch.isChecked();
    }

    public void setOfflineChecked(boolean checked) {
        offlineSwitch.setChecked(checked);
    }
}
