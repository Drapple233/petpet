package com.example.floatingpet

import android.app.*
import android.content.*
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.animation.ValueAnimator
import android.view.animation.AccelerateDecelerateInterpolator
import kotlin.math.abs
import kotlin.random.Random

class PetOverlayService : Service() {
    companion object {
        const val ACTION_SETTINGS = "com.example.floatingpet.SETTINGS"
        const val ACTION_MEDIA = "com.example.floatingpet.MEDIA"
        const val ACTION_TYPING = "com.example.floatingpet.TYPING"
    }
    private val prefs by lazy { getSharedPreferences("pet_settings", MODE_PRIVATE) }
    private lateinit var wm: WindowManager
    private var container: FrameLayout? = null
    private var head: ImageView? = null
    private var body: ImageView? = null
    private var speech: TextView? = null
    private lateinit var params: WindowManager.LayoutParams
    private var bodyBreathAnimator: ValueAnimator? = null
    private var headBreathAnimator: ValueAnimator? = null
    private var idleAnimator: ValueAnimator? = null
    private val handler = Handler(Looper.getMainLooper())
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var startX = 0
    private var startY = 0
    private var moved = false
    private var longPressing = false
    private var moveMode = false
    private var typing = false
    private var pressRunnable: Runnable? = null
    private var pressLoop: Runnable? = null
    private var lastMediaTalk = 0L
    private var lastTypingTalk = 0L
    private var lastBeatTalk = 0L
    private var lastLine = ""

    private val lines = mapOf(
        "tap" to listOf("欸？叫我吗！", "看见你啦。", "怎么啦怎么啦？", "戳我做什么呀。", "我在这里哦。", "哼，才不是在等你。", "再点一下试试？", "今天也要陪着我哦。"),
        "pat" to listOf("摸头可以，别弄乱啦。", "……再摸一会儿。", "被你摸得有点舒服。", "嗯？是在哄我吗？", "头发要被你揉翘了！", "今天的摸头额度还没用完。", "只准摸一小会儿哦。", "好啦好啦，知道你喜欢我。"),
        "music" to listOf("来啦！放点好听的。", "这首歌不错嘛。", "音乐时间，启动！", "让我跟着节奏动一动。", "耳朵已经准备好啦。", "这段我喜欢。", "再大声一点点……开玩笑的。", "听歌的时候也别忘了我。"),
        "typing" to listOf("认真打字的样子不错嘛。", "键盘哒哒，我也动动。", "是在写给我的吗？", "别打错字啦。", "我陪你一起忙。", "手速好快！", "休息一下眼睛嘛。", "我有在旁边加油哦。"),
        "beat" to listOf("咚、咚！跟上啦。", "这个节拍正合适。", "我也踩到拍子啦！", "跟着音乐点点头。", "节奏感还不错吧？", "再来一拍！")
    )

