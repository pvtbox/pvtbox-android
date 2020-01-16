package net.pvtbox.android.ui.imageviewer;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.signature.ObjectKey;

import net.pvtbox.android.R;
import net.pvtbox.android.application.Const;
import net.pvtbox.android.db.model.FileRealm;
import net.pvtbox.android.tools.JSON;
import net.pvtbox.android.tools.diskspace.DiskSpace;
import net.pvtbox.android.tools.diskspace.DiskSpaceTool;
import net.pvtbox.android.ui.BaseActivity;
import net.pvtbox.android.ui.files.IconTool;

import java.io.File;
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

import butterknife.BindView;
import butterknife.ButterKnife;
import io.realm.Realm;


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
public class PropertyViewerActivity extends BaseActivity {
    private static final String TAG = PropertyViewerActivity.class.getSimpleName();
    @Nullable
    @BindView(R.id.toolbar)
    Toolbar toolbar;
    @Nullable
    @BindView(R.id.image_viewer_image)
    ImageView imageViewerImage;
    @Nullable
    @BindView(R.id.image_viewer_size)
    TextView imageViewerSize;
    @Nullable
    @BindView(R.id.image_viewer_type)
    TextView imageViewerType;
    @Nullable
    @BindView(R.id.image_viewer_files)
    TextView imageViewerFiles;
    @Nullable
    @BindView(R.id.image_viewer_permission)
    TextView imageViewerPermission;
    @Nullable
    @BindView(R.id.image_viewer_added)
    TextView imageViewerAdded;
    @Nullable
    @BindView(R.id.image_viewer_modified)
    TextView imageViewerModified;
    @Nullable
    @BindView(R.id.image_viewer_path)
    TextView imageViewerPath;

