package com.ncapdevi.fragnav;

import androidx.annotation.AnimRes;
import androidx.annotation.NonNull;
import androidx.annotation.StyleRes;
import androidx.core.util.Pair;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 *
 */


public class FragNavTransactionOptions {
    final List<Pair<View, String>> sharedElements;
    @FragNavController.Transit
    final
    int transition;
    @AnimRes
    final
    int enterAnimation;
    @AnimRes
    final
    int exitAnimation;
    @AnimRes
    final
    int popEnterAnimation;
    @AnimRes
    final
    int popExitAnimation;
    @StyleRes
    final
    int transitionStyle;
    final String breadCrumbTitle;
    final String breadCrumbShortTitle;
    final boolean allowStateLoss;

    private FragNavTransactionOptions(@NonNull Builder builder) {
        sharedElements = builder.sharedElements;
        transition = builder.transition;
        enterAnimation = builder.enterAnimation;
        exitAnimation = builder.exitAnimation;
        transitionStyle = builder.transitionStyle;
        popEnterAnimation = builder.popEnterAnimation;
        popExitAnimation = builder.popExitAnimation;
        breadCrumbTitle = builder.breadCrumbTitle;
        breadCrumbShortTitle = builder.breadCrumbShortTitle;
        allowStateLoss = builder.allowStateLoss;
    }

    @NonNull
    public static Builder newBuilder() {
        return new Builder();
    }

    public static final class Builder {
        private List<Pair<View, String>> sharedElements;
        private int transition;
        private int enterAnimation;
        private int exitAnimation;
        private int transitionStyle;
        private int popEnterAnimation;
        private int popExitAnimation;
        private String breadCrumbTitle;
        private String breadCrumbShortTitle;
        private boolean allowStateLoss = false;

        private Builder() {
        }

        @NonNull
        public Builder addSharedElement(Pair<View, String> val) {
            if (sharedElements == null) {
                sharedElements = new ArrayList<>(3);
            }
            sharedElements.add(val);
            return this;
        }

        @NonNull
        public Builder sharedElements(List<Pair<View, String>> val) {
            sharedElements = val;
            return this;
        }

        @NonNull
        public Builder transition(@FragNavController.Transit int val) {
            transition = val;
            return this;
        }

        @NonNull
        Builder customAnimations(@AnimRes int enterAnimation, @AnimRes int exitAnimation) {
            this.enterAnimation = enterAnimation;
            this.exitAnimation = exitAnimation;
            return this;
        }

        @NonNull
        public Builder customAnimations(@AnimRes int enterAnimation, @AnimRes int exitAnimation, @AnimRes int popEnterAnimation, @AnimRes int popExitAnimation) {
            this.popEnterAnimation = popEnterAnimation;
            this.popExitAnimation = popExitAnimation;
            return customAnimations(enterAnimation, exitAnimation);
        }


        @NonNull
        public Builder transitionStyle(@StyleRes int val) {
            transitionStyle = val;
            return this;
        }

        @NonNull
        public Builder breadCrumbTitle(String val) {
            breadCrumbTitle = val;
            return this;
        }

        @NonNull
        public Builder breadCrumbShortTitle(String val) {
            breadCrumbShortTitle = val;
            return this;
        }

        @NonNull
        public Builder allowStateLoss(boolean allow) {
            allowStateLoss = allow;
            return this;
        }

        @NonNull
        public FragNavTransactionOptions build() {
            return new FragNavTransactionOptions(this);
        }
    }
}

