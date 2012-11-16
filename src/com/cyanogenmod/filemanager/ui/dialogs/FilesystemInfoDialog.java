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

package com.cyanogenmod.filemanager.ui.dialogs;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.Switch;
import android.widget.TextView;

import com.cyanogenmod.filemanager.FileManagerApplication;
import com.cyanogenmod.filemanager.R;
import com.cyanogenmod.filemanager.console.ConsoleBuilder;
import com.cyanogenmod.filemanager.model.DiskUsage;
import com.cyanogenmod.filemanager.model.MountPoint;
import com.cyanogenmod.filemanager.preferences.AccessMode;
import com.cyanogenmod.filemanager.preferences.FileManagerSettings;
import com.cyanogenmod.filemanager.preferences.Preferences;
import com.cyanogenmod.filemanager.ui.ThemeManager;
import com.cyanogenmod.filemanager.ui.ThemeManager.Theme;
import com.cyanogenmod.filemanager.ui.widgets.DiskUsageGraph;
import com.cyanogenmod.filemanager.util.CommandHelper;
import com.cyanogenmod.filemanager.util.DialogHelper;
import com.cyanogenmod.filemanager.util.FileHelper;
import com.cyanogenmod.filemanager.util.MountPointHelper;

/**
 * A class that wraps a dialog for showing information about a mount point.<br />
 * This class display information like mount point name, device name, size, type, ...
 */
public class FilesystemInfoDialog implements OnClickListener, OnCheckedChangeListener {

    /**
     * An interface to communicate when the user change the mount state
     * of a filesystem.
     */
    public interface OnMountListener {
        /**
         * Method invoked when the mount state of a mount point has changed.
         *
         * @param mountPoint The mount point that has changed
         */
        void onRemount(MountPoint mountPoint);
    }



    private static final String TAG = "FilesystemInfoDialog"; //$NON-NLS-1$

    private final MountPoint mMountPoint;
    /**
     * @hide
     */
    final DiskUsage mDiskUsage;

    private final Context mContext;
    private final AlertDialog mDialog;
    private final View mContentView;
    private View mInfoViewTab;
    private View mDiskUsageViewTab;
    private View mInfoView;
    private View mDiskUsageView;
    private Switch mSwStatus;
    /**
     * @hide
     */
    DiskUsageGraph mDiskUsageGraph;
    private TextView mInfoMsgView;

    private OnMountListener mOnMountListener;

    private boolean mIsMountAllowed;
    private final boolean mIsAdvancedMode;

