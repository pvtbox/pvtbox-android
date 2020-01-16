package net.pvtbox.android.ui.main_screen;

import android.util.Log;

import androidx.annotation.NonNull;

import net.pvtbox.android.db.model.FileRealm;
import net.pvtbox.android.ui.files.ModeProvider;
import net.pvtbox.android.ui.files.SelectionProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.realm.Realm;

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

class MainPresenterImpl implements MainPresenter, ModeProvider, SelectionProvider {
    private final static String TAG = MainPresenterImpl.class.getSimpleName();

    private boolean multiSelectEnabled;
    @NonNull
    private final Map<String, FileRealm> selectedItems = new HashMap<>();
    private boolean isRecent;
    private boolean isDownloads;
    private boolean moveActionActive;
    private boolean copyActionActive;
    private boolean inSearch;
    private boolean isOffline;
    private int offlineSelectedCount;
    private Sorting sorting = Sorting.none;

    @Override
    public void onItemSelected(FileRealm file) {
        Log.d(TAG, "onItemSelected");
        try (Realm realm = Realm.getDefaultInstance()) {
            file = realm.copyFromRealm(file);
        }
        if (file.getUuid() == null || file.getUuid().isEmpty()) return;
        selectedItems.put(file.getUuid(), file);
        if (file.isOffline() || file.isDownload() && !file.isOnlyDownload())
            offlineSelectedCount++;
    }

    @Override
    public void onItemUnselected(@NonNull FileRealm file) {
        Log.d(TAG, "onItemUnselected");
        selectedItems.remove(file.getUuid());
        if (file.isOffline() || file.isDownload() && !file.isOnlyDownload())
            offlineSelectedCount--;
    }

    @Override
    public void onItemsSelected(@NonNull List<FileRealm> files) {
        Log.d(TAG, "onItemsSelected");
        clearSelectedItems();
        for (FileRealm item: files) {
            onItemSelected(item);
        }
    }

    @Override
    public void clearSelectedItems() {
        Log.d(TAG, "clearSelectedItems");
        selectedItems.clear();
        offlineSelectedCount = 0;
    }

    @Override
    public FileRealm getSelectedItem() {
        Log.d(TAG, "getSelectedItem");
        return selectedItems.values().iterator().next();
    }

    @NonNull
    @Override
    public ArrayList<FileRealm> getSelectedItems() {
        return new ArrayList<>(selectedItems.values());
    }

    @Override
    public int getSelectedCount() {
        return selectedItems.size();
    }

    @Override
    public boolean selectedContains(String uuid) {
        return selectedItems.containsKey(uuid);
    }

    @Override
    public boolean isAllSelectedOffline() {
        return offlineSelectedCount == selectedItems.size();
    }

    @Override
    public boolean isAllSelectedNotOffline() {
        return offlineSelectedCount == 0;
    }

    @Override
    public void disableMultiSelect() {
        multiSelectEnabled = false;
    }

    @Override
    public void enableMultiSelect() {
        multiSelectEnabled = true;
    }

    @Override
    public void moveActionActivated() {
        moveActionActive = true;
    }

    @Override
    public boolean isMoveActionActive() {
        return moveActionActive;
    }

    @Override
    public void copyActionActivated() {
        copyActionActive = true;
    }

    @Override
    public boolean isCopyActionActive() {
        return copyActionActive;
    }

    @Override
    public void actionCancelled() {
        moveActionActive = copyActionActive = false;
    }

    @Override
    public void setRecent(boolean recent) {
        Log.d(TAG, String.format("setRecent: %s", recent));
        isRecent = recent;
    }

    @Override
    public boolean isRecent() {
        Log.d(TAG, String.format("isRecent: %s", isRecent));
        return isRecent;
    }

    @Override
    public void setDownloads(boolean downloads) {
        Log.d(TAG, String.format("setDownloads: %s", downloads));
        isDownloads = downloads;
    }

    @Override
    public boolean isDownloads() {
        Log.d(TAG, String.format("isDownloads: %s", isDownloads));
        return isDownloads;
    }

    @Override
    public void setOffline(boolean offline) {
        Log.d(TAG, String.format("setOffline: %s", offline));
        isOffline = offline;
    }

    @Override
    public boolean isOffline() {
        Log.d(TAG, String.format("isOffline: %s", isOffline));
        return isOffline;
    }

    @Override
    public void setSorting(Sorting sorting) {
        this.sorting = sorting;
    }
    @Override
    public void setSortByName() {
        sorting = Sorting.name;
    }

    @Override
    public void setSortByDate() {
        sorting = Sorting.date;
    }

    @Override
    public Sorting getSorting() {
        return sorting;
    }

    @Override
    public boolean isSortingByName() {
        return sorting == Sorting.name;
    }

    @Override
    public void setInSearch(boolean inSearch) {
        Log.d(TAG, String.format("setInSearch: %s", inSearch));
        this.inSearch = inSearch;
    }

    @Override
    public boolean inSearch() {
        return inSearch;
    }

    @Override
    public boolean isMultiSelect() {
        return multiSelectEnabled;
    }

}
