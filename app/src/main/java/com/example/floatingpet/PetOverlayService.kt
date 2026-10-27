package com.example.floatingpet

import android.app.*
import android.content.*
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.animation.ValueAnimator
import android.view.animation.AccelerateDecelerateInterpolator
import kotlin.math.abs

class PetOverlayService : Service() {
    companion object { const val ACTION_SETTINGS = "com.example.floatingpet.SETTINGS"; const val ACTION_MEDIA = "com.example.floatingpet.MEDIA"; const val ACTION_TYPING = "com.example.floatingpet.TYPING" }
    private val prefs by lazy { getSharedPreferences("pet_settings", MODE_PRIVATE) }
    private lateinit var wm: WindowManager
    private var container: FrameLayout? = null
    private var head: ImageView? = null
    private var body: ImageView? = null
    private lateinit var params: WindowManager.LayoutParams
    private var breathAnimator: ValueAnimator? = null
    private var idleAnimator: ValueAnimator? = null
    private val handler = Handler(Looper.getMainLooper())
    private var touchDownX = 0f; private var touchDownY = 0f; private var startX = 0; private var startY = 0
    private var moved = false; private var longPressing = false; private var mediaPulse = false; private var typing = false
    private var pressRunnable: Runnable? = null; private var pressLoop: Runnable? = null
    private val settingsReceiver = object : BroadcastReceiver() { override fun onReceive(c: Context?, i: Intent?) { if (i?.action == ACTION_SETTINGS) applySettings() else if (i?.action == ACTION_MEDIA && prefs.getBoolean("musicEnabled", true)) { mediaPulse = true; beatPulse() } else if (i?.action == ACTION_TYPING && prefs.getBoolean("typingEnabled", false)) { typing = i.getBooleanExtra("visible", false); if (typing) body?.animate()?.translationY(-8f)?.setDuration(120)?.withEndAction { body?.animate()?.translationY(0f)?.setDuration(180)?.start() } } } }

