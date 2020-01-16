package net.pvtbox.android.ui.collaboration;

import android.content.Context;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import net.pvtbox.android.R;

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

class CollaborationMenuDialog implements View.OnClickListener {
    private static final String TAG = CollaborationMenuDialog.class.getSimpleName();
    @NonNull
    private final Context context;
    private final CollaborationPresenter collaborationPresenter;
    private final CollaborationActionsHandler actionsHandler;
    private final int colorDisabled;
    private final int colorEnabled;
    private final BottomSheetDialog dialog;

    @Nullable
    @BindView(R.id.can_view)
    View canView;
    @Nullable
    @BindView(R.id.can_view_checkbox)
    CheckBox canViewCheckBox;
    @Nullable
    @BindView(R.id.can_view_text)
    TextView canViewText;
    @Nullable
    @BindView(R.id.can_edit)
    View canEdit;
    @Nullable
    @BindView(R.id.can_edit_checkbox)
    CheckBox canEditCheckBox;
    @Nullable
    @BindView(R.id.can_edit_text)
    TextView canEditText;
    @Nullable
    @BindView(R.id.delete_user)
    View deleteUser;
    @Nullable
    @BindView(R.id.quit_collaboration)
    View quit;
    private boolean isSelf;
    private String id;
    private String email;

    public CollaborationMenuDialog(@NonNull Context context, CollaborationPresenter collaborationPresenter,
                                   CollaborationActionsHandler actionsHandler) {
        this.context = context;
        this.collaborationPresenter = collaborationPresenter;
        this.actionsHandler = actionsHandler;

        LayoutInflater inflater = (LayoutInflater)
                context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View menu = Objects.requireNonNull(inflater).inflate(R.layout.collaboration_menu_dialog, null);
        ButterKnife.bind(this, menu);

        setCallbacks();
        dialog = new BottomSheetDialog(context);
        dialog.setContentView(menu);

        colorDisabled = ContextCompat.getColor(context, R.color.monsoon_light);
        colorEnabled = ContextCompat.getColor(context, R.color.black);
        Objects.requireNonNull(canViewText).setTextColor(colorDisabled);
        Objects.requireNonNull(canViewCheckBox).setEnabled(false);
    }

    private void setCallbacks() {
        Objects.requireNonNull(canEditCheckBox).setOnClickListener(this);
        Objects.requireNonNull(canEdit).setOnClickListener(this);
        Objects.requireNonNull(canViewCheckBox).setOnClickListener(this);
        Objects.requireNonNull(canView).setOnClickListener(this);
        Objects.requireNonNull(deleteUser).setOnClickListener(this);
        Objects.requireNonNull(quit).setOnClickListener(this);
    }

    public void show(String email, String id, String permission) {
        this.email = email;
        this.id = id;

        isSelf = Objects.equals(
                collaborationPresenter.getSelfMail(), email);

        Objects.requireNonNull(deleteUser).setVisibility(isSelf ? View.GONE : View.VISIBLE);
        Objects.requireNonNull(quit).setVisibility(isSelf ? View.VISIBLE : View.GONE);

        Objects.requireNonNull(canView).setVisibility(collaborationPresenter.isOwner() ? View.VISIBLE : View.GONE);
        Objects.requireNonNull(canEdit).setVisibility(collaborationPresenter.isOwner() ? View.VISIBLE : View.GONE);

        if (collaborationPresenter.isOwner()) {
            setCheckBoxes(permission, isSelf);
        }

        dialog.show();
    }

    private void setCheckBoxes(@NonNull String permission, boolean isSelf) {
        if (isSelf) {
            Objects.requireNonNull(canEditCheckBox).setEnabled(false);
            Objects.requireNonNull(canEditText).setTextColor(colorDisabled);
        } else {
            Objects.requireNonNull(canEditCheckBox).setEnabled(true);
            Objects.requireNonNull(canEditText).setTextColor(colorEnabled);
        }
        Objects.requireNonNull(canViewCheckBox).setChecked(true);
        if (permission.equals("view")) {
            canEditCheckBox.setChecked(false);
            return;
        }
        canEditCheckBox.setChecked(true);
    }

    @Override
    public void onClick(@NonNull View view) {
        Log.d(TAG, String.format("onClick: %s", view));
        switch (view.getId()) {
            case R.id.can_view:
            case R.id.can_view_checkbox:
                if (isSelf) {
                    Toast.makeText(context, R.string.can_permission_error_self, Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(context, R.string.can_view_permission_error_other, Toast.LENGTH_LONG).show();
                }
                break;
            case R.id.can_edit:
                if (!isSelf) {
                    Objects.requireNonNull(canEditCheckBox).setChecked(!canEditCheckBox.isChecked());
                }
            case R.id.can_edit_checkbox:
                if (isSelf) {
                    Toast.makeText(context, R.string.can_permission_error_self, Toast.LENGTH_LONG).show();
                } else {
                    String permission = Objects.requireNonNull(canEditCheckBox).isChecked() ? "edit" : "view";
                    actionsHandler.updatePermission(id, permission);
                }
                break;
            case R.id.quit_collaboration:
                if (collaborationPresenter.isOwner()) {
                    actionsHandler.cancel();
                } else {
                    actionsHandler.leave();
                }
                break;
            case R.id.delete_user:
                actionsHandler.deleteUser(id, email);
                break;
        }
        dialog.dismiss();
    }
}
