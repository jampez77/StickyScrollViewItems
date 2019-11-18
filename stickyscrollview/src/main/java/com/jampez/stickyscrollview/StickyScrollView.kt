package com.jampez.stickyscrollview

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.NestedScrollView
import kotlin.math.min

/**
 * Created by Jamie Pezone on 18/11/2019.
 * Ontrac Ltd
 * jamie.pezone@on-trac.co.uk
 */
class StickyScrollView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = android.R.attr.scrollViewStyle) : NestedScrollView(context, attrs, defStyle) {

    private var stickyViews: ArrayList<View>? = null
    private var currentlyStickingView: View? = null
    private var stickyViewTopOffset: Float = 0f
    private var stickyViewLeftOffset: Int = 0
    private var redirectTouchesToStickyView: Boolean = false
    private var clippingToPadding: Boolean = false
    private var clipToPaddingHasBeenSet: Boolean = false
    private var mShadowHeight: Int = 0
    private var mShadowDrawable: Drawable? = null
    private var hasNotDoneActionDown = true
    private val invalidateRunnable = object : Runnable {
        override fun run() {
            if (currentlyStickingView != null)
                invalidate()

            postDelayed(this, 16)
        }
    }

    init {
        setup()

        val a = context.obtainStyledAttributes(attrs, R.styleable.StickyScrollView, defStyle, 0)

        val density = context.resources.displayMetrics.density
        val defaultShadowHeightInPix = (DEFAULT_SHADOW_HEIGHT * density + 0.5f).toInt()

        mShadowHeight = a.getDimensionPixelSize(R.styleable.StickyScrollView_stuckShadowHeight, defaultShadowHeightInPix)

        val shadowDrawableRes = a.getResourceId(R.styleable.StickyScrollView_stuckShadowDrawable, -1)

        if (shadowDrawableRes != -1)
            mShadowDrawable = context.resources.getDrawable(shadowDrawableRes, context.theme)

        a.recycle()
    }

    private fun getLeftForViewRelativeOnlyChild(view: View): Int {
        var v = view
        var left = v.left
        while (v.parent !== getChildAt(0)) {
            v = v.parent as View
            left += v.left
        }
        return left
    }

    private fun getTopForViewRelativeOnlyChild(view: View): Int {
        var v = view
        var top = v.top
        while (v.parent !== getChildAt(0)) {
            v = v.parent as View
            top += v.top
        }
        return top
    }

    private fun getRightForViewRelativeOnlyChild(view: View): Int {
        var v = view
        var right = v.right
        while (v.parent !== getChildAt(0)) {
            v = v.parent as View
            right += v.right
        }
        return right
    }

