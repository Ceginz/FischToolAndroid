package com.example.autoclicker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class ClickAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "ClickA11yService"

        @Volatile
        var instance: ClickAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Servicio conectado")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // la detección se hace por captura de pantalla, no por eventos de accesibilidad
    }

    override fun onInterrupt() {
        Log.d(TAG, "Servicio interrumpido")
    }

    /** Simula un toque en (x, y) y muestra el círculo visual si está activado. */
    fun performTap(x: Int, y: Int, durationMs: Long = 60L) {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
        OverlayManager.showClickRipple(this, x, y)
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }
}
