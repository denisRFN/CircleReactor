package com.pricelens.circlereactor.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.media.projection.MediaProjection.Callback
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.WindowManager
import com.pricelens.circlereactor.tap.TapAccessibilityService
import com.pricelens.circlereactor.vision.CircleDetector

class CaptureService : Service() {
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private val detector = CircleDetector()
    private var lastFrameAtMs = 0L
    private var scaleX = 1f
    private var scaleY = 1f

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startAsForeground(notification("Waiting for capture permission"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopSelf()
            ACTION_START -> startCapture(intent)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        display?.release()
        reader?.close()
        projection?.stop()
        thread?.quitSafely()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startCapture(intent: Intent) {
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        val data = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA) ?: return
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = manager.getMediaProjection(resultCode, data)

        val metrics = displayMetrics()
        val captureWidth = 360
        val captureHeight = (metrics.heightPixels * (captureWidth.toFloat() / metrics.widthPixels)).toInt().coerceAtLeast(240)
        scaleX = metrics.widthPixels.toFloat() / captureWidth
        scaleY = metrics.heightPixels.toFloat() / captureHeight

        reader = ImageReader.newInstance(captureWidth, captureHeight, PixelFormat.RGBA_8888, 2)
        thread = HandlerThread("circle-capture").also { it.start() }
        handler = Handler(thread!!.looper)

        projection!!.registerCallback(object : Callback() {
            override fun onStop() {
                display?.release()
                reader?.close()
            }
        }, handler)

        reader!!.setOnImageAvailableListener({ imageReader -> onImage(imageReader) }, handler)
        display = projection!!.createVirtualDisplay(
            "CircleReactorDisplay",
            captureWidth,
            captureHeight,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader!!.surface,
            null,
            handler
        )

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(42, notification("Running: detecting 9 circles"))
    }

    private fun onImage(imageReader: ImageReader) {
        val now = SystemClock.uptimeMillis()
        if (now - lastFrameAtMs < 16L) {
            imageReader.acquireLatestImage()?.close()
            return
        }
        lastFrameAtMs = now

        val image = imageReader.acquireLatestImage() ?: return
        try {
            val plane = image.planes[0]
            val width = image.width
            val height = image.height
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * width
            val bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
            bitmap.copyPixelsFromBuffer(plane.buffer)
            val cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height)
            bitmap.recycle()

            val active = detector.process(cropped, now)
            if (active != null) {
                val x = active.target.x * scaleX
                val y = active.target.y * scaleY
                if (TapAccessibilityService.instance?.tap(x, y) == true) {
                    detector.markTapped(active.target, now)
                }
            }
            cropped.recycle()
        } finally {
            image.close()
        }
    }

    private fun displayMetrics(): DisplayMetrics {
        val metrics = DisplayMetrics()
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        return metrics
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "Circle Reactor", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun startAsForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(42, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(42, notification)
        }
    }

    private fun notification(text: String): Notification {
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID) else Notification.Builder(this)
        return builder
            .setContentTitle("Circle Reactor")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "circle_reactor_capture"
        private const val ACTION_START = "com.pricelens.circlereactor.START"
        private const val ACTION_STOP = "com.pricelens.circlereactor.STOP"
        private const val EXTRA_RESULT_CODE = "resultCode"
        private const val EXTRA_RESULT_DATA = "resultData"

        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, CaptureService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
            }
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, CaptureService::class.java).apply { action = ACTION_STOP })
        }
    }
}
