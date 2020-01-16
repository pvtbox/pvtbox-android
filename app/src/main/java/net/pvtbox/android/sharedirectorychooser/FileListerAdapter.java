package net.pvtbox.android.sharedirectorychooser;

import android.content.Context;
import android.os.Environment;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.recyclerview.widget.RecyclerView;

import net.pvtbox.android.R;
import net.pvtbox.android.application.Const;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import yogesh.firzen.filelister.FileListerDialog;
import yogesh.firzen.mukkiasevaigal.M;
import yogesh.firzen.mukkiasevaigal.S;

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
class FileListerAdapter extends RecyclerView.Adapter<FileListerAdapter.FileListHolder> {

    @NonNull
    private List<File> data = new LinkedList<>();
    private File defaultDir = Environment.getExternalStorageDirectory();
    private File selectedFile = defaultDir;
    private FileListerDialog.FILE_FILTER fileFilter = FileListerDialog.FILE_FILTER.ALL_FILES;
    private final Context context;
    private final FilesListerView listerView;
    private final boolean enablePBDir;
    private boolean unreadableDir;

    FileListerAdapter(@NonNull FilesListerView view, boolean enablePBDir) {
        this.context = view.getContext();
        listerView = view;
        this.enablePBDir = enablePBDir;
    }

    void start() {
        fileLister(defaultDir);
    }

    void setDefaultDir(File dir) {
        defaultDir = dir;
    }

    FileListerDialog.FILE_FILTER getFileFilter() {
        return fileFilter;
    }

    void setFileFilter(FileListerDialog.FILE_FILTER fileFilter) {
        this.fileFilter = fileFilter;

    }

    private void fileLister(@NonNull File dir) {
        LinkedList<File> fs = new LinkedList<>();
        if (dir.getAbsolutePath().equals("/")
                || dir.getAbsolutePath().equals("/storage")
                || dir.getAbsolutePath().equals("/storage/emulated")
                || dir.getAbsolutePath().equals("/mnt")) {
            unreadableDir = true;
            File[] vols = context.getExternalFilesDirs(null);
            if (vols != null && vols.length > 0) {
                for (File file : vols) {
                    if (file != null) {
                        String path = file.getAbsolutePath();
                        path = path.replaceAll("/Android/data/([a-zA-Z_][.\\w]*)/files", "");
                        fs.add(new File(path));
                    }
                }
            } else {
                fs.add(Environment.getExternalStorageDirectory());
            }
        } else {
            unreadableDir = false;
            File[] files = dir.listFiles(file -> {
                if (file.isHidden()) return false;
                switch (getFileFilter()) {
                    case ALL_FILES:
                        return true;
                    case AUDIO_ONLY:
                        return S.isAudio(file) || file.isDirectory();
                    case IMAGE_ONLY:
                        return S.isImage(file) || file.isDirectory();
                    case VIDEO_ONLY:
                        return S.isVideo(file) || file.isDirectory();
                    case DIRECTORY_ONLY:
                        return file.isDirectory();
                }
                return false;
            });
            if (files != null) {
                fs = new LinkedList<>(Arrays.asList(files));
            }
        }
        M.L("From FileListAdapter", fs);
        data = new LinkedList<>(fs);
        Collections.sort(data, (f1, f2) -> {
            if ((f1.isDirectory() && f2.isDirectory()) || (!f1.isDirectory() && !f2.isDirectory()))
                return f1.getName().compareToIgnoreCase(f2.getName());
            else if (f1.isDirectory() && !f2.isDirectory())
                return -1;
            else if (!f1.isDirectory() && f2.isDirectory())
                return 1;
            else return 0;
        });
        selectedFile = dir;
        if (!dir.getAbsolutePath().equals("/")) {
            dirUp();
        }
        notifyDataSetChanged();
        listerView.scrollToPosition(0);
    }

    private void dirUp() {
        if (!unreadableDir) {
            data.add(0, selectedFile.getParentFile());
            data.add(1, null);
        }

    }