    @Nullable
    private FileRealm file;
    private Realm realm;

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        finish();
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_viewer);
        realm = Realm.getDefaultInstance();
        String fileUuid = getIntent().getStringExtra(Const.FILE_OPERATION_TARGET_OBJECT);
        file = realm.where(FileRealm.class)
                .equalTo("uuid", fileUuid)
                .findFirst();
        ButterKnife.bind(this);
        initGui(realm.copyFromRealm(Objects.requireNonNull(file)));
    }

    @Override
    protected void onDestroy() {
        realm.close();
        super.onDestroy();
    }

    private void initGui(@NonNull FileRealm file) {
        setSupportActionBar(toolbar);

        ActionBar supportActionBar = getSupportActionBar();
        if (supportActionBar != null) {
            supportActionBar.setTitle(file.getName());

            supportActionBar.setDisplayHomeAsUpEnabled(true);
            supportActionBar.setHomeButtonEnabled(true);
            final Drawable back = ContextCompat.getDrawable(this, R.drawable.back_icon);
            Objects.requireNonNull(back).setTint(ContextCompat.getColor(this, R.color.white));
            supportActionBar.setHomeAsUpIndicator(back);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (file != null && file.isValid()) {
            file.addChangeListener((realmModel, changeSet) -> {
                if (changeSet == null) return;
                if (changeSet.isDeleted()) return;
                showProperty(realm.copyFromRealm(file));
            });
            showProperty(realm.copyFromRealm(file));
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (file != null) file.removeAllChangeListeners();
    }

    private void showProperty(@NonNull FileRealm file) {
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        Objects.requireNonNull(imageViewerImage).setMaxHeight(metrics.heightPixels / 4);
        imageViewerImage.setMaxWidth(metrics.widthPixels);
        Objects.requireNonNull(imageViewerPath).setText(file.getPath());

        Date createDate = new Date(file.getDateCreated());
        String timeAddedString = DateFormat.getDateTimeInstance(
                        DateFormat.LONG, DateFormat.SHORT,
                        Locale.getDefault()).format(createDate);
        Objects.requireNonNull(imageViewerAdded).setText(timeAddedString);
        Date lastChanges = new Date(file.getDateModified());
        Objects.requireNonNull(imageViewerModified).setText(DateFormat.getDateTimeInstance(
                        DateFormat.LONG, DateFormat.SHORT,
                        Locale.getDefault()).format(lastChanges));
        Objects.requireNonNull(imageViewerType).setText(file.getType());


        if (file.isFolder()) {
            imageViewerImage.setImageResource(
                    file.isCollaborated() ? R.drawable.folder_shared : R.drawable.folder);
            DiskSpace downloadedSizeSpace = DiskSpaceTool.getDiskQuantity(
                    file.getDownloadedSize());
            DiskSpace sizeSpace = DiskSpaceTool.getDiskQuantity(file.getSize());
            Objects.requireNonNull(imageViewerSize).setText(
                    getString(R.string.file_size,
                            downloadedSizeSpace.getFormatStringValue(),
                            getString(downloadedSizeSpace.getIdQuantity()),
                            sizeSpace.getFormatStringValue(),
                            getString(sizeSpace.getIdQuantity())
                    ));
            Objects.requireNonNull(imageViewerFiles).setText(
                    getString(R.string.file_count_format,
                            file.getFilesCount(),
                            file.getOfflineFilesCount()));
            if (file.getParentUuid() == null && !file.isCollaborated()) {
                Objects.requireNonNull(imageViewerPermission).setText(R.string.owner);
            } else {
                loadPermissions(file);
            }
        } else {
            DiskSpace downloadedSizeSpace = DiskSpaceTool.getDiskQuantity(
                    file.getDownloadedSize());
            DiskSpace sizeSpace = DiskSpaceTool.getDiskQuantity(file.getSize());
            Objects.requireNonNull(imageViewerSize).setText(
                    getString(R.string.file_size,
                            downloadedSizeSpace.getFormatStringValue(),
                            getString(downloadedSizeSpace.getIdQuantity()),
                            sizeSpace.getFormatStringValue(),
                            getString(sizeSpace.getIdQuantity())
                    ));
            openFile(file);

            if (file.getParentUuid() == null) {
                Objects.requireNonNull(imageViewerPermission).setText(R.string.owner);
            } else {
                loadPermissions(file);
            }
        }
    }

    private void loadPermissions(@NonNull FileRealm file) {
        Handler mainThread = new Handler();
        mainThread.post(() -> {
            FileRealm rootParent = dataBaseService.getRootParent(file);
            if (rootParent == null || !rootParent.isCollaborated()) {
                Objects.requireNonNull(imageViewerPermission).setText(R.string.owner);
            } else {
                getCollaborationsHttpClient().info(
                        rootParent.getUuid(),
                        response -> mainThread.post(() -> {
                            String type = JSON.optString(response.optJSONObject("data"), "access_type", "owner");
                            switch (Objects.requireNonNull(type)) {
                                case "owner":
                                    Objects.requireNonNull(imageViewerPermission).setText(R.string.owner);
                                    break;
                                case "edit":
                                    Objects.requireNonNull(imageViewerPermission).setText(R.string.can_edit);
                                    break;
                                case "view":
                                    Objects.requireNonNull(imageViewerPermission).setText(R.string.can_view);
                                    break;
                            }
                            Log.d(TAG, String.format("loadPermissions: %s", response));
                        }),
                        error -> mainThread.post(() -> Objects.requireNonNull(imageViewerPermission).setText(R.string.owner))
                );
            }
        });
    }

    private void openFile(@NonNull FileRealm file) {
        boolean isImage = fileTool.isImage(file.getType());

        if (isImage) {
            Objects.requireNonNull(imageViewerImage).setImageBitmap(null);
            tryOpen(file);
        } else {
            Objects.requireNonNull(imageViewerImage).setImageResource(IconTool.getIcon(file));
        }
    }

    private void tryOpen(@NonNull FileRealm fileItem) {
        String cameraPath = fileItem.getCameraPath();
        String path = fileItem.getPath();
        if (!new File(path).exists()) {
            if (cameraPath != null && new File(cameraPath).exists()) {
                path = cameraPath;
            } else {
                Objects.requireNonNull(imageViewerImage).setImageResource(IconTool.getIcon(fileItem));
                return;
            }
        }
        String signature = fileItem.getHashsum();
        if (signature == null) {
            signature = fileItem.getName();
        }
        Glide
                .with(this)
                .load(path)
                .signature(new ObjectKey(signature))
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .override(Objects.requireNonNull(imageViewerImage).getMaxWidth(), imageViewerImage.getMaxHeight())
                .error(IconTool.getIcon(fileItem))
                .into(imageViewerImage);
    }
}
