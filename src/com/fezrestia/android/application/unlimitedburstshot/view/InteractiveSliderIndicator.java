package com.fezrestia.android.application.unlimitedburstshot.view;

import android.app.Activity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.fezrestia.android.application.unlimitedburstshot.R;
import com.fezrestia.android.common.log.LogConfig;
import com.fezrestia.android.utility.java.Utility;

public class InteractiveSliderIndicator implements View.OnTouchListener {
    private static final String TAG = "InteractiveSliderIndicator";

    // Master context.
    private Activity mActivity;

    // Base container.
    private RelativeLayout mBaseContainer;

    // Images.
    private ImageView mGrip;

    // Flags.
    private boolean mIsTouchEnabled = false;
    private boolean mIsGripTouched = false;

    // Dimensions.
    private int mScreenHeight = 0;
    private int mVerticalScrollWorkRange = 0;
    private int mCurrentScrolledPosition = 0;

    // Interface.
    public interface OnInteractiveSliderRangeChangedListener {
        // Range is based on 0-100 range.
        abstract public void onInteractiveSliderRangeChanged(int range);
    }

    private OnInteractiveSliderRangeChangedListener mListener;

    // CONSTRUCTOR
    public InteractiveSliderIndicator(Activity act) {
        if(LogConfig.dLog) Log.d("TraceLog", TAG + ".CONSTRUCTOR:[IN]");

        // Master.
        mActivity = act;

        // Container from layout.
        mBaseContainer = (RelativeLayout) mActivity.findViewById(
                R.id.interactive_slider_indicator_container);
        mBaseContainer.setBackgroundResource(
                R.drawable.supercleancamera_interactive_slider_indicator_base);

        // Images.
        mGrip = (ImageView) mBaseContainer.findViewById(R.id.interactive_slider_indicator_grip);
        mGrip.setImageResource(R.drawable.supercleancamera_interactive_slider_indicator_grip);

        // Event listener.
        mBaseContainer.setOnTouchListener(this);

        // Initial visibility.
        mBaseContainer.setVisibility(View.INVISIBLE);

        // Refresh.
        mBaseContainer.requestLayout();
        mBaseContainer.invalidate();
    }

    public void setOnInteractiveSliderRangeChangedListener(
            OnInteractiveSliderRangeChangedListener listener) {
        mListener = listener;
    }

    public void setVisibility(int visibility) {
        // Set.
        mBaseContainer.setVisibility(visibility);

        // Switch touch event enable or not according to visibility.
        if (visibility == View.VISIBLE) {
            mIsTouchEnabled = true;
        } else {
            mIsTouchEnabled = false;
        }
    }

    public void reset() {
        // Reset grip.
        resetGripPosition();
    }

    private void resetGripPosition() {
        mBaseContainer.scrollTo(0, 0);
    }

    @Override
    public boolean onTouch(View view, MotionEvent motion) {
        // Check enabled/disabled.
        if (!mIsTouchEnabled) {
            return false;
        }

        switch (motion.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // User handle the grip or not.
                if (Utility.hitTest(mGrip, motion)) {
                    mIsGripTouched = true;
                } else {
                    mIsGripTouched = false;
                }

                // TODO:Move to onFinishInflate().
                // Get dimensions.
                if (mScreenHeight == 0) {
                    mScreenHeight = Utility.getLandscapeDisplayRect(mActivity).height();
                }
                mVerticalScrollWorkRange
                        = 2 * (mBaseContainer.getHeight() / 2 - mGrip.getHeight() / 2);

                break;

            case MotionEvent.ACTION_MOVE:
                if (!mIsGripTouched) {
                    // NOP.
                    break;
                }

                // Calculate the position difference from center vertical of screen.
                mCurrentScrolledPosition = mScreenHeight / 2 - (int) motion.getRawY();

                // Limit.
                if (mVerticalScrollWorkRange / 2 < mCurrentScrolledPosition) {
                    mCurrentScrolledPosition = mVerticalScrollWorkRange / 2;
                } else if (mCurrentScrolledPosition < (-1) * mVerticalScrollWorkRange / 2) {
                    mCurrentScrolledPosition = (-1) * mVerticalScrollWorkRange / 2;
                }

                if (mListener != null) {
                    // Calculate range.
                    float range = (float) (mCurrentScrolledPosition + mVerticalScrollWorkRange / 2)
                            / mVerticalScrollWorkRange * 100;

                    // Notify.
                    mListener.onInteractiveSliderRangeChanged((int) range);
                }

                // Change Position.
                mBaseContainer.scrollTo(0, mCurrentScrolledPosition);
                break;

            case MotionEvent.ACTION_UP:
                // fall-through.
            case MotionEvent.ACTION_CANCEL:
                // Reset.
                mIsGripTouched = false;
                break;
        }

        return true;
    }
}
