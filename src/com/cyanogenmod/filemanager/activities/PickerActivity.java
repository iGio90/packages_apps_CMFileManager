/*
 * Copyright (C) 2012 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.cyanogenmod.filemanager.activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.storage.StorageVolume;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.FrameLayout;
import android.widget.ListPopupWindow;
import android.widget.Toast;

import com.cyanogenmod.filemanager.R;
import com.cyanogenmod.filemanager.adapters.CheckableListAdapter;
import com.cyanogenmod.filemanager.adapters.CheckableListAdapter.CheckableItem;
import com.cyanogenmod.filemanager.console.ConsoleBuilder;
import com.cyanogenmod.filemanager.model.FileSystemObject;
import com.cyanogenmod.filemanager.preferences.FileManagerSettings;
import com.cyanogenmod.filemanager.preferences.Preferences;
import com.cyanogenmod.filemanager.ui.ThemeManager;
import com.cyanogenmod.filemanager.ui.ThemeManager.Theme;
import com.cyanogenmod.filemanager.ui.widgets.Breadcrumb;
import com.cyanogenmod.filemanager.ui.widgets.ButtonItem;
import com.cyanogenmod.filemanager.ui.widgets.NavigationView;
import com.cyanogenmod.filemanager.ui.widgets.NavigationView.OnFilePickedListener;
import com.cyanogenmod.filemanager.util.DialogHelper;
import com.cyanogenmod.filemanager.util.ExceptionUtil;
import com.cyanogenmod.filemanager.util.FileHelper;
import com.cyanogenmod.filemanager.util.StorageHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * The activity for allow to use a {@link NavigationView} like, to pick a file from other
 * application.
 */
