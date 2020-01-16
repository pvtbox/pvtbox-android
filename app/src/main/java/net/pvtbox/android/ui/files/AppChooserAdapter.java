package net.pvtbox.android.ui.files;

import android.app.DialogFragment;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import net.pvtbox.android.R;
import net.pvtbox.android.db.model.FileRealm;
import net.pvtbox.android.tools.FileTool;

import java.net.URLConnection;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;

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
public class AppChooserAdapter extends RecyclerView.Adapter<AppChooserAdapter.ViewHolder> {
    private static final String TAG = AppChooserAdapter.class.getSimpleName();
    private final List<AppItem> appItemList;
    private final FileRealm file;
    private final FileTool fileTool;
    private final Context context;
    private final DialogFragment dialogFragment;

    public AppChooserAdapter(List<AppItem> appItemList,
                             FileRealm fileItem,
                             FileTool fileTool,
                             Context context, DialogFragment dialogFragment) {
        this.appItemList = appItemList;
        this.file = fileItem;
        this.fileTool = fileTool;
        this.context = context;
        this.dialogFragment = dialogFragment;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater
                .from(parent.getContext())
                .inflate(R.layout.app_choose_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AppItem appItem = appItemList.get(position);
        assert holder.nameTextView != null;
        holder.nameTextView.setText(appItem.getName());
        assert holder.iconImageView != null;
        holder.iconImageView.setImageDrawable(appItem.getDrawable());
        holder.itemView.setTag(appItem);
        holder.itemView.setOnClickListener(this::onClick);
    }

    private void onClick(@NonNull View view) {
        AppItem appItem = (AppItem) view.getTag();
        String name = file.getName();
        String path = file.getPath();
        ResolveInfo ri = appItem.getResolveInfo();

        if (ri == null) {
            fileTool.openFile(name, path);
            dialogFragment.dismiss();
            return;
        }

        String truePath = FileTool.isExist(path) ? path : file.getCameraPath();
        if (truePath == null || truePath.isEmpty()) {
            Log.e(TAG, "onClick: path does not exist");
            dialogFragment.dismiss();
            return;
        }

        Intent intent = new Intent();
        String packageName = ri.activityInfo.packageName;

        intent.setComponent(new ComponentName(packageName, ri.activityInfo.name));
        intent.setAction(Intent.ACTION_VIEW);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setDataAndType(
                Uri.parse("file://" + truePath), URLConnection.guessContentTypeFromName(path));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setPackage(packageName);
        context.startActivity(intent);

        dialogFragment.dismiss();
    }

    @Override
    public int getItemCount() {
        if (appItemList == null) {
            return 0;
        } else {
            return appItemList.size();
        }
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        @Nullable
        @BindView(R.id.icon_image_view)
        ImageView iconImageView;
        @Nullable
        @BindView(R.id.name_text_view)
        TextView nameTextView;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ButterKnife.bind(this, itemView);
        }
    }
}
