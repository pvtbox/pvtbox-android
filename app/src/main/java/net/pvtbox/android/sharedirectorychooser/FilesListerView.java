package net.pvtbox.android.sharedirectorychooser;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;


import yogesh.firzen.filelister.FileListerDialog;

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
class FilesListerView extends RecyclerView {

    private FileListerAdapter adapter;

    public FilesListerView(@NonNull Context context) {
        super(context);
        init(false);
    }

    public FilesListerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(false);
    }

    public FilesListerView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(false);
    }

    FilesListerView(@NonNull Context context, boolean enablePBDir) {
        super(context);
        init(enablePBDir);
    }

    private void init(boolean enablePBDir) {
        setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false));
        adapter = new FileListerAdapter(this, enablePBDir);
    }

    void start() {
        setAdapter(adapter);
        adapter.start();
    }

    private void setDefaultDir(File file) {
        adapter.setDefaultDir(file);
    }

    void setDefaultDir(@NonNull String path) {
        setDefaultDir(new File(path));
    }

    File getSelected() {
        return adapter.getSelected();
    }

    void goToDefaultDir() {
        adapter.goToDefault();
    }

    void setFileFilter(FileListerDialog.FILE_FILTER fileFilter) {
        adapter.setFileFilter(fileFilter);
    }

    FileListerDialog.FILE_FILTER getFileFilter() {
        return adapter.getFileFilter();
    }
}
