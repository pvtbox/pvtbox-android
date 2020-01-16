package net.pvtbox.android.ui.getlink;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.SwitchCompat;

import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import net.pvtbox.android.R;
import net.pvtbox.android.api.ShareHttpClient;
import net.pvtbox.android.application.Const;
import net.pvtbox.android.db.DataBaseService;
import net.pvtbox.android.db.model.FileRealm;
import net.pvtbox.android.service.PreferenceService;
import net.pvtbox.android.service.signalserver.SignalServerService;
import net.pvtbox.android.ui.BaseActivity;
import net.pvtbox.android.ui.settings.PvtboxFragment;

import java.util.Objects;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;

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
public class GetLinkFragment extends PvtboxFragment implements GetLinkView {

    @Nullable
    @BindView(R.id.get_link_date_switch)
    SwitchCompat getLinkExpireSwitch;
    @Nullable
    @BindView(R.id.get_link_password_switch)
    SwitchCompat getLinkPasswordSwitch;
    @Nullable
    @BindView(R.id.create_link_button)
    AppCompatButton createLinkButton;
    private Unbinder unbinder;
    @Nullable
    @BindView(R.id.relativeLayoutSwitchDate)
    RelativeLayout relativeLayoutSwitchDate;
    @Nullable
    @BindView(R.id.relativeLayoutSwitchPassword)
    RelativeLayout relativeLayoutSwitchPassword;
    private View connectingToServer;

    @Nullable
    private GetLinkPresenter getLinkPresenter;
    private EditText passwordText;
    private AlertDialog passwordDialog;
    private AlertDialog expireDialog;

    private boolean getLinkExpireSwitchTouched;
    private boolean getLingPasswordSwitchTouched;