    /*
    private fun getBottomForViewRelativeOnlyChild(view: View): Int {
        var v = view
        var bottom = v.bottom
        while (v.parent !== getChildAt(0)) {
            v = v.parent as View
            bottom += v.bottom
        }
        return bottom
    }
    */

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        if (!clipToPaddingHasBeenSet)
            clippingToPadding = true
        notifyHierarchyChanged()
    }

    override fun setClipToPadding(clipToPadding: Boolean) {
        super.setClipToPadding(clipToPadding)
        clippingToPadding = clipToPadding
        clipToPaddingHasBeenSet = true
    }

    override fun addView(child: View) {
        super.addView(child)
        findStickyViews(child)
    }

    override fun addView(child: View, index: Int) {
        super.addView(child, index)
        findStickyViews(child)
    }

    /**
     * Add view during runtime
     */
    @Suppress("unused")
    fun addView(child: View, index: Int, params: LayoutParams) {
        super.addView(child, index, params)
        findStickyViews(child)
    }

    @Suppress("unused")
    fun addView(child: View, params: LayoutParams) {
        super.addView(child, params)
        findStickyViews(child)
    }

    override fun addView(child: View, width: Int, height: Int) {
        super.addView(child, width, height)
        findStickyViews(child)
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (currentlyStickingView != null) {
            canvas.save()
            canvas.translate((paddingLeft + stickyViewLeftOffset).toFloat(), scrollY.toFloat() + stickyViewTopOffset + (if (clippingToPadding) paddingTop else 0).toFloat())
            canvas.clipRect(0f, if (clippingToPadding) -stickyViewTopOffset else 0f, (width - stickyViewLeftOffset).toFloat(), (currentlyStickingView!!.height + mShadowHeight + 1).toFloat())

            if (mShadowDrawable != null) {
                val left = 0
                val right = currentlyStickingView!!.width
                val top = currentlyStickingView!!.height
                val bottom = currentlyStickingView!!.height + mShadowHeight
                mShadowDrawable!!.setBounds(left, top, right, bottom)
                mShadowDrawable!!.draw(canvas)
            }

            canvas.clipRect(0f, if (clippingToPadding) -stickyViewTopOffset else 0f, width.toFloat(), currentlyStickingView!!.height.toFloat())

            if (getStringTagForView(currentlyStickingView!!).contains(FLAG_HAS_TRANSPARENCY)) {
                showView(currentlyStickingView!!)
                currentlyStickingView!!.draw(canvas)
                hideView(currentlyStickingView!!)
            } else currentlyStickingView!!.draw(canvas)

            canvas.restore()
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN)
            redirectTouchesToStickyView = true

        if (redirectTouchesToStickyView) {
            redirectTouchesToStickyView = currentlyStickingView != null
            if (redirectTouchesToStickyView)
                redirectTouchesToStickyView = ev.y <= currentlyStickingView!!.height + stickyViewTopOffset && ev.x >= getLeftForViewRelativeOnlyChild(currentlyStickingView!!) && ev.x <= getRightForViewRelativeOnlyChild(currentlyStickingView!!)
        }

        if (redirectTouchesToStickyView)
            ev.offsetLocation(0f, -1 * (scrollY + stickyViewTopOffset - getTopForViewRelativeOnlyChild(currentlyStickingView!!)))

        return super.dispatchTouchEvent(ev)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (redirectTouchesToStickyView)
            ev.offsetLocation(0f, scrollY + stickyViewTopOffset - getTopForViewRelativeOnlyChild(currentlyStickingView!!))
        if (ev.action == MotionEvent.ACTION_DOWN)
            hasNotDoneActionDown = false
        if (hasNotDoneActionDown) {
            val down = MotionEvent.obtain(ev)
            down.action = MotionEvent.ACTION_DOWN
            super.onTouchEvent(down)
            hasNotDoneActionDown = false
        }

        if (ev.action == MotionEvent.ACTION_UP || ev.action == MotionEvent.ACTION_CANCEL)
            hasNotDoneActionDown = true
        performClick()
        return super.onTouchEvent(ev)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        doTheStickyThing()
    }

    private fun doTheStickyThing() {
        var viewThatShouldStick: View? = null
        var approachingView: View? = null
        for (v in stickyViews!!) {
            val viewTop = getTopForViewRelativeOnlyChild(v) - scrollY + if (clippingToPadding) 0 else paddingTop
            if (viewTop <= 0) {
                if (viewThatShouldStick == null || viewTop > getTopForViewRelativeOnlyChild(viewThatShouldStick) - scrollY + if (clippingToPadding) 0 else paddingTop)
                    viewThatShouldStick = v
            } else if (approachingView == null || viewTop < getTopForViewRelativeOnlyChild(approachingView) - scrollY + if (clippingToPadding) 0 else paddingTop)
                approachingView = v
        }
        if (viewThatShouldStick != null) {
            stickyViewTopOffset = (if (approachingView == null) 0 else min(0, getTopForViewRelativeOnlyChild(approachingView) - scrollY + (if (clippingToPadding) 0 else paddingTop) - viewThatShouldStick.height)).toFloat()
            if (viewThatShouldStick !== currentlyStickingView) {
                if (currentlyStickingView != null)
                    stopStickingCurrentlyStickingView()
                // only compute the left offset when we start sticking.
                stickyViewLeftOffset = getLeftForViewRelativeOnlyChild(viewThatShouldStick)
                startStickingView(viewThatShouldStick)
            }
        } else if (currentlyStickingView != null)
            stopStickingCurrentlyStickingView()
    }

    private fun startStickingView(viewThatShouldStick: View) {
        currentlyStickingView = viewThatShouldStick
        if (getStringTagForView(currentlyStickingView!!).contains(FLAG_HAS_TRANSPARENCY))
            hideView(currentlyStickingView!!)
        if ((currentlyStickingView!!.tag as String).contains(FLAG_NON_CONSTANT))
            post(invalidateRunnable)
    }

    private fun stopStickingCurrentlyStickingView() {
        if (getStringTagForView(currentlyStickingView!!).contains(FLAG_HAS_TRANSPARENCY))
            showView(currentlyStickingView!!)
        currentlyStickingView = null
        removeCallbacks(invalidateRunnable)
    }

    private fun notifyHierarchyChanged() {
        if (currentlyStickingView != null)
            stopStickingCurrentlyStickingView()
        stickyViews!!.clear()
        findStickyViews(getChildAt(0))
        doTheStickyThing()
        invalidate()
    }

    private fun findStickyViews(v: View) {
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) {
                val tag = getStringTagForView(v.getChildAt(i))
                if (tag.contains(STICKY_TAG))
                    stickyViews!!.add(v.getChildAt(i))
                else if (v.getChildAt(i) is ViewGroup)
                    findStickyViews(v.getChildAt(i))
            }
        } else {
            val tag = v.tag as String
            if (tag.contains(STICKY_TAG))
                stickyViews!!.add(v)
        }
    }

    @Suppress("unused") fun setShadowHeight(height: Int) { mShadowHeight = height }
    @Suppress("unused") fun notifyStickyAttributeChanged() { notifyHierarchyChanged() }
    private fun getStringTagForView(v: View): String { return java.lang.String.valueOf(v.tag)
    }
    private fun hideView(v: View) { v.alpha = 0f }
    private fun showView(v: View) { v.alpha = 1f }
    private fun setup() { stickyViews = ArrayList() }

    companion object {
        const val STICKY_TAG = "sticky"
        const val FLAG_NON_CONSTANT = "-non_constant"
        const val FLAG_HAS_TRANSPARENCY = "-has_transparency"
        const val DEFAULT_SHADOW_HEIGHT = 10 // dp;
    }
}