package net.pvtbox.android.ui.files;

import android.content.Context;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.pvtbox.android.R;
import net.pvtbox.android.db.DataBaseService;
import net.pvtbox.android.db.model.FileRealm;
import net.pvtbox.android.ui.files.presenter.FilesPresenter;

import java.util.Objects;

import io.realm.OrderedRealmCollection;
import io.realm.Realm;
import io.realm.RealmChangeListener;
import io.realm.RealmResults;

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

class FilesPresenterImpl implements
        FilesPresenter, FileListProvider, CurrentFolderProvider,
        RealmChangeListener<RealmResults<FileRealm>> {
    private final static String TAG = FilesPresenterImpl.class.getSimpleName();
    private static final int RECENT_FILE_LIST_SIZE_MIN = 8;
    private static final long cycleUpdateInterval = 60 * 1000;

    private final Context context;
    private final DataBaseService dataBaseService;
    private final ModeProvider modeProvider;
    private final SelectionProvider selectionProvider;
    private final FilesProviderListener filesProviderListener;
    private final String title;
    @Nullable
    private final FileRealm root;
    private final boolean isRoot;

    private FilesAdapter adapter;
    private Realm realm;
    private boolean enabled;
    @Nullable
    private RealmResults<FileRealm> fileListSubscription;
    private String query;

    @NonNull
    private final Handler handler = new Handler();

    @NonNull
    private final Runnable cycleUpdateWork = new Runnable() {
        @Override
        public void run() {
            if (!enabled) return;
            Log.d(TAG, "cycleUpdateWork");
            adapter.notifyDataSetChanged();
            cycleUpdate();
        }
    };

    public FilesPresenterImpl(
            Context context,
            ModeProvider modeProvider,
            SelectionProvider selectionProvider,
            FilesProviderListener filesProviderListener,
            DataBaseService dataBaseService,
            String title, @Nullable FileRealm root) {
        this.context = context;
        this.modeProvider = modeProvider;
        this.selectionProvider = selectionProvider;
        this.filesProviderListener = filesProviderListener;
        this.dataBaseService = dataBaseService;
        this.title = title;
        this.root = root;
        this.isRoot = root == null;
    }

    public void setAdapter(FilesAdapter adapter) {
        this.adapter = adapter;
    }

    @Override
    public String getTitle() {
        if (modeProvider.isMultiSelect()) {
            return String.format(context.getString(R.string.select), selectionProvider.getSelectedCount());
        } else {
            return title;
        }
    }

    @Override
    public void enable() {
        Log.d(TAG, "enable");
        enabled = true;
        realm = Realm.getDefaultInstance();
        getAndSubscribeForFileList();
        cycleUpdate();
    }

    private void cycleUpdate() {
        handler.removeCallbacks(cycleUpdateWork);
        handler.postDelayed(cycleUpdateWork, cycleUpdateInterval);
    }

    private void getAndSubscribeForFileList() {
        if (fileListSubscription != null) {
            fileListSubscription.removeChangeListener(this);
        }
        if (modeProvider.isRecent()) {
            fileListSubscription = dataBaseService.getRecentFileListResult(
                    query, getRecentFileListMaxItemsCount(), realm);
        } else if (modeProvider.isDownloads()) {
            fileListSubscription = dataBaseService.getDownloadsFileListResult(
                    query, realm);
        } else {
            fileListSubscription = dataBaseService.getFilesListResult(
                    isRoot ? null : Objects.requireNonNull(root).getUuid(),
                    modeProvider.getSorting(),
                    modeProvider.isOffline(),
                    query,
                    realm);
        }
        adapter.updateData(fileListSubscription);
        Objects.requireNonNull(fileListSubscription).addChangeListener(this);
        onChange(fileListSubscription);
    }

    @Override
    public void onChange(@NonNull RealmResults<FileRealm> files) {
        if (files.isEmpty()) {
            filesProviderListener.onEmpty();
        } else {
            filesProviderListener.onListLoaded();
        }
    }

    @Override
    public void disable() {
        Log.d(TAG, "disable");
        enabled = false;
        handler.removeCallbacks(cycleUpdateWork);
        adapter.notifyDataSetChanged();
        Objects.requireNonNull(fileListSubscription).removeAllChangeListeners();
        fileListSubscription = null;
        realm.close();
    }

    @Override
    public void refresh() {
        disable();
        enable();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
    }

    @Override
    public void onSortingChanged() {
        getAndSubscribeForFileList();
    }

    @Override
    public void onSearch(String query) {
        this.query = query;
        getAndSubscribeForFileList();
    }

    @Override
    public void onCheckedChange() {
        adapter.updateData(fileListSubscription);
    }

    @Nullable
    @Override
    public FileRealm getCurrentFolder() {
        return root;
    }

    @Override
    public boolean isRoot() {
        return isRoot;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Nullable
    @Override
    public OrderedRealmCollection<FileRealm> getFiles() { return fileListSubscription; }

    private int getRecentFileListMaxItemsCount() {
        DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
        float screenHeight = displayMetrics.heightPixels;
        float toolBarHeight= context.getResources().getDimension(R.dimen.tool_bar_height);
        float itemHeight = context.getResources().getDimension(R.dimen.height_file_item);

        int result = (int) ((screenHeight - toolBarHeight) / itemHeight);
        return Math.max(result, RECENT_FILE_LIST_SIZE_MIN);
    }

}
