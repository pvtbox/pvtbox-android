package net.pvtbox.android.ui.main_screen;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;

import android.text.InputType;
import android.util.Base64;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;
import com.levibostian.shutter_android.Shutter;
import com.levibostian.shutter_android.builder.ShutterResultCallback;
import com.levibostian.shutter_android.builder.ShutterResultListener;
import com.levibostian.shutter_android.vo.ShutterResult;
import com.ncapdevi.fragnav.FragNavController;
import com.ncapdevi.fragnav.FragNavTransactionOptions;

import net.pvtbox.android.R;
import net.pvtbox.android.application.App;
import net.pvtbox.android.application.Const;
import net.pvtbox.android.db.DataBaseService;
import net.pvtbox.android.db.model.DeviceRealm;
import net.pvtbox.android.db.model.FileRealm;
import net.pvtbox.android.service.PreferenceService;
import net.pvtbox.android.service.PvtboxService;
import net.pvtbox.android.service.signalserver.SignalServerService;
import net.pvtbox.android.sharedirectorychooser.ChooseDirectoryDialog;
import net.pvtbox.android.tools.FileTool;
import net.pvtbox.android.tools.SpeedTool;
import net.pvtbox.android.tools.diskspace.DiskSpace;
import net.pvtbox.android.tools.diskspace.DiskSpaceTool;
import net.pvtbox.android.ui.BaseActivity;
import net.pvtbox.android.ui.ShowFragmentActivity;
import net.pvtbox.android.ui.device.DeviceFragment;
import net.pvtbox.android.ui.files.CopyActivatedListener;
import net.pvtbox.android.ui.files.CurrentFolderProvider;
import net.pvtbox.android.ui.files.FilesActionsHandler;
import net.pvtbox.android.ui.files.FilesFragment;
import net.pvtbox.android.ui.files.FilesMenuActionsHandler;
import net.pvtbox.android.ui.files.ModeProvider;
import net.pvtbox.android.ui.files.MoveActivatedListener;
import net.pvtbox.android.ui.files.SelectionChangeListener;
import net.pvtbox.android.ui.files.SelectionChangeListenerProvider;
import net.pvtbox.android.ui.files.SelectionProvider;
import net.pvtbox.android.ui.files.TitleChangeListener;
import net.pvtbox.android.ui.files.menu.AddMenuDialog;
import net.pvtbox.android.ui.files.menu.MultiSelectMenuDialog;
import net.pvtbox.android.ui.files.menu.OnMenuListener;
import net.pvtbox.android.ui.login.LoginActivity;
import net.pvtbox.android.ui.notifications.NotificationsFragment;
import net.pvtbox.android.ui.settings.SettingsFragment;
import net.pvtbox.android.ui.support.SupportDialog;
import net.pvtbox.android.ui.support.SupportFragment;

import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Objects;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import io.realm.Realm;
import uk.co.deanwild.marqueetextview.MarqueeTextView;

