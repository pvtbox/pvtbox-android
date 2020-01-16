package com.amirarcane.lockscreen.util;

import android.annotation.TargetApi;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageView;

/**
 * Created by Arcane on 7/23/2017.
 */

public class Animate {

    @TargetApi(Build.VERSION_CODES.M)
    public static void animate(@NonNull AppCompatImageView view, @NonNull AnimatedVectorDrawable scanFingerprint) {
        view.setImageDrawable(scanFingerprint);
        scanFingerprint.start();
    }
}
