package net.pvtbox.android.ui.login;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.coordinatorlayout.widget.CoordinatorLayout;

import com.amirarcane.lockscreen.activity.EnterPinActivity;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;
import androidx.appcompat.widget.AppCompatButton;
import android.text.TextUtils;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import net.pvtbox.android.R;
import net.pvtbox.android.application.App;
import net.pvtbox.android.application.Const;
import net.pvtbox.android.service.PvtboxService;
import net.pvtbox.android.tools.EmailValidator;
import net.pvtbox.android.tools.JSON;
import net.pvtbox.android.ui.BaseActivity;
import net.pvtbox.android.ui.main_screen.MainActivity;

import org.json.JSONObject;

import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;


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
public class LoginActivity extends BaseActivity {

    @Nullable
    @BindView(R.id.radioBtnSignIn)
    RadioButton radioBtnSignIn;
    @Nullable
    @BindView(R.id.radioBtnSignUp)
    RadioButton radioBtnSignUp;
    @Nullable
    @BindView(R.id.loginRadioGroup)
    RadioGroup loginRadioGroup;
    @Nullable
    @BindView(R.id.emailTxt)
    EditTextClearFocusOnBack emailTxt;
    @Nullable
    @BindView(R.id.emailTxtHolder)
    TextInputLayout emailTxtHolder;
    @Nullable
    @BindView(R.id.passwordTxt)
    EditTextClearFocusOnBack passwordTxt;
    @Nullable
    @BindView(R.id.passwordTxtHolder)
    TextInputLayout passwordTxtHolder;
    @Nullable
    @BindView(R.id.passwordConfirmTxt)
    EditTextClearFocusOnBack passwordConfirmTxt;
    @Nullable
    @BindView(R.id.passwordConfirmTxtHolder)
    TextInputLayout passwordConfirmTxtHolder;
    @Nullable
    @BindView(R.id.passwordRemind)
    TextView passwordRemind;
    @Nullable
    @BindView(R.id.loginBtn)
    AppCompatButton loginBtn;
    @Nullable
    @BindView(R.id.content_layout)
    ScrollView contentLayout;
    @Nullable
    @BindView(R.id.coordinator)
    CoordinatorLayout coordinator;
    @Nullable
    @BindView(R.id.checkbox_policy_confirm)
    CheckBox checkboxPolicyConfirm;
    @Nullable
    @BindView(R.id.confirm_policy_layout)
    LinearLayout confirmPolicyLayout;
    @Nullable
    @BindView(R.id.hostButton)
    TextView hostButton;
    @Nullable
    @BindView(R.id.hostTxtHolder)
    TextInputLayout hostTxtHolder;
    @Nullable
    @BindView(R.id.hostTxt)
    EditTextClearFocusOnBack hostTxt;

