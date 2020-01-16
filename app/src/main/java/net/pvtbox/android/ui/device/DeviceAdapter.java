package net.pvtbox.android.ui.device;

import android.annotation.SuppressLint;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import net.pvtbox.android.R;
import net.pvtbox.android.db.model.DeviceRealm;
import net.pvtbox.android.tools.SpeedTool;
import net.pvtbox.android.tools.diskspace.DiskSpace;
import net.pvtbox.android.tools.diskspace.DiskSpaceTool;

import java.text.DecimalFormat;
import java.util.Objects;

import butterknife.BindView;
import butterknife.ButterKnife;
import io.realm.OrderedRealmCollection;
import io.realm.RealmRecyclerViewAdapter;


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
public class DeviceAdapter extends RealmRecyclerViewAdapter<DeviceRealm, DeviceAdapter.ViewHolder> {
    private final String inUse;
    private final Context context;
    @NonNull
    private final DecimalFormat decimalFormat = new DecimalFormat("#.##");
    private final DeviceManageMenu menu;

    public DeviceAdapter(
            @NonNull Context context, OrderedRealmCollection<DeviceRealm> devices,
            DeviceManageMenu menu) {
        super(devices, false, false);
        this.context = context;
        this.menu = menu;
        inUse = context.getString(R.string.space_in_use);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.device_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        boolean online = false;
        DeviceRealm ownDevice = getItem(0);
        if (ownDevice != null) {
            online = ownDevice.isOnline();
        }
        DeviceRealm device = getItem(position);
        if (device == null) {
            holder.itemView.setVisibility(View.GONE);
            return;
        } else {
            holder.itemView.setVisibility(View.VISIBLE);
        }
        device = device.getRealm().copyFromRealm(device);
        setTypeImage(holder, device.getDeviceType());
        Objects.requireNonNull(holder.deviceItemName).setText(device.getName());
        Objects.requireNonNull(holder.deviceItemOsTextView).setText(device.getOs());
        setOsImage(holder, device.getOsType());
        Objects.requireNonNull(holder.deviceItemDownloadSpeedTextView).setText(buildDownloadSpeedString(device));
        Objects.requireNonNull(holder.deviceItemUploadSpeedTextView).setText(buildUploadSpeedString(device));
        Objects.requireNonNull(holder.deviceItemSpaceInUseTextView).setText(buildSpaceString(device));

        Objects.requireNonNull(holder.deviceItemStatusDetails).setVisibility(
                device.isOwn() ? View.VISIBLE : online ? View.VISIBLE : View.GONE);
        holder.itemView.setOnClickListener(
                view -> menu.show(getItem(position)));
        if (device.isOwn() && !online) {
            device.setStatus(R.string.connecting_status);
            device.setOnline(false);
        } else if (!online || !device.isOnline()) {
            device.setOnline(false);
            device.setStatus(device.getStatus() == R.string.logged_out_status ||
                    device.getStatus() == R.string.wiped_status ?
                    device.getStatus() : R.string.power_off_status);
        } else if (device.isOwn() && device.isPaused()) {
            device.setStatus(R.string.paused_status);
        }
        setStatus(holder, device);
        setOnline(holder, device);

        if (position % 2 == 0 ) {
            holder.itemView.setBackgroundColor(ContextCompat.getColor(context,R.color.white));
        } else {
            int color = ContextCompat.getColor(context, R.color.background_color_property_file);
            holder.itemView.setBackgroundColor(color);
        }
    }

    private void setStatus(@NonNull ViewHolder holder, @NonNull DeviceRealm device) {
        int color;
        switch (device.getStatus()) {
            case R.string.synced_status:
                color = R.color.green;
                break;
            case R.string.logged_out_status:
            case R.string.power_off_status:
            case R.string.connecting_status:
            case R.string.wiped_status:
                color = R.color.aluminum;
                break;
            case R.string.paused_status:
            case R.string.indexing_status:
                color = R.color.primary_dark;
                break;
            default:
                device.setStatus(R.string.syncing_status);
                color = R.color.primary_dark;
                break;
        }
        Objects.requireNonNull(holder.deviceItemStatusText).setText(device.getStatus());
        holder.deviceItemStatusText.setTextColor(ContextCompat.getColor(context, color));
    }

