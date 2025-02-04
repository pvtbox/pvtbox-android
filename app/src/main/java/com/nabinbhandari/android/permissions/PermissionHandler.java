package com.nabinbhandari.android.permissions;

import android.content.Context;
import android.widget.Toast;

import androidx.annotation.NonNull;

import java.util.ArrayList;

/**
 * The class for handling permission callbacks.
 * <p>
 * Created on 7/16/2017 on 3:42 PM
 *
 * @author Nabin Bhandari
 */
@SuppressWarnings({"WeakerAccess", "BooleanMethodIsAlwaysInverted"})
public abstract class PermissionHandler {

    /**
     * This method will be called if all of the requested permissions are granted.
     */
    public abstract void onGranted();

    /**
     * This method will be called if some of the requested permissions have been denied.
     *
     * @param context           The android context.
     * @param deniedPermissions The list of permissions which have been denied.
     */
    public void onDenied(Context context, ArrayList<String> deniedPermissions) {
        if (Permissions.loggingEnabled) {
            StringBuilder builder = new StringBuilder();
            builder.append("Denied:");
            for (String permission : deniedPermissions) {
                builder.append(" ");
                builder.append(permission);
            }
            Permissions.log(builder.toString());
        }
        Toast.makeText(context, "Permission Denied.", Toast.LENGTH_SHORT).show();
    }

    /**
     * This method will be called if some permissions have previously been set not to ask again.
     *
     * @param context     the android context.
     * @param blockedList the list of permissions which have been set not to ask again.
     * @return The overrider of this method should return true if no further action is needed,
     * and should return false if the default action is to be taken, i.e. send user to settings.
     * <br><br>
     * Note: If the option {@link Permissions.Options#sendDontAskAgainToSettings(boolean)} has been
     * set to false, the user won't be sent to settings by default.
     */
    @SuppressWarnings({"UnusedParameters", "BooleanMethodIsAlwaysInverted"})
    public boolean onBlocked(Context context, @NonNull ArrayList<String> blockedList) {
        if (Permissions.loggingEnabled) {
            StringBuilder builder = new StringBuilder();
            builder.append("Set not to ask again:");
            for (String permission : blockedList) {
                builder.append(" ");
                builder.append(permission);
            }
            Permissions.log(builder.toString());
        }
        return false;
    }

    /**
     * This method will be called if some permissions have just been set not to ask again.
     *
     * @param context           The android context.
     * @param justBlockedList   The list of permissions which have just been set not to ask again.
     * @param deniedPermissions The list of currently unavailable permissions.
     */
    public void onJustBlocked(Context context, @NonNull ArrayList<String> justBlockedList,
                              @NonNull ArrayList<String> deniedPermissions) {
        if (Permissions.loggingEnabled) {
            StringBuilder builder = new StringBuilder();
            builder.append("Just set not to ask again:");
            for (String permission : justBlockedList) {
                builder.append(" ");
                builder.append(permission);
            }
            Permissions.log(builder.toString());
        }
        onDenied(context, deniedPermissions);
    }

}
