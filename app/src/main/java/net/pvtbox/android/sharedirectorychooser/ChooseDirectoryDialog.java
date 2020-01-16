package net.pvtbox.android.sharedirectorychooser;

import android.content.Context;
import android.content.DialogInterface;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import android.view.Window;


import yogesh.firzen.filelister.FileListerDialog;

import yogesh.firzen.filelister.OnFileSelectedListener;

import static android.content.DialogInterface.BUTTON_NEGATIVE;
import static android.content.DialogInterface.BUTTON_NEUTRAL;
import static android.content.DialogInterface.BUTTON_POSITIVE;

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

public class ChooseDirectoryDialog {

    private final AlertDialog alertDialog;

    private FilesListerView filesListerView;

    private OnFileSelectedListener onFileSelectedListener;

    private ChooseDirectoryDialog(@NonNull Context context, int themeResId, DialogInterface.OnDismissListener dismissListener, boolean enablePBDir) {
        //super(context, themeResId);
        alertDialog = new AlertDialog.Builder(context, themeResId).create();
        alertDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        alertDialog.setOnDismissListener(dismissListener);
        init(context, enablePBDir);
    }

    /**
     * Creates an instance of FileListerDialog with the specified Theme
     *
     * @param context Context of the App
     * @param themeId Theme Id for the dialog
     * @return Instance of FileListerDialog
     */
    @NonNull
    public static ChooseDirectoryDialog createFileListerDialog(@NonNull Context context, int themeId, DialogInterface.OnDismissListener dismissListener, boolean enablePBDir) {
        return new ChooseDirectoryDialog(context, themeId, dismissListener, enablePBDir);
    }

    private void init(@NonNull Context context, boolean enablePBDir) {
        filesListerView = new FilesListerView(context, enablePBDir);
        alertDialog.setView(filesListerView);
        alertDialog.setButton(BUTTON_POSITIVE, "Select", (dialogInterface, i) -> {
            dialogInterface.dismiss();
            if (onFileSelectedListener != null)
                onFileSelectedListener.onFileSelected(filesListerView.getSelected(), filesListerView.getSelected().getAbsolutePath());
        });
        alertDialog.setButton(BUTTON_NEUTRAL, "Default Dir", (dialogInterface, i) -> {
            //filesListerView.goToDefaultDir();
        });
        alertDialog.setButton(BUTTON_NEGATIVE, "Cancel", (dialogInterface, i) -> dialogInterface.dismiss());
    }

    /**
     * Display the FileListerDialog
     */
    public void show() {
        //getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        switch (filesListerView.getFileFilter()) {
            case DIRECTORY_ONLY:
                alertDialog.setTitle("Select a directory");
                break;
            case VIDEO_ONLY:
                alertDialog.setTitle("Select a Video file");
                break;
            case IMAGE_ONLY:
                alertDialog.setTitle("Select an Image file");
                break;
            case AUDIO_ONLY:
                alertDialog.setTitle("Select an Audio file");
                break;
            default:
                alertDialog.setTitle("Select a file");
        }
        filesListerView.start();
        alertDialog.show();
        alertDialog.getButton(BUTTON_NEUTRAL).setOnClickListener(view -> filesListerView.goToDefaultDir());
    }

    /**
     * Listener to know which file/directory is selected
     *
     * @param onFileSelectedListener Instance of the Listener
     */
    public void setOnFileSelectedListener(OnFileSelectedListener onFileSelectedListener) {
        this.onFileSelectedListener = onFileSelectedListener;
    }

    /**
     * Set the initial directory to show the list of files in that directory
     *
     * @param file String denoting to the directory
     */
    public void setDefaultDir(@NonNull String file) {
        filesListerView.setDefaultDir(file);
    }

    /**
     * Set the file filter for listing the files
     *
     * @param fileFilter One of the FILE_FILTER values
     */
    public void setFileFilter(FileListerDialog.FILE_FILTER fileFilter) {
        filesListerView.setFileFilter(fileFilter);
    }


}
