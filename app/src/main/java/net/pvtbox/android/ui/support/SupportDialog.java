package net.pvtbox.android.ui.support;

import android.content.Context;
import android.os.Handler;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatButton;

import net.pvtbox.android.R;
import net.pvtbox.android.api.AuthHttpClient;

import java.util.Objects;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;


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
public class SupportDialog {
    private final AlertDialog dialog;
    private final Context context;
    private final AuthHttpClient httpClient;

    @Nullable
    @BindView(R.id.send_button)
    AppCompatButton sendButton;
    @Nullable
    @BindView(R.id.support_message_edit_text)
    EditText supportMessage;
    @Nullable
    @BindView(R.id.support_subject_spinner)
    Spinner subjectSpinner;

    public SupportDialog(@NonNull Context context, AuthHttpClient httpClient, Runnable onDismissed) {
        this.context = context;
        this.httpClient = httpClient;

        LayoutInflater inflater = (LayoutInflater)
                context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = Objects.requireNonNull(inflater).inflate(R.layout.support_fragment, null);
        ButterKnife.bind(this, view);

        dialog = new AlertDialog.Builder(new ContextThemeWrapper(context, R.style.AppTheme))
                .setView(view)
                .create();
        WindowManager.LayoutParams attributes = Objects.requireNonNull(
                dialog.getWindow()).getAttributes();
        attributes.gravity = Gravity.BOTTOM;
        dialog.getWindow().setAttributes(attributes);
        dialog.setOnDismissListener(v -> onDismissed.run());
    }

    public void show() {
        dialog.show();
    }


    @OnClick(R.id.send_button)
    void onSendClicked() {
        Handler h = new Handler();
        SupportSendHelper.send(
                context, Objects.requireNonNull(supportMessage),
                Objects.requireNonNull(subjectSpinner), httpClient,
                () -> h.post(dialog::dismiss));
    }
}
