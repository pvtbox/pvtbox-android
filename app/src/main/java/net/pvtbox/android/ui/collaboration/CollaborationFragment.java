package net.pvtbox.android.ui.collaboration;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import net.pvtbox.android.R;
import net.pvtbox.android.api.CollaborationsHttpClient;
import net.pvtbox.android.application.Const;
import net.pvtbox.android.service.PreferenceService;
import net.pvtbox.android.service.signalserver.SignalServerService;
import net.pvtbox.android.tools.EmailValidator;
import net.pvtbox.android.ui.BaseActivity;
import net.pvtbox.android.ui.settings.PvtboxFragment;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import butterknife.BindView;
import butterknife.ButterKnife;

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

public class CollaborationFragment extends PvtboxFragment {

    @Nullable
    @BindView(R.id.collaboration_list_view)
    RecyclerView collaborationListView;
    @Nullable
    @BindView(R.id.swipe)
    SwipeRefreshLayout swipe;
    @Nullable
    @BindView(R.id.add_colleague_button)
    Button addColleagueButton;
    @Nullable
    @BindView(R.id.collaboration_mail_text_view)
    EditText mailText;
    @Nullable
    @BindView(R.id.collaboration_permission_spinner)
    Spinner permissionSpinner;
    @Nullable
    @BindView(R.id.add_colleague_layout)
    View addColleagueLayout;
    @Nullable
    @BindView(R.id.add_colleague_cancel)
    View addColleagueCancel;
    @Nullable
    @BindView(R.id.collaboration_list_layout)
    View listLayout;
    private View connectingToServer;

    @Nullable
    private CollaborationAdapter collaborationAdapter;

    private PreferenceService preferenceService;
    private CollaborationsHttpClient httpClient;

    @Nullable
    private CollaborationActionsHandler collaborationActionsHandler;

    @Nullable
    private String fileUuid;

