package net.pvtbox.android.ui.collaboration;

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.view.ContextThemeWrapper;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import net.pvtbox.android.R;
import net.pvtbox.android.api.CollaborationsHttpClient;
import net.pvtbox.android.application.Const;
import net.pvtbox.android.tools.JSON;

import org.json.JSONObject;

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

class CollaborationActionsHandler {

    private final Activity context;
    private final Handler handler = new Handler();
    private final CollaborationsHttpClient httpClient;
    private final CollaborationPresenter collaborationPresenter;
    private final CollaborationAdapter collaborationAdapter;
    private final String folderUuid;
    private final SwipeRefreshLayout swipe;
    private final QuitListener quitListener;
    private final CollaborationFragment collaborationFragment;

    interface QuitListener {
        void quit();
    }

    CollaborationActionsHandler(
            Activity context, CollaborationsHttpClient httpClient,
            CollaborationPresenter collaborationPresenter, CollaborationAdapter collaborationAdapter,
            String folderUuid, SwipeRefreshLayout swipe, QuitListener quitListener,
            CollaborationFragment collaborationFragment) {
        this.context = context;

        this.httpClient = httpClient;
        this.collaborationPresenter = collaborationPresenter;
        this.collaborationAdapter = collaborationAdapter;
        this.folderUuid = folderUuid;
        this.swipe = swipe;
        this.quitListener = quitListener;
        this.collaborationFragment = collaborationFragment;
    }

    void addColleague(String email, String permission) {
        Intent intent = new Intent(Const.OPERATIONS_PROGRESS_INTENT);
        intent.putExtra(Const.OPERATIONS_PROGRESS_SHOW_AND_DISMISS, false);
        intent.putExtra(Const.OPERATIONS_PROGRESS_MESSAGE,
                context.getString(
                        R.string.add_collaboration_progress));
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);

