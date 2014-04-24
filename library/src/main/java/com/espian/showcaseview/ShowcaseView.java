package com.espian.showcaseview;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.Button;
import android.widget.RelativeLayout;

import com.espian.showcaseview.anim.AnimationUtils;
import com.espian.showcaseview.drawing.ClingDrawer;
import com.espian.showcaseview.drawing.ClingDrawerImpl;
import com.espian.showcaseview.drawing.TextDrawer;
import com.espian.showcaseview.drawing.TextDrawerImpl;
import com.espian.showcaseview.targets.Target;
import com.espian.showcaseview.utils.Calculator;
import com.espian.showcaseview.utils.PointAnimator;
import com.github.espiandev.showcaseview.R;
import com.nineoldandroids.animation.Animator;

import static com.espian.showcaseview.anim.AnimationUtils.AnimationEndListener;
import static com.espian.showcaseview.anim.AnimationUtils.AnimationStartListener;

/**
 * A view which allows you to showcase areas of your app with an explanation.
 */
public class ShowcaseView extends RelativeLayout
        implements View.OnClickListener, View.OnTouchListener {

    protected static final String PREFS_SHOWCASE_INTERNAL = "showcase_internal";
    private static final Interpolator INTERPOLATOR = new AccelerateDecelerateInterpolator();
    private final Paint basicPaint;

    private int showcaseX = -1;
    private int showcaseY = -1;
    private float showcaseRadius = -1;
    private float legacyShowcaseX = -1;
    private float legacyShowcaseY = -1;
    private boolean isRedundant = false;

    // Touch items
    private boolean hasCustomClickListener = false;
    private boolean blockTouches = true;
    private boolean hideOnTouch = false;

    private int mBackgroundColor;
    private final Button mEndButton;
    private OnShowcaseEventListener mEventListener = OnShowcaseEventListener.NONE;
    private boolean mAlteredText = false;

    private float scaleMultiplier = 1f;
    private TextDrawer textDrawer;
    private ClingDrawer mShowcaseDrawer;

    public static final Target NONE = new Target() {
        @Override
        public Point getPoint() {
            return new Point(1000000, 1000000);
        }
    };

    private boolean mHasNoTarget = false;
    private Bitmap bitmapBuffer;
    private long fadeInMillis;
    private long fadeOutMillis;
    private long shotId;
    private ShotType selectedShotType = ShotType.NO_LIMIT;
    private boolean shouldCentreText;

    protected ShowcaseView(Context context) {
        this(context, null, R.styleable.CustomTheme_showcaseViewStyle);
    }

    protected ShowcaseView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        // Get the attributes for the ShowcaseView
        final TypedArray styled = context.getTheme()
                .obtainStyledAttributes(attrs, R.styleable.ShowcaseView, R.attr.showcaseViewStyle,
                        R.style.ShowcaseView);

        // Set the default animation times
        fadeInMillis = getResources().getInteger(android.R.integer.config_mediumAnimTime);
        fadeOutMillis = getResources().getInteger(android.R.integer.config_mediumAnimTime);

        mEndButton = (Button) LayoutInflater.from(context).inflate(R.layout.showcase_button, null);

        updateStyle(styled, false);

        init();
        basicPaint = new Paint();
    }

    private void init() {

        if (selectedShotType == ShotType.SINGLE_SHOT && hasShot()) {
            // The showcase has already been shot once, so we don't need to do anything
            setVisibility(View.GONE);
            isRedundant = true;
            return;
        }

        showcaseRadius = getResources().getDimension(R.dimen.showcase_radius);
        setOnTouchListener(this);

        if (mEndButton.getParent() == null) {
            int margin = (int) getResources().getDimension(R.dimen.button_margin);
            RelativeLayout.LayoutParams lps = (LayoutParams) generateDefaultLayoutParams();
            lps.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
            lps.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
            lps.setMargins(margin, margin, margin, margin);
            mEndButton.setLayoutParams(lps);
            mEndButton.setText(R.string.ok);
            if (!hasCustomClickListener) {
                mEndButton.setOnClickListener(this);
            }
            addView(mEndButton);
        }

    }

    private boolean hasShot() {
        return getContext()
                .getSharedPreferences(PREFS_SHOWCASE_INTERNAL, Context.MODE_PRIVATE)
                .getBoolean("hasShot" + shotId, false);
    }

    void setShowcaseView(final View view) {
        if (isRedundant || view == null) {
            isRedundant = true;
            return;
        }
        isRedundant = false;

        view.post(new Runnable() {
            @Override
            public void run() {
                //init();
                Point viewPoint = Calculator.getShowcasePointFromView(view);
                setShowcasePosition(viewPoint);
                invalidate();
            }
        });
    }

    void setShowcasePosition(Point point) {
        setShowcasePosition(point.x, point.y);
    }

    void setShowcasePosition(int x, int y) {
        if (isRedundant) {
            return;
        }
        showcaseX = x;
        showcaseY = y;
        //init();
        invalidate();
    }

    public void setTarget(final Target target) {
        setShowcase(target, false);
    }

    public void setShowcase(final Target target, final boolean animate) {
        postDelayed(new Runnable() {
            @Override
            public void run() {
                updateBitmap();
                Point targetPoint = target.getPoint();
                if (targetPoint != null) {
                    mHasNoTarget = false;
                    if (animate) {
                        Animator animator = PointAnimator.ofPoints(ShowcaseView.this, targetPoint);
                        animator.setDuration(fadeInMillis);
                        animator.setInterpolator(INTERPOLATOR);
                        animator.start();
                    } else {
                        setShowcasePosition(targetPoint);
                    }
                } else {
                    mHasNoTarget = true;
                    invalidate();
                }
            }
        }, 100);
    }

    private void updateBitmap() {
        if (bitmapBuffer == null || haveBoundsChanged()) {
            bitmapBuffer = Bitmap.createBitmap(getMeasuredWidth(), getMeasuredHeight(), Bitmap.Config.ARGB_8888);
        }
    }

    private boolean haveBoundsChanged() {
        return getMeasuredWidth() != bitmapBuffer.getWidth() ||
                getMeasuredHeight() != bitmapBuffer.getHeight();
    }

    public boolean hasShowcaseView() {
        return (showcaseX != 1000000 && showcaseY != 1000000) || !mHasNoTarget;
    }

    public void setShowcaseX(int x) {
        setShowcasePosition(x, showcaseY);
    }

    public void setShowcaseY(int y) {
        setShowcasePosition(showcaseX, y);
    }

    public int getShowcaseX() {
        return showcaseX;
    }

    public int getShowcaseY() {
        return showcaseY;
    }

    /**
     * Override the standard button click event
     *
     * @param listener Listener to listen to on click events
     */
    public void overrideButtonClick(OnClickListener listener) {
        if (isRedundant) {
            return;
        }
        if (mEndButton != null) {
            mEndButton.setOnClickListener(listener != null ? listener : this);
        }
        hasCustomClickListener = true;
    }

    public void setOnShowcaseEventListener(OnShowcaseEventListener listener) {
        if (listener != null) {
            mEventListener = listener;
        } else {
            mEventListener = OnShowcaseEventListener.NONE;
        }
    }

    public void setButtonText(CharSequence text) {
        if (mEndButton != null) {
            mEndButton.setText(text);
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (showcaseX < 0 || showcaseY < 0 || isRedundant) {
            super.dispatchDraw(canvas);
            return;
        }

        boolean recalculatedCling = mShowcaseDrawer.calculateShowcaseRect(showcaseX, showcaseY);
        boolean recalculateText = recalculatedCling || mAlteredText;
        mAlteredText = false;

        //Draw background color
        //canvas.drawColor(mBackgroundColor);

        // Draw the showcase drawable
        if (!mHasNoTarget) {
            mShowcaseDrawer.drawShowcase(bitmapBuffer, showcaseX, showcaseY, scaleMultiplier, showcaseRadius, mBackgroundColor);
            canvas.drawBitmap(bitmapBuffer, 0, 0, basicPaint);
        }

        // Draw the text on the screen, recalculating its position if necessary
        if (recalculateText) {
            textDrawer.calculateTextPosition(canvas.getWidth(), canvas.getHeight(), this, shouldCentreText);
        }
        textDrawer.draw(canvas, recalculateText);

        super.dispatchDraw(canvas);

    }

    @Override
    @TargetApi(9)
    public void onClick(View view) {
        // If the type is set to one-shot, store that it has shot
        if (selectedShotType == ShotType.SINGLE_SHOT) {
            SharedPreferences internal = getContext().getSharedPreferences(PREFS_SHOWCASE_INTERNAL, Context.MODE_PRIVATE);
            internal.edit().putBoolean("hasShot" + shotId, true).apply();
        }
        hide();
    }

    public void hide() {
        mEventListener.onShowcaseViewHide(this);
        fadeOutShowcase();
    }

    private void fadeOutShowcase() {
        AnimationUtils.createFadeOutAnimation(this, fadeOutMillis, new AnimationEndListener() {
            @Override
            public void onAnimationEnd() {
                setVisibility(View.GONE);
                mEventListener.onShowcaseViewDidHide(ShowcaseView.this);
            }
        }).start();
    }

    public void show() {
        mEventListener.onShowcaseViewShow(this);
        fadeInShowcase();
    }

    private void fadeInShowcase() {
        AnimationUtils.createFadeInAnimation(this, fadeInMillis,
                new AnimationStartListener() {
                    @Override
                    public void onAnimationStart() {
                        setVisibility(View.VISIBLE);
                    }
                }
        ).start();
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {

        float xDelta = Math.abs(motionEvent.getRawX() - showcaseX);
        float yDelta = Math.abs(motionEvent.getRawY() - showcaseY);
        double distanceFromFocus = Math.sqrt(Math.pow(xDelta, 2) + Math.pow(yDelta, 2));

        if (MotionEvent.ACTION_UP == motionEvent.getAction() &&
                hideOnTouch && distanceFromFocus > showcaseRadius) {
            this.hide();
            return true;
        }

        return blockTouches && distanceFromFocus > showcaseRadius;
    }

    public void setText(int titleTextResId, int subTextResId) {
        String titleText = getContext().getResources().getString(titleTextResId);
        String subText = getContext().getResources().getString(subTextResId);
        setText(titleText, subText);
    }

    public void setText(String titleText, String subText) {
        textDrawer.setContentTitle(titleText);
        textDrawer.setContentText(subText);
        mAlteredText = true;
        invalidate();
    }

    /**
     * Get the ghostly gesture hand for custom gestures
     *
     * @return a View representing the ghostly hand
     */
    public View getHand() {
        final View mHandy = ((LayoutInflater) getContext()
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(R.layout.handy, null);
        addView(mHandy);
        AnimationUtils.hide(mHandy);

        return mHandy;
    }

    private static void insertShowcaseView(ShowcaseView showcaseView, Activity activity) {
        ((ViewGroup) activity.getWindow().getDecorView()).addView(showcaseView);
    }

    public void setContentTitle(CharSequence title) {
        textDrawer.setContentTitle(title);
    }

    public void setContentText(CharSequence text) {
        textDrawer.setContentText(text);
    }

    private void setScaleMultiplier(float scaleMultiplier) {
        this.scaleMultiplier = scaleMultiplier;
    }

    /**
     * Builder class which allows easier creation of {@link ShowcaseView}s. It is recommended that you use this
     */
    public static class Builder {

        final ShowcaseView showcaseView;
        private final Activity activity;

        public Builder(Activity activity) {
            this.activity = activity;
            this.showcaseView = new ShowcaseView(activity);
            this.showcaseView.setTarget(NONE);
        }

        public ShowcaseView build() {
            insertShowcaseView(showcaseView, activity);
            return showcaseView;
        }

        public Builder setContentTitle(int resId) {
            return setContentTitle(activity.getString(resId));
        }

        public Builder setContentTitle(CharSequence title) {
            showcaseView.setContentTitle(title);
            return this;
        }

        public Builder setContentText(int resId) {
            return setContentText(activity.getString(resId));
        }

        public Builder setContentText(CharSequence text) {
            showcaseView.setContentText(text);
            return this;
        }

        public Builder setTarget(Target target) {
            showcaseView.setTarget(target);
            return this;
        }

        public Builder setStyle(int theme) {
            showcaseView.setStyle(theme);
            return this;
        }

        public Builder setOnClickListener(OnClickListener onClickListener) {
            showcaseView.overrideButtonClick(onClickListener);
            return this;
        }

        /**
         * Don't make the ShowcaseView block touches on itself. This doesn't
         * block touches in the showcased area.
         * <p/>
         * By default, the ShowcaseView does block touches
         */
        public Builder doNotBlockTouches() {
            showcaseView.setBlocksTouches(false);
            return this;
        }

        /**
         * Make this ShowcaseView hide when the user touches outside the showcased area.
         * This enables {@link #doNotBlockTouches()} as well.
         * <p/>
         * By default, the ShowcaseView doesn't hide on touch.
         */
        public Builder hideOnTouchOutside() {
            showcaseView.setBlocksTouches(true);
            showcaseView.setHideOnTouchOutside(true);
            return this;
        }

        /**
         * Set the ShowcaseView to only ever show once.
         *
         * @param shotId a unique identifier (<em>across the app</em>) to store
         *               whether this ShowcaseView has been shown.
         */
        public Builder singleShot(long shotId) {
            showcaseView.setSingleShot(shotId);
            return this;
        }

    }

    public void setShouldCentreText(boolean shouldCentreText) {
        this.shouldCentreText = shouldCentreText;
        mAlteredText = true;
        invalidate();
    }

    private void setSingleShot(long shotId) {
        this.shotId = shotId;
    }

    public void setButtonLayoutParams(RelativeLayout.LayoutParams layoutParams) {
        mEndButton.setLayoutParams(layoutParams);
    }

    /**
     * Set the duration of the fading in and fading out of the ShowcaseView
     */
    private void setFadeDurations(long fadeInMillis, long fadeOutMillis) {
        this.fadeInMillis = fadeInMillis;
        this.fadeOutMillis = fadeOutMillis;
    }

    /**
     * @see com.espian.showcaseview.ShowcaseView.Builder#hideOnTouchOutside()
     */
    public void setHideOnTouchOutside(boolean hideOnTouch) {
        this.hideOnTouch = hideOnTouch;
    }

    /**
     * @see com.espian.showcaseview.ShowcaseView.Builder#doNotBlockTouches()
     */
    public void setBlocksTouches(boolean blockTouches) {
        this.blockTouches = blockTouches;
    }

    public void setStyle(int theme) {
        TypedArray array = getContext().obtainStyledAttributes(theme, R.styleable.ShowcaseView);
        updateStyle(array, true);
    }

    private void updateStyle(TypedArray styled, boolean invalidate) {
        mBackgroundColor = styled.getInt(R.styleable.ShowcaseView_sv_backgroundColor, Color.argb(128, 80, 80, 80));
        int showcaseColor = styled.getColor(R.styleable.ShowcaseView_sv_showcaseColor, Color.parseColor("#33B5E5"));

        int titleTextAppearance = styled.getResourceId(R.styleable.ShowcaseView_sv_titleTextAppearance,
                R.style.TextAppearance_ShowcaseView_Title);
        int detailTextAppearance = styled.getResourceId(R.styleable.ShowcaseView_sv_detailTextAppearance,
                R.style.TextAppearance_ShowcaseView_Detail);

        styled.recycle();

        mShowcaseDrawer = new ClingDrawerImpl(getResources(), showcaseColor);

        // TODO: This isn't ideal, ClingDrawer and Calculator interfaces should be separate
        textDrawer = new TextDrawerImpl(getResources(), mShowcaseDrawer, getContext());
        textDrawer.setTitleStyling(titleTextAppearance);
        textDrawer.setDetailStyling(detailTextAppearance);

        if (invalidate) {
            invalidate();
        }
    }

    public enum ShotType {
        NO_LIMIT, SINGLE_SHOT
    }

}
