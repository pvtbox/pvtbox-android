package uk.co.deanwild.marqueetextview;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.View;

import net.pvtbox.android.R;

/**
 * Created by deanwild on 14/03/16.
 */
public class MarqueeTextView extends View {

    private static final int DEFAULT_SPEED = 5;
    private static final int DEFAULT_PAUSE_DURATION = 10000;
    private static final int DEFAULT_EDGE_EFFECT_WIDTH = 10;
    private static final int DEFAULT_EDGE_EFFECT_COLOR = Color.WHITE;

    private boolean marqueeEnabled = true;
    private boolean forceMarquee = false;
    private int textColor = Color.BLACK;
    private float textSize = getResources().getDisplayMetrics().scaledDensity * 20.0f;
    private int pauseDuration = DEFAULT_PAUSE_DURATION;
    private int speed = DEFAULT_SPEED;

    private boolean edgeEffectEnabled = false;
    private int edgeEffectWidth = DEFAULT_EDGE_EFFECT_WIDTH;
    private int edgeEffectColor = DEFAULT_EDGE_EFFECT_COLOR;

    private CharSequence text;

    private double wrapAroundPoint;
    private boolean animationRunning = false;
    private boolean paused = false;
    private boolean wrapped = false;
    private boolean centerText = true; // only applies to "unwrapped" text

    private TextPaint textPaint;
    private Paint leftPaint;
    private Paint rightPaint;


    private Rect textBounds;
    private RectF leftRect;
    private RectF rightRect;

    private int topOffset;
    private int xOffset;

    private Typeface customTypeface;

    public MarqueeTextView(Context context) {
        super(context);
        init(null);
    }

    public MarqueeTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public MarqueeTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    private void init(AttributeSet attrs) {

        if (attrs != null) {
            readAttrs(attrs);
        }

        textBounds = new Rect();

        setText(text);

    }

    private void readAttrs(AttributeSet attrs) {

        int[] attrsArray = new int[]{
                android.R.attr.textSize,
                android.R.attr.textColor,
                android.R.attr.text,
                R.attr.marqueeEnabled,
                R.attr.edgeEffectEnabled,
                R.attr.edgeEffectWidth,
                R.attr.edgeEffectColor,
                R.attr.pauseDuration,
                R.attr.forceMarquee,
                R.attr.centerText,
        };

        TypedArray ta = getContext().obtainStyledAttributes(attrs, attrsArray);

        textSize = ta.getDimension(0, textSize); // 2 is the index in the array of the textSize attribute
        textColor = ta.getColor(1, textColor); // 3 is the index of the array of the textColor attribute
        text = ta.getText(2);
        marqueeEnabled = ta.getBoolean(3, marqueeEnabled);
        edgeEffectEnabled = ta.getBoolean(4, edgeEffectEnabled);
        edgeEffectWidth = ta.getInt(5, edgeEffectWidth);
        edgeEffectColor = ta.getColor(6, edgeEffectColor);
        pauseDuration = ta.getInt(7, pauseDuration);
        forceMarquee = ta.getBoolean(8, forceMarquee);
        centerText = ta.getBoolean(9, centerText);

        ta.recycle();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (text != null) {

            float viewWidth = this.getWidth();

            int textWidth = textBounds.width();

            if (textWidth < viewWidth && !forceMarquee) { // text can fit in view, no marquee needed

                animationRunning = false;

                float leftMargin = 0;

                if (centerText) {
                    leftMargin = (viewWidth - textWidth) / 2;
                }

                canvas.drawText(text.toString(), leftMargin, topOffset, textPaint);

            } else { // not enough room, we must animate it

                if (!animationRunning) {

                    xOffset = 0;
                    wrapAroundPoint = -(textWidth + (textWidth * 0.05));
                    animationRunning = true;
                    wrapped = true;
                    paused = false;

                }

                canvas.drawText(text.toString(), xOffset, topOffset, textPaint);

                if (edgeEffectEnabled) {

                    if (xOffset < 0 || pauseDuration <= 0) {
                        canvas.drawRect(leftRect, leftPaint);
                    }

                    canvas.drawRect(rightRect, rightPaint);

                }

                if (!paused) {

                    xOffset -= speed;

                    if (xOffset < wrapAroundPoint) {
                        xOffset = (int) viewWidth;
                        wrapped = true;
                    }

                    if (wrapped && xOffset <= 0) {
                        wrapped = false;

                        if (pauseDuration > 0) {
                            xOffset = 0;
                            pause();
                        }
                    }

                    invalidateAfter(20);

                }
            }
        }
    }

    private synchronized void pause() {
        paused = true;
        removeCallbacks(pauseRunnable);
        postDelayed(pauseRunnable, pauseDuration);
    }

    private final Runnable pauseRunnable = () -> {
        paused = false;
        invalidate();
    };


    @SuppressWarnings("SameParameterValue")
    private void invalidateAfter(long delay) {
        removeCallbacks(invalidateRunnable);
        postDelayed(invalidateRunnable, delay);
    }

    private final Runnable invalidateRunnable = this::invalidate;


    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        int width;
        int height;

        if (widthMode == MeasureSpec.EXACTLY) {
            // Parent has told us how big to be. So be it.
            width = widthSize;
        } else {
            width = this.getWidth();
        }

        if (heightMode == MeasureSpec.EXACTLY) {
            // Parent has told us how big to be. So be it.
            height = heightSize;
        } else {

            TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            paint.density = getResources().getDisplayMetrics().density;
            paint.setTextSize(textSize);

            if (customTypeface != null) {
                paint.setTypeface(customTypeface);
            }

            height = (int) (Math.abs(paint.ascent()) + Math.abs(paint.descent()));
        }

        setMeasuredDimension(width, height);
        renewPaint();
    }

    private void renewPaint() {

        textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        textPaint.density = getResources().getDisplayMetrics().density;
        textPaint.setTextSize(textSize);
        textPaint.setColor(textColor);

        if (customTypeface != null) {
            textPaint.setTypeface(customTypeface);
        }

        int absEdgeEffectWidth = (getMeasuredWidth() / 100) * edgeEffectWidth;

        Shader leftShader = new LinearGradient(
                0,
                0,
                absEdgeEffectWidth,
                0,
                edgeEffectColor,
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP);

        leftPaint = new Paint();
        leftPaint.setShader(leftShader);

        int rightOffset = getMeasuredWidth() - absEdgeEffectWidth;

        Shader rightShader = new LinearGradient(
                rightOffset,
                0,
                getMeasuredWidth(),
                0,
                Color.TRANSPARENT,
                edgeEffectColor,
                Shader.TileMode.CLAMP);

        rightPaint = new Paint();
        rightPaint.setShader(rightShader);

        leftRect = new RectF(0, 0, absEdgeEffectWidth, getMeasuredHeight());
        rightRect = new RectF(rightOffset, 0, getMeasuredWidth(), getMeasuredHeight());

        textPaint.getTextBounds(text.toString(), 0, text.length(), textBounds);

        int viewheight = getMeasuredHeight();
        topOffset = (int) (viewheight / 2 - ((textPaint.descent() + textPaint.ascent()) / 2));
    }

    public void setSpeed(int speed) {
        this.speed = speed;
    }

    public void setText(CharSequence text) {

        if (text == null)
            text = "";

        this.text = text;
        animationRunning = false;
        requestLayout();
    }

    public void setTextColor(int color) {
        textColor = color;
        renewPaint();
        invalidate();
    }


}
