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
interface SettingsView {
    void openLogin();

    void showLicenceType(String licenceType);

    void showMail(String mail);

    void showAutoCameraUpdate(boolean autoCameraUpdate);

    void showCellularUpdate(boolean cellularNetwork);

    void showAlertCameraAutoUpdate();

    void showAlertLogout();

    void hideEnableRoaming();

    void showEnableRoaming();

    void setRoamingChecked(boolean checked);

    void setAutoStartChecked(boolean checked);

    void setStatisticChecked(boolean checked);

    void setDownloadMediaChecked(boolean checked);

    void setPasscodeChecked(boolean checked);
}