    private val settingsReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            when (i?.action) {
                ACTION_SETTINGS -> applySettings()
                ACTION_MEDIA -> if (prefs.getBoolean("musicEnabled", true)) {
                    beatPulse()
                    val now = System.currentTimeMillis()
                    if (now - lastMediaTalk > 12000L) {
                        lastMediaTalk = now
                        speak("music")
                    }
                }
                ACTION_TYPING -> if (prefs.getBoolean("typingEnabled", false)) {
                    val visible = i.getBooleanExtra("visible", false)
                    if (visible && !typing) {
                        val now = System.currentTimeMillis()
                        if (now - lastTypingTalk > 18000L) { lastTypingTalk = now; speak("typing") }
                        typingPulse()
                    }
                    typing = visible
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotification()
        showPet()
        val f = IntentFilter().apply { addAction(ACTION_SETTINGS); addAction(ACTION_MEDIA); addAction(ACTION_TYPING) }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(settingsReceiver, f, RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(settingsReceiver, f)
    }

    private fun createNotification() {
        val id = "floating_pet"
        if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(id, "FloatingPet 桌宠", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 9, Intent(this, PetOverlayService::class.java).setAction("STOP"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, id).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("FloatingPet 正在运行").setContentText("可在 FloatingPet 主界面关闭桌宠").addAction(Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel, "关闭", stop).build()).setOngoing(true).build()
        startForeground(17, n)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { stopSelf(); return START_NOT_STICKY }
        if (container == null) showPet()
        applySettings()
        return START_STICKY
    }

    private fun showPet() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val size = prefs.getInt("size", 220)
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        params = WindowManager.LayoutParams(size, (size * 1.18f).toInt(), type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START; x = 60; y = 220 }
        val frame = FrameLayout(this)
        body = ImageView(this).apply {
            setImageDrawable(android.graphics.drawable.Drawable.createFromStream(assets.open("mascot_body.png"), null))
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        head = ImageView(this).apply {
            setImageDrawable(android.graphics.drawable.Drawable.createFromStream(assets.open("mascot_head.png"), null))
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        speech = TextView(this).apply {
            textSize = 12f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = GradientDrawable().apply { setColor(Color.rgb(67, 51, 88)); cornerRadius = dp(14).toFloat() }
            visibility = android.view.View.GONE
            maxLines = 2
        }
        frame.addView(body, FrameLayout.LayoutParams(size, size, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
        frame.addView(head, FrameLayout.LayoutParams((size * .82f).toInt(), (size * .7f).toInt(), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = (size * .025f).toInt() })
        frame.addView(speech, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(2) })
        frame.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchDownX = e.rawX; touchDownY = e.rawY; startX = params.x; startY = params.y
                    moved = false; longPressing = false
                    head?.pivotX = (head?.width ?: size / 2) / 2f
                    head?.pivotY = (head?.height ?: size / 2) * .82f
                    pressRunnable = Runnable {
                        longPressing = true
                        if (!moveMode) { beginPressLoop(); speak("pat") }
                    }
                    handler.postDelayed(pressRunnable!!, 420)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - touchDownX; val dy = e.rawY - touchDownY
                    if (abs(dx) > 7 || abs(dy) > 7) {
                        moved = true
                        handler.removeCallbacks(pressRunnable ?: Runnable {})
                        if (moveMode) {
                            params.x = startX + dx.toInt(); params.y = startY + dy.toInt()
                            try { wm.updateViewLayout(frame, params) } catch (_: Exception) {}
                        } else {
                            head?.rotation = (dx / 3.2f).coerceIn(-36f, 36f)
                            head?.translationX = (dx * .38f).coerceIn(-size * .24f, size * .24f)
                            head?.translationY = (dy * .28f).coerceIn(-size * .18f, size * .18f)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(pressRunnable ?: Runnable {})
                    handler.removeCallbacks(pressLoop ?: Runnable {})
                    pressLoop = null
                    if (moveMode) {
                        prefs.edit().putInt("petX", params.x).putInt("petY", params.y).apply()
                    } else if (!moved && !longPressing) {
                        speak("tap"); pressHead()
                    }
                    longPressing = false
                    head?.animate()?.rotation(0f)?.translationX(0f)?.translationY(0f)?.setDuration(220)?.start()
                    true
                }
                else -> true
            }
        }
        container = frame
        wm.addView(frame, params)
        startIdleAnimations()
    }

    private fun applySettings() {
        val frame = container ?: return
        val size = prefs.getInt("size", 220)
        moveMode = prefs.getBoolean("moveMode", false)
        if (params.width != size || params.height != (size * 1.18f).toInt()) {
            params.width = size; params.height = (size * 1.18f).toInt()
            try { wm.updateViewLayout(frame, params) } catch (_: Exception) {}
        }
        if (prefs.contains("petX")) params.x = prefs.getInt("petX", params.x)
        if (prefs.contains("petY")) params.y = prefs.getInt("petY", params.y)
        body?.layoutParams = FrameLayout.LayoutParams(size, size, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        head?.layoutParams = FrameLayout.LayoutParams((size * .82f).toInt(), (size * .7f).toInt(), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = (size * .025f).toInt() }
        speech?.layoutParams = FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(2) }
        body?.requestLayout(); head?.requestLayout()
        try { wm.updateViewLayout(frame, params) } catch (_: Exception) {}
        bodyBreathAnimator?.cancel(); headBreathAnimator?.cancel(); idleAnimator?.cancel()
        startIdleAnimations()
    }

    private fun startIdleAnimations() {
        val breath = prefs.getInt("breathAmp", 7).toFloat()
        val bodyMs = (3100 - prefs.getInt("breathSpeed", 12) * 80).toLong().coerceAtLeast(700)
        bodyBreathAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = bodyMs; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE; interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { a ->
                val t = a.animatedValue as Float
                body?.translationY = -breath * 1.35f * t
                body?.scaleY = 1f + .055f * t
                body?.scaleX = 1f + .018f * t
            }
            start()
        }
        val headMs = (2100 - prefs.getInt("headBreathSpeed", 17) * 55).toLong().coerceAtLeast(600)
        headBreathAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = headMs; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE; interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { a ->
                val t = a.animatedValue as Float
                if (!longPressing && !moved) {
                    head?.translationY = -breath * .58f * t
                    head?.scaleY = 1f + .022f * t
                }
            }
            start()
        }
        val amp = prefs.getInt("headAmp", 12).toFloat()
        val speed = prefs.getInt("headSpeed", 15)
        val duration = (3300 - speed * 85).toLong().coerceAtLeast(500)
        idleAnimator = ValueAnimator.ofFloat(-1f, 1f).apply {
            this.duration = duration; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE; interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { if (!longPressing && !moved) head?.rotation = (it.animatedValue as Float) * amp }
            start()
        }
    }

    private fun beginPressLoop() {
        pressLoop = object : Runnable {
            override fun run() {
                if (longPressing && !moveMode) {
                    head?.translationY = (prefs.getInt("headAmp", 12) * 1.65f).coerceAtLeast(10f)
                    head?.scaleY = .94f
                    handler.postDelayed(this, 55)
                }
            }
        }
        handler.post(pressLoop!!)
    }

    private fun pressHead() {
        idleAnimator?.cancel()
        head?.animate()?.cancel()
        body?.animate()?.cancel()
        head?.animate()?.translationY(dp(22).toFloat())?.rotation(0f)?.scaleY(.90f)?.setDuration(100)?.withEndAction {
            head?.animate()?.translationY(0f)?.scaleY(1.04f)?.setDuration(160)?.withEndAction {
                head?.animate()?.scaleY(1f)?.setDuration(130)?.start()
            }?.start()
            body?.animate()?.translationY(-dp(7).toFloat())?.setDuration(110)?.withEndAction {
                body?.animate()?.translationY(0f)?.setDuration(200)?.start()
            }?.start()
            handler.postDelayed({ startIdleAnimations() }, 420)
        }?.start()
    }

    private fun beatPulse() {
        head?.animate()?.cancel()
        head?.animate()?.translationY(dp(9).toFloat())?.rotation(5f)?.setDuration(100)?.withEndAction {
            head?.animate()?.translationY(0f)?.rotation(-3f)?.setDuration(110)?.withEndAction {
                head?.animate()?.rotation(0f)?.setDuration(100)?.start()
            }?.start()
        }?.start()
        body?.animate()?.scaleX(1.045f)?.scaleY(1.055f)?.setDuration(100)?.withEndAction {
            body?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(180)?.start()
        }?.start()
        val now = System.currentTimeMillis()
        if (now - lastBeatTalk > 6500L) { lastBeatTalk = now; speak("beat") }
    }

    private fun typingPulse() {
        head?.animate()?.translationY(dp(7).toFloat())?.setDuration(90)?.withEndAction {
            head?.animate()?.translationY(0f)?.setDuration(140)?.start()
        }?.start()
        body?.animate()?.translationY(-dp(4).toFloat())?.setDuration(100)?.withEndAction {
            body?.animate()?.translationY(0f)?.setDuration(160)?.start()
        }?.start()
    }

    private fun speak(kind: String) {
        val pool = lines[kind] ?: return
        var line = pool[Random.nextInt(pool.size)]
        if (pool.size > 1 && line == lastLine) line = pool[(pool.indexOf(line) + 1 + Random.nextInt(pool.size - 1)) % pool.size]
        lastLine = line
        speech?.let { bubble ->
            bubble.text = line
            bubble.alpha = 0f
            bubble.scaleX = .92f; bubble.scaleY = .92f
            bubble.visibility = android.view.View.VISIBLE
            bubble.animate().cancel()
            bubble.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(140).start()
            handler.postDelayed({
                bubble.animate().alpha(0f).setDuration(250).withEndAction { bubble.visibility = android.view.View.GONE }.start()
            }, 2300)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        try { unregisterReceiver(settingsReceiver) } catch (_: Exception) {}
        handler.removeCallbacksAndMessages(null)
        bodyBreathAnimator?.cancel(); headBreathAnimator?.cancel(); idleAnimator?.cancel()
        container?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        container = null
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}