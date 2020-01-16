package net.pvtbox.android.ui.support;

import android.content.Context;
import android.content.Intent;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import net.pvtbox.android.R;
import net.pvtbox.android.api.AuthHttpClient;
import net.pvtbox.android.application.Const;
import net.pvtbox.android.tools.JSON;

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
public class SupportSendHelper {
    static void send(
            @NonNull Context context, @NonNull EditText supportMessage,
            @NonNull Spinner subjectSpinner, @NonNull AuthHttpClient httpClient,
            @NonNull Runnable onSent) {
        String text = supportMessage.getText().toString().trim();
        if (text.isEmpty()) {
            Toast.makeText(context, R.string.message_empty, Toast.LENGTH_LONG).show();
            return;
        }
        if (subjectSpinner.getSelectedItemPosition() == 0) {
            Toast.makeText(context, R.string.please_select_subject, Toast.LENGTH_LONG).show();
            return;
        }
        Intent intent = new Intent(Const.OPERATIONS_PROGRESS_INTENT);
        intent.putExtra(Const.OPERATIONS_PROGRESS_SHOW_AND_DISMISS, false);
        intent.putExtra(Const.OPERATIONS_PROGRESS_MESSAGE,
                context.getString(
                        R.string.send_message_to_support_progress));
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);

        String subject = "NONE";
        switch (subjectSpinner.getSelectedItemPosition()) {
            case 1:
                subject = "TECHNICAL";
                break;
            case 2:
                subject = "OTHER";
                break;
            case 3:
                subject = "FEEDBACK";
                break;
        }
        httpClient.support(
                text, subject,
                response -> {
                    intent.putExtra(Const.OPERATIONS_PROGRESS_SHOW_AND_DISMISS, true);
                    intent.putExtra(Const.OPERATIONS_PROGRESS_MESSAGE,
                            context.getString(
                                    R.string.send_message_to_support_successfully));
                    LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
                    onSent.run();
                },
                error -> {
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
                });
    }
}
