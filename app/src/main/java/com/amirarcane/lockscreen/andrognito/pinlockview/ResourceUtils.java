package com.amirarcane.lockscreen.andrognito.pinlockview;

import android.content.Context;

import androidx.annotation.ColorRes;
import androidx.annotation.DimenRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

/**
 * Created by aritraroy on 10/06/16.
 */
class ResourceUtils {

    private ResourceUtils() {
        throw new AssertionError();
    }

    public static int getColor(@NonNull Context context, @ColorRes int id) {
        return ContextCompat.getColor(context, id);
    }

    public static float getDimensionInPx(@NonNull Context context, @DimenRes int id) {
        return context.getResources().getDimension(id);
    }

}
