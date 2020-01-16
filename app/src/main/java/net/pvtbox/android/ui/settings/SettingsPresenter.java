package net.pvtbox.android.ui.settings;


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
interface SettingsPresenter {
    void onClickLogout();

    void setView(SettingsView settingsView);

    void onStart();

    void changeCheckAutoCamera(boolean isEnable);

    void onClickOnlyWifi();

    void onClickWifiEndCellular();

    void confirmCameraAutoUpdate();

    void confirmWipeAndLogOut();

    void confirmLogout();

    void onRoamingChecked(boolean checked);

    void onAutoStartChecked(boolean checked);

    void onStatisticChecked(boolean checked);

    void onDownloadMediaChecked(boolean checked);

    void onPasscodeChecked(boolean checked);
}
