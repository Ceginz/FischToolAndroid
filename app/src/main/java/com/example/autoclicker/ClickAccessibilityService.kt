package com.example.autoclicker

import android.accessibilityservice.AccessibilityService
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
        // El juego se dibuja como imagen (lienzo), no expone texto ni botones
        // accesibles, así que en la Fase 2 la detección se hará analizando
        // capturas de pantalla (MediaProjection), no eventos de accesibilidad.
        // Este servicio solo se usa para EJECUTAR los toques/gestos.
    }

    override fun onInterrupt() {
        Log.d(TAG, "Servicio interrumpido")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }
}