    private final BroadcastReceiver networkStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, @NonNull Intent intent) {
            onNetworkStatus(intent);
        }
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        BaseActivity activity = (BaseActivity) getActivity();
        DataBaseService dataBaseService = Objects.requireNonNull(activity).getDataBaseService();
        PreferenceService preferenceService = activity.getPreferenceService();
        ShareHttpClient httpClient = new ShareHttpClient(activity, preferenceService);
        getLinkPresenter = new GetLinkPresenterImpl(
                httpClient, preferenceService, activity, this, dataBaseService);
        setTitle(R.string.title_get_link);
        FileRealm file;
        String fileUuid;
        if (savedInstanceState == null) {
            fileUuid = Objects.requireNonNull(getActivity().getIntent().getExtras()).getString(PvtboxFragment.FILE_ITEM);
        } else {
            fileUuid = savedInstanceState.getString(PvtboxFragment.FILE_ITEM);
        }
        file = dataBaseService.getFileByUuid(fileUuid);
        getLinkPresenter.setData(file);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(PvtboxFragment.FILE_ITEM, Objects.requireNonNull(getLinkPresenter).getData().getUuid());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.get_link_fragment, null);
        unbinder = ButterKnife.bind(this, view);

        connectingToServer = Objects.requireNonNull(getActivity()).findViewById(R.id.connecting_to_server);

        View password = inflater.inflate(R.layout.password_dialog, null);
        final AlertDialog.Builder builder = new AlertDialog.Builder(
                new ContextThemeWrapper(getActivity(), R.style.AppTheme));
        builder.setView(password);
        passwordText = password.findViewById(R.id.password_dialog_user_input_edit_text);
        builder.setPositiveButton(R.string.ok, null);
        builder.setNegativeButton(R.string.cancel,
                (DialogInterface dialog, int which) -> dialog.cancel());
        passwordDialog = builder.create();
        passwordDialog.setOnDismissListener(v -> Objects.requireNonNull(getLinkPasswordSwitch).setChecked(Objects.requireNonNull(getLinkPresenter).isPasswordSet()));

        builder.setTitle(R.string.expire_title);
        View expire = inflater.inflate(R.layout.expre_dialog, null);
        RadioGroup group = expire.findViewById(R.id.expire_group);
        builder.setView(expire);
        builder.setPositiveButton(R.string.ok, (dialogInterface, i) -> {
            int ttl = 0;
            switch (group.getCheckedRadioButtonId()) {
                case R.id.expire_instantly:
                    ttl = -1;
                    break;
                case R.id.expire_1day:
                    ttl = 60 * 60 * 24;
                    break;
                case R.id.expire_3days:
                    ttl = 60 * 60 * 24 * 3;
                    break;
            }
            Objects.requireNonNull(getLinkPresenter).setExpire(ttl);
        });
        expireDialog = builder.create();
        expireDialog.setOnDismissListener(v -> {
            group.clearCheck();
            Objects.requireNonNull(getLinkExpireSwitch).setChecked(Objects.requireNonNull(getLinkPresenter).isExpireSet());
        });

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initGui();
    }

    @SuppressLint("ClickableViewAccessibility")
    @SuppressWarnings("SameReturnValue")
    private void initGui() {
        Objects.requireNonNull(createLinkButton).setOnClickListener(
                (View v) -> Objects.requireNonNull(getLinkPresenter).clickCreateLink(false));
        Objects.requireNonNull(getLinkExpireSwitch).setOnTouchListener((view, motionEvent) -> {
            getLinkExpireSwitchTouched = true;
            return false;
        });
        getLinkExpireSwitch.setOnClickListener(
                v -> {
                    if (getLinkExpireSwitchTouched) {
                        getLinkExpireSwitchTouched = false;
                        Objects.requireNonNull(getLinkPresenter).changeDataSwitch(getLinkExpireSwitch.isChecked());
                    }
                });
        getLinkExpireSwitch.setOnCheckedChangeListener((compoundButton, b) -> {
            if (getLinkExpireSwitchTouched) {
                getLinkExpireSwitchTouched = false;
                Objects.requireNonNull(getLinkPresenter).changeDataSwitch(getLinkExpireSwitch.isChecked());
            }
        });

        Objects.requireNonNull(getLinkPasswordSwitch).setOnTouchListener(((view, motionEvent) -> {
            getLingPasswordSwitchTouched = true;
            return false;
        }));
        getLinkPasswordSwitch.setOnClickListener(
                v -> {
                    if (getLingPasswordSwitchTouched) {
                        getLingPasswordSwitchTouched = false;
                        Objects.requireNonNull(getLinkPresenter).changePasswordSwitch(getLinkPasswordSwitch.isChecked());
                    }
                });
        getLinkPasswordSwitch.setOnCheckedChangeListener(
                (v, c) -> {
                    if (getLingPasswordSwitchTouched) {
                        getLingPasswordSwitchTouched = false;
                        Objects.requireNonNull(getLinkPresenter).changePasswordSwitch(getLinkPasswordSwitch.isChecked());
                    }
                });

        setOnClickErrorCreateFirst();
    }

    @Override
    public void onStart() {
        super.onStart();
        Objects.requireNonNull(getLinkPresenter).onStart();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        unbinder.unbind();
    }

    @Override
    public void hideCreateLinkButton() {
        Objects.requireNonNull(createLinkButton).setVisibility(View.GONE);
    }

    @Override
    public void showLink(String link) {
        View view = Objects.requireNonNull(getActivity()).findViewById(R.id.get_link_toolbar_layout);
        TextView textView = view.findViewById(R.id.get_link_text_link);
        textView.setText(link);
        view.setVisibility(View.VISIBLE);
        View buttonCopy = view.findViewById(R.id.get_link_copy_button);
        View buttonRemove = view.findViewById(R.id.get_link_remove_button);
        View buttonSend = view.findViewById(R.id.get_link_send_button);

        buttonCopy.setOnClickListener((View v) -> Objects.requireNonNull(getLinkPresenter).onClickCopy());
        buttonSend.setOnClickListener((View v) -> Objects.requireNonNull(getLinkPresenter).onClickSend());
        buttonRemove.setOnClickListener((View v) -> Objects.requireNonNull(getLinkPresenter).onClickRemove());
        Objects.requireNonNull(relativeLayoutSwitchPassword).setOnClickListener(null);
        Objects.requireNonNull(relativeLayoutSwitchDate).setOnClickListener(null);
        Objects.requireNonNull(getLinkExpireSwitch).setClickable(true);
        Objects.requireNonNull(getLinkPasswordSwitch).setClickable(true);
        if (getLinkExpireSwitch != null)
            getLinkExpireSwitch.setEnabled(true);
        if (getLinkPasswordSwitch != null)
            getLinkPasswordSwitch.setEnabled(true);
        Objects.requireNonNull(getLinkPasswordSwitch).setChecked(Objects.requireNonNull(getLinkPresenter).isPasswordSet());
        getLinkExpireSwitch.setChecked(getLinkPresenter.isExpireSet());
    }

    @Override
    public void hideLink() {
        setOnClickErrorCreateFirst();
        Objects.requireNonNull(getActivity()).findViewById(R.id.get_link_toolbar_layout).setVisibility(View.GONE);
        Objects.requireNonNull(getLinkExpireSwitch).setEnabled(false);
        Objects.requireNonNull(getLinkPasswordSwitch).setEnabled(false);
        Objects.requireNonNull(createLinkButton).setVisibility(View.VISIBLE);
    }

    private void setOnClickErrorCreateFirst() {
        Objects.requireNonNull(relativeLayoutSwitchPassword).setOnClickListener(v -> showErrorCreateFirst());
        Objects.requireNonNull(relativeLayoutSwitchDate).setOnClickListener(v -> showErrorCreateFirst());
        Objects.requireNonNull(getLinkExpireSwitch).setClickable(false);
        Objects.requireNonNull(getLinkPasswordSwitch).setClickable(false);
    }

    private void showErrorCreateFirst() {
        Toast.makeText(getActivity(), R.string.create_firstly, Toast.LENGTH_LONG).show();
    }

    @Override
    public void showPasswordDialog() {
        passwordText.setError(null);
        passwordText.setText(null);
        passwordDialog.show();
        passwordDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String password = passwordText.getText().toString().trim();
            if (password.isEmpty()) {
                passwordText.setError(Objects.requireNonNull(getActivity()).getString(R.string.empty_password));
                return;
            } else if (!password.matches("^[a-zA-Z\\p{Digit}\\p{Punct}]+\\z")) {
                passwordText.setError(Objects.requireNonNull(getActivity()).getString(
                        R.string.password_invalid_symbols_error));
                return;
            }

            Objects.requireNonNull(getLinkPresenter).setPassword(password);
            passwordDialog.cancel();
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(Const.NETWORK_STATUS);
        LocalBroadcastManager.getInstance(Objects.requireNonNull(getActivity())).registerReceiver(
                networkStatusReceiver, filter);
        connectingToServer.setVisibility(
                SignalServerService.IsConnected() ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onPause() {
        passwordDialog.cancel();
        LocalBroadcastManager.getInstance(Objects.requireNonNull(getActivity())).unregisterReceiver(networkStatusReceiver);
        super.onPause();
    }

    @Override
    public void updatePasswordSwitch() {
        Objects.requireNonNull(getLinkPasswordSwitch).setChecked(Objects.requireNonNull(getLinkPresenter).isPasswordSet());
    }

    @Override
    public void showExpireDialog() {
        expireDialog.show();
    }

    @Override
    public void showCreateButton() {
        Objects.requireNonNull(createLinkButton).setVisibility(View.VISIBLE);
    }

    @Override
    public void updateExpireSwitch() {
        Objects.requireNonNull(getLinkExpireSwitch).setChecked(Objects.requireNonNull(getLinkPresenter).isExpireSet());
    }

    private void onNetworkStatus(@NonNull Intent intent) {
        boolean signalConnecting = intent.getBooleanExtra(
                Const.NETWORK_STATUS_SIGNAL_CONNECTING, false);
        connectingToServer.setVisibility(signalConnecting ? View.VISIBLE : View.GONE);
    }

}
