package net.pvtbox.android.ui.settings;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.SwitchCompat;

import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.TextView;

import com.amirarcane.lockscreen.activity.EnterPinActivity;

import net.pvtbox.android.BuildConfig;
import net.pvtbox.android.R;
import net.pvtbox.android.application.Const;
import net.pvtbox.android.ui.BaseActivity;
import net.pvtbox.android.ui.login.LoginActivity;

import java.util.Objects;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;

import static android.app.Activity.RESULT_OK;

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
public class SettingsFragment extends PvtboxFragment implements SettingsView {

    @NonNull
    private static final String TAG = SettingsFragment.class.getSimpleName();

    @Nullable
    private
    SettingsPresenter settingsPresenter;
    @Nullable
    @BindView(R.id.setting_mail_text_view)
    TextView settingMailTextView;
    @Nullable
    @BindView(R.id.account_type)
    TextView accountType;
    @Nullable
    @BindView(R.id.setting_automatic_camera_upload_switch)
    SwitchCompat settingAutomaticCameraUploadSwitch;
    @Nullable
    @BindView(R.id.setting_wifi_only_radio_button)
    RadioButton settingWifiOnlyRadioButton;
    @Nullable
    @BindView(R.id.setting_wifi_end_cellular_radio_button)
    RadioButton settingWifiEndCellularRadioButton;
    @Nullable
    @BindView(R.id.version_label)
    TextView versionLabel;
    @Nullable
    @BindView(R.id.roaming_setting)
    View roamingSetting;
    @Nullable
    @BindView(R.id.setting_roaming_switch)
    SwitchCompat roamingSwitch;
    @Nullable
    @BindView(R.id.setting_autostart_switch)
    SwitchCompat autostartSwitch;
    @Nullable
    @BindView(R.id.setting_statistic_switch)
    SwitchCompat statisticSwitch;
    @Nullable
    @BindView(R.id.setting_passcode_switch)
    SwitchCompat passcodeSwitch;
    @Nullable
    @BindView(R.id.setting_download_media_switch)
    SwitchCompat downloadMediaSwitch;
    @Nullable
    @BindView(R.id.logout_button)
    AppCompatButton logoutButton;
    private Unbinder unbinder;


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        BaseActivity activity = (BaseActivity) getActivity();
        settingsPresenter = new SettingsPresenterImpl(
                Objects.requireNonNull(activity).getPreferenceService(), activity.getDataBaseService(),
                activity.getAuthHttpClient(), activity);
        settingsPresenter.setView(this);
        setTitle(R.string.title_settings);
    }

    @Override
    public void onStart() {
        super.onStart();
        Objects.requireNonNull(settingsPresenter).onStart();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.settings_fragment, null);
        unbinder = ButterKnife.bind(this, view);
        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        unbinder.unbind();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initGui();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1) {
            if (resultCode != EnterPinActivity.RESULT_BACK_PRESSED) {
                EnterPinActivity.deletePin(Objects.requireNonNull(getContext()));
            }
        }
    }

    private void initGui() {
        Objects.requireNonNull(versionLabel).setText(getString(R.string.app_version, BuildConfig.VERSION_NAME));
        Objects.requireNonNull(settingAutomaticCameraUploadSwitch).setOnCheckedChangeListener(((compoundButton, checked) ->
                Objects.requireNonNull(settingsPresenter).changeCheckAutoCamera(checked)));
        Objects.requireNonNull(settingWifiOnlyRadioButton).setOnClickListener((View v) ->
                Objects.requireNonNull(settingsPresenter).onClickOnlyWifi());
        Objects.requireNonNull(settingWifiEndCellularRadioButton).setOnClickListener((View v) ->
                Objects.requireNonNull(settingsPresenter).onClickWifiEndCellular());
        Objects.requireNonNull(roamingSwitch).setOnCheckedChangeListener((compoundButton, checked) ->
                Objects.requireNonNull(settingsPresenter).onRoamingChecked(checked));
        Objects.requireNonNull(autostartSwitch).setOnCheckedChangeListener(((compoundButton, checked) ->
                Objects.requireNonNull(settingsPresenter).onAutoStartChecked(checked)));
        Objects.requireNonNull(statisticSwitch).setOnCheckedChangeListener((compoundButton, checked) ->
                Objects.requireNonNull(settingsPresenter).onStatisticChecked(checked));
        Objects.requireNonNull(downloadMediaSwitch).setOnCheckedChangeListener((compoundButton, checked) ->
                Objects.requireNonNull(settingsPresenter).onDownloadMediaChecked(checked));
        Objects.requireNonNull(passcodeSwitch).setOnCheckedChangeListener((compoundButton, checked) ->
                Objects.requireNonNull(settingsPresenter).onPasscodeChecked(checked));
        Objects.requireNonNull(logoutButton).setOnClickListener((View v) ->
                Objects.requireNonNull(settingsPresenter).onClickLogout());

    }

    @Override
    public void openLogin() {
        Intent intent = new Intent();
        intent.putExtra("kill", true);
        Activity activity = getActivity();
        if(activity==null){
            Log.d(TAG, "activity=null");
            return;
        }
        activity.setResult(RESULT_OK, intent);
        activity.finish();

        Intent intentOpen = new Intent(activity.getApplicationContext(), LoginActivity.class);
        intentOpen.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |  Intent.FLAG_ACTIVITY_TASK_ON_HOME);
        activity.startActivity(intentOpen);
        activity.finish();
    }

    @Override
    public void showLicenceType(@NonNull String licenceType) {
        int idStringLicence;
        switch (licenceType) {
            case Const.FREE_LICENSE:
                idStringLicence = R.string.free_licence;
                break;
            case Const.FREE_TRIAL_LICENSE:
                idStringLicence = R.string.free_licence_trial;
                break;
            case Const.PAYED_PROFESSIONAL_LICENSE:
                idStringLicence = R.string.pro_licence;
                break;
            case Const.PAYED_BUSINESS_LICENSE:
                idStringLicence = R.string.business_licence;
                break;
            case Const.PAYED_BUSINESS_ADMIN_LICENSE:
                idStringLicence = R.string.business_admin_licence;
                break;
            default:
                idStringLicence = R.string.unknown_license;
                break;

        }
        Objects.requireNonNull(accountType).setText(idStringLicence);

    }

    @Override
    public void showMail(String mail) {
        Objects.requireNonNull(settingMailTextView).setText(mail);
    }

    @Override
    public void showAutoCameraUpdate(boolean autoCameraUpdate) {
        Objects.requireNonNull(settingAutomaticCameraUploadSwitch).setChecked(autoCameraUpdate);
    }

    @Override
    public void showCellularUpdate(boolean cellularNetwork) {
        Objects.requireNonNull(settingWifiOnlyRadioButton).setChecked(!cellularNetwork);
        Objects.requireNonNull(settingWifiEndCellularRadioButton).setChecked(cellularNetwork);
    }

    @Override
    public void showAlertCameraAutoUpdate() {
        AlertDialog.Builder builder = new AlertDialog.Builder(
                new ContextThemeWrapper(Objects.requireNonNull(getActivity()).getWindow().getContext(), R.style.AppTheme));
        builder.setMessage(R.string.automatic_camera_update_text)
                .setPositiveButton(R.string.ok, (dialog, id) -> Objects.requireNonNull(settingsPresenter).confirmCameraAutoUpdate());
        builder.setOnCancelListener(d -> Objects.requireNonNull(settingAutomaticCameraUploadSwitch).setChecked(false));
        builder.create();
        builder.show();
    }

    @Override
    public void showAlertLogout() {
        AlertDialog.Builder builder = new AlertDialog.Builder(
                new ContextThemeWrapper(Objects.requireNonNull(getActivity()).getWindow().getContext(), R.style.AppTheme));
        builder.setMessage(R.string.clear_file)
                .setPositiveButton(R.string.keep, (dialog, id) -> Objects.requireNonNull(settingsPresenter).confirmLogout())
                .setNegativeButton(R.string.clear_all, (dialog, id) -> Objects.requireNonNull(settingsPresenter).confirmWipeAndLogOut());

        builder.create();
        builder.show();
    }

    @Override
    public void hideEnableRoaming() {
        Objects.requireNonNull(roamingSetting).setVisibility(View.INVISIBLE);
    }

    @Override
    public void showEnableRoaming() {
        Objects.requireNonNull(roamingSetting).setVisibility(View.VISIBLE);
    }

    @Override
    public void setRoamingChecked(boolean checked) {
        Objects.requireNonNull(roamingSwitch).setChecked(checked);
    }

    @Override
    public void setAutoStartChecked(boolean checked) {
        Objects.requireNonNull(autostartSwitch).setChecked(checked);
    }

    @Override
    public void setStatisticChecked(boolean checked) {
        Objects.requireNonNull(statisticSwitch).setChecked(checked);
    }

    @Override
    public void setDownloadMediaChecked(boolean checked) {
        Objects.requireNonNull(downloadMediaSwitch).setChecked(checked);
    }

    @Override
    public void setPasscodeChecked(boolean checked) {
        Objects.requireNonNull(passcodeSwitch).setChecked(checked);
    }

}
