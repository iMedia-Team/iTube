package org.schabi.newpipe.player.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.VelocityTracker
import kotlin.math.abs

internal interface GestureDetectorCompatImpl {
    var isLongpressEnabled: Boolean
    fun onTouchEvent(ev: MotionEvent): Boolean
    fun setOnDoubleTapListener(listener: GestureDetector.OnDoubleTapListener)
}
/**
 * Creates a GestureDetector with the supplied listener.
 * You may only use this constructor from a UI thread (this is the usual situation).
 *
 * @param context  the application's context
 * @param listener the listener invoked for all the callbacks
 * @param configuration contains methods to standard constants used in the UI for timeouts,
 * sizes, and distances. By modifying the configuration, you can update the constants to match
 * your wishes.
 */
class ConfigurableGestureDetector(
    context: Context,
    listener: GestureDetector.OnGestureListener,
    private val configuration: Configuration = ViewConfiguration(context),
) : GestureDetectorCompatImpl {
    private var mTouchSlopSquare = 0
    private var mDoubleTapSlopSquare = 0
    private var mMinimumFlingVelocity = 0
    private var mMaximumFlingVelocity = 0
    private val mHandler: Handler
    val mListener: GestureDetector.OnGestureListener
    var mDoubleTapListener: GestureDetector.OnDoubleTapListener? = null
    var mStillDown = false
    var mDeferConfirmSingleTap = false
    private var mInLongPress = false
    private var mAlwaysInTapRegion = false
    private var mAlwaysInBiggerTapRegion = false
    var mCurrentDownEvent: MotionEvent? = null
    private var mPreviousUpEvent: MotionEvent? = null

    /**
     * True when the user is still touching for the second tap (down, move, and
     * up events). Can only be true if there is a double tap listener attached.
     */
    private var mIsDoubleTapping = false
    private var mLastFocusX = 0f
    private var mLastFocusY = 0f
    private var mDownFocusX = 0f
    private var mDownFocusY = 0f
    /**
     * Set whether long press is enabled, if this is enabled when a user
     * presses and holds down you get a long press event and nothing further.
     * If it's disabled the user can press and hold down and then later
     * moved their finger and you will get scroll events. By default
     * long press is enabled.
     * @return true if long press is enabled, else false.
     */
    override var isLongpressEnabled = true

    /**
     * Determines speed during touch scrolling
     */
    private var mVelocityTracker: VelocityTracker? = null

    @SuppressLint("HandlerLeak")
    private inner class GestureHandler : Handler(Looper.myLooper() ?: Looper.getMainLooper()) {

        override fun handleMessage(msg: Message) {
            when (msg.what) {
                SHOW_PRESS -> mListener.onShowPress(mCurrentDownEvent!!)
                LONG_PRESS -> dispatchLongPress()
                TAP -> // If the user's finger is still down, do not count it as a tap
                    if (mDoubleTapListener != null) {
                        if (!mStillDown) {
                            mDoubleTapListener?.onSingleTapConfirmed(mCurrentDownEvent!!)
                        } else {
                            mDeferConfirmSingleTap = true
                        }
                    }

                else -> throw RuntimeException("Unknown message $msg") // never
            }
        }
    }

    init {
        mHandler = GestureHandler()
        mListener = listener
        if (listener is GestureDetector.OnDoubleTapListener) {
            setOnDoubleTapListener(listener as GestureDetector.OnDoubleTapListener)
        }
        init(configuration)
    }

    interface Configuration {
        val scaledTouchSlop: Int
        val scaledDoubleTapSlop: Int
        val scaledMinimumFlingVelocity: Int
        val scaledMaximumFlingVelocity: Int
        val longPressTimeout: Int
        val tapTimeout: Int
        val doubleTapTimeout: Int
    }

    open class ViewConfiguration(context: Context) : Configuration {
        private val viewConfiguration = android.view.ViewConfiguration.get(context)
        override val scaledDoubleTapSlop: Int get() = viewConfiguration.scaledDoubleTapSlop
        override val scaledTouchSlop: Int get() = viewConfiguration.scaledTouchSlop
        override val scaledMinimumFlingVelocity: Int get() = viewConfiguration.scaledMinimumFlingVelocity
        override val scaledMaximumFlingVelocity: Int get() = viewConfiguration.scaledMaximumFlingVelocity
        override val longPressTimeout: Int = android.view.ViewConfiguration.getLongPressTimeout()
        override val tapTimeout: Int = android.view.ViewConfiguration.getTapTimeout()
        override val doubleTapTimeout: Int = android.view.ViewConfiguration.getDoubleTapTimeout()
    }

    private fun init(configuration: Configuration) {
        val touchSlop = configuration.scaledTouchSlop
        val doubleTapSlop = configuration.scaledDoubleTapSlop
        mMinimumFlingVelocity = configuration.scaledMinimumFlingVelocity
        mMaximumFlingVelocity = configuration.scaledMaximumFlingVelocity
        mTouchSlopSquare = touchSlop * touchSlop
        mDoubleTapSlopSquare = doubleTapSlop * doubleTapSlop
    }

    /**
     * Sets the listener which will be called for double-tap and related
     * gestures.
     *
     * @param listener the listener invoked for all the callbacks, or
     * null to stop listening for double-tap gestures.
     */
    override fun setOnDoubleTapListener(listener: GestureDetector.OnDoubleTapListener) {
        mDoubleTapListener = listener
    }

    /**
     * Analyzes the given motion event and if applicable triggers the
     * appropriate callbacks on the [GestureDetector.OnGestureListener] supplied.
     *
     * @param ev The current motion event.
     * @return true if the [GestureDetector.OnGestureListener] consumed the event,
     * else false.
     */
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val action = ev.action
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain()
        }
        mVelocityTracker!!.addMovement(ev)
        val pointerUp = action and MotionEvent.ACTION_MASK == MotionEvent.ACTION_POINTER_UP
        val skipIndex = if (pointerUp) ev.actionIndex else -1

        // Determine focal point
        var sumX = 0f
        var sumY = 0f
        val count = ev.pointerCount
        for (i in 0 until count) {
            if (skipIndex == i) continue
            sumX += ev.getX(i)
            sumY += ev.getY(i)
        }
        val div = if (pointerUp) count - 1 else count
        val focusX = sumX / div
        val focusY = sumY / div
        var handled = false
        when (action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_POINTER_DOWN -> {
                run {
                    mLastFocusX = focusX
                    mDownFocusX = mLastFocusX
                }
                run {
                    mLastFocusY = focusY
                    mDownFocusY = mLastFocusY
                }
                // Cancel long press and taps
                cancelTaps()
            }

            MotionEvent.ACTION_POINTER_UP -> {
                run {
                    mLastFocusX = focusX
                    mDownFocusX = mLastFocusX
                }
                run {
                    mLastFocusY = focusY
                    mDownFocusY = mLastFocusY
                }

                // Check the dot product of current velocities.
                // If the pointer that left was opposing another velocity vector, clear.
                mVelocityTracker!!.computeCurrentVelocity(1000, mMaximumFlingVelocity.toFloat())
                val upIndex = ev.actionIndex
                val id1 = ev.getPointerId(upIndex)
                val x1 = mVelocityTracker!!.getXVelocity(id1)
                val y1 = mVelocityTracker!!.getYVelocity(id1)
                var i = 0
                while (i < count) {
                    if (i == upIndex) {
                        i++
                        continue
                    }
                    val id2 = ev.getPointerId(i)
                    val x = x1 * mVelocityTracker!!.getXVelocity(id2)
                    val y = y1 * mVelocityTracker!!.getYVelocity(id2)
                    val dot = x + y
                    if (dot < 0) {
                        mVelocityTracker!!.clear()
                        break
                    }
                    i++
                }
            }

            MotionEvent.ACTION_DOWN -> {
                if (mDoubleTapListener != null) {
                    val hadTapMessage = mHandler.hasMessages(TAP)
                    if (hadTapMessage) mHandler.removeMessages(TAP)
                    if (mCurrentDownEvent != null && mPreviousUpEvent != null &&
                        hadTapMessage && isConsideredDoubleTap(
                                mCurrentDownEvent!!, mPreviousUpEvent!!, ev
                            )
                    ) {
                        // This is a second tap
                        mIsDoubleTapping = true
                        // Give a callback with the first tap of the double-tap
                        handled = handled or mDoubleTapListener!!.onDoubleTap(mCurrentDownEvent!!)
                        // Give a callback with down event of the double-tap
                        handled = handled or mDoubleTapListener!!.onDoubleTapEvent(ev)
                    } else {
                        // This is a first tap
                        mHandler.sendEmptyMessageDelayed(TAP, configuration.doubleTapTimeout.toLong())
                    }
                }
                run {
                    mLastFocusX = focusX
                    mDownFocusX = mLastFocusX
                }
                run {
                    mLastFocusY = focusY
                    mDownFocusY = mLastFocusY
                }
                if (mCurrentDownEvent != null) {
                    mCurrentDownEvent?.recycle()
                }
                val mCurrentDownEvent = MotionEvent.obtain(ev)
                this@ConfigurableGestureDetector.mCurrentDownEvent = mCurrentDownEvent
                mAlwaysInTapRegion = true
                mAlwaysInBiggerTapRegion = true
                mStillDown = true
                mInLongPress = false
                mDeferConfirmSingleTap = false
                if (isLongpressEnabled) {
                    mHandler.removeMessages(LONG_PRESS)
                    mHandler.sendEmptyMessageAtTime(
                        LONG_PRESS,
                        mCurrentDownEvent.downTime +
                            configuration.tapTimeout + configuration.longPressTimeout
                    )
                }
                mHandler.sendEmptyMessageAtTime(
                    SHOW_PRESS,
                    mCurrentDownEvent.downTime + configuration.tapTimeout
                )
                handled = handled or mListener.onDown(ev)
            }

            MotionEvent.ACTION_MOVE -> run {
                if (mInLongPress) {
                    return@run
                }
                val scrollX = mLastFocusX - focusX
                val scrollY = mLastFocusY - focusY
                if (mIsDoubleTapping) {
                    // Give the move events of the double-tap
                    handled = handled or mDoubleTapListener!!.onDoubleTapEvent(ev)
                } else if (mAlwaysInTapRegion) {
                    val deltaX = (focusX - mDownFocusX).toInt()
                    val deltaY = (focusY - mDownFocusY).toInt()
                    val distance = deltaX * deltaX + deltaY * deltaY
                    if (distance > mTouchSlopSquare) {
                        handled = mListener.onScroll(mCurrentDownEvent!!, ev, scrollX, scrollY)
                        mLastFocusX = focusX
                        mLastFocusY = focusY
                        mAlwaysInTapRegion = false
                        mHandler.removeMessages(TAP)
                        mHandler.removeMessages(SHOW_PRESS)
                        mHandler.removeMessages(LONG_PRESS)
                    }
                    if (distance > mTouchSlopSquare) {
                        mAlwaysInBiggerTapRegion = false
                    }
                } else if (abs(scrollX) >= 1 || abs(scrollY) >= 1) {
                    handled = mListener.onScroll(mCurrentDownEvent!!, ev, scrollX, scrollY)
                    mLastFocusX = focusX
                    mLastFocusY = focusY
                }
            }

            MotionEvent.ACTION_UP -> {
                mStillDown = false
                val currentUpEvent = MotionEvent.obtain(ev)
                if (mIsDoubleTapping) {
                    // Finally, give the up event of the double-tap
                    handled = handled or mDoubleTapListener!!.onDoubleTapEvent(ev)
                } else if (mInLongPress) {
                    mHandler.removeMessages(TAP)
                    mInLongPress = false
                } else if (mAlwaysInTapRegion) {
                    handled = mListener.onSingleTapUp(ev)
                    if (mDeferConfirmSingleTap && mDoubleTapListener != null) {
                        mDoubleTapListener!!.onSingleTapConfirmed(ev)
                    }
                } else {
                    // A fling must travel the minimum tap distance
                    val velocityTracker = mVelocityTracker
                    val pointerId = ev.getPointerId(0)
                    velocityTracker!!.computeCurrentVelocity(1000, mMaximumFlingVelocity.toFloat())
                    val velocityY = velocityTracker.getYVelocity(pointerId)
                    val velocityX = velocityTracker.getXVelocity(pointerId)
                    if (abs(velocityY) > mMinimumFlingVelocity || abs(velocityX) > mMinimumFlingVelocity) {
                        handled = mListener.onFling(
                            mCurrentDownEvent!!, ev, velocityX, velocityY
                        )
                    }
                }
                if (mPreviousUpEvent != null) {
                    mPreviousUpEvent!!.recycle()
                }
                // Hold the event we obtained above - listeners may have changed the original.
                mPreviousUpEvent = currentUpEvent
                if (mVelocityTracker != null) {
                    // This may have been cleared when we called out to the
                    // application above.
                    mVelocityTracker!!.recycle()
                    mVelocityTracker = null
                }
                mIsDoubleTapping = false
                mDeferConfirmSingleTap = false
                mHandler.removeMessages(SHOW_PRESS)
                mHandler.removeMessages(LONG_PRESS)
            }

            MotionEvent.ACTION_CANCEL -> cancel()
        }
        return handled
    }

    private fun cancel() {
        mHandler.removeMessages(SHOW_PRESS)
        mHandler.removeMessages(LONG_PRESS)
        mHandler.removeMessages(TAP)
        mVelocityTracker!!.recycle()
        mVelocityTracker = null
        mIsDoubleTapping = false
        mStillDown = false
        mAlwaysInTapRegion = false
        mAlwaysInBiggerTapRegion = false
        mDeferConfirmSingleTap = false
        if (mInLongPress) {
            mInLongPress = false
        }
    }

    private fun cancelTaps() {
        mHandler.removeMessages(SHOW_PRESS)
        mHandler.removeMessages(LONG_PRESS)
        mHandler.removeMessages(TAP)
        mIsDoubleTapping = false
        mAlwaysInTapRegion = false
        mAlwaysInBiggerTapRegion = false
        mDeferConfirmSingleTap = false
        if (mInLongPress) {
            mInLongPress = false
        }
    }

    private fun isConsideredDoubleTap(
        firstDown: MotionEvent,
        firstUp: MotionEvent,
        secondDown: MotionEvent?
    ): Boolean {
        if (!mAlwaysInBiggerTapRegion) {
            return false
        }
        if (secondDown!!.eventTime - firstUp.eventTime > configuration.doubleTapTimeout) {
            return false
        }
        val deltaX = firstDown.x.toInt() - secondDown.x.toInt()
        val deltaY = firstDown.y.toInt() - secondDown.y.toInt()
        return deltaX * deltaX + deltaY * deltaY < mDoubleTapSlopSquare
    }

    fun dispatchLongPress() {
        mHandler.removeMessages(TAP)
        mDeferConfirmSingleTap = false
        mInLongPress = true
        mListener.onLongPress(mCurrentDownEvent!!)
    }

    companion object {
        // constants for Message.what used by GestureHandler below
        private const val SHOW_PRESS = 1
        private const val LONG_PRESS = 2
        private const val TAP = 3
    }
}
