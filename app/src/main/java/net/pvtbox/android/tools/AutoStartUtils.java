package net.pvtbox.android.tools;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatCheckBox;
import android.view.ContextThemeWrapper;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.pvtbox.android.R;

import java.util.ArrayList;
import java.util.Arrays;
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

public class AutoStartUtils {
    public static final int AUTO_START_REQUEST = 111;

    @NonNull
    private static final List<Intent> POWERMANAGER_INTENTS = new ArrayList<>(Arrays.asList(
            new Intent().setComponent(new ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
            new Intent().setComponent(new ComponentName("com.letv.android.letvsafe", "com.letv.android.letvsafe.AutobootManageActivity")),
            new Intent().setComponent(new ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")),
            new Intent().setComponent(new ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
            new Intent().setComponent(new ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")),
            new Intent().setComponent(new ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")),
            new Intent().setComponent(new ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")),
            new Intent().setComponent(new ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")),
            new Intent().setComponent(new ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
            new Intent().setComponent(new ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.entry.FunctionActivity")).setData(Uri.parse("mobilemanager://function/entry/AutoStart"))
    ));

    public interface OnDone {
        void call();
    }

    public static void StartPowerSaverIntent(@NonNull Activity context, @NonNull OnDone onDoneCallback) {
        SharedPreferences settings = context.getSharedPreferences("ProtectedApps", Context.MODE_PRIVATE);
        boolean skipMessage = settings.getBoolean("skipAppListMessage", false);
        if (!skipMessage) {
            SharedPreferences.Editor editor = settings.edit();
            PackageManager manager = context.getPackageManager();
            for (Intent intent : POWERMANAGER_INTENTS) {
                if (manager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null) {
                    LinearLayout layout = new LinearLayout(context);
                    layout.setLayoutParams(new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT));
                    layout.setPadding(16, 48, 8, 0);
                    AppCompatCheckBox checkbox = new AppCompatCheckBox(context);
                    checkbox.setChecked(true);
                    layout.addView(checkbox);
                    TextView textView = new TextView(context);
                    textView.setText(R.string.dont_show_again);
                    layout.addView(textView);

                    new AlertDialog.Builder(new ContextThemeWrapper(context, R.style.AppTheme))
                            .setTitle(R.string.add_to_auto_start)
                            .setMessage(R.string.add_to_auto_start_description)
                            .setView(layout)
                            .setPositiveButton("Go to settings", (d, i) -> {
                                editor.putBoolean("skipAppListMessage", checkbox.isChecked());
                                editor.apply();
                                context.startActivityForResult(intent, AUTO_START_REQUEST);
                            })
                            .setNegativeButton(R.string.cancel, (d, i) -> {
                                editor.putBoolean("skipAppListMessage", checkbox.isChecked());
                                editor.apply();
                                onDoneCallback.call();
                            })
                            .setCancelable(false)
                            .show();
                    return;
                }
            }
        }
        onDoneCallback.call();
    }
}