public class PickerActivity extends Activity
        implements OnCancelListener, OnDismissListener, OnFilePickedListener {

    private static final String TAG = "PickerActivity"; //$NON-NLS-1$

    private static boolean DEBUG = false;

    private final BroadcastReceiver mNotificationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null) {
                if (intent.getAction().compareTo(FileManagerSettings.INTENT_THEME_CHANGED) == 0) {
                    applyTheme();
                }
            }
        }
    };

    private String mMimeType;
    private FileSystemObject mFso;  // The picked item
    private AlertDialog mDialog;
    private Handler mHandler;
    /**
     * @hide
     */
    NavigationView mNavigationView;
    private View mRootView;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onCreate(Bundle state) {
        if (DEBUG) {
            Log.d(TAG, "PickerActivity.onCreate"); //$NON-NLS-1$
        }

        // Register the broadcast receiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(FileManagerSettings.INTENT_THEME_CHANGED);
        registerReceiver(this.mNotificationReceiver, filter);

        // Initialize the activity
        init();

        //Save state
        super.onCreate(state);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onDestroy() {
        if (DEBUG) {
            Log.d(TAG, "PickerActivity.onDestroy"); //$NON-NLS-1$
        }

        // Unregister the receiver
        try {
            unregisterReceiver(this.mNotificationReceiver);
        } catch (Throwable ex) {
            /**NON BLOCK**/
        }

        //All destroy. Continue
        super.onDestroy();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        measureHeight();
    }

    /**
     * Method that displays a dialog with a {@link NavigationView} to select the
     * proposed file
     */
    private void init() {
        // Check that call has a valid request (GET_CONTENT a and mime type)
        String action = getIntent().getAction();
        this.mMimeType = getIntent().getType();
        if (action.compareTo(Intent.ACTION_GET_CONTENT.toString()) != 0 ||
             this.mMimeType == null) {
            setResult(Activity.RESULT_CANCELED);
            finish();
            return;
        }

        // Create or use the console
        if (!initializeConsole()) {
            // Something when wrong. Display a message and exit
            DialogHelper.showToast(this, R.string.msgs_cant_create_console, Toast.LENGTH_SHORT);
            cancel();
            return;
        }

        // Create the root file
        this.mRootView = getLayoutInflater().inflate(R.layout.picker, null, false);
        this.mRootView.post(new Runnable() {
            @Override
            public void run() {
                measureHeight();
            }
        });

        // Breadcrumb
        Breadcrumb breadcrumb = (Breadcrumb)this.mRootView.findViewById(R.id.breadcrumb_view);
        // Set the free disk space warning level of the breadcrumb widget
        String fds = Preferences.getSharedPreferences().getString(
                FileManagerSettings.SETTINGS_DISK_USAGE_WARNING_LEVEL.getId(),
                (String)FileManagerSettings.SETTINGS_DISK_USAGE_WARNING_LEVEL.getDefaultValue());
        breadcrumb.setFreeDiskSpaceWarningLevel(Integer.parseInt(fds));

        // Navigation view
        this.mNavigationView =
                (NavigationView)this.mRootView.findViewById(R.id.navigation_view);
        this.mNavigationView.setMimeType(this.mMimeType);
        this.mNavigationView.setOnFilePickedListener(this);
        this.mNavigationView.setBreadcrumb(breadcrumb);

        // Apply the current theme
        applyTheme();

        // Create the dialog
        this.mDialog = DialogHelper.createDialog(
            this, R.drawable.ic_launcher, R.string.picker_title, this.mRootView);
        this.mDialog.setButton(
                DialogInterface.BUTTON_NEUTRAL,
                getString(R.string.cancel),
                new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dlg, int which) {
                dlg.cancel();
            }
        });
        this.mDialog.setCancelable(true);
        this.mDialog.setOnCancelListener(this);
        this.mDialog.setOnDismissListener(this);
        DialogHelper.delegateDialogShow(this, this.mDialog);

        // Set content description of storage volume button
        ButtonItem fs = (ButtonItem)this.mRootView.findViewById(R.id.ab_filesystem_info);
        fs.setContentDescription(getString(R.string.actionbar_button_storage_cd));

        this.mHandler = new Handler();
        this.mHandler.post(new Runnable() {
            @Override
            public void run() {
                // Navigate to. The navigation view will redirect to the appropriate directory
                PickerActivity.this.mNavigationView.changeCurrentDir(FileHelper.ROOT_DIRECTORY);
            }
        });

    }

    /**
     * Method that measure the height needed to avoid resizing when
     * change to a new directory. This method fixed the height of the window
     * @hide
     */
    void measureHeight() {
        // Calculate the dialog size based on the window height
        DisplayMetrics displaymetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
        final int height = displaymetrics.heightPixels;

        Configuration config = getResources().getConfiguration();
        int percent = config.orientation == Configuration.ORIENTATION_LANDSCAPE ? 55 : 70;

        FrameLayout.LayoutParams params =
                new FrameLayout.LayoutParams(
                        LayoutParams.WRAP_CONTENT, (height * percent) / 100);
        this.mRootView.setLayoutParams(params);
    }

    /**
     * Method that initializes a console
     */
    private boolean initializeConsole() {
        try {
            // Is there a console allocate
            if (!ConsoleBuilder.isAlloc()) {
                // Create a ChRooted console
                ConsoleBuilder.createDefaultConsole(this, false, false);
            }
            // There is a console allocated. Use it.
            return true;
        } catch (Throwable _throw) {
            // Capture the exception
            ExceptionUtil.translateException(this, _throw, true, false);
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onDismiss(DialogInterface dialog) {
        if (this.mFso != null) {
            Intent result = new Intent();
            result.setData(Uri.fromFile(new File(this.mFso.getFullPath())));
            setResult(Activity.RESULT_OK, result);
            finish();
        } else {
            cancel();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCancel(DialogInterface dialog) {
        cancel();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onFilePicked(FileSystemObject item) {
        this.mFso = item;
        this.mDialog.dismiss();
    }

    /**
     * Method invoked when an action item is clicked.
     *
     * @param view The button pushed
     */
    public void onActionBarItemClick(View view) {
        switch (view.getId()) {
            //######################
            //Breadcrumb Actions
            //######################
            case R.id.ab_filesystem_info:
                //Show a popup with the storage volumes to select
                showStorageVolumesPopUp(view);
                break;

            default:
                break;
        }
    }

    /**
     * Method that cancels the activity
     */
    private void cancel() {
        setResult(Activity.RESULT_CANCELED);
        finish();
    }

    /**
     * Method that shows a popup with the storage volumes
     *
     * @param anchor The view on which anchor the popup
     */
    private void showStorageVolumesPopUp(View anchor) {
        // Create a list (but not checkable)
        final StorageVolume[] volumes = StorageHelper.getStorageVolumes(PickerActivity.this);
        List<CheckableItem> descriptions = new ArrayList<CheckableItem>();
        if (volumes != null) {
            int cc = volumes.length;
            for (int i = 0; i < cc; i++) {
                String desc = StorageHelper.getStorageVolumeDescription(this, volumes[i]);
                CheckableItem item = new CheckableItem(desc, false, false);
                descriptions.add(item);
            }
        }
        CheckableListAdapter adapter =
                new CheckableListAdapter(getApplicationContext(), descriptions);

        //Create a show the popup menu
        final ListPopupWindow popup = DialogHelper.createListPopupWindow(this, adapter, anchor);
        popup.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                popup.dismiss();
                if (volumes != null) {
                    PickerActivity.this.
                        mNavigationView.changeCurrentDir(volumes[position].getPath());
                }
            }
        });
        popup.show();
    }

    /**
     * Method that applies the current theme to the activity
     * @hide
     */
    void applyTheme() {
        Theme theme = ThemeManager.getCurrentTheme(this);
        theme.setBaseTheme(this, true);

        // View
        theme.setBackgroundDrawable(this, this.mRootView, "background_drawable"); //$NON-NLS-1$
        this.mNavigationView.applyTheme();
    }
}
