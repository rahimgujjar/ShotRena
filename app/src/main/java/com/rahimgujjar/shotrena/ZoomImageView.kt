package com.rahimgujjar.shotrena

import android.content.Context
import android.graphics.Matrix
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewTreeObserver
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.abs

class ZoomImageView(context: Context, attrs: AttributeSet?) : AppCompatImageView(context, attrs),
    View.OnTouchListener {

    private var scaleDetector: ScaleGestureDetector
    private var gestureDetector: GestureDetector
    private val displayMatrix = Matrix()
    private val mMatrixValues = FloatArray(9)
    private var initScale = 1.0f
    private val midScale = 2.5f
    private val maxScale = 5.0f

    init {
        scaleType = ScaleType.MATRIX
        setOnTouchListener(this)
        scaleDetector = ScaleGestureDetector(context, ScaleListener())
        gestureDetector = GestureDetector(context, DoubleTapListener())
    }

    fun resetZoom() {
        viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (width > 0 && height > 0 && drawable != null) {
                    fitToScreen()
                    viewTreeObserver.removeOnGlobalLayoutListener(this)
                }
            }
        })
        requestLayout()
    }

    private fun fitToScreen() {
        if (drawable == null) return
        val dWidth = drawable.intrinsicWidth.toFloat()
        val dHeight = drawable.intrinsicHeight.toFloat()
        val vWidth = width.toFloat()
        val vHeight = height.toFloat()

        val scale = if (dWidth > vWidth && dHeight <= vHeight) {
            vWidth / dWidth
        } else if (dHeight > vHeight && dWidth <= vWidth) {
            vHeight / dHeight
        } else {
            (vWidth / dWidth).coerceAtMost(vHeight / dHeight)
        }

        initScale = scale
        val dx = (vWidth - dWidth * scale) / 2
        val dy = (vHeight - dHeight * scale) / 2

        displayMatrix.reset()
        displayMatrix.postScale(scale, scale)
        displayMatrix.postTranslate(dx, dy)
        imageMatrix = displayMatrix
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        if (event == null) return false

        v?.parent?.requestDisallowInterceptTouchEvent(true)

        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        val pointerCount = event.pointerCount
        var avgX = 0f
        var avgY = 0f
        for (i in 0 until pointerCount) {
            avgX += event.getX(i)
            avgY += event.getY(i)
        }
        avgX /= pointerCount
        avgY /= pointerCount

        if (pointerCount != lastPointerCount) {
            isDragging = false
            lastX = avgX
            lastY = avgY
        }
        lastPointerCount = pointerCount

        when (event.action) {
            MotionEvent.ACTION_MOVE -> {
                val dx = avgX - lastX
                val dy = avgY - lastY

                if (!isDragging) {
                    isDragging = abs(dx) > 0 || abs(dy) > 0
                }

                if (isDragging && drawable != null) {
                    displayMatrix.postTranslate(dx, dy)
                    checkMatrixBounds()
                    imageMatrix = displayMatrix
                }
                lastX = avgX
                lastY = avgY
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                lastPointerCount = 0
                if (event.action == MotionEvent.ACTION_UP) performClick()
                v?.parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }

    private var lastPointerCount = 0
    private var isDragging = false
    private var lastX = 0f
    private var lastY = 0f

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scaleFactor = detector.scaleFactor
            val currentScale = getScale()
            if (drawable == null) return true

            val minAllowed = initScale * 0.5f
            val maxAllowed = initScale * maxScale

            if ((currentScale < maxAllowed && scaleFactor > 1.0f) ||
                (currentScale > minAllowed && scaleFactor < 1.0f)
            ) {
                displayMatrix.postScale(scaleFactor, scaleFactor, detector.focusX, detector.focusY)
                checkMatrixBounds()
                imageMatrix = displayMatrix
            }
            return true
        }
    }

    private inner class DoubleTapListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (isAutoScale) return true
            val x = e.x
            val y = e.y
            val currentScale = getScale()

            val targetScale = when {
                currentScale < initScale * midScale -> initScale * midScale
                currentScale < initScale * maxScale -> initScale * maxScale
                else -> initScale
            }

            postDelayed(AutoScaleRunnable(targetScale, x, y), 16)
            isAutoScale = true
            return true
        }
    }

    private var isAutoScale = false
    private inner class AutoScaleRunnable(
        private val targetScale: Float,
        private val x: Float,
        private val y: Float
    ) : Runnable {
        private val bigger = 1.07f
        private val smaller = 0.93f
        private val tmpScale: Float = if (getScale() < targetScale) bigger else smaller

        override fun run() {
            displayMatrix.postScale(tmpScale, tmpScale, x, y)
            checkMatrixBounds()
            imageMatrix = displayMatrix

            val currentScale = getScale()
            if ((tmpScale > 1f && currentScale < targetScale) ||
                (tmpScale < 1f && targetScale < currentScale)
            ) {
                postDelayed(this, 16)
            } else {
                val scale = targetScale / currentScale
                displayMatrix.postScale(scale, scale, x, y)
                checkMatrixBounds()
                imageMatrix = displayMatrix
                isAutoScale = false
            }
        }
    }

    private fun getScale(): Float {
        displayMatrix.getValues(mMatrixValues)
        return mMatrixValues[Matrix.MSCALE_X]
    }

    private fun getMatrixRectF(): RectF {
        val rect = RectF()
        val d = drawable
        if (d != null) {
            rect.set(0f, 0f, d.intrinsicWidth.toFloat(), d.intrinsicHeight.toFloat())
            displayMatrix.mapRect(rect)
        }
        return rect
    }

    private fun checkMatrixBounds() {
        val rect = getMatrixRectF()
        var deltaX: Float
        var deltaY: Float
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()

        deltaY = if (rect.height() >= viewHeight) {
            when {
                rect.top > 0 -> -rect.top
                rect.bottom < viewHeight -> viewHeight - rect.bottom
                else -> 0f
            }
        } else {
            (viewHeight - rect.height()) / 2 - rect.top
        }

        deltaX = if (rect.width() >= viewWidth) {
            when {
                rect.left > 0 -> -rect.left
                rect.right < viewWidth -> viewWidth - rect.right
                else -> 0f
            }
        } else {
            (viewWidth - rect.width()) / 2 - rect.left
        }

        displayMatrix.postTranslate(deltaX, deltaY)
    }
}
