package net.pvtbox.android.ui.files.dialog;

import android.content.Context;
import android.content.DialogInterface;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import android.view.ContextThemeWrapper;

import net.pvtbox.android.R;

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

public class UpdateFileDialog {
    @NonNull
    private final AlertDialog dialog;
    @NonNull
    private final String positiveButtonText;
    @NonNull
    private final String negativeButtonText;

    public UpdateFileDialog(@NonNull Context context) {
        AlertDialog.Builder builder = new AlertDialog.Builder(
                new ContextThemeWrapper(context, R.style.AppTheme));
        builder.setMessage(R.string.this_file_not_actual_do_want_download_new);
        positiveButtonText = context.getString(R.string.download);
        negativeButtonText = context.getString(R.string.open);
        dialog = builder.create();
    }

    public void show(
            DialogInterface.OnClickListener openAction,
            DialogInterface.OnClickListener downloadAction) {
        dialog.setButton(DialogInterface.BUTTON_POSITIVE, positiveButtonText, downloadAction);
        dialog.setButton(DialogInterface.BUTTON_NEGATIVE, negativeButtonText, openAction);
        dialog.show();
    }
}
