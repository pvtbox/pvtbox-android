package net.pvtbox.android.ui.notifications;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import net.pvtbox.android.R;
import net.pvtbox.android.api.CollaborationsHttpClient;
import net.pvtbox.android.db.model.DeviceRealm;
import net.pvtbox.android.tools.JSON;
import net.pvtbox.android.ui.BaseActivity;
import net.pvtbox.android.ui.SafelyLinearLayoutManager;
import net.pvtbox.android.ui.settings.PvtboxFragment;

import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Objects;

import butterknife.BindView;
import butterknife.ButterKnife;
import io.realm.ObjectChangeSet;
import io.realm.Realm;
import io.realm.RealmObjectChangeListener;

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
public class NotificationsFragment extends PvtboxFragment implements RealmObjectChangeListener<DeviceRealm>, NotificationsListener {
    private static final String TAG = NotificationsFragment.class.getSimpleName();

    private static final int defaultLimit = 30;

    @Nullable
    @BindView(R.id.no_records_found_layout)
    LinearLayout noRecordsFound;
    @Nullable
    @BindView(R.id.swipeContainer)
    SwipeRefreshLayout swipeContainer;
    @Nullable
    @BindView(R.id.loading_layout)
    LinearLayout loading;
    @Nullable
    @BindView(R.id.notifications_list_view)
    RecyclerView notificationsView;