    override fun onCreate() { super.onCreate(); createNotification(); showPet(); val f = IntentFilter().apply { addAction(ACTION_SETTINGS); addAction(ACTION_MEDIA); addAction(ACTION_TYPING) }; if (Build.VERSION.SDK_INT >= 33) registerReceiver(settingsReceiver, f, RECEIVER_NOT_EXPORTED) else @Suppress("DEPRECATION") registerReceiver(settingsReceiver, f) }
    private fun createNotification() {
        val id = "floating_pet"
        if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(id, "FloatingPet 桌宠", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 9, Intent(this, PetOverlayService::class.java).setAction("STOP"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, id).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("FloatingPet 正在运行").setContentText("可在 FloatingPet 主界面关闭桌宠").addAction(Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel, "关闭", stop).build()).setOngoing(true).build()
        startForeground(17, n)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int { if (intent?.action == "STOP") { stopSelf(); return START_NOT_STICKY }; if (container == null) showPet(); applySettings(); return START_STICKY }
    private fun showPet() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val size = prefs.getInt("size", 220)
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        params = WindowManager.LayoutParams(size, (size * 1.18f).toInt(), type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START; x = 60; y = 220 }
        val frame = FrameLayout(this)
        body = ImageView(this).apply { setImageDrawable(android.graphics.drawable.Drawable.createFromStream(assets.open("mascot_body.png"), null)); scaleType = ImageView.ScaleType.FIT_CENTER }
        head = ImageView(this).apply { setImageDrawable(android.graphics.drawable.Drawable.createFromStream(assets.open("mascot_head.png"), null)); scaleType = ImageView.ScaleType.FIT_CENTER }
        frame.addView(body, FrameLayout.LayoutParams(size, size, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
        frame.addView(head, FrameLayout.LayoutParams((size * .82f).toInt(), (size * .7f).toInt(), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = (size * .025f).toInt() })
        frame.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchDownX = e.rawX; touchDownY = e.rawY; startX = params.x; startY = params.y; moved = false; longPressing = false
                    head?.pivotX = (head?.width ?: size / 2) / 2f; head?.pivotY = (head?.height ?: size / 2) * .82f
                    pressRunnable = Runnable { longPressing = true; beginPressLoop() }; handler.postDelayed(pressRunnable!!, 420)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - touchDownX; val dy = e.rawY - touchDownY
                    if (abs(dx) > 7 || abs(dy) > 7) { moved = true; handler.removeCallbacks(pressRunnable ?: Runnable {}); params.x = startX + dx.toInt(); params.y = startY + dy.toInt(); try { wm.updateViewLayout(frame, params) } catch (_: Exception) {}; val target = (dx / 5f).coerceIn(-28f, 28f); head?.rotation = target; head?.translationX = (dx * .22f).coerceIn(-size * .18f, size * .18f); head?.translationY = (dy * .16f).coerceIn(-size * .12f, size * .12f) }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(pressRunnable ?: Runnable {}); handler.removeCallbacks(pressLoop ?: Runnable {}); pressLoop = null
                    if (!moved && !longPressing) pressHead()
                    longPressing = false
                    head?.animate()?.rotation(0f)?.translationX(0f)?.translationY(0f)?.setDuration(260)?.start()
                    true
                }
                else -> true
            }
        }
        container = frame; wm.addView(frame, params); startIdleAnimations()
    }
    private fun applySettings() {
        val frame = container ?: return
        val size = prefs.getInt("size", 220)
        val locked = prefs.getBoolean("sizeLocked", false)
        if (params.width != size || params.height != (size * 1.18f).toInt()) { params.width = size; params.height = (size * 1.18f).toInt(); try { wm.updateViewLayout(frame, params) } catch (_: Exception) {} }
        frame.findViewWithTag<android.view.View>("none")
        body?.layoutParams = FrameLayout.LayoutParams(size, size, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        head?.layoutParams = FrameLayout.LayoutParams((size * .82f).toInt(), (size * .7f).toInt(), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = (size * .025f).toInt() }
        body?.requestLayout(); head?.requestLayout()
        breathAnimator?.cancel(); idleAnimator?.cancel(); startIdleAnimations()
        frame.alpha = if (locked) 1f else 1f
    }
    private fun startIdleAnimations() {
        val breath = prefs.getInt("breathAmp", 7).toFloat(); val breathMs = (2600 - prefs.getInt("breathSpeed", 12) * 75).toLong().coerceAtLeast(550)
        breathAnimator = ValueAnimator.ofFloat(0f, 1f).apply { duration = breathMs; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE; interpolator = AccelerateDecelerateInterpolator(); addUpdateListener { a -> val t = a.animatedValue as Float; body?.translationY = -breath * t; body?.scaleY = 1f + t * .025f; if (!longPressing) head?.translationY = 2f - breath * .34f * t }; start() }
        val amp = prefs.getInt("headAmp", 12).toFloat(); val speed = prefs.getInt("headSpeed", 15); val duration = (3300 - speed * 85).toLong().coerceAtLeast(500)
        idleAnimator = ValueAnimator.ofFloat(-1f, 1f).apply { this.duration = duration; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE; interpolator = AccelerateDecelerateInterpolator(); addUpdateListener { if (!longPressing && !moved) head?.rotation = (it.animatedValue as Float) * amp }; start() }
    }
    private fun beginPressLoop() { pressLoop = object : Runnable { override fun run() { if (longPressing) { head?.translationY = (prefs.getInt("headAmp", 12) * 1.5f).coerceAtLeast(8).toFloat(); head?.rotation = 0f; handler.postDelayed(this, 45) } } }; handler.post(pressLoop!!) }
    private fun pressHead() { idleAnimator?.cancel(); head?.animate()?.cancel(); head?.animate()?.translationY((prefs.getInt("headAmp", 12) * 1.8f).coerceAtLeast(14).toFloat())?.setDuration(90)?.withEndAction { head?.animate()?.translationY(0f)?.setDuration(300)?.start(); startIdleAnimations() }?.start() }
    private fun beatPulse() { body?.animate()?.scaleX(1.035f)?.scaleY(1.04f)?.setDuration(90)?.withEndAction { body?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(170)?.start() }?.start(); handler.postDelayed({ mediaPulse = false }, 220) }
    override fun onDestroy() { try { unregisterReceiver(settingsReceiver) } catch (_: Exception) {}; handler.removeCallbacksAndMessages(null); breathAnimator?.cancel(); idleAnimator?.cancel(); container?.let { try { wm.removeView(it) } catch (_: Exception) {} }; container = null; super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
