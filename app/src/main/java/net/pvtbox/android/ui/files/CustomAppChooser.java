package net.pvtbox.android.ui.files;

import android.app.DialogFragment;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import net.pvtbox.android.R;
import net.pvtbox.android.db.model.FileRealm;
import net.pvtbox.android.tools.FileTool;
import net.pvtbox.android.ui.BaseActivity;

import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
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
public class CustomAppChooser extends DialogFragment {
    private final static String FILE = "file item";
    @Nullable
    @BindView(R.id.empty_list_view)
    TextView emptyListView;

    private FileTool fileTool;
    @Nullable
    @BindView(R.id.app_list_view)
    RecyclerView appListView;
    private Unbinder unbinder;


    @NonNull
    public static CustomAppChooser newInstance(@NonNull FileRealm file) {
        Bundle args = new Bundle();

        CustomAppChooser fragment = new CustomAppChooser();
        args.putSerializable(FILE, file.getUuid());
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.custom_app_chooser, null);
        unbinder = ButterKnife.bind(this, view);
        getDialog().setTitle(R.string.open_with);
        fileTool = ((BaseActivity) getActivity()).getFileTool();
        return view;
    }


    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        String uuid = getArguments().getString(FILE);
        FileRealm file = ((BaseActivity) getActivity()).getDataBaseService().getFileByUuid(uuid);
        initGui(Objects.requireNonNull(file));
    }

    private void initGui(@NonNull FileRealm file) {
        PackageManager pm = getActivity().getPackageManager();
        Intent viewIntent = new Intent(Intent.ACTION_VIEW);

        String type = URLConnection.guessContentTypeFromName(file.getPath());
        Uri uri = Uri.parse("file://"+file.getPath());
        viewIntent.setDataAndType(uri, type);

        List<ResolveInfo> resolveInfos = pm.queryIntentActivities(viewIntent, 0);

        List<AppItem> appItemList = new ArrayList<>();

        for (int i = 0; i < resolveInfos.size(); i++) {
            ResolveInfo ri = resolveInfos.get(i);
            String packageName = ri.activityInfo.loadLabel(pm).toString();
            Drawable drawable = ri.activityInfo.loadIcon(pm);

            appItemList.add(new AppItem(packageName, drawable, ri));
        }
        appItemList.add(
                new AppItem(
                        getActivity().getString(R.string.android),
                        getActivity().getDrawable(R.drawable.android),
                        null));

        if (appItemList.isEmpty()) {
            Objects.requireNonNull(emptyListView).setVisibility(View.VISIBLE);
            Objects.requireNonNull(appListView).setVisibility(View.GONE);
        } else {
            AppChooserAdapter appChooserAdapter =
                    new AppChooserAdapter(appItemList, file, fileTool, getActivity(), this);
            Objects.requireNonNull(appListView).setAdapter(appChooserAdapter);
            appListView.setHasFixedSize(true);
            appListView.setNestedScrollingEnabled(false);
            appListView.setLayoutManager(new LinearLayoutManager(getActivity()));
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        unbinder.unbind();
    }
}
