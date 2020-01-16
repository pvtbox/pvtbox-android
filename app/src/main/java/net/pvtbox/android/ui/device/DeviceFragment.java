package net.pvtbox.android.ui.device;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.pvtbox.android.R;
import net.pvtbox.android.api.AuthHttpClient;
import net.pvtbox.android.application.Const;
import net.pvtbox.android.db.DataBaseService;
import net.pvtbox.android.db.model.DeviceRealm;
import net.pvtbox.android.service.PreferenceService;
import net.pvtbox.android.service.PvtboxService;
import net.pvtbox.android.tools.JSON;
import net.pvtbox.android.ui.BaseActivity;
import net.pvtbox.android.ui.login.LoginActivity;
import net.pvtbox.android.ui.settings.PvtboxFragment;

import org.json.JSONObject;

import java.util.Objects;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import io.realm.Realm;
import io.realm.RealmChangeListener;
import io.realm.RealmResults;
import io.realm.Sort;

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
public class DeviceFragment extends PvtboxFragment
        implements RealmChangeListener<RealmResults<DeviceRealm>> {
    @Nullable
    @BindView(R.id.count_device_text_view)
    TextView countDeviceTextView;
    @Nullable
    @BindView(R.id.device_list_view)
    RecyclerView deviceListView;
    private Unbinder unbinder;

    @Nullable
    private DeviceStatusDetailsDialog deviceStatusDetailsDialog;
    @Nullable
    private DeviceAdapter deviceAdapter;

    @Nullable
    private Realm realm = null;
    private RealmResults<DeviceRealm> devices;
    @Nullable
    private DeviceManageMenu deviceManageMenu;
    @NonNull
    private final Handler handler = new Handler();
    private DataBaseService dataBaseService;
    private PreferenceService preferenceService;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        BaseActivity activity = (BaseActivity) getActivity();
        dataBaseService = Objects.requireNonNull(activity).getDataBaseService();
        preferenceService = activity.getPreferenceService();
        deviceStatusDetailsDialog = new DeviceStatusDetailsDialog(
                activity, dataBaseService);
        deviceManageMenu = new DeviceManageMenu(activity, preferenceService,
                (v, device) -> {
                    Objects.requireNonNull(deviceManageMenu).dismiss(0);
                    switch (v.getId()) {
                        case R.id.action_details:
                            deviceStatusDetailsDialog.show();
                            break;
                        case R.id.action_remove_node: {
                            AlertDialog.Builder builder = new AlertDialog.Builder(
                                    new ContextThemeWrapper(
                                            Objects.requireNonNull(getActivity()).getWindow().getContext(),
                                            R.style.AppTheme));
                            builder.setTitle(R.string.are_you_sure)
                                    .setMessage(activity.getString(
                                            R.string.remove_node_message, device.getName()))
                                    .setPositiveButton(R.string.yes, (dialog, id) -> removeNode(
                                            device.getId()))
                                    .setNegativeButton(R.string.cancel, (dialog, id) -> {
                                    });
                            builder.show();
                            break;
                        }
                        case R.id.action_logout:
                            if (!device.isOwn()) {
                                if (Objects.equals(
                                        preferenceService.getLicenseType(), Const.FREE_LICENSE)) {
                                    Toast.makeText(
                                            getActivity(),
                                            R.string.not_available_for_free_license,
                                            Toast.LENGTH_LONG).show();
                                    return;
                                } else if (device.isLogoutInProgress()) {
                                    return;
                                }
                            }
                            logout(device.isOwn(), device.getId());
                            break;
                        case R.id.action_wipe: {
                            if (!device.isOwn()) {
                                if (Objects.equals(
                                        preferenceService.getLicenseType(), Const.FREE_LICENSE)) {
                                    Toast.makeText(
                                            getActivity(),
                                            R.string.not_available_for_free_license,
                                            Toast.LENGTH_LONG).show();
                                    return;
                                } else if (device.isWipeInProgress()) {
                                    return;
                                }
                            }
                            AlertDialog.Builder builder = new AlertDialog.Builder(
                                    new ContextThemeWrapper(
                                            Objects.requireNonNull(getActivity()).getWindow().getContext(),
                                            R.style.AppTheme));
                            builder.setTitle(R.string.are_you_sure)
                                    .setMessage(activity.getString(
                                            R.string.all_files_will_be_wiped, device.getName()))
                                    .setPositiveButton(R.string.yes, (dialog, id) -> wipe(
                                            device.isOwn(), device.getId()))
                                    .setNegativeButton(R.string.cancel, (dialog, id) -> {
                                    });
                            builder.show();
                            break;
                        }
                    }
                });
        realm = Realm.getDefaultInstance();
        devices = realm.where(DeviceRealm.class)
                .sort(
                        new String[] {"own", "online", "id"},
                        new Sort[] {Sort.DESCENDING, Sort.DESCENDING, Sort.DESCENDING})
                .findAll();
        deviceAdapter = new DeviceAdapter(activity, devices, deviceManageMenu);
    }

    private void wipe(boolean own, String id) {
        if (own) {
            BaseActivity activity = (BaseActivity) getActivity();
            preferenceService.setLoggedIn(false);
            PvtboxService.stopService(Objects.requireNonNull(activity), true);
            Intent intent = new Intent();
            intent.putExtra("kill", true);
            activity.setResult(RESULT_OK, intent);
            activity.finish();

            Intent intentOpen = new Intent(activity.getApplicationContext(), LoginActivity.class);
            intentOpen.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_TASK_ON_HOME);
            activity.startActivity(intentOpen);
            activity.finish();
        } else {
            BaseActivity activity = (BaseActivity) getActivity();
            assert activity != null;
            activity.showSnack(
                    activity, getString(R.string.sending_remote_action),
                    false, null, false);
            AuthHttpClient http = activity.getAuthHttpClient();
            http.wipeNode(
                    id,
                    response -> handler.post(() -> {
                        activity.showSnack(
                                activity, getString(R.string.remote_action_sent),
                                true, null, false);
                        dataBaseService.updateDeviceWipeInProgress(id);
                    }),
                    error -> handler.post(() -> onError(id, error, activity)));
        }
    }

    private void onError(String id, JSONObject error, @NonNull BaseActivity activity) {
        activity.showSnack(
                activity,
                Objects.requireNonNull(JSON.optString(error, "info", getString(R.string.network_error))),
                true, null, false);
        String errcode = JSON.optString(error, "errcode");
        if (Objects.equals("NODE_WIPED", errcode)) {
            dataBaseService.updateDeviceWipeInProgress(id);
        } else if (Objects.equals("NODE_LOGOUT_EXIST", errcode)) {
            dataBaseService.updateDeviceLogoutInProgress(id);
        }
    }

    private void logout(boolean own, String id) {
        BaseActivity activity = (BaseActivity) getActivity();
        AuthHttpClient http = Objects.requireNonNull(activity).getAuthHttpClient();
        if (own) {
            preferenceService.setLoggedIn(false);
            http.logout(Objects.requireNonNull(preferenceService.getUserHash()));
            PvtboxService.stopService(activity, false);
            Intent intent = new Intent();
            intent.putExtra("kill", true);
            activity.setResult(RESULT_OK, intent);
            activity.finish();

            Intent intentOpen = new Intent(activity.getApplicationContext(), LoginActivity.class);
            intentOpen.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_TASK_ON_HOME);
            activity.startActivity(intentOpen);
            activity.finishAndRemoveTask();
        } else {
            activity.showSnack(
                    activity, getString(R.string.sending_remote_action),
                    false, null, false);
            http.logoutNode(
                    id,
                    response -> handler.post(() -> {
                        dataBaseService.updateDeviceLogoutInProgress(id);
                        activity.showSnack(
                                activity, getString(R.string.remote_action_sent),
                                true, null, false);
                    }),
                    error -> handler.post(() -> onError(id, error, activity)));
        }
    }

    private void removeNode(String id) {
        BaseActivity activity = (BaseActivity) getActivity();
        Objects.requireNonNull(activity).showSnack(
                activity, getString(R.string.removing_node),
                false, null, false);
        AuthHttpClient http = activity.getAuthHttpClient();
        http.hideNode(
                id,
                response -> handler.post(() -> {
                    activity.showSnack(
                            activity, getString(R.string.removed_node),
                            true, null, false);
                    dataBaseService.deleteDevice(id);
                }),
                error -> handler.post(() -> onError(id, error, activity)));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Objects.requireNonNull(realm).close();
    }

    @Override
    public void onPause() {
        Objects.requireNonNull(deviceStatusDetailsDialog).dissmiss();
        super.onPause();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.device_fragment, null);
        unbinder = ButterKnife.bind(this, view);
        setTitle(R.string.title_my_devices);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initList();
        devices.addChangeListener(this);
        onChange(devices);
    }

    private void initList() {
        Objects.requireNonNull(deviceListView).setAdapter(deviceAdapter);
        deviceListView.setItemAnimator(null);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(getActivity());
        deviceListView.setLayoutManager(layoutManager);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        devices.removeChangeListener(this);
        unbinder.unbind();
    }

    @Override
    public void onChange(@NonNull RealmResults<DeviceRealm> devices) {
        long totalCount = devices.where().count();
        long onlineCount = devices.where().equalTo("online", true).count();
        Objects.requireNonNull(countDeviceTextView).setText(
                Objects.requireNonNull(getActivity()).getString(R.string.current_devices, onlineCount, totalCount));
        Objects.requireNonNull(deviceAdapter).updateData(devices);
    }
}