    private boolean enabled = true;
    private boolean loginState = true;
    private boolean rulesConfirmed = false;
    private boolean showingHost = false;
    @Nullable
    private Timer timer;
    @Nullable
    private Snackbar snack;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        ButterKnife.bind(this);
        Objects.requireNonNull(App.getApplication()).shouldShowFreeLicenseMessage = false;
        boolean enable = true;
        if (savedInstanceState != null) {
            enable = savedInstanceState.getBoolean("enabled", true);
            loginState = savedInstanceState.getBoolean("loginState", true);
            rulesConfirmed = savedInstanceState.getBoolean("rulesConfirmed", false);
        }
        Objects.requireNonNull(emailTxt).setText(preferenceService.getMail());
        Objects.requireNonNull(checkboxPolicyConfirm).setChecked(rulesConfirmed);
        checkboxPolicyConfirm.setOnCheckedChangeListener((compoundButton, checked) -> onRulesCheckChange(checked));
        if (loginState) {
            showLogin();
        } else {
            showRegister();
        }
        if (!enable) {
            showProgressBar();
        }
        if (preferenceService.isSelfHosted()) {
            showHost();
            Objects.requireNonNull(hostTxt).setText(preferenceService.getHost());
        } else {
            hideHost();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        if (snack != null) {
            if (snack.isShown()) snack.dismiss();
            snack = null;
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("loginState", loginState);
        outState.putBoolean("enabled", enabled);
        outState.putBoolean("rulesConfirmed", rulesConfirmed);
    }

    private void onRulesCheckChange(boolean checked) {
        rulesConfirmed = checked;
    }

    @OnClick({R.id.hostButton, R.id.radioBtnSignIn, R.id.radioBtnSignUp, R.id.passwordRemind, R.id.loginBtn, R.id.text_view_rules, R.id.text_view_privacy_policy})
    public void onViewClicked(@NonNull View view) {
        preferenceService.setHost(Objects.requireNonNull(Objects.requireNonNull(hostTxt).getText()).toString().trim().toLowerCase());
        switch (view.getId()) {
            case R.id.radioBtnSignIn:
                loginState = true;
                showLogin();
                break;
            case R.id.radioBtnSignUp:
                loginState = false;
                showRegister();
                break;
            case R.id.passwordRemind:
                remindPassword();
                break;
            case R.id.loginBtn:
                actionClicked();
                break;
            case R.id.text_view_rules:
                showRules();
                break;
            case R.id.text_view_privacy_policy:
                showPolicy();
                break;
            case R.id.hostButton:
                if (showingHost) {
                    hideHost();
                } else {
                    showHost();
                }
        }
    }

    private void showHost() {
        showingHost = true;
        showLogin();
        Objects.requireNonNull(hostTxtHolder).setVisibility(View.VISIBLE);
        Objects.requireNonNull(hostButton).setText(R.string.regular_user);
        Objects.requireNonNull(radioBtnSignUp).setEnabled(false);
    }

    private void hideHost() {
        showingHost = false;
        Objects.requireNonNull(hostTxtHolder).setVisibility(View.GONE);
        Objects.requireNonNull(hostButton).setText(R.string.self_hosted_user);
        Objects.requireNonNull(radioBtnSignUp).setEnabled(true);
    }

    private void actionClicked() {
        String mail = Objects.requireNonNull(Objects.requireNonNull(emailTxt).getText()).toString().trim().toLowerCase();
        String password = Objects.requireNonNull(Objects.requireNonNull(passwordTxt).getText()).toString();
        String passwordConfirm = Objects.requireNonNull(Objects.requireNonNull(passwordConfirmTxt).getText()).toString();
        String host = Objects.requireNonNull(Objects.requireNonNull(hostTxt).getText()).toString().trim().toLowerCase();
        if (!validate(mail, password, host)) {
            return;
        }
        if (loginState) {
            showProgressBar();
            login(mail, password);
        } else {
            if (!rulesConfirmed) {
                showSnackbar(getBaseContext().getString(R.string.please_confirm_rule_and_policy));
                return;
            }
            if (password.equals(passwordConfirm)) {
                showErrorPasswordConfirm(null);
                showProgressBar();
                register(mail, password);
            } else {
                showErrorPasswordConfirm(getBaseContext().getString(R.string.password_must_equals));
            }
        }
    }

    private boolean validate(@NonNull String email, @NonNull String password, @NonNull String host) {
        boolean result = true;
        if (!EmailValidator.isValid(email)) {
            showErrorMail(getBaseContext().getString(R.string.wrong_email_format));
            result = false;
        } else {
            showErrorMail(null);
        }
        preferenceService.setMail(email);
        if (host.isEmpty() && showingHost) {
            Objects.requireNonNull(hostTxtHolder).setError(getBaseContext().getString(R.string.empty_host));
            result = false;
        } else {
            Objects.requireNonNull(hostTxtHolder).setError(null);
            if (showingHost) {
                preferenceService.setHost(host);
            } else {
                preferenceService.setHost(Const.BASE_URL);
            }
        }
        if (password.isEmpty()) {
            showErrorPassword(getBaseContext().getString(R.string.empty_password));
            result = false;
        } else {
            if (!loginState && password.length() < 6) {
                showErrorPassword(getBaseContext().getString(R.string.password_length_error));
                result = false;
            } else if (!loginState && !password.matches(
                    "^[a-zA-Z\\p{Digit}\\p{Punct}]+\\z")) {
                showErrorPassword(getBaseContext().getString(
                        R.string.password_invalid_symbols_error));
                result = false;
            } else {
                showErrorPassword(null);
            }
        }

        return result;
    }

    private void login(String email, @NonNull String password) {
        Handler handler = new Handler();
        authHttpClient.login(
                email, password,
                response -> handler.post(() -> onLoggedIn(response)),
                error -> handler.post(() -> onError(error)));
    }

    private void register(String email, @NonNull String password) {
        Handler handler = new Handler();
        authHttpClient.register(
                email, password,
                response -> handler.post(() -> onLoggedIn(response)),
                error -> handler.post(() -> onError(error)));
    }

    private void onLoggedIn(@NonNull JSONObject response) {
        String userHash = JSON.optString(response, "user_hash", null);
        if (userHash == null || userHash.isEmpty()) {
            onError(response);
            return;
        }
        String host = preferenceService.getHost();
        boolean wipeNeeded = !preferenceService.getCurrentHost().equals(host);
        if (wipeNeeded) {
            preferenceService.setCurrentHost(host);
            preferenceService.setSelfHosted(!host.equals(Const.BASE_URL));
        }
        if (wipeNeeded || !userHash.equals(preferenceService.getUserHash())) {
            preferenceService.setAutoCameraUpdate(false);
            preferenceService.setCameraFolderUuid(null);
            preferenceService.setLastEventUuid(null);
            dataBaseService.clearDb();
        }
        preferenceService.setUserHash(userHash);
        preferenceService.setLoggedIn(true);
        PvtboxService.startPbService(getBaseContext(), response.toString());
        if (preferenceService.askSetPasscode()) {
            preferenceService.unsetAskSetPasscode();
            AlertDialog.Builder builder = new AlertDialog.Builder(
                    new ContextThemeWrapper(this.getWindow().getContext(), R.style.AppTheme));
            builder.setTitle(R.string.ask_set_passcode_title);
            builder.setMessage(R.string.ask_set_passcode_message)
                    .setPositiveButton(R.string.set_passcode, (dialog, id) -> {
                        Intent intent = EnterPinActivity.getIntent(this, true);
                        startActivityForResult(intent, 1);
                    });
            builder.setNegativeButton(R.string.no_thanks, (dialog, which) -> openMain());
            builder.create();
            builder.show();
        } else {
            openMain();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1) {
            openMain();
        }
    }

    private void onError(@Nullable JSONObject error) {
        showContent();
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        String message;
        if (error == null) {
            message = getBaseContext().getString(R.string.network_error);
        } else {
            String errcode = JSON.optString(error, "errcode");
            if (Objects.equals(errcode, "LOCKED_CAUSE_TOO_MANY_BAD_LOGIN")) {
                JSONObject data = error.optJSONObject("data");
                assert data != null;
                long lockSeconds = data.optLong("bl_lock_seconds");
                long lastTimestamp = data.optLong("bl_last_timestamp");
                long currentTimestamp = data.optLong("bl_current_timestamp");
                final long[] countdown = {lockSeconds - currentTimestamp + lastTimestamp};
                message = getBaseContext().getString(R.string.ip_locked_template, countdown[0]);
                timer = new Timer();
                snack = showSnackbar(message);
                snack.setDuration(Snackbar.LENGTH_INDEFINITE);
                snack.show();
                Handler handler = new Handler();
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        countdown[0] -= 1;
                        if (countdown[0] <= 0) {
                            if (timer != null) {
                                timer.cancel();
                                handler.post(() -> {
                                    snack.setDuration(Snackbar.LENGTH_LONG);
                                    snack.setText(
                                            getBaseContext().getString(R.string.ip_unlocked));
                                    snack.show();
                                    snack = null;
                                });
                            }
                            return;
                        }
                        handler.post(() -> snack.setText(getBaseContext().getString(
                                R.string.ip_locked_template, countdown[0])));
                    }
                }, 0, 1000);
                return;
            } else {
                message = JSON.optString(error, "info", getBaseContext().getString(R.string.network_error));
            }
        }

        showSnackbar(Objects.requireNonNull(message));
    }

    private void showLogin() {
        Objects.requireNonNull(passwordRemind).setVisibility(View.VISIBLE);
        Objects.requireNonNull(passwordConfirmTxtHolder).setVisibility(View.GONE);
        Objects.requireNonNull(confirmPolicyLayout).setVisibility(View.GONE);
        Objects.requireNonNull(radioBtnSignIn).setChecked(true);
        Objects.requireNonNull(radioBtnSignUp).setChecked(false);
        Objects.requireNonNull(loginBtn).setText(R.string.sign_in);
    }

    private void showRegister() {
        Objects.requireNonNull(loginBtn).setText(R.string.sign_up);
        Objects.requireNonNull(passwordRemind).setVisibility(View.GONE);
        Objects.requireNonNull(passwordConfirmTxtHolder).setVisibility(View.VISIBLE);
        Objects.requireNonNull(confirmPolicyLayout).setVisibility(View.VISIBLE);
        Objects.requireNonNull(radioBtnSignUp).setChecked(true);
        Objects.requireNonNull(radioBtnSignIn).setChecked(false);
    }

    private void enableAll(boolean value) {
        Objects.requireNonNull(loginBtn).setEnabled(value);
        Objects.requireNonNull(radioBtnSignIn).setEnabled(value);
        Objects.requireNonNull(radioBtnSignUp).setEnabled(value);
        Objects.requireNonNull(loginRadioGroup).setEnabled(value && !showingHost);
        Objects.requireNonNull(emailTxt).setEnabled(value);
        Objects.requireNonNull(emailTxtHolder).setEnabled(value);
        Objects.requireNonNull(passwordTxt).setEnabled(value);
        Objects.requireNonNull(passwordTxtHolder).setEnabled(value);
        Objects.requireNonNull(passwordConfirmTxt).setEnabled(value);
        Objects.requireNonNull(passwordConfirmTxtHolder).setEnabled(value);
        Objects.requireNonNull(passwordRemind).setEnabled(value);
        Objects.requireNonNull(contentLayout).setEnabled(value);
        Objects.requireNonNull(coordinator).setEnabled(value);
        Objects.requireNonNull(checkboxPolicyConfirm).setEnabled(value);
        Objects.requireNonNull(confirmPolicyLayout).setEnabled(value);
        enabled = value;
    }

    @NonNull
    private Snackbar showSnackbar(@NonNull String message) {
        Snackbar snackbar = Snackbar.make(Objects.requireNonNull(coordinator), message, Snackbar.LENGTH_LONG);
        View view = snackbar.getView();
        view.setBackgroundColor(getResources().getColor(R.color.white));
        TextView tv = view.findViewById(com.google.android.material.R.id.snackbar_text);
        if (tv != null) {
            tv.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            tv.setGravity(Gravity.CENTER_HORIZONTAL);
            tv.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            tv.setMaxLines(4);
        }
        snackbar.show();
        return snackbar;
    }

    private void showErrorMail(String error) {
        Objects.requireNonNull(emailTxtHolder).setError(error);
    }

    private void showErrorPassword(String error) {
        Objects.requireNonNull(passwordTxtHolder).setError(error);
    }

    private void showErrorPasswordConfirm(String error) {
        Objects.requireNonNull(passwordConfirmTxtHolder).setError(error);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    private void openMain() {
        preferenceService.setLoggedIn(true);
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        Intent initIntent = null;
        if (getIntent().getExtras() != null) {
            initIntent = (Intent) getIntent().getExtras().get(Const.INIT_INTENT);
        }
        intent.putExtra(Const.INIT_INTENT, initIntent);
        startActivity(intent);
        finish();
    }

    private void remindPassword() {
        String mail = Objects.requireNonNull(Objects.requireNonNull(emailTxt).getText()).toString().trim().toLowerCase();
        if (!EmailValidator.isValid(mail)) {
            showErrorMail(getBaseContext().getString(R.string.wrong_email_format));
            return;
        } else {
            showErrorMail(null);
        }
        showProgressBar();
        Handler handler = new Handler();
        authHttpClient.remindPassword(
                mail,
                response -> handler.post(() -> onError(response)),
                error -> handler.post(() -> onError(error)));
    }

    private void showContent() {
        Objects.requireNonNull(contentLayout).setVisibility(View.VISIBLE);
        Objects.requireNonNull(loginBtn).setAlpha(1f);
        enableAll(true);
        if (loginState) {
            showLogin();
        } else {
            showRegister();
        }
    }

    private void showProgressBar() {
        Objects.requireNonNull(loginBtn).setText(R.string.please_wait);
        loginBtn.setAlpha(.5f);
        enableAll(false);
    }

    private void showRules() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(preferenceService.getHost() + "/rules"));
        startActivity(intent);
    }

    private void showPolicy() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(preferenceService.getHost() + "/privacy-policy"));
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        unregisterOperationProgressReceiver();
    }
}
