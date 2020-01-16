package net.pvtbox.android.ui.main_screen;

import net.pvtbox.android.db.model.FileRealm;
import net.pvtbox.android.ui.files.ModeProvider;

import java.util.List;

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

public interface MainPresenter {
    void onItemSelected(FileRealm fileItem);
    void onItemUnselected(FileRealm fileItem);
    void onItemsSelected(List<FileRealm> files);
    void clearSelectedItems();

    void disableMultiSelect();
    void enableMultiSelect();

    void moveActionActivated();
    void copyActionActivated();
    void actionCancelled();

    void setRecent(boolean recent);
    void setDownloads(boolean downloads);
    void setOffline(boolean offline);

    void setInSearch(boolean inSearch);

    void setSorting(ModeProvider.Sorting sorting);
    void setSortByName();
    void setSortByDate();
}
