package com.example.autoclicker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class ClickAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "ClickA11yService"
        private const val HOLD_SEGMENT_MS = 150L

        @Volatile
        var instance: ClickAccessibilityService? = null
            private set
    }

    private var isHolding = false
    private var currentStroke: GestureDescription.StrokeDescription? = null
    private var holdX = 0f
    private var holdY = 0f

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Servicio conectado")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {
        Log.d(TAG, "Servicio interrumpido")
    }

    /** Toque simple (botón de lanzar caña, SHAKE, etc). */
    fun performTap(x: Int, y: Int, durationMs: Long = 60L) {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
        OverlayManager.showClickRipple(this, x, y)
    }

    /** Empieza (o mantiene) un toque sostenido en (x, y). */
    fun startOrContinueHold(x: Int, y: Int) {
        holdX = x.toFloat()
        holdY = y.toFloat()
        if (isHolding) return

        isHolding = true
        val path = Path().apply { moveTo(holdX, holdY) }
        val stroke = GestureDescription.StrokeDescription(path, 0, HOLD_SEGMENT_MS, true)
        currentStroke = stroke
        OverlayManager.showClickRipple(this, x, y)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), holdCallback, null)
    }

    /** Suelta el toque sostenido, si había uno activo. */
    fun release() {
        if (!isHolding) return
        isHolding = false
        val stroke = currentStroke ?: return
        currentStroke = null
        val path = Path().apply { moveTo(holdX, holdY) }
        val finalStroke = stroke.continueStroke(path, 0, 10, false)
        dispatchGesture(GestureDescription.Builder().addStroke(finalStroke).build(), null, null)
    }

    private val holdCallback = object : GestureResultCallback() {
        override fun onCompleted(gestureDescription: GestureDescription?) {
            if (!isHolding) return
            val stroke = currentStroke ?: return
            val path = Path().apply { moveTo(holdX, holdY) }
            val next = stroke.continueStroke(path, 0, HOLD_SEGMENT_MS, true)
            currentStroke = next
            dispatchGesture(GestureDescription.Builder().addStroke(next).build(), this, null)
        }

        override fun onCancelled(gestureDescription: GestureDescription?) {
            isHolding = false
            currentStroke = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }
}
