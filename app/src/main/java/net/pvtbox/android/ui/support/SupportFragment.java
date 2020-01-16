package net.pvtbox.android.ui.support;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatButton;

import android.os.Handler;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.Spinner;

import net.pvtbox.android.R;
import net.pvtbox.android.api.AuthHttpClient;
import net.pvtbox.android.ui.BaseActivity;
import net.pvtbox.android.ui.settings.PvtboxFragment;


import org.jetbrains.annotations.NotNull;

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
public class SupportFragment extends PvtboxFragment {

    @Nullable
    @BindView(R.id.send_button)
    AppCompatButton sendButton;
    @Nullable
    @BindView(R.id.support_message_edit_text)
    EditText supportMessage;
    @Nullable
    @BindView(R.id.support_subject_spinner)
    Spinner subjectSpinner;
    private Unbinder unbinder;
    private AuthHttpClient httpClient;


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        setTitle(R.string.title_support);
        BaseActivity activity = (BaseActivity) getActivity();
        assert activity != null;
        httpClient = activity.getAuthHttpClient();
    }

    @Override
    public void onCreateOptionsMenu(@NotNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.send, menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_send) {
            InputMethodManager keyboard = (InputMethodManager) Objects.requireNonNull(Objects.requireNonNull(getActivity())).getSystemService(
                    Context.INPUT_METHOD_SERVICE);
            if (keyboard != null) {
                keyboard.hideSoftInputFromWindow(Objects.requireNonNull(supportMessage).getWindowToken(), 0);
            }
            Handler h = new Handler();
            SupportSendHelper.send(
                    Objects.requireNonNull(getActivity()), Objects.requireNonNull(supportMessage),
                    Objects.requireNonNull(subjectSpinner), httpClient,
                    () -> h.postDelayed(() -> getActivity().finish(), 1000));
        }
        return super.onOptionsItemSelected(item);

    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.support_fragment, null);
        unbinder = ButterKnife.bind(this, view);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        assert sendButton != null;
        Handler h = new Handler();
        sendButton.setOnClickListener((View v) -> SupportSendHelper.send(
                Objects.requireNonNull(getActivity()), Objects.requireNonNull(supportMessage),
                Objects.requireNonNull(subjectSpinner), httpClient,
                () -> h.postDelayed(() -> getActivity().finish(), 1000)));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        unbinder.unbind();
    }
}
