package com.example.autoclicker

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import kotlin.math.abs

object OverlayManager {

    private var windowManager: WindowManager? = null
    private var barView: View? = null
    private var miniView: View? = null

    var isPaused: Boolean = false
        private set

    private var onClose: (() -> Unit)? = null

    fun show(context: Context, onCloseRequested: () -> Unit) {
        if (barView != null || miniView != null) return
        onClose = onCloseRequested
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        addControlBar(context)
    }

    fun hide() {
        barView?.let { runCatching { windowManager?.removeView(it) } }
        miniView?.let { runCatching { windowManager?.removeView(it) } }
        barView = null
        miniView = null
        windowManager = null
        isPaused = false
    }

    private fun overlayType() = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

    private fun addControlBar(context: Context) {
        val bar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#CC222222"))
            setPadding(20, 14, 20, 14)
        }

        val btnClose = Button(context).apply { text = "✕" }
        val btnPause = Button(context).apply { text = if (isPaused) "▶" else "⏸" }
        val btnMin = Button(context).apply { text = "—" }

        listOf(btnClose, btnPause, btnMin).forEach {
            it.setPadding(28, 10, 28, 10)
            it.setTextColor(Color.WHITE)
        }

        bar.addView(btnClose)
        bar.addView(btnPause)
        bar.addView(btnMin)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 40
        params.y = 120

        makeDraggable(bar, params)

        btnClose.setOnClickListener { onClose?.invoke() }
        btnPause.setOnClickListener {
            isPaused = !isPaused
            btnPause.text = if (isPaused) "▶" else "⏸"
        }
        btnMin.setOnClickListener {
            runCatching { windowManager?.removeView(bar) }
            barView = null
            addMiniIcon(context, params.x, params.y)
        }

        windowManager?.addView(bar, params)
        barView = bar
    }

    private fun addMiniIcon(context: Context, lastX: Int, lastY: Int) {
        val icon = FrameLayout(context).apply {
            setBackgroundColor(Color.parseColor("#CC222222"))
        }
        val size = 130
        val params = WindowManager.LayoutParams(
            size, size,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = lastX
        params.y = lastY

        makeDraggable(icon, params)

        icon.setOnClickListener {
            runCatching { windowManager?.removeView(icon) }
            miniView = null
            addControlBar(context)
        }

        windowManager?.addView(icon, params)
        miniView = icon
    }

    private fun makeDraggable(view: View, params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f

        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (abs(dx) > 8 || abs(dy) > 8) {
                        params.x = initialX + dx
                        params.y = initialY + dy
                        runCatching { windowManager?.updateViewLayout(v, params) }
                    }
                    false
                }
                else -> false
            }
        }
    }

    /** Muestra un círculo rojo breve en (x, y) si la visualización de clicks está activada. */
    fun showClickRipple(context: Context, x: Int, y: Int) {
        if (!PrefsHelper.showClicks(context)) return

        val handler = Handler(context.mainLooper)
        handler.post {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val dot = View(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#88FF0000"))
                }
            }
            val size = 60
            val params = WindowManager.LayoutParams(
                size, size,
                overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.TOP or Gravity.START
            params.x = x - size / 2
            params.y = y - size / 2

            runCatching {
                wm.addView(dot, params)
                handler.postDelayed({ runCatching { wm.removeView(dot) } }, 350)
            }
        }
    }
}