import static yogesh.firzen.filelister.FileListerDialog.FILE_FILTER.DIRECTORY_ONLY;

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
public class MainActivity extends BaseActivity
        implements FragNavController.RootFragmentListener, SearchView.OnQueryTextListener,
        SelectionChangeListenerProvider, MoveActivatedListener, CopyActivatedListener,
        CurrentFolderProvider, TitleChangeListener {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int REQUEST_CODE_PICK_PHOTO = 1;
    private static final int REQUEST_CODE_PICK_FILE = 2;
    private static final int REQUEST_CODE_SEND_FILE = 3;

    @NonNull
    private final String CURRENT_TAG = "current tag";
    private static final int ALL = FragNavController.TAB1;
    private static final int RECENT = FragNavController.TAB2;
    private static final int OFFLINE = FragNavController.TAB3;
    private static final int DOWNLOADS = FragNavController.TAB4;
    private TextView notificationsBadge;
    private AddMenuDialog addMenuDialog;
    private EditText newFolderDialogText;
    private AlertDialog newFolderDialog;
    private EditText insertLinkDialogText;
    private AlertDialog insertLinkDialog;
    @Nullable
    private MultiSelectMenuDialog multiSelectDialog;
    private FilesMenuActionsHandler filesMenuActionsHandler;
    private FilesActionsHandler filesActionsHandler;
    private final BroadcastReceiver networkStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, @NonNull Intent intent) {
            onNetworkStatus(intent);
        }
    };
    @Nullable
    private ShutterResultListener shutterResultListener;
    @Nullable
    private Realm realm;
    @Nullable
    private DeviceRealm ownDevice;
    private final Runnable initNetworkStatusRunnable = this::initNetworkStatus;
    private final Handler handler = new Handler();
    private int primaryColor;
    private int grayColor;

    enum Tag {
        all, recent, offline, downloads
    }

    private Tag currentTag = Tag.all;

    private MainPresenter mainPresenter;
    private ModeProvider modeProvider;
    private SelectionProvider selectionProvider;

    private ArrayList<Fragment> rootFragments;
    private FragNavController fragNavController;
    private TextView userLoginTextView;
    private ImageView infoLogoButton;

    private ActionBarDrawerToggle actionBarDrawerToggle;
    private BadgeDrawerArrowDrawable badgeDrawable;
    private MenuItem currentItem;

    @Nullable
    @BindView(R.id.search_view)
    SearchView searchView;
    @Nullable
    @BindView(R.id.toolbar)
    Toolbar toolbar;
    @Nullable
    @BindView(R.id.nav_view)
    NavigationView navView;
    @Nullable
    @BindView(R.id.drawer_layout)
    DrawerLayout drawerLayout;
    @Nullable
    @BindView(R.id.fabAddButton)
    FloatingActionButton fabAddButton;
    @Nullable
    @BindView(R.id.fabPasteButton)
    FloatingActionButton fabPasteButton;
    @Nullable
    @BindView(R.id.fabCancelButton)
    FloatingActionButton fabCancelButton;
    @Nullable
    @BindView(R.id.fabDownloadsButton)
    FloatingActionButton fabDownloadsButton;
    @Nullable
    @BindView(R.id.connecting_to_server)
    View connectingToServer;
    @Nullable
    @BindView(R.id.title)
    TextView title;
    @Nullable
    @BindView(R.id.connecting_info)
    View connectingInfo;
    @Nullable
    @BindView(R.id.download)
    TextView download;
    @Nullable
    @BindView(R.id.upload)
    TextView upload;
    @Nullable
    @BindView(R.id.peers_connected)
    MarqueeTextView peersConnected;
    @Nullable
    @BindView(R.id.ni_exit)
    View exit;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        primaryColor = ContextCompat.getColor(this, R.color.primary);
        grayColor = ContextCompat.getColor(this, R.color.aluminum);

        MainPresenterImpl presenter = new MainPresenterImpl();
        mainPresenter = presenter;
        modeProvider = presenter;
        selectionProvider = presenter;

        super.onCreate(savedInstanceState);

        mainPresenter.setSorting(preferenceService.getSorting());

        setContentView(R.layout.activity_home);
        ButterKnife.bind(this);

        if (savedInstanceState != null) {
            onRestoreInstanceState(savedInstanceState);
        }
        currentItem = Objects.requireNonNull(navView).getMenu().getItem(currentTag.ordinal());

        FragNavTransactionOptions fragNavTransactionOptions =
                FragNavTransactionOptions.newBuilder()
                        .customAnimations(
                                R.anim.push_left_in, R.anim.push_left_out,
                                R.anim.push_right_in, R.anim.push_right_out)
                        .build();
        FragNavController.Builder builder = Objects.requireNonNull(FragNavController.newBuilder(
                savedInstanceState,
                getSupportFragmentManager(),
                R.id.content_home, this))
                .defaultTransactionOptions(fragNavTransactionOptions);

        rootFragments = new ArrayList<>(3);
        rootFragments.add(FilesFragment.newInstance(getString(R.string.all_files), null));
        rootFragments.add(FilesFragment.newInstance(getString(R.string.recent_files), null));
        rootFragments.add(FilesFragment.newInstance(getString(R.string.offline_file), null));
        rootFragments.add(FilesFragment.newInstance(getString(R.string.downloads), null));
        builder.rootFragments(rootFragments);
        builder.rootFragmentListener(this, 4);
        fragNavController = builder.build();

        filesActionsHandler = new FilesActionsHandler(
                this, preferenceService, selectionProvider, this, this,
                fileTool, dataBaseService);
        filesMenuActionsHandler = new FilesMenuActionsHandler(
                this,
                selectionProvider,
                this,
                this,
                this,
                filesActionsHandler);

        if (!FileTool.isExist(Const.DEFAULT_PATH)) {
            fileTool.createDirectory(Const.DEFAULT_PATH);
        }

        initToolBar();
        initDrawer();
        initSearch();
        initMultiSelectMenu();
        initAddMenu();
        initNewFolderDialog();
        initInsertLinkDialog();

        IntentFilter filter = new IntentFilter(Const.NETWORK_STATUS);
        LocalBroadcastManager.getInstance(this).registerReceiver(networkStatusReceiver, filter);

        onNewIntent(getIntent());

        Objects.requireNonNull(peersConnected).setSpeed(2);
    }

    private void initNetworkStatus() {
        Log.d(TAG, "initNetworkStatus");
        handler.removeCallbacks(initNetworkStatusRunnable);
        realm = Realm.getDefaultInstance();
        ownDevice = realm.where(DeviceRealm.class)
                .equalTo("own", true)
                .findFirst();
        if (ownDevice == null) {
            deinitNetworkStatus();
            handler.postDelayed(initNetworkStatusRunnable, 1000);
            return;
        }
        ownDevice.addChangeListener((realmModel, changeSet) -> {
            if (changeSet == null) return;
            else if (changeSet.isDeleted()) {
                deinitNetworkStatus();
                handler.postDelayed(initNetworkStatusRunnable, 1000);
                return;
            }
            if (changeSet.isFieldChanged("downloadSpeed") ||
                    changeSet.isFieldChanged("downloadedSize")) {
                SpeedTool.Speed s = SpeedTool.getSpeed(ownDevice.getDownloadSpeed());
                DiskSpace space = DiskSpaceTool.getDiskQuantity(ownDevice.getDownloadedSize());
                Objects.requireNonNull(download).setText(getString(R.string.network_speed_status_format,
                        s.getSpeed(),
                        getString(s.getIdQuantity()),
                        space.getSize(),
                        getString(space.getIdQuantity())
                ));
            }
            if (changeSet.isFieldChanged("uploadSpeed") ||
                    changeSet.isFieldChanged("uploadedSize")) {
                SpeedTool.Speed s = SpeedTool.getSpeed(ownDevice.getUploadSpeed());
                DiskSpace space = DiskSpaceTool.getDiskQuantity(ownDevice.getUploadedSize());
                Objects.requireNonNull(upload).setText(getString(R.string.network_speed_status_format,
                        s.getSpeed(),
                        getString(s.getIdQuantity()),
                        space.getSize(),
                        getString(space.getIdQuantity())
                ));
            }
            if (changeSet.isFieldChanged("connectedNodesCount")) {
                updateConnectedPeers();
            }
            if (changeSet.isFieldChanged("paused")) {
                updateDownloadsButton(null);
            }
            if (changeSet.isFieldChanged("notificationsCount")) {
                updateNotificationsBadge(ownDevice.getNotificationsCount());
            }
        });
        SpeedTool.Speed s = SpeedTool.getSpeed(ownDevice.getDownloadSpeed());
        DiskSpace space = DiskSpaceTool.getDiskQuantity(ownDevice.getDownloadedSize());
        Objects.requireNonNull(download).setText(getString(R.string.network_speed_status_format,
                s.getSpeed(),
                getString(s.getIdQuantity()),
                space.getSize(),
                getString(space.getIdQuantity())
        ));
        s = SpeedTool.getSpeed(ownDevice.getUploadSpeed());
        space = DiskSpaceTool.getDiskQuantity(ownDevice.getUploadedSize());
        Objects.requireNonNull(upload).setText(getString(R.string.network_speed_status_format,
                s.getSpeed(),
                getString(s.getIdQuantity()),
                space.getSize(),
                getString(space.getIdQuantity())
        ));
        updateConnectedPeers();
        updateDownloadsButton(null);
        updateNotificationsBadge(ownDevice.getNotificationsCount());
    }

    private void updateConnectedPeers() {
        int peersCount = ownDevice == null ? 0 : ownDevice.getConnectedNodesCount();

        Objects.requireNonNull(peersConnected).setText(peersCount == 1 ? getString(R.string.peer_connected) :
                getString(R.string.peers_connected_format, peersCount));
        peersConnected.setTextColor(ContextCompat.getColor(this, R.color.green));
        if (peersCount == 0) {
            handler.postDelayed(() -> {
                if (ownDevice != null && ownDevice.getConnectedNodesCount() == 0) {
                    peersConnected.setTextColor(ContextCompat.getColor(this, R.color.red));
                    peersConnected.setText(getString(R.string.connect_more_devices));
                }
            }, 5000);
        }
    }

    private void updateDownloadsButton(@Nullable Boolean paused) {
        if (paused == null) {
            paused = Objects.requireNonNull(ownDevice).isPaused();
        }
        Objects.requireNonNull(fabDownloadsButton).setBackgroundTintList(ColorStateList.valueOf(
                paused ? primaryColor : grayColor));
        fabDownloadsButton.setImageResource(
                paused ? R.drawable.play : R.drawable.pause);
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);

        if (!PvtboxService.isServiceRunning(this)) {
            Log.i(TAG, "onNewIntent: starting service");
            PvtboxService.startPbService(this, null);
            handler.postDelayed(() -> handleNewIntent(intent), 3000);
        } else {
            Log.i(TAG, "onNewIntent: service already running");
            handler.postDelayed(() -> handleNewIntent(intent), 1000);
        }
    }

    private void handleNewIntent(@NonNull Intent intent) {
        if (intent.getExtras() == null) return;
        Intent initIntent = (Intent) intent.getExtras().get(Const.INIT_INTENT);
        if (initIntent == null) return;
        intent.removeExtra(Const.INIT_INTENT);

        Log.i(TAG, String.format(
                "handleNewIntent: %s, extras: %s", initIntent, initIntent.getExtras()));

        String action = initIntent.getAction();
        boolean share = initIntent.getBooleanExtra(Const.SHARING_ENABLE, false);
        Uri data = initIntent.getData();

        if (Intent.ACTION_VIEW.equals(action)) {
            if (data != null) {
                String[] shareDataList = data.toString().split("/");
                String shareHash = shareDataList[shareDataList.length - 1];
                if (shareHash != null && !shareHash.isEmpty()) {
                    showDirectoryChooser(shareHash);
                }
            }
        } else if (Intent.ACTION_SEND.equals(action)) {
            Uri uri = initIntent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (uri == null) {
                Toast.makeText(this, R.string.unsupported_action, Toast.LENGTH_LONG).show();
            } else {
                filesActionsHandler.onAddFile(uri, share);
            }
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            ArrayList<Uri> uris = initIntent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (uris == null) {
                Toast.makeText(this, R.string.unsupported_action, Toast.LENGTH_LONG).show();
            } else {
                filesActionsHandler.onAddShareFiles(uris, share);
            }
        }
    }

    private void showDirectoryChooser(String shareHash) {
        ChooseDirectoryDialog dialog = ChooseDirectoryDialog.createFileListerDialog(
                this, R.style.AppTheme_Dark_DialogWhite, null, true);
        dialog.setFileFilter(DIRECTORY_ONLY);
        dialog.setDefaultDir(Const.DEFAULT_PATH);
        dialog.setOnFileSelectedListener(
                (file, path) -> {
                    PvtboxService.startPbService(
                            this, shareHash, path);
                    Intent i = new Intent(Const.OPERATIONS_PROGRESS_INTENT);
                    String message = this.getString(R.string.share_download_started);
                    i.putExtra(Const.OPERATIONS_PROGRESS_MESSAGE, message);
                    i.putExtra(Const.OPERATIONS_PROGRESS_SHOW_AND_DISMISS, false);
                    i.putExtra(Const.OPERATIONS_PROGRESS_SHOW_SHARE_CANCEL, true);
                    LocalBroadcastManager.getInstance(this).sendBroadcast(i);
                });
        dialog.show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.d(TAG, "onCreateOptionsMenu");
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.home, menu);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(@NonNull Menu menu) {
        Log.d(TAG, "onPrepareOptionsMenu");
        boolean isMultiSelect = modeProvider.isMultiSelect();
        menu.setGroupVisible(R.id.base_actions, !isMultiSelect);
        menu.findItem(R.id.multi_select_menu_action).setVisible(isMultiSelect);
        menu.findItem(R.id.action_sort_descending_date).setChecked(!modeProvider.isSortingByName());
        menu.findItem(R.id.action_sort_alphabetically).setChecked(modeProvider.isSortingByName());
        if (!isMultiSelect) {
            if (modeProvider.isRecent() || modeProvider.isDownloads()) {
                menu.findItem(R.id.action_sort_descending_date).setVisible(false);
                menu.findItem(R.id.action_sort_alphabetically).setVisible(false);
            }
            if (modeProvider.isCopyActionActive() || modeProvider.isMoveActionActive()) {
                menu.findItem(R.id.action_select).setVisible(false);
            }
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        Log.d(TAG, "onOptionsItemSelected");
        switch (item.getItemId()) {
            case R.id.action_sort_alphabetically:
                item.setChecked(true);
                mainPresenter.setSortByName();
                ((FilesFragment) Objects.requireNonNull(fragNavController.getCurrentFrag())).onSortingChanged();
                preferenceService.setSorting(modeProvider.getSorting());
                break;
            case R.id.action_sort_descending_date:
                item.setChecked(true);
                mainPresenter.setSortByDate();
                ((FilesFragment) Objects.requireNonNull(fragNavController.getCurrentFrag())).onSortingChanged();
                preferenceService.setSorting(modeProvider.getSorting());
                break;
            case R.id.action_select:
                Objects.requireNonNull(getSelectionChangeListener()).onMultiSelectEnabled();
                break;
            case R.id.multi_select_menu_action:
                Objects.requireNonNull(multiSelectDialog).show();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");

        initNetworkStatus();

        if (preferenceService.getUserHash() == null || preferenceService.getUserHash().isEmpty()) {
            Toast.makeText(getApplicationContext(), R.string.logged_out_by_action, Toast.LENGTH_LONG).show();
            Intent intentLogin = new Intent(getApplicationContext(), LoginActivity.class);
            intentLogin.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intentLogin.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intentLogin);
            finish();
            return;
        }

        if (currentItem != null) {
            currentItem.setChecked(true);
        }
    }

    @Override
    protected void onPause() {
        if (!Objects.requireNonNull(searchView).isIconified()) {
            searchView.onActionViewCollapsed();
            ((FilesFragment) Objects.requireNonNull(fragNavController.getCurrentFrag())).onSearch("");
            mainPresenter.setInSearch(false);
            actionBarDrawerToggle.setHomeAsUpIndicator(R.drawable.back_home);
            actionBarDrawerToggle.setDrawerIndicatorEnabled(
                    Objects.requireNonNull(fragNavController.getCurrentStack()).size() == 1);
            showTitle();
        }
        deinitNetworkStatus();
        super.onPause();
    }

    private void deinitNetworkStatus() {
        if (ownDevice != null) {
            ownDevice.removeAllChangeListeners();
            ownDevice = null;
        }
        if (realm != null) {
            realm.close();
            realm = null;
        }
        handler.removeCallbacks(initNetworkStatusRunnable);
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy");
        LocalBroadcastManager.getInstance(this).unregisterReceiver(networkStatusReceiver);
        Objects.requireNonNull(connectingToServer).setVisibility(View.GONE);
        Objects.requireNonNull(connectingInfo).setVisibility(View.GONE);
        super.onDestroy();
    }

    private void onNetworkStatus(@NonNull Intent intent) {
        if (modeProvider.isMultiSelect() || modeProvider.inSearch()) return;
        boolean signalConnecting = intent.getBooleanExtra(
                Const.NETWORK_STATUS_SIGNAL_CONNECTING, false);
        int header = intent.getIntExtra(
                Const.NETWORK_STATUS_INFO_HEADER, R.string.connecting_to_server);
        int info = intent.getIntExtra(Const.NETWORK_STATUS_INFO, R.string.wait_while_connecting);
        Objects.requireNonNull(connectingInfo).setOnClickListener(v -> new AlertDialog.Builder(
                new ContextThemeWrapper(this, R.style.AppTheme))
                .setTitle(header)
                .setMessage(info)
                .setPositiveButton(R.string.ok, null)
                .show());
        if (signalConnecting) {
            showConnecting();
        } else {
            showTitle();
        }
    }

    @Nullable
    @Override
    public SelectionChangeListener getSelectionChangeListener() {
        return (SelectionChangeListener) fragNavController.getCurrentFrag();
    }

    @Nullable
    @Override
    public FileRealm getCurrentFolder() {
        CurrentFolderProvider provider = (CurrentFolderProvider) fragNavController.getCurrentFrag();
        return provider == null ? null : provider.getCurrentFolder();
    }

    @SuppressWarnings("SameReturnValue")
    private void initDrawer() {
        Log.d(TAG, "initDrawer");
        if (fragNavController.getCurrentFrag() instanceof FilesFragment) {
            ((FilesFragment) fragNavController.getCurrentFrag()).setEnabled(true);
        }

        Objects.requireNonNull(navView).setNavigationItemSelectedListener(item -> {
            handler.postDelayed(() -> Objects.requireNonNull(drawerLayout).closeDrawers(), 300);
            int id = item.getItemId();
            mainPresenter.disableMultiSelect();
            assert fragNavController.getCurrentStack() != null;
            Objects.requireNonNull(getSelectionChangeListener()).onMultiSelectDisabled();
            getSelectionChangeListener().onCancelSelection();
            onCancelled();
            mainPresenter.clearSelectedItems();
            if (fragNavController.getCurrentFrag() instanceof FilesFragment) {
                ((FilesFragment) fragNavController.getCurrentFrag()).setEnabled(false);
            }
            if (id == R.id.all_files) {
                item.setChecked(true);
                currentTag = Tag.all;
                fragNavController.switchTab(ALL);
                currentItem = item;
                ((FilesFragment) Objects.requireNonNull(fragNavController.getCurrentFrag())).setEnabled(true);
            } else if (id == R.id.recent_files) {
                item.setChecked(true);
                currentTag = Tag.recent;
                fragNavController.switchTab(RECENT);
                currentItem = item;
                ((FilesFragment) Objects.requireNonNull(fragNavController.getCurrentFrag())).setEnabled(true);
            } else if (id == R.id.offline_files) {
                item.setChecked(true);
                currentTag = Tag.offline;
                fragNavController.switchTab(OFFLINE);
                currentItem = item;
                ((FilesFragment) Objects.requireNonNull(fragNavController.getCurrentFrag())).setEnabled(true);
            } else if (id == R.id.downloads) {
                item.setChecked(true);
                currentTag = Tag.downloads;
                fragNavController.switchTab(DOWNLOADS);
                currentItem = item;
                ((FilesFragment) Objects.requireNonNull(fragNavController.getCurrentFrag())).setEnabled(true);
            } else if (id == R.id.ni_notifications) {
                item.setChecked(false);
                currentItem.setChecked(true);
                openNotifications();
            } else if (id == R.id.ni_devices) {
                item.setChecked(false);
                currentItem.setChecked(true);
                openDeviceList();
            } else if (id == R.id.ni_settings) {
                item.setChecked(false);
                currentItem.setChecked(true);
                openSettings();
            } else if (id == R.id.ni_support) {
                item.setChecked(false);
                currentItem.setChecked(true);
                openSupport();
            } else if (id == R.id.ni_help) {
                item.setChecked(false);
                currentItem.setChecked(true);
                openHelp();
            }

            mainPresenter.setRecent(currentTag == Tag.recent);
            mainPresenter.setOffline(currentTag == Tag.offline);
            mainPresenter.setDownloads(currentTag == Tag.downloads);
            if (modeProvider.isRecent() || modeProvider.isDownloads()) {
                Objects.requireNonNull(fabAddButton).hide();
            } else {
                Objects.requireNonNull(fabAddButton).show();
            }
            if (modeProvider.isDownloads()) {
                Objects.requireNonNull(fabDownloadsButton).show();
            } else {
                Objects.requireNonNull(fabDownloadsButton).hide();
            }
            actionBarDrawerToggle.setDrawerIndicatorEnabled(
                    fragNavController.getCurrentStack().size() == 1);
            return true;
        });
        notificationsBadge = navView.getMenu()
                .findItem(R.id.ni_notifications)
                .getActionView()
                .findViewById(R.id.notifications_badge);
        actionBarDrawerToggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar, R.string.open_drawer, R.string.close_drawer) {

            @Override
            public void onDrawerSlide(View drawerView, float slideOffset) {
                super.onDrawerSlide(drawerView, slideOffset);
                if (searchView != null) {
                    InputMethodManager imm = (InputMethodManager) getSystemService(
                            Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.hideSoftInputFromWindow(searchView.getWindowToken(), 0);
                    }
                }
            }

            @Override
            public void onDrawerClosed(View drawerView) {
                super.onDrawerClosed(drawerView);
            }

            @Override
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                if (searchView != null) {
                    InputMethodManager imm = (InputMethodManager) getSystemService(
                            Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.hideSoftInputFromWindow(searchView.getWindowToken(), 0);
                    }
                }
            }
        };
        Objects.requireNonNull(drawerLayout).addDrawerListener(actionBarDrawerToggle);
        badgeDrawable = new BadgeDrawerArrowDrawable(Objects.requireNonNull(getSupportActionBar()).getThemedContext());
        actionBarDrawerToggle.setDrawerArrowDrawable(badgeDrawable);
        actionBarDrawerToggle.setHomeAsUpIndicator(R.drawable.back_home);
        actionBarDrawerToggle.syncState();
        actionBarDrawerToggle.setToolbarNavigationClickListener(view -> onBackPressed());
        actionBarDrawerToggle.setDrawerIndicatorEnabled(
                Objects.requireNonNull(fragNavController.getCurrentStack()).size() == 1);
        updateNotificationsBadge(0);
    }

    @SuppressLint("SetTextI18n")
    private void updateNotificationsBadge(long count) {
        if (count == 0) {
            badgeDrawable.setEnabled(false);
            notificationsBadge.setVisibility(View.INVISIBLE);
        } else {
            badgeDrawable.setEnabled(true);
            notificationsBadge.setVisibility(View.VISIBLE);
            if (count > 99) {
                badgeDrawable.setText("99+");
            } else {
                badgeDrawable.setText(String.valueOf(count));
            }
            if (count > 999) {
                notificationsBadge.setText("999+");
            } else {
                notificationsBadge.setText(String.valueOf(count));
            }
        }
    }

    @OnClick(R.id.ni_exit)
    protected void exitClick() {
        exitApp();
        sendBroadcast(new Intent(Const.ACTION_EXIT));
    }

    private void openHelp() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(
                preferenceService.getHost() + "/faq"));
        startActivity(intent);
    }

    private void openSupport() {
        Intent intent = new Intent(getApplication(), ShowFragmentActivity.class);
        intent.putExtra(ShowFragmentActivity.CLASS, SupportFragment.class);
        startActivity(intent);
    }

    private void openDeviceList() {
        Intent intent = new Intent(getApplication(), ShowFragmentActivity.class);
        intent.putExtra(ShowFragmentActivity.CLASS, DeviceFragment.class);
        startActivityForResult(intent, 13);
    }

    private void openNotifications() {
        Intent intent = new Intent(getApplication(), ShowFragmentActivity.class);
        intent.putExtra(ShowFragmentActivity.CLASS, NotificationsFragment.class);
        startActivityForResult(intent, 131);
    }

    private void openSettings() {
        Intent intent = new Intent(getApplication(), ShowFragmentActivity.class);
        intent.putExtra(ShowFragmentActivity.CLASS, SettingsFragment.class);
        startActivityForResult(intent, 13);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        Log.d(TAG, "onActivityResult");
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 13) {
            if (data != null) {
                boolean kill = data.getBooleanExtra("kill", false);
                if (kill) {
                    finish();
                    return;
                }
            }
        }
        if (requestCode == 131) {
            if (data != null) {
                String folderName = data.getStringExtra("folder_name");
                openAllFilesFolder(folderName);
                return;
            }
        }
        if (resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                switch (requestCode) {
                    case REQUEST_CODE_PICK_PHOTO:
                        filesActionsHandler.onAddPhoto(uri);
                        break;
                    case REQUEST_CODE_PICK_FILE:
                        filesActionsHandler.onAddFile(uri, false);
                        break;
                    case REQUEST_CODE_SEND_FILE:
                        filesActionsHandler.onAddFile(uri, true);
                }
            }
        }
        if (shutterResultListener != null) {
            shutterResultListener.onActivityResult(requestCode, resultCode, data);
            shutterResultListener = null;
        }
    }

    private void openAllFilesFolder(String name) {
        mainPresenter.disableMultiSelect();
        Objects.requireNonNull(getSelectionChangeListener()).onMultiSelectDisabled();
        getSelectionChangeListener().onCancelSelection();
        onCancelled();
        mainPresenter.clearSelectedItems();
        if (fragNavController.getCurrentFrag() instanceof FilesFragment) {
            ((FilesFragment) fragNavController.getCurrentFrag()).setEnabled(false);
        }
        Menu menu = Objects.requireNonNull(navView).getMenu();
        currentItem = menu.getItem(0);
        currentItem.setChecked(true);
        currentTag = Tag.all;
        fragNavController.switchTab(ALL);
        ((FilesFragment) Objects.requireNonNull(fragNavController.getCurrentFrag())).setEnabled(true);

        mainPresenter.setRecent(false);
        mainPresenter.setOffline(false);
        mainPresenter.setDownloads(false);
        Objects.requireNonNull(fabAddButton).show();
        Objects.requireNonNull(fabDownloadsButton).hide();

        fragNavController.clearStack();
        actionBarDrawerToggle.setDrawerIndicatorEnabled(true);

        FileRealm file = dataBaseService.getFileByName(name, null);
        if (file != null && file.isFolder()) {
            FilesFragment newFragment = FilesFragment.newInstance(file.getName(), file);
            newFragment.setEnabled(true);
            pushFragment(newFragment);
        } else {
            ((FilesFragment) fragNavController.getCurrentFrag()).setTitleToBar();
        }
    }

    @Override
    protected void onPostResume() {
        Log.d(TAG, "onPostResume");
        super.onPostResume();
        handler.postDelayed(() -> {
            userLoginTextView = findViewById(R.id.userLoginCaption);
            infoLogoButton = findViewById(R.id.infoRightLogo);

            if (userLoginTextView == null || preferenceService == null) {
                return;
            }

            String mail = preferenceService.getMail();
            userLoginTextView.setText(mail);

            if (infoLogoButton != null) {
                infoLogoButton.setOnClickListener((View v) -> openSettings());
            }
        }, 500);
    }

    private void initToolBar() {
        Log.d(TAG, "initToolBar");
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
    }

    @SuppressWarnings("SameReturnValue")
    private void initSearch() {
        Objects.requireNonNull(searchView).setOnQueryTextListener(this);
        searchView.setOnSearchClickListener(view -> {
            mainPresenter.setInSearch(true);
            actionBarDrawerToggle.setDrawerIndicatorEnabled(false);
            actionBarDrawerToggle.setHomeAsUpIndicator(R.drawable.close_multi_select);
            showSearch();
        });
        searchView.setOnCloseListener(() -> {
            mainPresenter.setInSearch(false);
            actionBarDrawerToggle.setDrawerIndicatorEnabled(
                    Objects.requireNonNull(fragNavController.getCurrentStack()).size() == 1);
            actionBarDrawerToggle.setHomeAsUpIndicator(R.drawable.back_home);
            ((FilesFragment) Objects.requireNonNull(fragNavController.getCurrentFrag())).onSearch("");
            showTitle();
            return false;
        });
    }

    private void initMultiSelectMenu() {
        multiSelectDialog = new MultiSelectMenuDialog(
                this, toolbar, modeProvider, selectionProvider, filesMenuActionsHandler);
        filesMenuActionsHandler.setDialog(multiSelectDialog);
        filesMenuActionsHandler.setOfflineSwitch(multiSelectDialog);
    }

    private void initAddMenu() {
        addMenuDialog = new AddMenuDialog(
                this,
                new OnMenuListener() {
                    @Override
                    public void onClick(@NonNull View view) {
                        onAddMenuItemClick(view);
                    }

                    @Override
                    public void onDismiss(DialogInterface dialogInterface) {

                    }

                    @Override
                    public void onShow(DialogInterface dialogInterface) {

                    }
                });
    }

    private void initNewFolderDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(
                new ContextThemeWrapper(this, R.style.AppTheme));
        builder.setTitle(R.string.new_folder);
        View viewDialog = LayoutInflater.from(this).inflate(R.layout.new_folder_dialog, null);
        newFolderDialogText = viewDialog.findViewById(R.id.name_file_edit_text);
        builder.setView(viewDialog);
        builder.setPositiveButton(
                R.string.ok, null);
        builder.setNegativeButton(
                R.string.cancel, (DialogInterface dialog, int which) -> newFolderDialog.dismiss());
        newFolderDialog = builder.create();
        Objects.requireNonNull(newFolderDialog.getWindow()).setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    }

    private void initInsertLinkDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(
                new ContextThemeWrapper(this, R.style.AppTheme));
        builder.setTitle(R.string.insert_share_link);
        View viewDialog = LayoutInflater.from(this).inflate(R.layout.insert_link_dialog, null);
        insertLinkDialogText = viewDialog.findViewById(R.id.insert_link_edit_text);
        builder.setView(viewDialog);
        builder.setPositiveButton(
                R.string.ok, null);
        builder.setNegativeButton(
                R.string.cancel, (DialogInterface dialog, int which) -> insertLinkDialog.dismiss());
        insertLinkDialog = builder.create();
    }

    private void onNewFolderCreateClicked() {
        Log.d(TAG, "onNewFolderCreateClicked");
        String name = newFolderDialogText.getText().toString().trim();
        if (name.isEmpty()) {
            newFolderDialogText.setError(getString(R.string.empty_name_error));
            return;
        }

        if (name.getBytes(StandardCharsets.UTF_8).length > 255) {
            newFolderDialogText.setError(getString(R.string.filename_too_long));
            return;
        }

        FileRealm currentFolder = getCurrentFolder();
        FileRealm existingFile = dataBaseService.getFileByName(
                name, currentFolder == null ? null : currentFolder.getUuid());
        if (existingFile != null) {
            if (existingFile.isFolder()) {
                newFolderDialogText.setError(String.format(
                        getString(R.string.folder_with_name_already_exist), name));
            } else {
                newFolderDialogText.setError(String.format(
                        getString(R.string.file_with_name_already_exist), name));
            }

            return;
        }
        filesActionsHandler.onAddFolder(name);
        newFolderDialog.dismiss();

    }

    private void onLinkInserted() {
        Log.d(TAG, "onLinkInserted");
        String link = insertLinkDialogText.getText().toString().trim()
                .replace("\n", "").replace("\r", "");
        if (link.isEmpty()) {
            insertLinkDialogText.setError(getString(R.string.empty_link_error));
            return;
        }

        String[] shareDataList = link.split("/");
        if (shareDataList.length != 5 || shareDataList[shareDataList.length - 1].length() != 32) {
            insertLinkDialogText.setError(getString(R.string.invalid_link));
            return;
        }
        if (!(shareDataList[0].contains("https:") || shareDataList[0].contains("http:"))) {
            insertLinkDialogText.setError(getString(R.string.invalid_link));
            return;
        }
        if (!preferenceService.getHost().contains(shareDataList[2])) {
            insertLinkDialogText.setError(getString(R.string.invalid_link));
            return;
        }
        String shareHash = shareDataList[shareDataList.length - 1];
        String url = String.format(
                "https://%s/ws/webshare/%s",
                Objects.requireNonNull(App.getApplication()).getSignalServerUrl(),
                shareHash);
        getAuthHttpClient().checkSharePassword(
                url,
                () -> handler.post(() -> {
                    insertLinkDialog.dismiss();
                    showDirectoryChooser(shareHash);
                }),
                () -> handler.post(() -> {
                    insertLinkDialogText.getText().clear();
                    insertLinkDialogText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                    insertLinkDialog.setTitle(R.string.enter_password);
                    insertLinkDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(
                            v -> onSharePasswordEntered(shareHash));
                }),
                info -> handler.post(() -> {
                    insertLinkDialog.dismiss();
                    Toast.makeText(getApplicationContext(), info, Toast.LENGTH_LONG).show();
                }));
    }

    private void onSharePasswordEntered(String shareHash) {
        Log.d(TAG, "onSharePasswordEntered");
        insertLinkDialogText.setError(null);
        String password = insertLinkDialogText.getText().toString().trim()
                .replace("\n", "").replace("\r", "");
        if (password.isEmpty()) {
            insertLinkDialogText.setError(getString(R.string.empty_password));
            return;
        }
        if (password.length() > 32) {
            insertLinkDialogText.setError(getString(R.string.password_length_big_error));
            return;
        }

        String shareHashAndPasswd = String.format(
                "%s?passwd=%s",
                shareHash,
                Base64.encodeToString(password.getBytes(), Base64.DEFAULT));
        String url = String.format(
                "https://%s/ws/webshare/%s",
                Objects.requireNonNull(App.getApplication()).getSignalServerUrl(),
                shareHashAndPasswd);
        getAuthHttpClient().checkSharePassword(
                url,
                () -> handler.post(() -> {
                    insertLinkDialog.dismiss();
                    showDirectoryChooser(shareHashAndPasswd);
                }),
                () -> handler.post(() -> insertLinkDialogText.setError(
                        getString(R.string.wrong_password))),
                info -> handler.post(() -> {
                    insertLinkDialog.dismiss();
                    Toast.makeText(getApplicationContext(), info, Toast.LENGTH_LONG).show();
                }));
    }

    private void onAddMenuItemClick(@NonNull View view) {
        Log.d(TAG, "onAddMenuItemClick");
        switch (view.getId()) {
            case R.id.action_new_folder:
                newFolderDialogText.setError(null);
                newFolderDialogText.setText(null);
                newFolderDialog.show();
                newFolderDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(
                        v -> onNewFolderCreateClicked());
                break;
            case R.id.action_insert_link:
                insertLinkDialogText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
                insertLinkDialogText.setError(null);
                insertLinkDialogText.setText(null);
                insertLinkDialog.setTitle(R.string.insert_share_link);
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    Log.d(TAG, "initInsertLinkDialog: clipboard not null");
                    ClipData clip = clipboard.getPrimaryClip();
                    if (clip != null) {
                        Log.d(TAG, "initInsertLinkDialog: clip not null");
                        if (clip.getItemCount() > 0) {
                            Log.d(TAG, "initInsertLinkDialog: clip have items");
                            CharSequence text = clip.getItemAt(0).getText();
                            insertLinkDialogText.setText(text);
                        }
                    }
                }
                insertLinkDialog.show();
                Objects.requireNonNull(insertLinkDialog.getWindow()).setSoftInputMode(
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                insertLinkDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(
                        v -> onLinkInserted());
                break;
            case R.id.action_send_files:
            case R.id.action_add_files:
                Intent fileIntent = new Intent(Intent.ACTION_GET_CONTENT);
                fileIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                fileIntent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                fileIntent.setType("*/*");
                try {
                    startActivityForResult(
                            fileIntent, view.getId() == R.id.action_send_files ?
                                    REQUEST_CODE_SEND_FILE : REQUEST_CODE_PICK_FILE);
                } catch (ActivityNotFoundException e) {
                    Log.e(TAG, "No activity can handle picking a file. Showing alternatives.");
                }
                break;
            case R.id.action_add_photo:
                Intent galleryIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                galleryIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                galleryIntent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                galleryIntent.setType("image/* video/*");
                startActivityForResult(galleryIntent, REQUEST_CODE_PICK_PHOTO);
                break;
            case R.id.action_take_photo: {
                Intent intent = new Intent(Const.PAUSE_MONITOR_INTENT);
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
                shutterResultListener = Shutter.with(this)
                        .takePhoto()
                        .usePrivateAppInternalStorage()
                        .snap(new ShutterResultCallback() {
                            @Override
                            public void onComplete(@NonNull ShutterResult result) {
                                Intent intent = new Intent(Const.RESTART_MONITOR_INTENT);
                                LocalBroadcastManager.getInstance(getApplicationContext())
                                        .sendBroadcast(intent);
                                String path = result.getAbsoluteFilePath();
                                if (path == null) return;
                                filesActionsHandler.onAddFile(path, false);
                            }

                            @Override
                            public void onError(@NotNull String s, @NotNull Throwable throwable) {
                                Intent intent = new Intent(Const.RESTART_MONITOR_INTENT);
                                LocalBroadcastManager.getInstance(getApplicationContext())
                                        .sendBroadcast(intent);
                                Toast.makeText(getApplicationContext(), s, Toast.LENGTH_LONG).show();
                            }
                        });
                break;
            }
            case R.id.action_import_camera:
                addMenuDialog.switchCameraImport();
            case R.id.action_import_camera_switch:
                if (preferenceService.getAutoCameraUpdate()) {
                    preferenceService.setAutoCameraUpdate(false);
                    Intent intent = new Intent(Const.RESTART_MONITOR_INTENT);
                    LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
                    addMenuDialog.dismiss(300);
                } else {
                    if (dataBaseService.isNodeOnline()) {
                        confirmCameraAutoUpdate();
                    } else {
                        showAlertCameraAutoUpdate();
                    }
                }
                return;
        }
        addMenuDialog.dismiss(0);
    }

    private void showAlertCameraAutoUpdate() {
        AlertDialog.Builder builder = new AlertDialog.Builder(
                new ContextThemeWrapper(this.getWindow().getContext(), R.style.AppTheme));
        builder.setMessage(R.string.automatic_camera_update_text)
                .setPositiveButton(R.string.ok, (dialog, id) -> confirmCameraAutoUpdate())
                .setOnCancelListener(dialog -> {
                    addMenuDialog.switchCameraImport();
                    addMenuDialog.dismiss(300);
                });
        builder.create();
        builder.show();
    }

    private void confirmCameraAutoUpdate() {
        preferenceService.setAutoCameraUpdate(true);
        Intent intent = new Intent(Const.RESTART_MONITOR_INTENT);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        addMenuDialog.dismiss(300);
    }

    @OnClick(R.id.fabAddButton)
    protected void onAddButtonClicked() {
        Log.d(TAG, "onAddButtonClicked");
        if (addMenuDialog.isShowing()) {
            addMenuDialog.dismiss(0);
        } else {
            addMenuDialog.show(preferenceService.getAutoCameraUpdate());
        }
    }

    @OnClick(R.id.fabPasteButton)
    protected void onPasteButtonClicked(@SuppressWarnings("unused") View view) {
        Log.d(TAG, "onPasteButtonClicked");
        boolean handled = true;
        if (modeProvider.isCopyActionActive()) {
            filesActionsHandler.onCopyAction();
        } else if (modeProvider.isMoveActionActive()) {
            handled = filesActionsHandler.onMoveAction();
        } else {
            Log.e(TAG, "onPasteButtonClicked without active action");
        }
        if (handled) onCancelButtonClicked();
    }

    @SuppressWarnings("WeakerAccess")
    @OnClick(R.id.fabCancelButton)
    protected void onCancelButtonClicked() {
        Log.d(TAG, "onCancelButtonClicked");
        Objects.requireNonNull(fabPasteButton).hide();
        Objects.requireNonNull(fabCancelButton).hide();
        mainPresenter.actionCancelled();
        mainPresenter.clearSelectedItems();
        Objects.requireNonNull(getSelectionChangeListener()).onMultiSelectDisabled();
    }

    @OnClick(R.id.fabDownloadsButton)
    protected void onDownloadsButtonClicked() {
        Log.d(TAG, "onDownloadsButtonClicked");
        boolean paused = ownDevice != null && ownDevice.isPaused();
        updateDownloadsButton(!paused);

        Intent intent = new Intent(
                paused ? Const.DOWNLOADS_RESUME_OPERATION : Const.DOWNLOADS_PAUSE_OPERATION);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);

        showSnack(this, getString(
                paused ? R.string.resumed_downloads : R.string.paused_downlaods),
                true, null, false);
    }

    @Override
    public void onMoveActivated() {
        mainPresenter.moveActionActivated();
        Objects.requireNonNull(fabPasteButton).show();
        Objects.requireNonNull(fabCancelButton).show();
    }

    @Override
    public void onCopyActivated() {
        mainPresenter.copyActionActivated();
        Objects.requireNonNull(fabPasteButton).show();
        Objects.requireNonNull(fabCancelButton).show();
    }

    @Override
    public void onCancelled() {
        mainPresenter.actionCancelled();
        Objects.requireNonNull(fabPasteButton).hide();
        Objects.requireNonNull(fabCancelButton).hide();
    }

    public void onMultiSelectDisabled() {
        mainPresenter.disableMultiSelect();
        if (Objects.requireNonNull(fragNavController.getCurrentStack()).size() == 1 && !modeProvider.inSearch()) {
            actionBarDrawerToggle.setDrawerIndicatorEnabled(true);
        }
        if (!modeProvider.inSearch()) {
            actionBarDrawerToggle.setHomeAsUpIndicator(R.drawable.back_home);
        }
        invalidateOptionsMenu();
    }

    public void onMultiSelectEnabled() {
        showTitle();
        mainPresenter.enableMultiSelect();
        actionBarDrawerToggle.setDrawerIndicatorEnabled(false);
        actionBarDrawerToggle.setHomeAsUpIndicator(R.drawable.close_multi_select);
        invalidateOptionsMenu();
    }

    @Override
    public boolean onQueryTextSubmit(@NonNull String query) {
        Log.d(TAG, String.format("onQueryTextSubmit: %s", query));
        ((FilesFragment) Objects.requireNonNull(fragNavController.getCurrentFrag())).onSearch(query);
        return true;
    }

    @Override
    public boolean onQueryTextChange(@NonNull String newText) {
        Log.d(TAG, String.format("onQueryTextChange: %s", newText));
        ((FilesFragment) Objects.requireNonNull(fragNavController.getCurrentFrag())).onSearch(newText);
        return true;
    }

    @Override
    public void onBackPressed() {
        if (!Objects.requireNonNull(searchView).isIconified()) {
            searchView.onActionViewCollapsed();
            ((FilesFragment) Objects.requireNonNull(fragNavController.getCurrentFrag())).onSearch("");
            mainPresenter.setInSearch(false);
            actionBarDrawerToggle.setHomeAsUpIndicator(R.drawable.back_home);
            actionBarDrawerToggle.setDrawerIndicatorEnabled(
                    Objects.requireNonNull(fragNavController.getCurrentStack()).size() == 1);
            showTitle();
            return;
        }
        if (modeProvider.isMultiSelect()) {
            mainPresenter.disableMultiSelect();
            Objects.requireNonNull(getSelectionChangeListener()).onMultiSelectDisabled();
            getSelectionChangeListener().onCancelSelection();
            invalidateOptionsMenu();
            return;
        }
        turnBack();
    }

    public void pushFragment(Fragment fragment) {
        if (!Objects.requireNonNull(searchView).isIconified()) {
            searchView.onActionViewCollapsed();
            ((FilesFragment) Objects.requireNonNull(fragNavController.getCurrentFrag())).onSearch("");
            mainPresenter.setInSearch(false);
            actionBarDrawerToggle.setHomeAsUpIndicator(R.drawable.back_home);
            showTitle();
        }
        actionBarDrawerToggle.setDrawerIndicatorEnabled(false);
        fragNavController.pushFragment(fragment);
    }

    private void turnBack() {
        assert fragNavController.getCurrentStack() != null;
        try {
            fragNavController.popFragment();
        } catch (Exception e) {
            Log.d(TAG, String.format("turnBack exception: %s (%s)", e.getClass(), e.getMessage()));
            e.printStackTrace();
            finish();
            return;
        }
        if (fragNavController.getCurrentStack().size() == 1) {
            actionBarDrawerToggle.setDrawerIndicatorEnabled(true);
        }
    }

    public void changeTitle(String newTitle) {
        showTitle();
        Objects.requireNonNull(title).setText(newTitle);
    }

    private void showTitle() {
        if (!SignalServerService.IsConnected() && !modeProvider.isMultiSelect()) {
            showConnecting();
            return;
        }
        Log.d(TAG, "showTitle");
        Objects.requireNonNull(title).setVisibility(View.VISIBLE);
        Objects.requireNonNull(connectingInfo).setVisibility(View.GONE);
        Objects.requireNonNull(connectingToServer).setVisibility(View.GONE);
    }

    private void showConnecting() {
        Log.d(TAG, "showConnecting");
        Objects.requireNonNull(title).setVisibility(View.GONE);
        Objects.requireNonNull(connectingInfo).setVisibility(View.VISIBLE);
        Objects.requireNonNull(connectingToServer).setVisibility(View.VISIBLE);
    }

    private void showSearch() {
        Log.d(TAG, "showSearch");
        Objects.requireNonNull(title).setVisibility(View.GONE);
        Objects.requireNonNull(connectingInfo).setVisibility(View.GONE);
        Objects.requireNonNull(connectingToServer).setVisibility(View.GONE);
    }

    @SuppressWarnings("unused")
    @Override
    public Fragment getRootFragment(int i) {
        return rootFragments.get(i);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (fragNavController != null) {
            fragNavController.onSaveInstanceState(outState);
        }
        outState.putInt(CURRENT_TAG, currentTag.ordinal());
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        Log.d(TAG, "onRestoreInstanceState");
        int anInt = savedInstanceState.getInt(CURRENT_TAG);
        currentTag = Tag.values()[anInt];
        Menu menu = Objects.requireNonNull(navView).getMenu();
        menu.getItem(0).setChecked(false);
        MenuItem item = menu.getItem(anInt);
        currentItem = item;
        item.setChecked(true);
        mainPresenter.setRecent(currentTag == Tag.recent);
        mainPresenter.setOffline(currentTag == Tag.offline);
        mainPresenter.setDownloads(currentTag == Tag.downloads);
        if (modeProvider.isRecent() || modeProvider.isDownloads()) {
            Objects.requireNonNull(fabAddButton).hide();
        } else {
            Objects.requireNonNull(fabAddButton).show();
        }
    }

    public MainPresenter getMainPresenter() {
        return mainPresenter;
    }

    public ModeProvider getModeProvider() {
        return modeProvider;
    }

    public SelectionProvider getSelectionProvider() {
        return selectionProvider;
    }

    @NonNull
    public MoveActivatedListener getMoveActivatedListener() {
        return this;
    }

    @NonNull
    public CopyActivatedListener getCopyActivatedListener() {
        return this;
    }

    public DataBaseService getDataBaseService() {
        return dataBaseService;
    }

    public FileTool getFileTool() {
        return fileTool;
    }
}
