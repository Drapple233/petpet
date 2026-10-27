package com.example.floatingpet

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.animation.ValueAnimator
import android.view.animation.AccelerateDecelerateInterpolator
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import kotlin.math.abs

class PetOverlayService : Service() {
    private lateinit var wm: WindowManager
    private var container: FrameLayout? = null
    private var head: ImageView? = null
    private var body: ImageView? = null
    private lateinit var params: WindowManager.LayoutParams
    private var breathAnimator: ValueAnimator? = null
    private var idleAnimator: ValueAnimator? = null
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var startX = 0
    private var startY = 0
    private var lastTap = 0L

    override fun onCreate() {
        super.onCreate()
        createNotification()
        showPet()
    }
    private fun createNotification() {
        val channelId = "floating_pet"
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(channelId, "FloatingPet 桌宠", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val stop = PendingIntent.getService(this, 9, Intent(this, PetOverlayService::class.java).setAction("STOP"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("FloatingPet 正在运行")
            .setContentText("点击通知中的停止按钮可关闭桌宠")
            .addAction(Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel, "关闭桌宠", stop).build())
            .setOngoing(true).build()
        startForeground(17, n)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { stopSelf(); return START_NOT_STICKY }
        if (container == null) showPet()
        return START_STICKY
    }
    private fun showPet() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val overlayType = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        params = WindowManager.LayoutParams(220, 260, overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START; x = 80; y = 260 }
        val frame = FrameLayout(this)
        body = ImageView(this).apply {
            setImageDrawable(android.graphics.drawable.Drawable.createFromStream(assets.open("mascot_body.png"), null))
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        head = ImageView(this).apply {
            setImageDrawable(android.graphics.drawable.Drawable.createFromStream(assets.open("mascot_head.png"), null))
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        frame.addView(body, FrameLayout.LayoutParams(220, 220, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
        frame.addView(head, FrameLayout.LayoutParams(180, 155, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = 8 })
        frame.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchDownX = event.rawX; touchDownY = event.rawY
                    startX = params.x; startY = params.y
                    head?.pivotX = (head?.width ?: 90) / 2f
                    head?.pivotY = (head?.height ?: 75).toFloat() * .8f
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - touchDownX; val dy = event.rawY - touchDownY
                    if (abs(dx) > 6 || abs(dy) > 6) {
                        params.x = startX + dx.toInt(); params.y = startY + dy.toInt()
                        try { wm.updateViewLayout(frame, params) } catch (_: Exception) {}
                    }
                    head?.rotation = (dx / 10f).coerceIn(-22f, 22f)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val now = System.currentTimeMillis()
                    if (abs(event.rawX - touchDownX) < 8 && abs(event.rawY - touchDownY) < 8) {
                        if (now - lastTap < 350) jump() else head?.animate()?.rotation(0f)?.setDuration(180)?.start()
                        lastTap = now
                    } else head?.animate()?.rotation(0f)?.setDuration(220)?.start()
                    true
                }
                else -> true
            }
        }
        container = frame
        wm.addView(frame, params)
        startIdleAnimations()
    }
    private fun startIdleAnimations() {
        breathAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1700; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { a ->
                val t = a.animatedValue as Float
                body?.translationY = -3f - 4f * t
                head?.translationY = 2f - 3f * t
            }; start()
        }
        idleAnimator = ValueAnimator.ofFloat(-3f, 3f).apply {
            duration = 2400; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { head?.rotation = it.animatedValue as Float }; start()
        }
    }
    private fun jump() {
        body?.animate()?.cancel()
        body?.animate()?.translationY(-45f)?.setDuration(150)?.withEndAction {
            body?.animate()?.translationY(0f)?.setDuration(220)?.start()
        }?.start()
    }
    override fun onDestroy() {
        breathAnimator?.cancel(); idleAnimator?.cancel()
        container?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        container = null
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
