package net.pvtbox.android.ui.start;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.Nullable;

import net.pvtbox.android.R;
import net.pvtbox.android.application.Const;
import net.pvtbox.android.tools.ShareActivityHelper;

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
public class SendViaPvtboxActivity extends Activity {
    private static final String TAG = SendViaPvtboxActivity.class.getSimpleName();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        Log.w(TAG, "onCreate");
        setTheme(R.style.AppTheme_PBNoActionBar);
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        ShareActivityHelper.handleShareIntent(intent, this);
        intent.putExtra(Const.SHARING_ENABLE, true);
        intent.setComponent(new ComponentName(this, StartActivity.class));
        startActivity(intent);
        finish();
    }


}
