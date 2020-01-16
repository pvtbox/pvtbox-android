package net.pvtbox.android.ui.getlink;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import net.pvtbox.android.R;
import net.pvtbox.android.api.ShareHttpClient;
import net.pvtbox.android.application.Const;
import net.pvtbox.android.db.DataBaseService;
import net.pvtbox.android.db.model.FileRealm;
import net.pvtbox.android.service.PreferenceService;
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
public class GetLinkPresenterImpl implements GetLinkPresenter {
    private static final String TAG = GetLinkPresenterImpl.class.getSimpleName();
    private final Handler handler = new Handler();
    private final GetLinkView getLinkView;
    @Nullable
    private String link;
    private FileRealm file;
    private final Context context;

    @Nullable
    private String password = "";
    private boolean passwordSet;
    private int expire = 0;
    private boolean linkCreated = false;
    private final PreferenceService preferenceService;
    private final ShareHttpClient shareHttpClient;
    private final DataBaseService dataBaseService;

    public GetLinkPresenterImpl(
            ShareHttpClient shareHttpClient, PreferenceService preferenceService,
            Context context, GetLinkView getLinkView, DataBaseService dataBaseService) {
        this.shareHttpClient = shareHttpClient;
        this.preferenceService = preferenceService;
        this.context = context;
        this.getLinkView = getLinkView;
        this.dataBaseService = dataBaseService;
    }

    @Override
    public void setData(FileRealm file) {
        this.file = file;
    }

    @Override
    public FileRealm getData() {
        return file;
    }


    @Override
    public void onStart() {
        if (file.isShared()) {
            String shareLink = file.getShareLink();
            passwordSet = file.isShareSecured();
            password = passwordSet ? null : "";
            expire = file.getShareExpire();
            getLinkView.showLink(shareLink);
            getLinkView.hideCreateLinkButton();
            link = file.getShareLink();
            linkCreated = true;
        } else {
            linkCreated = false;
            passwordSet = false;
            expire = 0;
        }
    }

    @Override
    public void clickCreateLink(boolean keepPassword) {
        Intent intent = new Intent(Const.OPERATIONS_PROGRESS_INTENT);
        intent.putExtra(Const.OPERATIONS_PROGRESS_SHOW_AND_DISMISS, false);
        intent.putExtra(Const.OPERATIONS_PROGRESS_MESSAGE,
                linkCreated ? context.getString(R.string.share_update_progress) :
                        context.getString(R.string.create_link_progress));
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
        getLinkView.hideCreateLinkButton();

        shareHttpClient.sharingEnable(
                file.getUuid(), expire, keepPassword ? null : password, keepPassword,
                response -> handler.post(() -> {
                    intent.putExtra(Const.OPERATIONS_PROGRESS_SHOW_AND_DISMISS, true);
                    intent.putExtra(Const.OPERATIONS_PROGRESS_MESSAGE,
                            linkCreated ?
                                    context.getString(R.string.share_updated_successfully) :
                                    context.getString(R.string.link_created_successfully));
                    LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
                    JSONObject data = response.optJSONObject("data");
                    assert data != null;
                    link = JSON.optString(data, "share_link");
                    getLinkView.showLink(link);
                    dataBaseService.saveShareLink(data);
                    linkCreated = true;
                    password = passwordSet ? null : "";
                    file.setShareSecured(passwordSet);
                    file.setShared(true);
                    file.setShareLink(link);
                    file.setShareExpire(expire);
                }),
                error -> handler.post(() -> {
                    onError(error);
                    try {
                        getLinkView.showCreateButton();
                        passwordSet = file.isShareSecured();
                        password = passwordSet ? null : "";
                        expire = file.getShareExpire();
                        getLinkView.updatePasswordSwitch();
                    } catch (Exception e) {
                        Log.w(TAG, "clickCreateLink: ", e);
                    }
                }));
    }

    @Override
    public void changeDataSwitch(boolean checked) {
        if (checked) {
            if (Objects.equals(preferenceService.getLicenseType(), Const.FREE_LICENSE)) {
                Toast.makeText(context, R.string.not_available_for_free_license, Toast.LENGTH_LONG).show();
                getLinkView.updateExpireSwitch();
                return;
            }
            getLinkView.showExpireDialog();
        } else {
            setExpire(0);
        }
    }

    @Override
    public void changePasswordSwitch(boolean isChecked) {
        if (isChecked) {
            if (Objects.equals(preferenceService.getLicenseType(), Const.FREE_LICENSE)) {
                Toast.makeText(context, R.string.not_available_for_free_license, Toast.LENGTH_LONG).show();
                getLinkView.updatePasswordSwitch();
                return;
            }
            getLinkView.showPasswordDialog();
        } else {
            setPassword("");
        }
    }

    @Override
    public void onClickCopy() {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            return;
        }
        ClipData clip = ClipData.newPlainText(link, link);
        clipboard.setPrimaryClip(clip);
        Intent intent = new Intent(Const.OPERATIONS_PROGRESS_INTENT);
        intent.putExtra(Const.OPERATIONS_PROGRESS_SHOW_AND_DISMISS, true);
        intent.putExtra(Const.OPERATIONS_PROGRESS_MESSAGE,
                context.getString(
                        R.string.link_copied_successfully));
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    @Override
    public void onClickSend() {
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, link);
        sendIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        sendIntent.setType("text/plain");
        context.startActivity(sendIntent);
    }

    @Override
    public void onClickRemove() {
        Intent intent = new Intent(Const.OPERATIONS_PROGRESS_INTENT);
        intent.putExtra(Const.OPERATIONS_PROGRESS_SHOW_AND_DISMISS, false);
        intent.putExtra(Const.OPERATIONS_PROGRESS_MESSAGE,
                context.getString(R.string.share_cancel_progress));
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);


        shareHttpClient.sharingDisable(
                file.getUuid(),
                response -> handler.post(() -> {
                    intent.putExtra(Const.OPERATIONS_PROGRESS_SHOW_AND_DISMISS, true);
                    intent.putExtra(Const.OPERATIONS_PROGRESS_MESSAGE,
                            context.getString(R.string.share_cancelled_successfully));
                    LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
                    try {
                        dataBaseService.disableShare(file.getUuid());
                        getLinkView.hideLink();
                        linkCreated = false;
                        passwordSet = false;
                        file.setShareLink(null);
                        file.setShared(false);
                        file.setShareSecured(false);
                    } catch (Exception e) {
                        Log.w(TAG, "onClickRemove: ", e);
                    }
                }),
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

    @Override
    public void setPassword(@NonNull String password) {
        Log.d(TAG, String.format("setPassword: %s", password));
        this.password = password;
        passwordSet = !password.isEmpty();
        getLinkView.updatePasswordSwitch();
        clickCreateLink(false);
    }


    @Override
    public boolean isPasswordSet() {
        return passwordSet;
    }

    @Override
    public boolean isExpireSet() {
        return expire != 0;
    }

    @Override
    public void setExpire(int ttl) {
        expire = ttl;
        getLinkView.updateExpireSwitch();
        clickCreateLink(true);
    }
}