    @NotNull
    @Override
    public FileListerAdapter.FileListHolder onCreateViewHolder(@NotNull ViewGroup parent, int viewType) {
        return new FileListerAdapter.FileListHolder(View.inflate(getContext(), yogesh.firzen.filelister.R.layout.item_file_lister, null));
    }

    @Override
    public void onBindViewHolder(@NonNull FileListerAdapter.FileListHolder holder, int position) {
        File f = data.get(position);
        if (f != null) {
            holder.name.setText(f.getName());
        } else if (!unreadableDir) {
            holder.name.setText("Create a new Folder here");
            holder.icon.setImageResource(yogesh.firzen.filelister.R.drawable.ic_create_new_folder_black_48dp);
        }
        if (unreadableDir) {
            if (f != null) {
                if (position == 0) {
                    holder.name.setText(f.getName() + " (Internal)");
                } else {
                    holder.name.setText(f.getName() + " (External)");
                }
            }
        }
        if (position == 0 && f != null && !unreadableDir) {
            holder.icon.setImageResource(yogesh.firzen.filelister.R.drawable.ic_subdirectory_up_black_48dp);
        } else if (f != null) {
            if (f.isDirectory())
                holder.icon.setImageResource(yogesh.firzen.filelister.R.drawable.ic_folder_black_48dp);
            else if (S.isImage(f))
                holder.icon.setImageResource(yogesh.firzen.filelister.R.drawable.ic_photo_black_48dp);
            else if (S.isVideo(f))
                holder.icon.setImageResource(yogesh.firzen.filelister.R.drawable.ic_videocam_black_48dp);
            else if (S.isAudio(f))
                holder.icon.setImageResource(yogesh.firzen.filelister.R.drawable.ic_audiotrack_black_48dp);
            else
                holder.icon.setImageResource(yogesh.firzen.filelister.R.drawable.ic_insert_drive_file_black_48dp);
        }

        DrawableCompat.setTint(holder.icon.getDrawable(), ContextCompat.getColor(context, R.color.primary));


    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    File getSelected() {
        return selectedFile;
    }

    void goToDefault() {
        fileLister(defaultDir);
    }

    class FileListHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        final TextView name;
        final ImageView icon;

        FileListHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(yogesh.firzen.filelister.R.id.name);
            icon = itemView.findViewById(yogesh.firzen.filelister.R.id.icon);
            itemView.findViewById(yogesh.firzen.filelister.R.id.layout).setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            if (data.get(getPosition()) == null) {
                View view = View.inflate(getContext(), yogesh.firzen.filelister.R.layout.dialog_create_folder, null);
                final AppCompatEditText editText = view.findViewById(yogesh.firzen.filelister.R.id.edittext);
                AlertDialog.Builder builder = new AlertDialog.Builder(getContext())
                        .setView(view)
                        .setTitle("Enter the folder name")
                        .setPositiveButton("Create", (dialog, which) -> {

                        });
                final AlertDialog dialog = builder.create();
                dialog.show();
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v1 -> {
                    String name = Objects.requireNonNull(editText.getText()).toString();
                    if (TextUtils.isEmpty(name)) {
                        M.T(getContext(), "Please enter a valid folder name");
                    } else {
                        File file = new File(selectedFile, name);
                        if (file.exists()) {
                            M.T(getContext(), "This folder already exists.\n Please provide another name for the folder");
                        } else {
                            dialog.dismiss();
                            //noinspection ResultOfMethodCallIgnored
                            file.mkdirs();
                            fileLister(file);
                        }
                    }
                });
            } else {
                File f = data.get(getPosition());
                selectedFile = f;
                M.L("From FileLister", f.getAbsolutePath());
                if (!enablePBDir && f.getAbsolutePath().equals(Const.DEFAULT_PATH)) {
                    Toast.makeText(
                            getContext(),
                            R.string.you_can_not_download_file_in_pb_directory,
                            Toast.LENGTH_LONG)
                            .show();
                }
                else if (f.isDirectory()) {
                    fileLister(f);
                }
            }
        }
    }

    private Context getContext() {
        return context;
    }
}
