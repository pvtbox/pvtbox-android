package net.pvtbox.android.ui.collaboration;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import net.pvtbox.android.R;
import net.pvtbox.android.tools.JSON;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Objects;

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
class CollaborationAdapter extends RecyclerView.Adapter<CollaborationAdapter.ViewHolder>  {
    private final Context context;
    private final CollaborationPresenter collaborationPresenter;
    private CollaborationMenuDialog collaborationMenu;
    private JSONArray colleagues;

    CollaborationAdapter(Context context,
                         CollaborationPresenter collaborationPresenter) {
        this.context = context;
        this.collaborationPresenter = collaborationPresenter;
    }

    void setCollaborationItemList(
            JSONArray colleagues) {
        this.colleagues = colleagues == null ? new JSONArray() : colleagues;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(
                R.layout.collaboration_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        JSONObject info = colleagues.optJSONObject(position);
        if (info == null) {
            holder.itemView.setVisibility(View.GONE);
            return;
        } else {
            holder.itemView.setVisibility(View.VISIBLE);
        }

        String mail = JSON.optString(info,"email", "");
        boolean isSelf = Objects.equals(mail, collaborationPresenter.getSelfMail());
        if (isSelf) {
            mail = String.format(context.getString(R.string.self), mail);
        }
        Objects.requireNonNull(
                holder.statusTextView).setText(JSON.optString(info, "status", ""));
        Objects.requireNonNull(holder.mailTextView).setText(mail);

        String permission = JSON.optString(info,"access_type", "");
        if (Objects.equals(permission, "owner")) {
            Objects.requireNonNull(holder.permissionCanTextView).setVisibility(View.GONE);
        } else {
            Objects.requireNonNull(holder.permissionCanTextView).setVisibility(View.VISIBLE);
        }
        Objects.requireNonNull(holder.permissionTextView).setText(permission);
        if (collaborationPresenter.isOwner() || isSelf) {
            Objects.requireNonNull(holder.permissionLayout).setTag(info);
            holder.permissionLayout.setOnClickListener(this::onMenuClick);
        }
    }

    private void onMenuClick(@NonNull View view) {
        JSONObject info = (JSONObject) view.getTag();
        collaborationMenu.show(
                JSON.optString(info, "email"),
                JSON.optString(info, "colleague_id"),
                JSON.optString(info, "access_type"));
    }

    @Override
    public int getItemCount() {
        if (colleagues == null) {
            return 0;
        }
        return colleagues.length();
    }

    public void setMenu(CollaborationMenuDialog collaborationMenu) {
        this.collaborationMenu = collaborationMenu;
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        @Nullable
        @BindView(R.id.collaboration_item_mail_text_view)
        TextView mailTextView;
        @Nullable
        @BindView(R.id.collaboration_item_status_text_view)
        TextView statusTextView;
        @Nullable
        @BindView(R.id.collaboration_permission_text_view)
        TextView permissionTextView;
        @Nullable
        @BindView(R.id.collaboration_permission_can_text)
        View permissionCanTextView;
        @Nullable
        @BindView(R.id.collaboration_item_permission_layout)
        View permissionLayout;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ButterKnife.bind(this, itemView);
        }
    }
}
