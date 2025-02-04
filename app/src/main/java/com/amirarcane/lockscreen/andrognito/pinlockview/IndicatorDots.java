package com.amirarcane.lockscreen.andrognito.pinlockview;

import android.animation.LayoutTransition;
import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;


import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

import net.pvtbox.android.R;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * It represents a set of indicator dots which when attached with {@link PinLockView}
 * can be used to indicate the current length of the input
 * <p>
 * Created by aritraroy on 01/06/16.
 */
public class IndicatorDots extends LinearLayout {

    @IntDef({IndicatorType.FIXED, IndicatorType.FILL, IndicatorType.FILL_WITH_ANIMATION})
    @Retention(RetentionPolicy.SOURCE)
    public @interface IndicatorType {
        int FIXED = 0;
        int FILL = 1;
        int FILL_WITH_ANIMATION = 2;
    }

    private static final int DEFAULT_PIN_LENGTH = 4;
    private static final int DEFAULT_ANIMATION_DURATION = 200;

    private int mDotDiameter;
    private int mDotSpacing;
    private int mFillDrawable;
    private int mEmptyDrawable;
    private int mPinLength;
    private int mIndicatorType;

    private int mPreviousLength;

    public IndicatorDots(@NonNull Context context) {
        this(context, null);
    }

    public IndicatorDots(@NonNull Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public IndicatorDots(@NonNull Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.PinLockView);

        try {
            mDotDiameter = (int) typedArray.getDimension(R.styleable.PinLockView_dotDiameter, ResourceUtils.getDimensionInPx(getContext(), R.dimen.dot_diameter));
            mDotSpacing = (int) typedArray.getDimension(R.styleable.PinLockView_dotSpacing, ResourceUtils.getDimensionInPx(getContext(), R.dimen.dot_spacing));
            mFillDrawable = typedArray.getResourceId(R.styleable.PinLockView_dotFilledBackground,
                    R.drawable.dot_filled);
            mEmptyDrawable = typedArray.getResourceId(R.styleable.PinLockView_dotEmptyBackground,
                    R.drawable.dot_empty);
            mPinLength = typedArray.getInt(R.styleable.PinLockView_pinLength, DEFAULT_PIN_LENGTH);
            mIndicatorType = typedArray.getInt(R.styleable.PinLockView_indicatorType,
                    IndicatorType.FIXED);
        } finally {
            typedArray.recycle();
        }

        initView(context);
    }

    private void initView(Context context) {
        if (mIndicatorType == 0) {
            for (int i = 0; i < mPinLength; i++) {
                View dot = new View(context);
                emptyDot(dot);

                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(mDotDiameter,
                        mDotDiameter);
                params.setMargins(mDotSpacing, 0, mDotSpacing, 0);
                dot.setLayoutParams(params);

                addView(dot);
            }
        } else if (mIndicatorType == 2) {
            LayoutTransition layoutTransition = new LayoutTransition();
            layoutTransition.setDuration(DEFAULT_ANIMATION_DURATION);
            layoutTransition.setStartDelay(LayoutTransition.APPEARING, 0);
            setLayoutTransition(layoutTransition);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // If the indicator type is not fixed
        if (mIndicatorType != 0) {
            ViewGroup.LayoutParams params = this.getLayoutParams();
            params.height = mDotDiameter;
            requestLayout();
        }
    }

    void updateDot(int length) {
        if (mIndicatorType == 0) {
            if (length > 0) {
                if (length > mPreviousLength) {
                    fillDot(getChildAt(length - 1));
                } else {
                    emptyDot(getChildAt(length));
                }
                mPreviousLength = length;
            } else {
                // When {@code mPinLength} is 0, we need to reset all the views back to empty
                for (int i = 0; i < getChildCount(); i++) {
                    View v = getChildAt(i);
                    emptyDot(v);
                }
                mPreviousLength = 0;
            }
        } else {
            if (length > 0) {
                if (length > mPreviousLength) {
                    View dot = new View(getContext());
                    fillDot(dot);

                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(mDotDiameter,
                            mDotDiameter);
                    params.setMargins(mDotSpacing, 0, mDotSpacing, 0);
                    dot.setLayoutParams(params);

                    addView(dot, length - 1);
                } else {
                    removeViewAt(length);
                }
                mPreviousLength = length;
            } else {
                removeAllViews();
                mPreviousLength = 0;
            }
        }
    }

    private void emptyDot(@NonNull View dot) {
        dot.setBackgroundResource(mEmptyDrawable);
    }

    private void fillDot(@NonNull View dot) {
        dot.setBackgroundResource(mFillDrawable);
    }

    public void setPinLength(int pinLength) {
        this.mPinLength = pinLength;
        removeAllViews();
        initView(getContext());
    }

    public void setIndicatorType(@IndicatorType int type) {
        this.mIndicatorType = type;
        removeAllViews();
        initView(getContext());
    }
}
