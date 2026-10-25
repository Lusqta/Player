package com.lucas.player.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var peaks: FloatArray = FloatArray(70) { 0.15f }
    private var progress: Float = 0f // 0.0 to 1.0

    private val playedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E676")
        strokeCap = Paint.Cap.ROUND
    }

    private val unplayedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#424242")
        strokeCap = Paint.Cap.ROUND
    }

    var onSeekListener: ((Float) -> Unit)? = null

    fun setWaveform(newPeaks: FloatArray?) {
        if (newPeaks != null && newPeaks.isNotEmpty()) {
            peaks = newPeaks
        } else {
            // Default gentle wave placeholder
            peaks = FloatArray(70) { i ->
                (0.2f + 0.5f * kotlin.math.sin(i * 0.15).toFloat().coerceAtLeast(0f))
            }
        }
        invalidate()
    }

    fun setProgress(newProgress: Float) {
        val clamped = newProgress.coerceIn(0f, 1f)
        if (kotlin.math.abs(progress - clamped) > 0.001f) {
            progress = clamped
            invalidate()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val newProgress = (event.x / width.toFloat()).coerceIn(0f, 1f)
                progress = newProgress
                invalidate()
                onSeekListener?.invoke(newProgress)
                return true
            }
            MotionEvent.ACTION_UP -> {
                val newProgress = (event.x / width.toFloat()).coerceIn(0f, 1f)
                progress = newProgress
                invalidate()
                onSeekListener?.invoke(newProgress)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0 || peaks.isEmpty()) return

        val count = peaks.size
        val barWidth = (width.toFloat() / count.toFloat())
        val spacing = barWidth * 0.3f
        val actualBarWidth = (barWidth - spacing).coerceAtLeast(2f)

        playedPaint.strokeWidth = actualBarWidth
        unplayedPaint.strokeWidth = actualBarWidth

        val centerY = height / 2f
        val maxHalfHeight = (height * 0.45f)

        for (i in 0 until count) {
            val x = i * barWidth + barWidth / 2f
            val normPeak = peaks[i].coerceIn(0.08f, 1.0f)
            val barHalfHeight = normPeak * maxHalfHeight

            val isPlayed = (x / width.toFloat()) <= progress
            val paint = if (isPlayed) playedPaint else unplayedPaint

            canvas.drawLine(x, centerY - barHalfHeight, x, centerY + barHalfHeight, paint)
        }
    }
}
