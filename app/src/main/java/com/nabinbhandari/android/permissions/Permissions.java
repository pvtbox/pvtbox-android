package com.nabinbhandari.android.permissions;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;

/**
 * <pre>
 * Helper class for handling runtime permissions.
 * Created on 6/11/2017 on 9:32 PM
 * </pre>
 *
 * @author Nabin Bhandari
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public class Permissions {

    static boolean loggingEnabled = true;

    /**
     * Disable logs.
     */
    public static void disableLogging() {
        loggingEnabled = false;
    }

    static void log(@NonNull String message) {
        if (loggingEnabled) Log.d("Permissions", message);
    }

    /**
     * Check/Request a permission and call the callback methods of permission handler accordingly.
     *
     * @param context    the android context.
     * @param permission the permission to be requested.
     * @param rationale  Explanation to be shown to user if s/he has denied permission earlier.
     *                   If this parameter is null, permissions will be requested without showing
     *                   the rationale dialog.
     * @param handler    The permission handler object for handling callbacks of various user
     *                   actions such as permission granted, permission denied, etc.
     */
    public static void check(@NonNull Context context, String permission, String rationale,
                             @NonNull PermissionHandler handler) {
        check(context, new String[]{permission}, rationale, null, handler);
    }

    /**
     * Check/Request a permission and call the callback methods of permission handler accordingly.
     *
     * @param context     the android context.
     * @param permission  the permission to be requested.
     * @param rationaleId The string resource id of the explanation to be shown to user if s/he has
     *                    denied permission earlier. If resource is not found, permissions will be
     *                    requested without showing the rationale dialog.
     * @param handler     The permission handler object for handling callbacks of various user
     *                    actions such as permission granted, permission denied, etc.
     */
    public static void check(@NonNull Context context, String permission, int rationaleId,
                             @NonNull PermissionHandler handler) {
        String rationale = null;
        try {
            rationale = context.getString(rationaleId);
        } catch (Exception ignored) {
        }
        check(context, new String[]{permission}, rationale, null, handler);
    }

    /**
     * Check/Request permissions and call the callback methods of permission handler accordingly.
     *
     * @param context     Android context.
     * @param permissions The array of one or more permission(s) to request.
     * @param rationale   Explanation to be shown to user if s/he has denied permission earlier.
     *                    If this parameter is null, permissions will be requested without showing
     *                    the rationale dialog.
     * @param options     The options for handling permissions.
     * @param handler     The permission handler object for handling callbacks of various user
     *                    actions such as permission granted, permission denied, etc.
     */
    public static void check(@NonNull final Context context, String[] permissions, String rationale,
                             Options options, @NonNull final PermissionHandler handler) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            handler.onGranted();
            log("Android version < 23");
        } else {
            ArrayList<String> permissionsList = new ArrayList<>();
            Collections.addAll(permissionsList, permissions);
            boolean allPermissionProvided = true;
            for (String aPermission : permissionsList) {
                if (context.checkSelfPermission(aPermission) != PackageManager.PERMISSION_GRANTED) {
                    allPermissionProvided = false;
                    break;
                }
            }

            if (allPermissionProvided) {
                handler.onGranted();
                log("Permission(s) " + (PermissionsActivity.permissionHandler == null ?
                        "already granted." : "just granted from settings."));
                PermissionsActivity.permissionHandler = null;
            } else {
                PermissionsActivity.permissionHandler = handler;
                Intent intent = new Intent(context, PermissionsActivity.class);
                intent.putExtra(PermissionsActivity.EXTRA_PERMISSIONS, permissionsList);
                intent.putExtra(PermissionsActivity.EXTRA_RATIONALE, rationale);
                intent.putExtra(PermissionsActivity.EXTRA_OPTIONS, options);
                context.startActivity(intent);
            }
        }
    }

    /**
     * Check/Request permissions and call the callback methods of permission handler accordingly.
     *
     * @param context     Android context.
     * @param permissions The array of one or more permission(s) to request.
     * @param rationaleId The string resource id of the explanation to be shown to user if s/he has
     *                    denied permission earlier. If resource is not found, permissions will be
     *                    requested without showing the rationale dialog.
     * @param options     The options for handling permissions.
     * @param handler     The permission handler object for handling callbacks of various user
     *                    actions such as permission granted, permission denied, etc.
     */
    public static void check(@NonNull final Context context, String[] permissions, int rationaleId,
                             Options options, @NonNull final PermissionHandler handler) {
        String rationale = null;
        try {
            rationale = context.getString(rationaleId);
        } catch (Exception ignored) {
        }
        check(context, permissions, rationale, options, handler);
    }

    /**
     * Options to customize while requesting permissions.
     */
    public static class Options implements Serializable {
        String settingsText = "Settings";
        String rationaleDialogTitle = "Permissions Required";
        String settingsDialogTitle = "Permissions Required";
        String settingsDialogMessage = "Required permission(s) have been set" +
                " not to ask again! Please provide them from settings.";
        boolean sendBlockedToSettings = true;

        /**
         * Sets the button text for "settings" while asking user to go to settings.
         *
         * @param settingsText The text for "settings".
         * @return same instance.
         */
        @NonNull
        public Options setSettingsText(String settingsText) {
            this.settingsText = settingsText;
            return this;
        }

        /**
         * Sets the title text for permission rationale dialog.
         *
         * @param rationaleDialogTitle the title text.
         * @return same instance.
         */
        @NonNull
        public Options setRationaleDialogTitle(String rationaleDialogTitle) {
            this.rationaleDialogTitle = rationaleDialogTitle;
            return this;
        }

        /**
         * Sets the title text of the dialog which asks user to go to settings, in the case when
         * permission(s) have been set not to ask again.
         *
         * @param settingsDialogTitle the title text.
         * @return same instance.
         */
        @NonNull
        public Options setSettingsDialogTitle(String settingsDialogTitle) {
            this.settingsDialogTitle = settingsDialogTitle;
            return this;
        }

        /**
         * Sets the message of the dialog which asks user to go to settings, in the case when
         * permission(s) have been set not to ask again.
         *
         * @param settingsDialogMessage the dialog message.
         * @return same instance.
         */
        @NonNull
        public Options setSettingsDialogMessage(String settingsDialogMessage) {
            this.settingsDialogMessage = settingsDialogMessage;
            return this;
        }

        /**
         * In the case the user has previously set some permissions not to ask again, if this flag
         * is true the user will be prompted to go to settings and provide the permissions otherwise
         * the method {@link PermissionHandler#onDenied(Context, ArrayList)} will be invoked
         * directly. The default state is true.
         *
         * @param send whether to ask user to go to settings or not.
         * @return same instance.
         */
        @NonNull
        public Options sendDontAskAgainToSettings(boolean send) {
            sendBlockedToSettings = send;
            return this;
        }
    }

}
