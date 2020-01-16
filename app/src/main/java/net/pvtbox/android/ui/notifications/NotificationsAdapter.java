package net.pvtbox.android.ui.notifications;

import android.content.Context;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import net.pvtbox.android.R;
import net.pvtbox.android.tools.JSON;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.DateFormat;
import java.util.Locale;
import java.util.Objects;

import butterknife.BindView;
import butterknife.ButterKnife;

import static java.lang.Math.min;

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
class NotificationsAdapter extends RecyclerView.Adapter<NotificationsAdapter.ViewHolder> {
    private final Context context;
    private final NotificationsListener listener;
    @NonNull
    private JSONArray notifications = new JSONArray();

    NotificationsAdapter(Context context, NotificationsListener listener) {
        this.context = context;
        this.listener = listener;
    }

    void add(@NonNull JSONArray newNotifications) {
        int count = notifications.length();

        for (int i = 0; i < newNotifications.length(); ++i) {
            JSONObject notif = newNotifications.optJSONObject(i);
            if (notif != null) {
                notifications.put(notif);
            }
        }
        notifyItemRangeInserted(count, newNotifications.length());
    }

    void clear() {
        notifications = new JSONArray();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(
                R.layout.notification_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        JSONObject notification = notifications.optJSONObject(position);
        if (notification == null) {
            return;
        }
        Objects.requireNonNull(holder.date).setText(DateFormat.getDateTimeInstance(
                DateFormat.LONG, DateFormat.SHORT,
                Locale.getDefault()).format(
                notification.optLong("timestamp", 0) * 1000));

        String s = JSON.optString(notification, "text", "");
        JSONArray search = notification.optJSONArray("search");
        JSONArray replace = notification.optJSONArray("replace");
        if (search != null && replace != null) {
            for (int i = 0; i < min(search.length(), replace.length()); ++i) {
                String searchStr = JSON.optString(search, i, "");
                String replaceStr = JSON.optString(replace, i, "");
                try {
                    if ("{folder_name}".equals(searchStr)) {
                        notification.put("folder_name", replaceStr);
                    } else if ("{colleague_id}".equals(searchStr)) {
                        notification.put("colleague_id", replaceStr);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                String replacement = String.format(
                        "<font color=\"#FC914D\">%s</font>",
                        replaceStr);
                s = Objects.requireNonNull(s).replace(searchStr, replacement);
            }
        }
        String replacement = "<font color=\"#01AB33\">You</font> ";
        s = Objects.requireNonNull(s).replace("You ", replacement);
        Objects.requireNonNull(holder.text).setText(Html.fromHtml(s));

        if (notification.optBoolean("read")) {
            holder.itemView.setBackgroundColor(ContextCompat.getColor(context, R.color.white));
        } else {
            int color = ContextCompat.getColor(context, R.color.background_color_property_file);
            holder.itemView.setBackgroundColor(color);
        }

        holder.itemView.setTag(position);

        holder.itemView.setOnClickListener(this::onClicked);
    }

    private void onClicked(@NonNull View view) {
        int position = (Integer) view.getTag();
        JSONObject notification = notifications.optJSONObject(position);
        switch (Objects.requireNonNull(JSON.optString(notification, "action", ""))) {
            case "collaboration_join":
            case "collaboration_include":
                listener.openFolder(JSON.optString(notification, "folder_name", ""));
                break;
            case "collaboration_invite":
                listener.joinCollaboration(notification.optInt("colleague_id", 0));
                break;
        }
        if (!notification.optBoolean("read")) {
            try {
                notification.put("read", true);
            } catch (JSONException e) {
                e.printStackTrace();
                return;
            }
            notifyItemChanged(position);
        }
    }

    @Override
    public int getItemCount() {
        return notifications.length();
    }

    long getLastItemId() {
        JSONObject lastNotification = notifications.optJSONObject(notifications.length() - 1);
        if (lastNotification == null) {
            return 0;
        }
        return lastNotification.optLong("notification_id", 0);
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        @Nullable
        @BindView(R.id.notification_text)
        TextView text;
        @Nullable
        @BindView(R.id.notification_date)
        TextView date;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ButterKnife.bind(this, itemView);
        }
    }
}