    private final BroadcastReceiver networkStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, @NonNull Intent intent) {
            onNetworkStatus(intent);
        }
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        setTitle(R.string.collaboration_settings);
        fileUuid = Objects.requireNonNull(Objects.requireNonNull(getActivity()).getIntent().getExtras())
                .getString(PvtboxFragment.FILE_ITEM);
        BaseActivity activity = (BaseActivity) getActivity();
        preferenceService = activity.getPreferenceService();
        httpClient = activity.getCollaborationsHttpClient();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.collaboration_fragment, null);
        ButterKnife.bind(this, view);

        connectingToServer = Objects.requireNonNull(getActivity()).findViewById(R.id.connecting_to_server);

        return view;
    }

    @Override
    public void onResume() {
        Objects.requireNonNull(getActivity()).getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        super.onResume();
        IntentFilter filter = new IntentFilter(Const.NETWORK_STATUS);
        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(
                networkStatusReceiver, filter);
        connectingToServer.setVisibility(
                SignalServerService.IsConnected() ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onPause() {
        LocalBroadcastManager.getInstance(Objects.requireNonNull(getActivity())).unregisterReceiver(networkStatusReceiver);
        super.onPause();
    }

    @Override
    public void onCreateOptionsMenu(@NotNull Menu menu, @NotNull MenuInflater inflater) {

    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_send) {
            InputMethodManager keyboard = (InputMethodManager) Objects.requireNonNull(getActivity()).getSystemService(
                    Context.INPUT_METHOD_SERVICE);
            if (keyboard != null) {
                keyboard.hideSoftInputFromWindow(Objects.requireNonNull(mailText).getWindowToken(), 0);
            }
            onAddColleague();
        }
        return super.onOptionsItemSelected(item);
    }

    private void onAddColleague() {
        String email = Objects.requireNonNull(mailText).getText().toString().trim();
        if (EmailValidator.isValid(email)) {
            String perm = Objects.requireNonNull(permissionSpinner).getSelectedItemId() == 0 ? "view" : "edit";
            Objects.requireNonNull(collaborationActionsHandler).addColleague(email, perm);
            mailText.setText(null);
            closeColleagueAdd();
        } else {
            Toast.makeText(
                    getActivity(), R.string.wrong_email_format, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        CollaborationPresenter collaborationPresenter = new CollaborationPresenterImpl(preferenceService.getMail());
        collaborationAdapter = new CollaborationAdapter(
                getActivity(), collaborationPresenter);
        collaborationActionsHandler = new CollaborationActionsHandler(
                getActivity(), httpClient,
                collaborationPresenter, collaborationAdapter, fileUuid, swipe,
                () -> Objects.requireNonNull(getActivity()).finish(), this);
        CollaborationMenuDialog collaborationMenu = new CollaborationMenuDialog(
                Objects.requireNonNull(getActivity()), collaborationPresenter, collaborationActionsHandler);
        collaborationAdapter.setMenu(collaborationMenu);
        initGui();
    }

    private void initGui() {
        initSpinner();
        Objects.requireNonNull(swipe).setOnRefreshListener(() -> {
            Objects.requireNonNull(collaborationActionsHandler).getInfo();
            closeColleagueAdd();
        });
        swipe.setRefreshing(true);
        initList();
    }

    @SuppressWarnings("SameReturnValue")
    void showAddColleagueButton() {
        Objects.requireNonNull(addColleagueButton).setVisibility(View.VISIBLE);
        addColleagueButton.setOnClickListener(view -> {
            if (Objects.requireNonNull(addColleagueLayout).getVisibility() == View.VISIBLE) {
                onAddColleague();
            } else {
                addColleagueLayout.setVisibility(View.VISIBLE);
                addColleagueButton.setBackgroundColor(
                        ContextCompat.getColor(Objects.requireNonNull(getActivity()), R.color.primary));
            }
        });
        Objects.requireNonNull(listLayout).setOnFocusChangeListener((view, b) -> closeColleagueAdd());
        Objects.requireNonNull(mailText).setOnEditorActionListener((textView, i, keyEvent) -> {
            if (i == EditorInfo.IME_ACTION_DONE ||
                    (keyEvent.getKeyCode() == KeyEvent.KEYCODE_ENTER &&
                            keyEvent.getAction() == KeyEvent.ACTION_DOWN)) {
                onAddColleague();
            }
            return false;
        });
        Objects.requireNonNull(addColleagueCancel).setOnClickListener(v -> closeColleagueAdd());
    }

    private void closeColleagueAdd() {
        Objects.requireNonNull(addColleagueLayout).setVisibility(View.GONE);
        Objects.requireNonNull(addColleagueButton).setBackgroundColor(
                ContextCompat.getColor(Objects.requireNonNull(getActivity()), R.color.monsoon_light));
        InputMethodManager keyboard = (InputMethodManager) getActivity().getSystemService(
                Context.INPUT_METHOD_SERVICE);
        Objects.requireNonNull(keyboard).hideSoftInputFromWindow(Objects.requireNonNull(mailText).getWindowToken(), 0);
    }

    private void initList() {
        Objects.requireNonNull(collaborationListView).setAdapter(collaborationAdapter);
        collaborationListView.setLayoutManager(new LinearLayoutManager(getActivity()));
        Objects.requireNonNull(collaborationActionsHandler).getInfo();
    }

    private void initSpinner() {
        List<String> listPermission = new ArrayList<>();
        Context context = getActivity();

        listPermission.add(Objects.requireNonNull(context).getString(R.string.can_view));
        listPermission.add(context.getString(R.string.can_edit));
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                context, R.layout.collaboration_spinner_item, listPermission);
        adapter.setDropDownViewResource(R.layout.collaboration_dropdown_spinner_item);

        Objects.requireNonNull(permissionSpinner).setAdapter(adapter);
    }

    private void onNetworkStatus(@NonNull Intent intent) {
        boolean signalConnecting = intent.getBooleanExtra(
                Const.NETWORK_STATUS_SIGNAL_CONNECTING, false);
        connectingToServer.setVisibility(signalConnecting ? View.VISIBLE : View.GONE);
        if (!signalConnecting) Objects.requireNonNull(collaborationActionsHandler).getInfo();
    }
}