    @NonNull
    @SuppressLint("DefaultLocale")
    private String buildSpaceString(@NonNull DeviceRealm device) {
        DiskSpace space = DiskSpaceTool.getDiskQuantity(device.getDiskUsage());
        return inUse + " " + String.format("%.2f", space.getSize()) + " " + context.getString(space.getIdQuantity());
    }

    @NonNull
    private String buildDownloadSpeedString(@NonNull DeviceRealm device) {
        SpeedTool.Speed speed = SpeedTool.getSpeed(device.getDownloadSpeed());

        String format = decimalFormat.format(speed.getSpeed());
        return format + " " + context.getString(speed.getIdQuantity());
    }

    @NonNull
    private String buildUploadSpeedString(@NonNull DeviceRealm device) {
        SpeedTool.Speed speed = SpeedTool.getSpeed(device.getUploadSpeed());
        String format = decimalFormat.format(speed.getSpeed());
        return format + " " + context.getString(speed.getIdQuantity());
    }

    private void setOnline(@NonNull ViewHolder holder, @NonNull DeviceRealm device) {
        Objects.requireNonNull(holder.deviceItemOnlineImageView).setImageResource(
                device.isOnline()
                        ? R.drawable.online_circle
                        : R.drawable.offline_circle
        );
    }

    private void setTypeImage(@NonNull ViewHolder holder, @NonNull String type) {
        int icon;
        switch (type.toLowerCase()) {
            case "phone":
            case "tablet":
                icon = R.drawable.smartphone;
                break;
            default:
                icon = R.drawable.pc;
                break;
        }
        Objects.requireNonNull(holder.deviceItemTypeImageView).setImageResource(icon);
    }

    private void setOsImage(@NonNull ViewHolder holder, @NonNull String os) {
        int icon;
        switch (os.toLowerCase()) {
            case "linux":
                icon = R.drawable.gnu;
                break;
            case "windows":
                icon = R.drawable.windows;
                break;
            case "mac":
            case "ios":
            case "darwin":
                icon = R.drawable.apple;
                break;
            case "android":
                icon = R.drawable.android;
                break;
            default:
                icon = R.drawable.devices_menu_icon;
                break;
        }
        Objects.requireNonNull(holder.deviceItemOsImageView).setImageResource(icon);
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        @Nullable
        @BindView(R.id.device_item_type_image_view)
        ImageView deviceItemTypeImageView;
        @Nullable
        @BindView(R.id.device_item_name)
        TextView deviceItemName;
        @Nullable
        @BindView(R.id.device_item_os_image_view)
        ImageView deviceItemOsImageView;
        @Nullable
        @BindView(R.id.device_item_os_text_view)
        TextView deviceItemOsTextView;
        @Nullable
        @BindView(R.id.device_item_status_text)
        TextView deviceItemStatusText;
        @Nullable
        @BindView(R.id.device_item_status_details)
        View deviceItemStatusDetails;
        @Nullable
        @BindView(R.id.device_item_space_in_use_text_view)
        TextView deviceItemSpaceInUseTextView;
        @Nullable
        @BindView(R.id.device_item_download_speed_text_view)
        TextView deviceItemDownloadSpeedTextView;
        @Nullable
        @BindView(R.id.device_item_upload_speed_text_view)
        TextView deviceItemUploadSpeedTextView;
        @Nullable
        @BindView(R.id.device_item_online_image_view)
        ImageView deviceItemOnlineImageView;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ButterKnife.bind(this, itemView);
        }
    }
}