        httpClient.add(
                folderUuid, email, permission,
                response -> {
                    intent.putExtra(Const.OPERATIONS_PROGRESS_SHOW_AND_DISMISS, true);
                    intent.putExtra(Const.OPERATIONS_PROGRESS_MESSAGE,
                            context.getString(
                                    R.string.add_collaboration_successfully));
                    LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
                    getInfo();
                },
                this::onError);
    }

    private void onError(@Nullable JSONObject error) {
        Intent intent = new Intent(Const.OPERATIONS_PROGRESS_INTENT);

        String networkError = context.getString(R.string.network_error);
        String err = error == null ? networkError : JSON.optString(error,
                "info", error.optJSONObject("info") == null ? networkError :
                        Objects.requireNonNull(error.optJSONObject("info")).optJSONArray("error_file_name") == null ?
                                networkError :
                                JSON.optString(Objects.requireNonNull(error.optJSONObject("info"))
                                                .optJSONArray("error_file_name")
                                        , 0, networkError));
        intent.putExtra(Const.OPERATIONS_PROGRESS_MESSAGE,
                String.format(context.getString(
                        R.string.operation_error), err));
        intent.putExtra(Const.OPERATIONS_PROGRESS_SHOW_AND_DISMISS, true);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    void getInfo() {
        httpClient.info(
                folderUuid,
                response -> handler.post(() -> {
                    swipe.setRefreshing(false);
                    JSONObject data = response.optJSONObject("data");
                    if (data == null) {
                        collaborationFragment.showAddColleagueButton();
                        collaborationAdapter.setCollaborationItemList(null);
                        collaborationAdapter.notifyDataSetChanged();
                        return;
                    }
                    collaborationPresenter.setIsOwner(
                            data.optBoolean("collaboration_is_owner", false));
                    collaborationAdapter.setCollaborationItemList(
                            data.optJSONArray("colleagues"));
                    collaborationAdapter.notifyDataSetChanged();
                    if (collaborationPresenter.isOwner()) {
                        collaborationFragment.showAddColleagueButton();
                    }
                }),
                error -> handler.post(() -> {
                    swipe.setRefreshing(false);
                    collaborationFragment.showAddColleagueButton();
                    collaborationAdapter.setCollaborationItemList(null);
                    collaborationAdapter.notifyDataSetChanged();
                }));
    }

    void deleteUser(String id, String email) {
        AlertDialog.Builder builder = new AlertDialog.Builder(
                new ContextThemeWrapper(context.getWindow().getContext(), R.style.AppTheme));
        builder.setTitle(R.string.are_you_sure)
                .setMessage(context.getString(R.string.collaboration_remove_user_message, email))
                .setPositiveButton(R.string.yes, (dialog, i) -> deleteUserFromCollaboration(id))
                .setNegativeButton(R.string.cancel, (dialog, i) -> {
                });
        builder.show();
    }

    private void deleteUserFromCollaboration(String id) {
        Intent intent = new Intent(Const.OPERATIONS_PROGRESS_INTENT);
        intent.putExtra(Const.OPERATIONS_PROGRESS_SHOW_AND_DISMISS, false);
        intent.putExtra(Const.OPERATIONS_PROGRESS_MESSAGE,
                context.getString(
                        R.string.delete_user_progress));
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);

        httpClient.delete(
                folderUuid, id,
                response -> {
                    intent.putExtra(Const.OPERATIONS_PROGRESS_SHOW_AND_DISMISS, true);
                    intent.putExtra(Const.OPERATIONS_PROGRESS_MESSAGE,
                            context.getString(
                                    R.string.delete_user_successfully));
                    LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
                    getInfo();
                },
                this::onError);
    }

    void leave() {
        AlertDialog.Builder builder = new AlertDialog.Builder(
                new ContextThemeWrapper(context.getWindow().getContext(), R.style.AppTheme));
        builder.setTitle(R.string.are_you_sure)
                .setMessage(R.string.collaboration_leave_message)
                .setPositiveButton(R.string.yes, (dialog, id) -> leaveCollaboration())
                .setNegativeButton(R.string.cancel, (dialog, id) -> {
                });
        builder.show();
    }

    private void leaveCollaboration() {
        Intent intent = new Intent(Const.OPERATIONS_PROGRESS_INTENT);
        intent.putExtra(Const.OPERATIONS_PROGRESS_SHOW_AND_DISMISS, false);
        intent.putExtra(Const.OPERATIONS_PROGRESS_MESSAGE,
                context.getString(
                        R.string.quit_collaboration_progress));
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);

        httpClient.leave(
                folderUuid,
                response -> {
                    intent.putExtra(Const.OPERATIONS_PROGRESS_SHOW_AND_DISMISS, true);
                    intent.putExtra(Const.OPERATIONS_PROGRESS_MESSAGE,
                            context.getString(
                                    R.string.quit_collaboration_successfully));
                    LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
                    handler.post(quitListener::quit);
                },
                this::onError);
    }

    void cancel() {
        AlertDialog.Builder builder = new AlertDialog.Builder(
                new ContextThemeWrapper(context.getWindow().getContext(), R.style.AppTheme));
        builder.setTitle(R.string.are_you_sure)
                .setMessage(R.string.collaboration_cancel_message)
                .setPositiveButton(R.string.yes, (dialog, id) -> cancelCollaboration())
                .setNegativeButton(R.string.cancel, (dialog, id) -> {
                });
        builder.show();
    }

    private void cancelCollaboration() {
        Intent intent = new Intent(Const.OPERATIONS_PROGRESS_INTENT);
        intent.putExtra(Const.OPERATIONS_PROGRESS_SHOW_AND_DISMISS, false);
        intent.putExtra(Const.OPERATIONS_PROGRESS_MESSAGE,
                context.getString(
                        R.string.quit_collaboration_progress));
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
        httpClient.cancel(
                folderUuid,
                response -> {
                    intent.putExtra(Const.OPERATIONS_PROGRESS_SHOW_AND_DISMISS, true);
                    intent.putExtra(Const.OPERATIONS_PROGRESS_MESSAGE,
                            context.getString(
                                    R.string.quit_collaboration_successfully));
                    LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
                    handler.post(quitListener::quit);
                },
                this::onError);
    }

    public void updatePermission(String id, String permission) {
        Intent intent = new Intent(Const.OPERATIONS_PROGRESS_INTENT);
        intent.putExtra(Const.OPERATIONS_PROGRESS_SHOW_AND_DISMISS, false);
        intent.putExtra(Const.OPERATIONS_PROGRESS_MESSAGE,
                context.getString(
                        R.string.update_permission_progress));
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);

        httpClient.edit(
                folderUuid, id, permission,
                response -> {
                    intent.putExtra(Const.OPERATIONS_PROGRESS_SHOW_AND_DISMISS, true);
                    intent.putExtra(Const.OPERATIONS_PROGRESS_MESSAGE,
                            context.getString(
                                    R.string.update_permission_successfully));
                    LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
                    getInfo();
                },
                this::onError);
    }
}
