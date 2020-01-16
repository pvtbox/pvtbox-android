package net.pvtbox.android.ui.files;



import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.signature.ObjectKey;

import net.pvtbox.android.R;
import net.pvtbox.android.db.model.FileRealm;
import net.pvtbox.android.tools.FileTool;
import io.realm.RealmRecyclerViewAdapter;

import java.io.File;
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
public class FilesAdapter extends RealmRecyclerViewAdapter<FileRealm, FilesAdapter.ViewHolder> {

    private final static String TAG = FilesAdapter.class.getSimpleName();

    public static final int TARGET_SIZE = 100;

    @NonNull
    private final Context context;
    private final FileItemListener fileItemListener;
    private final SelectionChangeListener selectionChangeListener;
    private final SelectionProvider selectionProvider;
    private final ModeProvider modeProvider;
    private final int greenColor;
    private final int grayColor;
    private final int whiteColor;
    private final int selectedColor;

    public FilesAdapter(@NonNull Context context,
                        FileItemListener fileItemListener,
                        SelectionProvider selectionProvider,
                        SelectionChangeListener selectionChangeListener,
                        ModeProvider modeProvider) {
        super(null, true, true);
        this.context = context;
        this.fileItemListener = fileItemListener;
        this.selectionChangeListener = selectionChangeListener;
        this.selectionProvider = selectionProvider;
        this.modeProvider = modeProvider;
        greenColor = ContextCompat.getColor(context, R.color.green);
        grayColor = ContextCompat.getColor(context, R.color.monsoon);
        whiteColor = ContextCompat.getColor(context, R.color.white);
        selectedColor = ContextCompat.getColor(context, R.color.iron);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(
                R.layout.file_card_view, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FileRealm file = getItem(position);
        if (file == null) {
            holder.itemView.setVisibility(View.GONE);
            return;
        } else if (holder.itemView.getVisibility() != View.VISIBLE) {
            holder.itemView.setVisibility(View.VISIBLE);
        }
        holder.file = file;
        Objects.requireNonNull(holder.fileDownloadProgressBar).getProgressDrawable().setTint(
                greenColor);
        holder.fileDownloadProgressBar.getIndeterminateDrawable().setTint(
                grayColor);
        Objects.requireNonNull(holder.nameCaption).setText(file.getName());
        setDateAndSize(holder, file);

        boolean isSelected = selectionProvider.selectedContains(file.getUuid());
        initCheckBox(holder, file, isSelected);
        setIconDownload(holder, file);

        Objects.requireNonNull(holder.cardRoot).setBackgroundColor(isSelected ? selectedColor : whiteColor);

        Objects.requireNonNull(holder.dotesImg).setVisibility(modeProvider.isMultiSelect() ? View.GONE : View.VISIBLE);
        setShare(holder, file);

        Objects.requireNonNull(holder.tapToDownloadView).setVisibility(
                modeProvider.isMultiSelect() || file.isFolder() || file.isProcessing() ||
                        file.isOffline() || file.isDownload() || file.isDownloadActual() ||
                        file.getCameraPath() != null ? View.GONE : View.VISIBLE);

        holder.dotesImg.setOnClickListener(v -> fileItemListener.onFileMenuClicked(holder.file));

        setLongClick(holder);
        setOnClick(holder, isSelected, file, position);
        setProgress(holder, file);
        setIcon(holder, file);

        setEnable(holder, file);
    }

    private void setShare(@NonNull ViewHolder holder, @NonNull FileRealm file) {
        Objects.requireNonNull(holder.linksImg).setVisibility(file.isShared() ? View.VISIBLE : View.INVISIBLE);
    }

    private void setDateAndSize(@NonNull ViewHolder holder, @NonNull FileRealm file) {
        String dateAndSize = FileUtils.getSizeAndDateString(file, context);
        Objects.requireNonNull(holder.dateCaption).setText(dateAndSize);
    }

    private void setEnable(@NonNull ViewHolder holder, @NonNull FileRealm file) {
        boolean enabled = !file.isProcessing();
        holder.itemView.setEnabled(enabled);
        Objects.requireNonNull(holder.dotesImg).setEnabled(enabled);
        Objects.requireNonNull(holder.selectedCheckBox).setEnabled(enabled);
        if (enabled) {
            Objects.requireNonNull(holder.leftImage).setColorFilter(null);
            if (Objects.requireNonNull(holder.fileDownloadProgressBar).isIndeterminate() &&
                    !file.isDownload() && file.getDownloadPath() == null) {
                holder.fileDownloadProgressBar.setIndeterminate(false);
                Objects.requireNonNull(holder.fileDownloadStatus).setText(null);
                holder.fileDownloadProgressBar.setVisibility(View.INVISIBLE);
                holder.fileDownloadStatus.setVisibility(View.INVISIBLE);
            }
        } else {
            Objects.requireNonNull(holder.leftImage).setColorFilter(R.color.monsoon_transparent);
            if (!Objects.requireNonNull(holder.fileDownloadProgressBar).isIndeterminate()) {
                holder.fileDownloadProgressBar.setIndeterminate(true);
                Objects.requireNonNull(holder.fileDownloadStatus).setText(R.string.processing_download);
                holder.fileDownloadProgressBar.setVisibility(View.VISIBLE);
                holder.fileDownloadStatus.setVisibility(View.VISIBLE);
            }
        }
    }

    private void setIconDownload(@NonNull ViewHolder holder, @NonNull FileRealm file) {
        if (file.isOffline()) {
            if (Objects.equals(Objects.requireNonNull(holder.cardRoot).getTag(), "offline")) return;
            holder.cardRoot.setTag("offline");
            Glide
                    .with(context)
                    .load(R.drawable.offline_active_green)
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .error(R.drawable.offline_active_green)
                    .into(Objects.requireNonNull(holder.downloadIcon));
            holder.downloadIcon.setVisibility(View.VISIBLE);
        } else if (file.isDownloadActual()
                && (!file.isFolder()
                || file.getSize() == file.getDownloadedSize())) {
            if (Objects.equals(Objects.requireNonNull(holder.cardRoot).getTag(), "downloaded")) return;
            holder.cardRoot.setTag("downloaded");
            Glide
                    .with(context)
                    .load(R.drawable.offline)
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .error(R.drawable.offline)
                    .into(Objects.requireNonNull(holder.downloadIcon));
            holder.downloadIcon.setVisibility(View.VISIBLE);
        } else {
            if (Objects.requireNonNull(holder.cardRoot).getTag() == null) return;
            holder.cardRoot.setTag(null);
            Objects.requireNonNull(holder.downloadIcon).setVisibility(View.GONE);
        }
    }

    private void initCheckBox(@NonNull ViewHolder holder, FileRealm file, boolean isSelected) {
        Objects.requireNonNull(holder.selectedCheckBox).setOnCheckedChangeListener(null);
        holder.selectedCheckBox.setChecked(isSelected);
        holder.selectedCheckBox.setVisibility(
                modeProvider.isMultiSelect() ? View.VISIBLE : View.GONE);
        holder.selectedCheckBox.setOnCheckedChangeListener(
                (buttonView, isChecked) -> setChecked(file, isChecked));
    }

    private void setIcon(@NonNull ViewHolder holder, @NonNull FileRealm file) {
        int icon = IconTool.getIcon(file);
        boolean image = FileTool.isImageOrVideoFile(file.getName());
        if (image) {
            holder.iconResource = 0;
            setIconFromFile(holder, file, icon);
        } else {
            if (holder.iconResource != icon) {
                String signature = file.getHashsum();
                if (signature == null) {
                    signature = file.getName();
                }
                Glide
                        .with(context)
                        .load(icon)
                        .dontAnimate()
                        .signature(new ObjectKey(signature))
                        .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                        .override(TARGET_SIZE, TARGET_SIZE)
                        .error(icon)
                        .into(Objects.requireNonNull(holder.leftImage));
                holder.iconResource = icon;
            }
        }
    }

    private void setIconFromFile(@NonNull ViewHolder holder, @NonNull FileRealm file, int icon) {
        String path = file.getPath();
        boolean fileExist = FileTool.isExist(path);
        String cameraPath = file.getCameraPath();
        boolean haveCamera = cameraPath != null && !cameraPath.isEmpty();
        boolean canPreview = fileExist || haveCamera;
        String signature = file.getHashsum();
        if (signature == null) {
            signature = file.getName();
        }
        if (canPreview) {
            File f = fileExist ? new File(path) : new File(cameraPath);
            Glide
                    .with(context)
                    .load(f)
                    .dontAnimate()
                    .signature(new ObjectKey(signature))
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .override(TARGET_SIZE, TARGET_SIZE)
                    .error(icon)
                    .into(Objects.requireNonNull(holder.leftImage));
        } else {
            Glide.with(context).clear(Objects.requireNonNull(holder.leftImage));
            Glide
                    .with(context)
                    .load(icon)
                    .dontAnimate()
                    .signature(new ObjectKey(signature))
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .override(TARGET_SIZE, TARGET_SIZE)
                    .error(icon)
                    .into(holder.leftImage);

        }
    }

    private void setProgress(@NonNull ViewHolder holder, @NonNull FileRealm file) {
        int progress = (int)((double)file.getDownloadedSize() / (double)file.getSize() * 100.0);
        if (file.isDownload() || file.isProcessing() || file.getDownloadPath() != null) {
            if (Objects.requireNonNull(holder.fileDownloadProgressBar).getVisibility() != View.VISIBLE) {
                holder.fileDownloadProgressBar.setVisibility(View.VISIBLE);
                Objects.requireNonNull(holder.fileDownloadStatus).setVisibility(View.VISIBLE);
            }

            int status = R.string.processing_download;
            if (!file.isProcessing() && file.getDownloadStatus() != 0) {
                status = file.getDownloadStatus();
            }

            if (progress < 100. && status == R.string.downloading) {
                if (holder.fileDownloadProgressBar.isIndeterminate()) {
                    holder.fileDownloadProgressBar.setIndeterminate(false);
                }
                Objects.requireNonNull(holder.fileDownloadStatus).setText(R.string.downloading);
                holder.fileDownloadProgressBar.setProgress(progress);
                holder.fileDownloadStatus.setTextColor(greenColor);
            } else {
                if (!holder.fileDownloadProgressBar.isIndeterminate()) {
                    holder.fileDownloadProgressBar.setIndeterminate(true);
                }
                if (progress != 100 || file.isFolder() || file.isProcessing()) {
                    if (!file.isFolder()) {
                        Objects.requireNonNull(holder.fileDownloadStatus).setText(status);
                    } else {
                        Objects.requireNonNull(holder.fileDownloadStatus).setText(R.string.processing_download);
                    }
                    holder.fileDownloadStatus.setTextColor(grayColor);
                } else {
                    Objects.requireNonNull(holder.fileDownloadStatus).setText(R.string.finishing_download);
                }
            }
        } else if (file.getUuid() != null && !file.getUuid().isEmpty()) {
            if (Objects.requireNonNull(holder.fileDownloadProgressBar).getVisibility() != View.INVISIBLE) {
                holder.fileDownloadProgressBar.setVisibility(View.INVISIBLE);
                Objects.requireNonNull(holder.fileDownloadStatus).setVisibility(View.INVISIBLE);
            }
        }
    }

    private void setOnClick(@NonNull ViewHolder holder, boolean isSelected, FileRealm file, int position) {
        if (modeProvider.isMultiSelect()) {
            holder.itemView.setOnClickListener((View v) -> {
                boolean isChecked = !isSelected;
                setChecked(file, isChecked);
                notifyItemChanged(position);
            });
        } else {
            holder.itemView.setOnClickListener(v -> fileItemListener.onFileClicked(holder.file));
        }
    }

    @SuppressWarnings("SameReturnValue")
    private void setLongClick(@NonNull ViewHolder holder) {
        holder.itemView.setOnLongClickListener(v -> {
            if (modeProvider.isMultiSelect()) {
                selectionChangeListener.onMultiSelectDisabled();
                selectionChangeListener.onCancelSelection();
            } else {
                selectionChangeListener.onMultiSelectEnabled();
                setChecked(holder.file, true);
            }
            vibrate();
            return true;
        });
    }

    private void setChecked(FileRealm file, boolean isChecked) {
        if (isChecked) {
            selectionChangeListener.onFileChecked(file);
        } else {
            selectionChangeListener.onFileUnchecked(file);
        }
    }

    private void vibrate() {
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                ((Vibrator) Objects.requireNonNull(context.getSystemService(
                        Context.VIBRATOR_SERVICE))).vibrate(
                                VibrationEffect.createOneShot(150, 10));
            } else {
                ((Vibrator) Objects.requireNonNull(context.getSystemService(
                        Context.VIBRATOR_SERVICE))).vibrate(150);
            }
        } catch (NullPointerException e) {
            Log.e(TAG, "vibrate failed");
        }
    }


    public class ViewHolder extends RecyclerView.ViewHolder {
        @Nullable
        @BindView(R.id.leftImage)
        ImageView leftImage;
        @Nullable
        @BindView(R.id.nameCaption)
        TextView nameCaption;
        @Nullable
        @BindView(R.id.dateCaption)
        TextView dateCaption;
        @Nullable
        @BindView(R.id.linkImg)
        ImageView linksImg;
        @Nullable
        @BindView(R.id.dotesImg)
        RelativeLayout dotesImg;
        @Nullable
        @BindView(R.id.selectedChckBox)
        CheckBox selectedCheckBox;
        @Nullable
        @BindView(R.id.cardRoot)
        View cardRoot;
        @Nullable
        @BindView(R.id.fileTapToDownload)
        View tapToDownloadView;
        @Nullable
        @BindView(R.id.fileDownloadProgressBar)
        ProgressBar fileDownloadProgressBar;
        @Nullable
        @BindView(R.id.fileDownloadStatus)
        TextView fileDownloadStatus;
        @Nullable
        @BindView(R.id.download_icon)
        ImageView downloadIcon;

        int iconResource = 0;
        @Nullable
        FileRealm file;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ButterKnife.bind(this, itemView);
            ViewGroup.LayoutParams layoutParams = Objects.requireNonNull(cardRoot).getLayoutParams();
            layoutParams.height -= 1;
            cardRoot.setLayoutParams(layoutParams);
        }
    }
}