    @Nullable
    private NotificationsAdapter notificationsAdapter;
    private CollaborationsHttpClient httpClient;
    @Nullable
    private Realm realm;
    @Nullable
    private DeviceRealm device;
    private Handler handler;
    private boolean fetchInProgress = false;
    private boolean loadedAll = false;
    private boolean operationPending = false;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        notificationsAdapter = new NotificationsAdapter(getContext(), this);
        BaseActivity activity = (BaseActivity) getActivity();
        httpClient = Objects.requireNonNull(activity).getCollaborationsHttpClient();
        handler = new Handler();
    }

    private void subscribeForNotificationsCount() {
        Log.d(TAG, "subscribeForNotificationsCount");
        if (realm == null) {
            realm = Realm.getDefaultInstance();
        }
        device = realm.where(DeviceRealm.class).equalTo("own", true).findFirst();
        if (device == null || !device.isValid()) {
            handler.postDelayed(this::subscribeForNotificationsCount, 500);
            return;
        }
        device.addChangeListener(this);
        long limit = device.getNotificationsCount();
        loadNotifications(0, limit < defaultLimit ? defaultLimit : limit);
    }

    private void loadNotifications(long from, long limit) {
        fetchInProgress = true;
        httpClient.getNotifications(
                from, limit,
                this::onNotificationsReceived,
                error -> handler.post(this::showNoRecordsFound));
    }

    private void onNotificationsReceived(@NonNull JSONObject response) {
        JSONArray newNotifications = response.optJSONArray("data");
        if (newNotifications != null) {
            handler.post(() -> updateNotifications(newNotifications));
        }
    }

    private void updateNotifications(@NonNull JSONArray newNotifications) {
        fetchInProgress = false;
        if (newNotifications.length() < defaultLimit) {
            loadedAll = true;
        }
        Objects.requireNonNull(swipeContainer).setRefreshing(false);
        Objects.requireNonNull(notificationsAdapter).add(newNotifications);
        if (newNotifications.length() > 0) {
            Objects.requireNonNull(loading).setVisibility(View.GONE);
            Objects.requireNonNull(noRecordsFound).setVisibility(View.GONE);
            Objects.requireNonNull(notificationsView).setVisibility(View.VISIBLE);
        } else {
            showNoRecordsFound();
        }
    }


    private void showNoRecordsFound() {
        Objects.requireNonNull(loading).setVisibility(View.GONE);
        if (Objects.requireNonNull(notificationsAdapter).getItemCount() > 0) {
            Objects.requireNonNull(notificationsView).setVisibility(View.VISIBLE);
            Objects.requireNonNull(noRecordsFound).setVisibility(View.GONE);
        } else {
            Objects.requireNonNull(notificationsView).setVisibility(View.GONE);
            Objects.requireNonNull(noRecordsFound).setVisibility(View.VISIBLE);
        }
        Objects.requireNonNull(swipeContainer).setRefreshing(false);
        fetchInProgress = false;
        loadedAll = false;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView");
        View view = inflater.inflate(R.layout.notifications_fragment, null);
        ButterKnife.bind(this, view);
        setTitle(R.string.notifications);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initListView();
        initSwipeToRefresh();
    }

    @Override
    public void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();
        enable();
    }

    @Override
    public void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();
        disable();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        Log.d(TAG, String.format("onHiddenChanged: %s", hidden));
        super.onHiddenChanged(hidden);
        if (hidden) {
            disable();
        } else {
            enable();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        disable();
    }


    private void initListView() {
        LinearLayoutManager linearLayoutManager = new SafelyLinearLayoutManager(getContext());
        linearLayoutManager.setItemPrefetchEnabled(true);
        linearLayoutManager.isMeasurementCacheEnabled();
        Objects.requireNonNull(notificationsView).setLayoutManager(linearLayoutManager);
        notificationsView.setAdapter(notificationsAdapter);
        notificationsView.setHasFixedSize(true);
        notificationsView.setItemViewCacheSize(30);
        notificationsView.setDrawingCacheEnabled(true);
        notificationsView.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH);
        notificationsView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (fetchInProgress || loadedAll) {
                    return;
                }
                LinearLayoutManager linearLayoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (linearLayoutManager == null) return;

                if (linearLayoutManager.findLastCompletelyVisibleItemPosition() >
                        Objects.requireNonNull(notificationsAdapter).getItemCount() - defaultLimit) {
                    loadNotifications(notificationsAdapter.getLastItemId(), defaultLimit);
                }
            }
        });
    }

    private void initSwipeToRefresh() {
        Objects.requireNonNull(swipeContainer).setOnRefreshListener(() -> {
            Log.d(TAG, "onRefresh");
            refresh();
        });
    }

    private void enable() {
        subscribeForNotificationsCount();
    }

    private void disable() {
        if (device != null) {
            device.removeChangeListener(this);
        }
        device = null;
        if (realm != null) {
            realm.close();
        }
        realm = null;
    }

    private void refresh() {
        if (device != null && (device.getNotificationsCount() > 0 ||
                Objects.requireNonNull(notificationsAdapter).getItemCount() == 0)) {
            long limit = device.getNotificationsCount();
            loadedAll = false;
            Objects.requireNonNull(swipeContainer).setRefreshing(false);
            Objects.requireNonNull(notificationsView).setVisibility(View.GONE);
            Objects.requireNonNull(noRecordsFound).setVisibility(View.GONE);
            Objects.requireNonNull(loading).setVisibility(View.VISIBLE);
            Objects.requireNonNull(notificationsAdapter).clear();
            loadNotifications(0, limit < defaultLimit ? defaultLimit : limit);
        } else {
            handler.post(() -> Objects.requireNonNull(swipeContainer).setRefreshing(false));
        }
    }

    @Override
    public void onChange(@NotNull DeviceRealm deviceRealm, ObjectChangeSet changeSet) {
        if (changeSet != null &&
                changeSet.isFieldChanged("notificationsCount")) {
            handler.post(this::onNotificationsCountChanged);
        }
    }

    private void onNotificationsCountChanged() {
        if (device != null && device.getNotificationsCount() > 0) {
            if (fetchInProgress) {
                handler.postDelayed(this::onNotificationsCountChanged, 1000);
            } else {
                refresh();
            }
        }
    }

    @Override
    public void openFolder(String name) {
        Log.i(TAG, String.format("openFolder: %s", name));
        Intent intent = new Intent();
        intent.putExtra("folder_name", name);
        Objects.requireNonNull(getActivity()).setResult(RESULT_OK, intent);
        getActivity().finish();
    }

    @Override
    public void joinCollaboration(int colleagueId) {
        Log.i(TAG, String.format("joinCollaboration: %d", colleagueId));
        if (operationPending) return;
        operationPending = true;
        BaseActivity activity = (BaseActivity) getActivity();
        Objects.requireNonNull(activity).showSnack(
                activity, getString(R.string.accepting_invitation),
                false, null, false);
        httpClient.join(
                colleagueId,
                response -> handler.post(() -> {
                    activity.showSnack(
                            activity, getString(R.string.accepted_invitation),
                            true, null, false);
                    operationPending = false;
                }),
                error -> handler.post(() -> {
                    String networkError = getString(R.string.network_error);
                    String err = error == null ? networkError : JSON.optString(error,
                            "info", error.optJSONObject("info") == null ? networkError :
                                    Objects.requireNonNull(error.optJSONObject("info")).optJSONArray("error_file_name") == null ?
                                            networkError :
                                            JSON.optString(Objects.requireNonNull(error.optJSONObject("info"))
                                                            .optJSONArray("error_file_name")
                                                    , 0, networkError));
                    activity.showSnack(
                            activity,
                            Objects.requireNonNull(err),
                            true, null, false);
                    operationPending = false;
                }));
    }
}
