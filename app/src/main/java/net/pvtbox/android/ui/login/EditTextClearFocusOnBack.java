package net.pvtbox.android.ui.login;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatEditText;
import android.util.AttributeSet;
import android.view.KeyEvent;

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

class EditTextClearFocusOnBack extends AppCompatEditText{
    public EditTextClearFocusOnBack(@NonNull Context context) {
        super(context);
    }

    public EditTextClearFocusOnBack(@NonNull Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public EditTextClearFocusOnBack(@NonNull Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public boolean onKeyPreIme(int keyCode, KeyEvent event)
    {
        if(keyCode == KeyEvent.KEYCODE_BACK)
        {
            clearFocus();
        }
        return super.onKeyPreIme(keyCode, event);
    }
}
