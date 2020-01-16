package net.pvtbox.android.ui.files.menu;

import android.content.DialogInterface;
import android.view.View;

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

public interface OnMenuListener extends View.OnClickListener, DialogInterface.OnDismissListener, DialogInterface.OnShowListener {

    @Override
    void onClick(View view);

    @Override
    void onDismiss(DialogInterface dialogInterface);

    @Override
    void onShow(DialogInterface dialogInterface);
}
