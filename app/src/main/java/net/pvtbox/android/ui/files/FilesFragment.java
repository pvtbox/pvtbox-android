package net.pvtbox.android.ui.files;

import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import net.pvtbox.android.R;
import net.pvtbox.android.application.Const;
import net.pvtbox.android.db.DataBaseService;
import net.pvtbox.android.db.model.FileRealm;
import net.pvtbox.android.tools.FileTool;
import net.pvtbox.android.ui.SafelyLinearLayoutManager;
import net.pvtbox.android.ui.ShowFragmentActivity;
import net.pvtbox.android.ui.files.dialog.UpdateFileDialog;
import net.pvtbox.android.ui.files.menu.FileMenuDialog;
import net.pvtbox.android.ui.files.presenter.FilesPresenter;
import net.pvtbox.android.ui.imageviewer.ImageViewerFragment;
import net.pvtbox.android.ui.main_screen.MainActivity;
import net.pvtbox.android.ui.main_screen.MainPresenter;
import net.pvtbox.android.ui.settings.PvtboxFragment;

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
public class FilesFragment extends Fragment
        implements FilesProviderListener, SelectionChangeListener,
        CurrentFolderProvider, FileItemListener {
    private static final String TAG = FilesFragment.class.getSimpleName();
    private static final java.lang.String TITLE = "TITLE";
    private static final String ROOT_ITEM = "ROOT_ITEM";

    private MainPresenter mainPresenter;
    private ModeProvider modeProvider;
    private SelectionProvider selectionProvider;
    @Nullable
    private FilesAdapter filesAdapter;
    @Nullable
    private FilesPresenter filesPresenter;
    @Nullable
    private FileListProvider fileListProvider;
    private DataBaseService dataBaseService;
    private FileTool fileTool;

    @Nullable
    private FilesMenuActionsHandler filesMenuActionsHandler;
    @Nullable
    private FilesPresenterImpl currentFolderProvider;
    private MoveActivatedListener moveActivatedListener;
    private CopyActivatedListener copyActivatedListener;

    @Nullable
    private FileMenuDialog fileMenuDialog;
    @Nullable
    private UpdateFileDialog updateFileDialog;

    private boolean enabled = false;

    @Nullable
    @BindView(R.id.empty_root_layout)
    LinearLayout emptyRootLayout;
    @Nullable
    @BindView(R.id.swipeContainer)
    SwipeRefreshLayout swipeContainer;
    @Nullable
    @BindView(R.id.empty_layout)
    LinearLayout emptyLayout;
    @Nullable
    @BindView(R.id.find_nothing_layout)
    LinearLayout findNothingLayout;
    @Nullable
    @BindView(R.id.loading_layout)
    LinearLayout loadingLayout;
    @Nullable
    @BindView(R.id.files_list_view)
    RecyclerView filesListView;

    @NonNull
    public static FilesFragment newInstance(String title, @Nullable FileRealm rootItem) {
        Bundle args = new Bundle();
        args.putSerializable(TITLE, title);
        args.putSerializable(ROOT_ITEM, rootItem == null ? null : rootItem.getUuid());
        FilesFragment fragment = new FilesFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);

        MainActivity activity = (MainActivity) getActivity();
        this.mainPresenter = Objects.requireNonNull(activity).getMainPresenter();
        this.modeProvider = activity.getModeProvider();
        this.selectionProvider = activity.getSelectionProvider();
        this.copyActivatedListener = activity.getCopyActivatedListener();
        this.moveActivatedListener = activity.getMoveActivatedListener();
        this.dataBaseService = activity.getDataBaseService();
        this.fileTool = activity.getFileTool();

        Bundle b = getArguments();
        String title = Objects.requireNonNull(b).getString(TITLE);
        String rootUuid = b.getString(ROOT_ITEM);
        FileRealm root = dataBaseService.getFileByUuid(rootUuid);

        FilesPresenterImpl presenter = new FilesPresenterImpl(
                getContext(), modeProvider, selectionProvider, this,
                dataBaseService, title, root);
        filesPresenter = presenter;
        fileListProvider = presenter;
        currentFolderProvider = presenter;
        filesAdapter = new FilesAdapter(
                Objects.requireNonNull(getContext()), this, selectionProvider, this,
                modeProvider);
        presenter.setAdapter(filesAdapter);

        FilesActionsHandler filesActionsHandler = new FilesActionsHandler(
                Objects.requireNonNull(getActivity()), activity.getPreferenceService(), selectionProvider,
                currentFolderProvider,
                () -> this, fileTool, dataBaseService);
        filesMenuActionsHandler = new FilesMenuActionsHandler(
                getContext(),
                selectionProvider,
                () -> this,
                moveActivatedListener,
                copyActivatedListener,
                filesActionsHandler);
        updateFileDialog = new UpdateFileDialog(getContext());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView");
        View view = inflater.inflate(R.layout.files_fragment, null);
        ButterKnife.bind(this, view);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initFilesListView();
        initSwipeToRefresh();
        fileMenuDialog = new FileMenuDialog(
                Objects.requireNonNull(getContext()), modeProvider, (SelectionProvider) mainPresenter,
                filesMenuActionsHandler);
        Objects.requireNonNull(filesMenuActionsHandler).setDialog(fileMenuDialog);
        filesMenuActionsHandler.setOfflineSwitch(fileMenuDialog);
    }

    @Override
    public void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();
        if (enabled) enable();
    }

    @Override
    public void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();
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
        Objects.requireNonNull(filesPresenter).onDestroy();
    }

    public void onSearch(@NonNull String query) {
        if (Objects.requireNonNull(filesPresenter).isEnabled()) {
            filesPresenter.onSearch(query.trim());
        }
    }

    public void setEnabled(boolean enabled) {
        Log.d(TAG, String.format("setEnabled: %s", enabled));
        this.enabled = enabled;
    }

    public void onSortingChanged() {
        Objects.requireNonNull(filesPresenter).onSortingChanged();
    }

    private void initFilesListView() {
        LinearLayoutManager linearLayoutManager = new SafelyLinearLayoutManager(getContext());
        linearLayoutManager.setItemPrefetchEnabled(true);
        linearLayoutManager.isMeasurementCacheEnabled();
        Objects.requireNonNull(filesListView).setLayoutManager(linearLayoutManager);
        filesListView.setAdapter(filesAdapter);
        filesListView.setHasFixedSize(true);
        filesListView.setItemViewCacheSize(30);
        filesListView.setDrawingCacheEnabled(true);
        filesListView.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH);
    }

    private void initSwipeToRefresh() {
        Objects.requireNonNull(swipeContainer).setOnRefreshListener(() -> {
            Log.d(TAG, "onRefresh");
            refresh();
            swipeContainer.setRefreshing(false);
        });
    }

    private void enable() {
        if (!Objects.requireNonNull(filesPresenter).isEnabled()) {
            filesPresenter.enable();
        }
        setTitleToBar();
    }

    private void disable() {
        if (Objects.requireNonNull(filesPresenter).isEnabled()) {
            filesPresenter.disable();
        }
    }

    private void refresh() {
        if (Objects.requireNonNull(filesPresenter).isEnabled()) {
            filesPresenter.refresh();
        }
    }

    public void setTitleToBar() {
        Log.d(TAG, "setTitleToPath");
        AppCompatActivity appCompatActivity = (AppCompatActivity) getActivity();

        ActionBar supportActionBar = Objects.requireNonNull(appCompatActivity).getSupportActionBar();
        assert supportActionBar != null;

        assert getActivity() != null;
        ((TitleChangeListener) getActivity()).changeTitle(Objects.requireNonNull(filesPresenter).getTitle());
        int color;
        if (modeProvider.isMultiSelect()) {
            color = ContextCompat.getColor(getActivity(), R.color.grey);
        } else {
            color = ContextCompat.getColor(getActivity(), R.color.toolbar_color);
        }
        supportActionBar.setBackgroundDrawable(new ColorDrawable(color));
    }

    // implements FilesProviderListener

    @Override
    public void onListLoaded() {
        Log.d(TAG, "onListLoaded");
        Objects.requireNonNull(filesListView).setVisibility(View.VISIBLE);
        Objects.requireNonNull(loadingLayout).setVisibility(View.GONE);
        Objects.requireNonNull(findNothingLayout).setVisibility(View.GONE);
        Objects.requireNonNull(emptyLayout).setVisibility(View.GONE);
        Objects.requireNonNull(emptyRootLayout).setVisibility(View.GONE);
    }

    @Override
    public void onEmpty() {
        Log.d(TAG, "onEmpty");
        Objects.requireNonNull(loadingLayout).setVisibility(View.GONE);
        Objects.requireNonNull(filesListView).setVisibility(View.GONE);
        if (modeProvider.inSearch()) {
            Objects.requireNonNull(emptyLayout).setVisibility(View.GONE);
            Objects.requireNonNull(emptyRootLayout).setVisibility(View.GONE);
            Objects.requireNonNull(findNothingLayout).setVisibility(View.VISIBLE);
        } else if (modeProvider.isRecent() || modeProvider.isDownloads()
                || !Objects.requireNonNull(filesPresenter).isRoot()) {
            Objects.requireNonNull(emptyRootLayout).setVisibility(View.GONE);
            Objects.requireNonNull(findNothingLayout).setVisibility(View.GONE);
            Objects.requireNonNull(emptyLayout).setVisibility(View.VISIBLE);
        } else {
            Objects.requireNonNull(emptyLayout).setVisibility(View.GONE);
            Objects.requireNonNull(findNothingLayout).setVisibility(View.GONE);
            Objects.requireNonNull(emptyRootLayout).setVisibility(View.VISIBLE);
        }
    }

    // implements SelectionChangeListener

    @Override
    public void onFileChecked(@NonNull FileRealm file) {
        Log.d(TAG, String.format("onFileChecked: %s", file.getName()));
        if (!file.isValid()) {
            refresh();
            return;
        }
        mainPresenter.onItemSelected(file);
        setTitleToBar();
    }

    @Override
    public void onFileUnchecked(@NonNull FileRealm file) {
        Log.d(TAG, String.format("onFileUnchecked: %s", file.getName()));
        if (!file.isValid()) {
            refresh();
            return;
        }
        mainPresenter.onItemUnselected(file);
        setTitleToBar();
    }

    @Override
    public void onSelectAll() {
        mainPresenter.onItemsSelected(Objects.requireNonNull(fileListProvider).getFiles());
        Objects.requireNonNull(filesPresenter).onCheckedChange();
        setTitleToBar();
    }

    @Override
    public void onCancelSelection() {
        Log.d(TAG, "onCancelSelection");
        mainPresenter.clearSelectedItems();
        Objects.requireNonNull(filesPresenter).onCheckedChange();
    }

    @Override
    public void onMultiSelectDisabled() {
        Log.d(TAG, "onMultiSelectDisabled");
        ((MainActivity) Objects.requireNonNull(getActivity())).onMultiSelectDisabled();
        Objects.requireNonNull(filesPresenter).onCheckedChange();
        setTitleToBar();
    }

    @Override
    public void onMultiSelectEnabled() {
        Log.d(TAG, "onMultiSelectEnabled");
        if (modeProvider.isCopyActionActive()) {
            copyActivatedListener.onCancelled();
        } else if (modeProvider.isMoveActionActive()) {
            moveActivatedListener.onCancelled();
        }
        ((MainActivity) Objects.requireNonNull(getActivity())).onMultiSelectEnabled();
        Objects.requireNonNull(filesPresenter).onCheckedChange();
        setTitleToBar();
    }

    // implements CurrentFolderProvider
    @Nullable
    @Override
    public FileRealm getCurrentFolder() {
        return currentFolderProvider == null ? null : currentFolderProvider.getCurrentFolder();
    }

    // implements FileItemListener
    @Override
    public void onFileMenuClicked(@NonNull FileRealm file) {
        if (!file.isValid()) {
            refresh();
            return;
        }
        Log.d(TAG, String.format("onFileMenuClicked: %s", file.getName()));
        mainPresenter.clearSelectedItems();
        if (modeProvider.isCopyActionActive()) {
            copyActivatedListener.onCancelled();
        } else if (modeProvider.isMoveActionActive()) {
            moveActivatedListener.onCancelled();
        }
        mainPresenter.onItemSelected(file);
        Objects.requireNonNull(fileMenuDialog).show();
    }

    @Override
    public void onFileClicked(@NonNull FileRealm file) {
        Log.d(TAG, String.format("onFileClicked: %s", file.getName()));
        if (!file.isValid()) {
            refresh();
            return;
        }
        if (file.isProcessing()) return;
        if (file.isOffline() || file.isFolder()) {
            openFile(file);
        } else {
            if (file.isDownload()) {
                Intent intent = new Intent(Const.REMOVE_FAILED_INTENT);
                String eventUuid = file.getEventUuid();
                intent.putExtra(Const.UUID, eventUuid);
                LocalBroadcastManager.getInstance(Objects.requireNonNull(getContext())).sendBroadcast(intent);
                return;
            }

            if (FileTool.isExist(file.getPath())) {
                if (file.isDownloadActual()) {
                    openFile(file);
                } else {
                    Objects.requireNonNull(updateFileDialog).show((v, a) -> openFile(file), (v, a) -> downloadFile(file));
                }
            } else {
                if (file.getCameraPath() != null && !file.getCameraPath().isEmpty() &&
                        FileTool.isExist(file.getCameraPath())) {
                    openFile(file);
                } else {
                    if (file.getCameraPath() != null) {
                        dataBaseService.dropCameraPath(file.getCameraPath());
                    }
                    downloadFile(file);
                }
            }
        }
    }

    private void openFile(@NonNull FileRealm file) {
        switch (file.getType()) {
            case "dir":
                if (selectionProvider.selectedContains(file.getUuid())) {
                    if (modeProvider.isCopyActionActive()) {
                        Toast.makeText(
                                getContext(),
                                String.format(Objects.requireNonNull(getContext()).getString(
                                        R.string.can_not_copy_folder_to_self),
                                        file.getName()),
                                Toast.LENGTH_LONG).show();
                        return;
                    } else if (modeProvider.isMoveActionActive()) {
                        Toast.makeText(
                                getContext(),
                                String.format(Objects.requireNonNull(getContext()).getString(
                                        R.string.can_not_move_folder_to_self),
                                        file.getName()),
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                }
                FilesFragment newFragment = newInstance(file.getName(), file);
                newFragment.setEnabled(true);
                ((MainActivity) Objects.requireNonNull(getActivity())).pushFragment(newFragment);
                break;
            case "jpg":
            case "png":
            case "jpeg":
            case "bmp":
                Intent intent = new Intent(getActivity(), ShowFragmentActivity.class);
                intent.putExtra(ShowFragmentActivity.CLASS, ImageViewerFragment.class);
                intent.putExtra(PvtboxFragment.FILE_ITEM, file.getUuid());
                startActivity(intent);
                break;
            default:
                if (!file.isOffline() &&
                        file.getCameraPath() != null &&
                        !file.getCameraPath().isEmpty() &&
                        FileTool.isExist(file.getCameraPath())) {
                    fileTool.openFile(file.getName(), file.getCameraPath());
                } else {
                    fileTool.openFile(file.getName(), file.getPath());
                }
                break;
        }
    }

    private void downloadFile(@NonNull FileRealm item) {
        if (!item.isValid()) {
            refresh();
            return;
        }
        if (item.isProcessing()) return;
        dataBaseService.downloadFile(item.getUuid());
    }
}