    /**
     * Constructor of <code>FilesystemInfoDialog</code>.
     *
     * @param context The current context
     * @param mountPoint The mount point information
     * @param diskUsage The disk usage of the mount point
     */
    public FilesystemInfoDialog(Context context, MountPoint mountPoint, DiskUsage diskUsage) {
        super();

        //Save the context
        this.mContext = context;

        //Save data
        this.mMountPoint = mountPoint;
        this.mDiskUsage = diskUsage;
        this.mIsMountAllowed = false;
        this.mIsAdvancedMode =
                FileManagerApplication.getAccessMode().compareTo(AccessMode.SAFE) != 0;

        //Inflate the content
        LayoutInflater li =
                (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        this.mContentView = li.inflate(R.layout.filesystem_info_dialog, null);

        // Apply the current theme
        applyTheme();

        //Create the dialog
        this.mDialog = DialogHelper.createDialog(
                                        context,
                                        0,
                                        R.string.filesystem_info_dialog_title,
                                        this.mContentView);
        this.mDialog.setButton(
                DialogInterface.BUTTON_NEGATIVE,
                this.mContext.getString(android.R.string.cancel),
                (DialogInterface.OnClickListener)null);

        //Fill the dialog
        fillData(this.mContentView);
    }

    /**
     * Method that shows the dialog.
     */
    public void show() {
        DialogHelper.delegateDialogShow(this.mContext, this.mDialog);
    }

    /**
     * Method that sets the listener for listen mount events.
     *
     * @param onMountListener The mount listener
     */
    public void setOnMountListener(OnMountListener onMountListener) {
        this.mOnMountListener = onMountListener;
    }

    /**
     * Method that fill the dialog with the data of the mount point.
     *
     * @param contentView The content view
     */
    @SuppressWarnings({ "boxing" })
    private void fillData(View contentView) {
        //Get the tab views
        this.mInfoViewTab = contentView.findViewById(R.id.filesystem_info_dialog_tab_info);
        this.mDiskUsageViewTab =
                contentView.findViewById(R.id.filesystem_info_dialog_tab_disk_usage);
        this.mInfoView = contentView.findViewById(R.id.filesystem_tab_info);
        this.mDiskUsageView = contentView.findViewById(R.id.filesystem_tab_diskusage);
        this.mDiskUsageGraph =
                (DiskUsageGraph)contentView.findViewById(R.id.filesystem_disk_usage_graph);

        // Set the user preference about free disk space warning level
        String fds = Preferences.getSharedPreferences().getString(
                FileManagerSettings.SETTINGS_DISK_USAGE_WARNING_LEVEL.getId(),
                (String)FileManagerSettings.
                    SETTINGS_DISK_USAGE_WARNING_LEVEL.getDefaultValue());
        this.mDiskUsageGraph.setFreeDiskSpaceWarningLevel(Integer.parseInt(fds));

        //Register the listeners
        this.mInfoViewTab.setOnClickListener(this);
        this.mDiskUsageViewTab.setOnClickListener(this);

        //Gets text views
        this.mSwStatus = (Switch)contentView.findViewById(R.id.filesystem_info_status);
        this.mSwStatus.setOnCheckedChangeListener(this);
        TextView tvMountPoint =
                (TextView)contentView.findViewById(R.id.filesystem_info_mount_point);
        TextView tvDevice = (TextView)contentView.findViewById(R.id.filesystem_info_device);
        TextView tvType = (TextView)contentView.findViewById(R.id.filesystem_info_type);
        TextView tvOptions = (TextView)contentView.findViewById(R.id.filesystem_info_options);
        TextView tvDumpPass = (TextView)contentView.findViewById(R.id.filesystem_info_dump_pass);
        TextView tvTotal =
                (TextView)contentView.findViewById(R.id.filesystem_info_total_disk_usage);
        TextView tvUsed = (TextView)contentView.findViewById(R.id.filesystem_info_used_disk_usage);
        TextView tvFree = (TextView)contentView.findViewById(R.id.filesystem_info_free_disk_usage);
        this.mInfoMsgView = (TextView)contentView.findViewById(R.id.filesystem_info_msg);

        //Fill the text views
        tvMountPoint.setText(this.mMountPoint.getMountPoint());
        tvDevice.setText(this.mMountPoint.getDevice());
        tvType.setText(this.mMountPoint.getType());
        tvOptions.setText(this.mMountPoint.getOptions());
        tvDumpPass.setText(
                String.format("%d / %d",  //$NON-NLS-1$
                        this.mMountPoint.getDump(),
                        this.mMountPoint.getPass()));
        if (this.mDiskUsage != null) {
            tvTotal.setText(FileHelper.getHumanReadableSize(this.mDiskUsage.getTotal()));
            tvUsed.setText(FileHelper.getHumanReadableSize(this.mDiskUsage.getUsed()));
            tvFree.setText(FileHelper.getHumanReadableSize(this.mDiskUsage.getFree()));
        } else {
            tvTotal.setText("-"); //$NON-NLS-1$
            tvUsed.setText("-"); //$NON-NLS-1$
            tvFree.setText("-"); //$NON-NLS-1$
        }

        //Configure status switch
        boolean hasPrivileged = false;
        try {
            hasPrivileged = ConsoleBuilder.isPrivileged();
        } catch (Throwable ex) {/**NON BLOCK**/}
        boolean mountAllowed =
                MountPointHelper.isMountAllowed(this.mMountPoint);
        if (this.mIsAdvancedMode) {
            if (hasPrivileged) {
                if (!mountAllowed) {
                    this.mInfoMsgView.setText(
                            this.mContext.getString(
                                    R.string.filesystem_info_cant_be_mounted_msg));
                    this.mInfoMsgView.setVisibility(View.VISIBLE);
                }
            } else {
                this.mInfoMsgView.setVisibility(View.VISIBLE);
                this.mInfoMsgView.setOnClickListener(this);
            }
        } else {
            mountAllowed = false;
            this.mInfoMsgView.setVisibility(View.GONE);
            this.mInfoMsgView.setOnClickListener(null);
        }
        this.mIsMountAllowed = hasPrivileged && mountAllowed && this.mIsAdvancedMode;
        this.mSwStatus.setEnabled(this.mIsMountAllowed);
        this.mSwStatus.setChecked(MountPointHelper.isReadWrite(this.mMountPoint));

        //Change the tab
        onClick(this.mInfoViewTab);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.filesystem_info_dialog_tab_info:
                if (!this.mInfoViewTab.isSelected()) {
                    this.mInfoViewTab.setSelected(true);
                    ((TextView)this.mInfoViewTab).setTextAppearance(
                            this.mContext, R.style.primary_text_appearance);
                    this.mDiskUsageViewTab.setSelected(false);
                    ((TextView)this.mDiskUsageViewTab).setTextAppearance(
                            this.mContext, R.style.secondary_text_appearance);
                    this.mInfoView.setVisibility(View.VISIBLE);
                    this.mDiskUsageView.setVisibility(View.GONE);

                    // Apply theme
                    applyTabTheme();
                }
                this.mInfoMsgView.setVisibility(
                        this.mIsMountAllowed || !this.mIsAdvancedMode ? View.GONE : View.VISIBLE);
                break;

            case R.id.filesystem_info_dialog_tab_disk_usage:
                if (!this.mDiskUsageViewTab.isSelected()) {
                    this.mInfoViewTab.setSelected(false);
                    ((TextView)this.mInfoViewTab).setTextAppearance(
                            this.mContext, R.style.secondary_text_appearance);
                    this.mDiskUsageViewTab.setSelected(true);
                    ((TextView)this.mDiskUsageViewTab).setTextAppearance(
                            this.mContext, R.style.primary_text_appearance);
                    this.mInfoView.setVisibility(View.GONE);
                    this.mDiskUsageView.setVisibility(View.VISIBLE);

                    // Apply theme
                    applyTabTheme();
                }
                this.mDiskUsageGraph.post(new Runnable() {
                    @Override
                    public void run() {
                        //Animate disk usage graph
                        FilesystemInfoDialog.this.mDiskUsageGraph.drawDiskUsage(
                                FilesystemInfoDialog.this.mDiskUsage);
                    }
                });
                break;

            case R.id.filesystem_info_msg:
                //Change the console
                boolean superuser = ConsoleBuilder.changeToPrivilegedConsole(this.mContext);
                if (superuser) {
                    this.mInfoMsgView.setOnClickListener(null);

                    // Is filesystem able to be mounted?
                    boolean mountAllowed = MountPointHelper.isMountAllowed(this.mMountPoint);
                    if (mountAllowed) {
                        this.mInfoMsgView.setVisibility(View.GONE);
                        this.mInfoMsgView.setBackground(null);
                        this.mSwStatus.setEnabled(true);
                        this.mIsMountAllowed = true;
                        break;
                    }

                    // Show the message
                    this.mInfoMsgView.setText(
                            this.mContext.getString(
                                    R.string.filesystem_info_cant_be_mounted_msg));
                    this.mInfoMsgView.setVisibility(View.VISIBLE);
                    this.mIsMountAllowed = false;
                }
                break;

            default:
                break;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        switch (buttonView.getId()) {
            case R.id.filesystem_info_status:
                //Mount the filesystem
                Switch sw = (Switch)buttonView;
                boolean ret = false;
                try {
                    ret = CommandHelper.remount(
                            this.mContext,
                            this.mMountPoint, isChecked, null);
                    //Hide warning message
                    this.mInfoMsgView.setVisibility(View.GONE);
                    //Communicate the mount change
                    if (this.mOnMountListener != null) {
                        this.mOnMountListener.onRemount(this.mMountPoint);
                    }

                } catch (Throwable e) {
                    Log.e(TAG,
                            String.format(
                                    "Fail to remount %s", //$NON-NLS-1$
                                    this.mMountPoint.getMountPoint()), e);
                }
                if (!ret) {
                    //Show warning message
                    this.mInfoMsgView.setText(R.string.filesystem_info_mount_failed_msg);
                    this.mInfoMsgView.setVisibility(View.VISIBLE);
                    sw.setChecked(!isChecked);
                }
                break;

            default:
                break;
        }
    }

    /**
     * Method that applies the current theme to the activity
     */
    private void applyTheme() {
        Theme theme = ThemeManager.getCurrentTheme(this.mContext);
        theme.setBackgroundDrawable(
                this.mContext, this.mContentView, "background_drawable"); //$NON-NLS-1$
        applyTabTheme();
        View v = this.mContentView.findViewById(R.id.filesystem_info_dialog_tab_divider1);
        theme.setBackgroundColor(this.mContext, v, "horizontal_divider_color"); //$NON-NLS-1$
        v = this.mContentView.findViewById(R.id.filesystem_info_dialog_tab_divider2);
        theme.setBackgroundColor(this.mContext, v, "vertical_divider_color"); //$NON-NLS-1$

        v = this.mContentView.findViewById(R.id.filesystem_info_status_label);
        theme.setTextColor(this.mContext, (TextView)v, "text_color"); //$NON-NLS-1$
        v = this.mContentView.findViewById(R.id.filesystem_info_status);
        theme.setTextColor(this.mContext, (TextView)v, "text_color"); //$NON-NLS-1$
        v = this.mContentView.findViewById(R.id.filesystem_info_mount_point_label);
        theme.setTextColor(this.mContext, (TextView)v, "text_color"); //$NON-NLS-1$
        v = this.mContentView.findViewById(R.id.filesystem_info_mount_point);
        theme.setTextColor(this.mContext, (TextView)v, "text_color"); //$NON-NLS-1$
        v = this.mContentView.findViewById(R.id.filesystem_info_device_label);
        theme.setTextColor(this.mContext, (TextView)v, "text_color"); //$NON-NLS-1$
        v = this.mContentView.findViewById(R.id.filesystem_info_device);
        theme.setTextColor(this.mContext, (TextView)v, "text_color"); //$NON-NLS-1$
        v = this.mContentView.findViewById(R.id.filesystem_info_type_label);
        theme.setTextColor(this.mContext, (TextView)v, "text_color"); //$NON-NLS-1$
        v = this.mContentView.findViewById(R.id.filesystem_info_type);
        theme.setTextColor(this.mContext, (TextView)v, "text_color"); //$NON-NLS-1$
        v = this.mContentView.findViewById(R.id.filesystem_info_options_label);
        theme.setTextColor(this.mContext, (TextView)v, "text_color"); //$NON-NLS-1$
        v = this.mContentView.findViewById(R.id.filesystem_info_options);
        theme.setTextColor(this.mContext, (TextView)v, "text_color"); //$NON-NLS-1$
        v = this.mContentView.findViewById(R.id.filesystem_info_dump_pass_label);
        theme.setTextColor(this.mContext, (TextView)v, "text_color"); //$NON-NLS-1$
        v = this.mContentView.findViewById(R.id.filesystem_info_dump_pass);
        theme.setTextColor(this.mContext, (TextView)v, "text_color"); //$NON-NLS-1$
        v = this.mContentView.findViewById(R.id.filesystem_info_msg);
        theme.setTextColor(this.mContext, (TextView)v, "text_color"); //$NON-NLS-1$
        ((TextView)v).setCompoundDrawablesWithIntrinsicBounds(
                theme.getDrawable(this.mContext, "filesystem_warning_drawable"), //$NON-NLS-1$
                null, null, null);

        v = this.mContentView.findViewById(R.id.filesystem_info_total_disk_usage_label);
        theme.setTextColor(this.mContext, (TextView)v, "text_color"); //$NON-NLS-1$
        v = this.mContentView.findViewById(R.id.filesystem_info_total_disk_usage);
        theme.setTextColor(this.mContext, (TextView)v, "text_color"); //$NON-NLS-1$
        v = this.mContentView.findViewById(R.id.filesystem_info_used_disk_usage_label);
        theme.setTextColor(this.mContext, (TextView)v, "text_color"); //$NON-NLS-1$
        v = this.mContentView.findViewById(R.id.filesystem_info_used_disk_usage);
        theme.setTextColor(this.mContext, (TextView)v, "text_color"); //$NON-NLS-1$
        v = this.mContentView.findViewById(R.id.filesystem_info_free_disk_usage_label);
        theme.setTextColor(this.mContext, (TextView)v, "text_color"); //$NON-NLS-1$
        v = this.mContentView.findViewById(R.id.filesystem_info_free_disk_usage);
        theme.setTextColor(this.mContext, (TextView)v, "text_color"); //$NON-NLS-1$
    }

    /**
     * Method that applies the current theme to the tab host
     */
    private void applyTabTheme() {
        // Apply the theme
        Theme theme = ThemeManager.getCurrentTheme(this.mContext);
        View v = this.mContentView.findViewById(R.id.filesystem_info_dialog_tab_info);
        theme.setTextColor(this.mContext, (TextView)v, "text_color"); //$NON-NLS-1$
        v = this.mContentView.findViewById(R.id.filesystem_info_dialog_tab_disk_usage);
        theme.setTextColor(this.mContext, (TextView)v, "text_color"); //$NON-NLS-1$
    }

}
