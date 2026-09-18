package com.example.autoclicker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.view.WindowManager
import androidx.core.app.NotificationCompat

class ScreenCaptureService : Service() {

    companion object {
        const val CHANNEL_ID = "autoclicker_capture"
        const val NOTIF_ID = 1001
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_DATA = "data"
        private const val START_DELAY_MS = 5000L
        private const val CAST_HOLD_MS = 2000L
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private lateinit var handlerThread: HandlerThread
    private lateinit var handler: Handler
    private lateinit var mainHandler: Handler

    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0
    private var lastProcessTime = 0L
    private var lastCastTime = 0L
    private val minIntervalMs = 400L
    private val castCooldownMs = 5000L

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() { stopSelf() }
    }

    override fun onCreate() {
        super.onCreate()
        handlerThread = HandlerThread("CaptureThread")
        handlerThread.start()
        handler = Handler(handlerThread.looper)
        mainHandler = Handler(Looper.getMainLooper())
        createNotificationChannel()
    }

    private fun getRealScreenSize(): Pair<Int, Int> {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            val point = Point()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealSize(point)
            point.x to point.y
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification("Cambia a Roblox ahora — iniciando en 5s…")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIF_ID, notification)
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val data = intent?.getParcelableExtra<Intent>(EXTRA_DATA)
        if (data == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)
        mediaProjection?.registerCallback(projectionCallback, handler)

        mainHandler.post { OverlayManager.show(applicationContext) { stopSelf() } }

        mainHandler.postDelayed({
            val (w, h) = getRealScreenSize()
            screenWidth = w
            screenHeight = h
            screenDensity = resources.displayMetrics.densityDpi
            updateNotification("Pantalla detectada: ${screenWidth}x${screenHeight}")
            startCapture()
        }, START_DELAY_MS)

        return START_STICKY
    }

    private fun startCapture() {
        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "AutoClickerCapture",
            screenWidth, screenHeight, screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, handler
        )

        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            val now = System.currentTimeMillis()

            if (OverlayManager.isPaused || now - lastProcessTime < minIntervalMs) {
                image.close()
                return@setOnImageAvailableListener
            }
            lastProcessTime = now

            try {
                val bitmap = imageToBitmap(image)
                handleFrame(bitmap, now)
                bitmap.recycle()
            } catch (e: Exception) {
                // no tumbar el servicio si un frame falla al analizarse
            } finally {
                image.close()
            }
        }, handler)
    }

    private fun handleFrame(bitmap: Bitmap, now: Long) {
        val service = ClickAccessibilityService.instance ?: return
        val w = bitmap.width
        val h = bitmap.height

        if (ScreenDetector.isShakeButtonVisible(bitmap)) {
            service.release()
            val (sx, sy) = ScreenDetector.shakeButtonPoint(w, h)
            service.performTap(sx, sy)
            updateNotification("SHAKE en ($sx,$sy)")
            return
        }

        val state = ScreenDetector.analyzeReelBar(bitmap)
        val fillX = state.barFillX
        val lineX = state.lineX
        val fillTxt = fillX?.let { "%.0f%%".format(it * 100) } ?: "no detectada"
        val lineTxt = lineX?.let { "%.0f%%".format(it * 100) } ?: "no detectada"

        when {
            fillX != null && lineX != null -> {
                val (hx, hy) = ScreenDetector.holdPoint(w, h)
                if (lineX > fillX) {
                    service.startOrContinueHold(hx, hy)
                } else {
                    service.release()
                }
                updateNotification("Toque ($hx,$hy) | Barra:$fillTxt Línea:$lineTxt")
            }
            fillX != null || lineX != null -> {
                service.release()
                updateNotification("Parcial → Barra:$fillTxt Línea:$lineTxt")
            }
            else -> {
                service.release()
                if (now - lastCastTime > castCooldownMs) {
                    lastCastTime = now
                    val (cx, cy) = ScreenDetector.castButtonPoint(w, h)
                    service.performTap(cx, cy, CAST_HOLD_MS)
                    updateNotification("Lanzando (hold 2s) en ($cx,$cy)")
                } else {
                    updateNotification("Esperando… [captura ${w}x${h}]")
                }
            }
        }
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width

        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride, image.height, Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)

        return if (rowPadding == 0) bitmap
        else Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Detección de pantalla", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AutoClicker")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(text))
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.post { OverlayManager.hide() }
        imageReader?.setOnImageAvailableListener(null, null)
        virtualDisplay?.release()
        mediaProjection?.unregisterCallback(projectionCallback)
        mediaProjection?.stop()
        imageReader?.close()
        handlerThread.quitSafely()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
