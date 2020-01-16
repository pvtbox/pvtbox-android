package net.pvtbox.android.ui.imageviewer;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.signature.ObjectKey;
import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;

import net.pvtbox.android.R;
import net.pvtbox.android.db.model.FileRealm;
import net.pvtbox.android.tools.FileTool;
import net.pvtbox.android.ui.BaseActivity;
import net.pvtbox.android.ui.settings.PvtboxFragment;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.net.URLConnection;
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

public class ImageViewerFragment extends Fragment {
    @Nullable
    @BindView(R.id.image)
    SubsamplingScaleImageView image;
    @Nullable
    @BindView(R.id.image_view)
    ImageView imageView;
    private Unbinder unbinder;
    @Nullable
    private FileRealm file;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.layout_image_viewer, null);
        unbinder = ButterKnife.bind(this, view);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        String uuid = Objects.requireNonNull(getActivity()).getIntent().getStringExtra(PvtboxFragment.FILE_ITEM);
        file = ((BaseActivity) getActivity()).getDataBaseService().getFileByUuid(uuid);
        String name = Objects.requireNonNull(file).getName();

        setTitle(name);
        showImage(file);
        initBackButton();
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(@NotNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.share, menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_share) {
            Intent sendIntent = new Intent();
            sendIntent.setAction(Intent.ACTION_SEND);
            String path = getPath(Objects.requireNonNull(file));
            sendIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(new File(Objects.requireNonNull(path))));
            sendIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            sendIntent.setType(URLConnection.guessContentTypeFromName(path));
            startActivity(sendIntent);
        }
        return super.onOptionsItemSelected(item);

    }

    private void initBackButton() {
        Toolbar toolbar = Objects.requireNonNull(getActivity()).findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(view1 -> getActivity().finish());
    }

    @Nullable
    private String getPath(@NonNull FileRealm file) {
        String path;
        String cameraPath = file.getCameraPath();
        if(cameraPath == null){
            path = file.getPath();
        } else {
            path = FileTool.isExist(file.getPath()) ? file.getPath() : cameraPath;
        } return path;
    }

    private void setTitle(String name) {
        AppCompatActivity appCompatActivity = (AppCompatActivity) getActivity();
        if (appCompatActivity != null) {
            ActionBar bar = appCompatActivity.getSupportActionBar();
            if (bar != null) {
                bar.setTitle(name);
            }
        }
    }

    private void showImage(@NonNull FileRealm file) {
        Objects.requireNonNull(image).setOnImageEventListener(new SubsamplingScaleImageView.OnImageEventListener() {
            @Override
            public void onReady() {

            }

            @Override
            public void onImageLoaded() {

            }

            @Override
            public void onPreviewLoadError(Exception e) {
                image.setVisibility(View.GONE);
                Objects.requireNonNull(imageView).setVisibility(View.VISIBLE);
                String signature = file.getHashsum();
                if (signature == null) {
                    signature = file.getName();
                }
                Glide
                        .with(Objects.requireNonNull(getActivity()))
                        .load(getPath(file))
                        .signature(new ObjectKey(signature))
                        .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                        .into(imageView);
            }

            @Override
            public void onImageLoadError(Exception e) {
                image.setVisibility(View.GONE);
                Objects.requireNonNull(imageView).setVisibility(View.VISIBLE);
                String signature = file.getHashsum();
                if (signature == null) {
                    signature = file.getName();
                }
                Glide
                        .with(Objects.requireNonNull(getActivity()))
                        .load(getPath(file))
                        .signature(new ObjectKey(signature))
                        .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                        .into(imageView);
            }

            @Override
            public void onTileLoadError(Exception e) {
                image.setVisibility(View.GONE);
                Objects.requireNonNull(imageView).setVisibility(View.VISIBLE);
                String signature = file.getHashsum();
                if (signature == null) {
                    signature = file.getName();
                }
                Glide
                        .with(Objects.requireNonNull(getActivity()))
                        .load(getPath(file))
                        .signature(new ObjectKey(signature))
                        .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                        .into(imageView);
            }

            @Override
            public void onPreviewReleased() {

            }
        });
        image.setImage(ImageSource.uri(Uri.fromFile(new File(Objects.requireNonNull(getPath(file))))));
    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        unbinder.unbind();
    }
}
