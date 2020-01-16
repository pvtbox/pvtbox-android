package net.pvtbox.android.ui.files;

import android.content.Context;
import android.content.DialogInterface;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;

import net.pvtbox.android.R;
import net.pvtbox.android.service.OperationService;
import net.pvtbox.android.ui.files.menu.DismissibleDialog;
import net.pvtbox.android.ui.files.menu.OfflineSwitch;
import net.pvtbox.android.ui.files.menu.OnMenuListener;

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
public class FilesMenuActionsHandler implements OnMenuListener {
    private static final String TAG = FilesMenuActionsHandler.class.getSimpleName();

    private final Context context;
    private final SelectionProvider selectionProvider;
    private final SelectionChangeListenerProvider selectionChangeListenerProvider;
    private final MoveActivatedListener moveActivatedListener;
    private final CopyActivatedListener copyActivatedListener;
    private final FilesActionsHandler filesActionsHandler;
    private DismissibleDialog dialog;
    private OfflineSwitch offlineSwitch;
    private boolean disableMultiSelect = true;
    private boolean clearSelected = true;

    public FilesMenuActionsHandler(Context context,
                                   SelectionProvider selectionProvider,
                                   SelectionChangeListenerProvider selectionChangeListenerProvider,
                                   MoveActivatedListener moveActivatedListener,
                                   CopyActivatedListener copyActivatedListener,
                                   FilesActionsHandler filesActionsHandler) {

        this.context = context;
        this.selectionProvider = selectionProvider;
        this.selectionChangeListenerProvider = selectionChangeListenerProvider;
        this.moveActivatedListener = moveActivatedListener;
        this.copyActivatedListener = copyActivatedListener;
        this.filesActionsHandler = filesActionsHandler;
    }

    public void setDialog(DismissibleDialog dialog) {
        this.dialog = dialog;
    }
    public void setOfflineSwitch(OfflineSwitch offlineSwitch) { this.offlineSwitch = offlineSwitch; }

    private void showToast(String message) {
        Toast toast = Toast.makeText(context, message, Toast.LENGTH_LONG);
        toast.show();
    }

    @Override
    public void onClick(@NonNull View view) {
        Log.d(TAG, String.format("onClick view: %s", view));
        boolean dismissMenu = true;
        int dismissTimeout = 0;
        disableMultiSelect = true;
        clearSelected = true;

        int viewId = view.getId();
        if (viewId != R.id.action_select_all &&
                viewId != R.id.action_cancel_selection &&
                viewId != R.id.action_properties &&
                viewId != R.id.action_open_with) {
            if (selectionProvider.getSelectedCount() == 0) {
                showToast(context.getString(R.string.nothing_selected));
                return;
            }
            if (OperationService.isProcessing()) {
                showToast(context.getString(
                        R.string.action_disabled_while_another_acton_performing));
                return;
            }
            if (viewId == R.id.action_create_offline_copy) {
                boolean isAllSelectedOffline = selectionProvider.isAllSelectedOffline();
                boolean isAllSelectedNotOffline = selectionProvider.isAllSelectedNotOffline();
                if (!(isAllSelectedOffline || isAllSelectedNotOffline)) {
                    showToast(context.getString(R.string.error_offline_inappropriate));
                    return;
                }
            }
        }

        switch (viewId) {
            case R.id.action_select_all:
                Objects.requireNonNull(selectionChangeListenerProvider.getSelectionChangeListener()).onSelectAll();
                dismissMenu = false;
                disableMultiSelect = false;
                clearSelected = false;
                break;
            case R.id.action_open_with:
                filesActionsHandler.onOpenWithAction();
                break;
            case R.id.action_share:
                clearSelected = false;
                filesActionsHandler.onShareAction();
                break;
            case R.id.action_create_offline_copy:
                offlineSwitch.setOfflineChecked(!offlineSwitch.isOfflineChecked());
            case R.id.action_create_offline_copy_switch:
                filesActionsHandler.onSwitchOfflineAction(offlineSwitch.isOfflineChecked());
                dismissTimeout = 300;
                break;
            case R.id.action_download_to:
                filesActionsHandler.onDownloadAction();
                clearSelected = false;
                break;
            case R.id.action_copy:
                copyActivatedListener.onCopyActivated();
                clearSelected = false;
                break;
            case R.id.action_move:
                moveActivatedListener.onMoveActivated();
                clearSelected = false;
                break;
            case R.id.action_rename:
                filesActionsHandler.onRenameAction();
                clearSelected = false;
                break;
            case R.id.action_delete:
                filesActionsHandler.onDeleteAction();
                break;
            case R.id.action_cancel:
                filesActionsHandler.onCancelDownloadsAction();
                break;
            case R.id.action_properties:
                filesActionsHandler.onPropertiesAction();
                break;
            default:
                Log.e(TAG, "onMultiSelectMenuItemClick, undefined action");
        }
        if (clearSelected)
            Objects.requireNonNull(selectionChangeListenerProvider.getSelectionChangeListener()).onCancelSelection();
        if (disableMultiSelect)
            Objects.requireNonNull(selectionChangeListenerProvider.getSelectionChangeListener()).onMultiSelectDisabled();
        if (dismissMenu)
            dialog.dismiss(dismissTimeout);
    }

    @Override
    public void onDismiss(DialogInterface dialogInterface) {
        Log.d(TAG, "onDismiss");
        if (clearSelected)
            Objects.requireNonNull(selectionChangeListenerProvider.getSelectionChangeListener()).onCancelSelection();
        if (disableMultiSelect)
            Objects.requireNonNull(selectionChangeListenerProvider.getSelectionChangeListener()).onMultiSelectDisabled();
    }

    @Override
    public void onShow(DialogInterface dialogInterface) {
        disableMultiSelect = true;
        clearSelected = true;
    }
}
