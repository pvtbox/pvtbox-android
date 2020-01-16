package net.pvtbox.android.ui.device;

import android.content.Context;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import net.pvtbox.android.R;
import net.pvtbox.android.application.Const;
import net.pvtbox.android.db.model.DeviceRealm;
import net.pvtbox.android.service.PreferenceService;
import net.pvtbox.android.ui.files.menu.DismissibleDialog;

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
class DeviceManageMenu implements DismissibleDialog {
    private final PreferenceService preferenceService;
    private final int colorDisabled;
    private final int colorEnabled;
    private final BottomSheetDialog dialog;
    private final DeviceManageMenuListener listener;
    private DeviceRealm device;
    private View details;
    private View removeNode;
    private View logout;
    private View wipe;
    private TextView logoutText;
    private TextView wipeText;

    public DeviceManageMenu(
            @NonNull Context context, PreferenceService preferenceService,
            DeviceManageMenuListener listener) {
        this.preferenceService = preferenceService;
        this.listener = listener;
        colorDisabled = ContextCompat.getColor(context, R.color.monsoon_light);
        colorEnabled = ContextCompat.getColor(context, R.color.black);
        LayoutInflater inflater = (LayoutInflater)
                context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View menu = Objects.requireNonNull(inflater).inflate(R.layout.manage_device_menu, null);
        dialog = new BottomSheetDialog(context);
        dialog.setContentView(menu);
        init(menu);
        setCallbacks();
    }

    public void show(@Nullable DeviceRealm device) {
        if (device == null) { return; }
        this.device = device.getRealm().copyFromRealm(device);
        setup();
        dialog.show();
    }

    private void setup() {
        details.setVisibility(device.isOwn() ? View.VISIBLE : View.GONE);
        removeNode.setVisibility(!device.isOwn() && !device.isOnline() ? View.VISIBLE : View.GONE);
        logout.setVisibility(device.getStatus() != R.string.wiped_status && !device.isWipeInProgress() ?
                View.VISIBLE : View.GONE);
        wipe.setVisibility(device.getStatus() != R.string.wiped_status ? View.VISIBLE : View.GONE);
        if (!device.isOwn() &&
                Objects.equals(preferenceService.getLicenseType(), Const.FREE_LICENSE)) {
            logoutText.setTextColor(colorDisabled);
            wipeText.setTextColor(colorDisabled);
        } else {
            logoutText.setText(device.isLogoutInProgress() ?
                    R.string.logout_node_in_progress : R.string.logout_node);
            wipeText.setText(device.isWipeInProgress() ?
                    R.string.logout_and_wipe_node_in_progress : R.string.logout_and_wipe_node);
            logoutText.setTextColor(device.isLogoutInProgress() ?
                            colorDisabled : colorEnabled);
            wipeText.setTextColor(device.isWipeInProgress() ?
                            colorDisabled : colorEnabled);
        }
    }

    private void init(@NonNull View menu) {
        details = menu.findViewById(R.id.action_details);
        removeNode = menu.findViewById(R.id.action_remove_node);
        logout = menu.findViewById(R.id.action_logout);
        wipe = menu.findViewById(R.id.action_wipe);
        logoutText = menu.findViewById(R.id.action_logout_text);
        wipeText = menu.findViewById(R.id.action_wipe_text);
    }

    private void setCallbacks() {
        details.setOnClickListener(v -> listener.onClick(v, device));
        removeNode.setOnClickListener(v -> listener.onClick(v, device));
        logout.setOnClickListener(v -> listener.onClick(v, device));
        wipe.setOnClickListener(v -> listener.onClick(v, device));
    }

    @Override
    public void dismiss(int dismissTimeout) {
        if (dismissTimeout > 0) {
            new Handler().postDelayed(dialog::dismiss, dismissTimeout);
        } else {
            dialog.dismiss();
        }
    }
}
