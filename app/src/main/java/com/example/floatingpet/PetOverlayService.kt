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
        const val ACTION_MEDIA_PAUSED = "com.example.floatingpet.MEDIA_PAUSED"
        const val ACTION_AUDIO_BEAT = "com.example.floatingpet.AUDIO_BEAT"
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
    private var chargeCancelledByMove = false
    private var interactionAnimating = false
    private var moveMode = false
    private var typing = false
    private var pressRunnable: Runnable? = null
    private var pressLoop: Runnable? = null
    private var lastMediaTalk = 0L
    private var lastTypingTalk = 0L
    private var lastBeatTalk = 0L
    private var lastLine = ""
    private var mediaPlaying = false
    private var mediaBeatRunnable: Runnable? = null
    private var idleRandomRunnable: Runnable? = null
    private var pressStartedAt = 0L
    private var lastKeyPulse = 0L

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
                    val wasPlaying = mediaPlaying
                    mediaPlaying = true
                    if (!wasPlaying) {
                        val now = System.currentTimeMillis()
                        if (now - lastMediaTalk > 12000L) { lastMediaTalk = now; speak("music") }
                        startEstimatedBeatLoop()
                    }
                }
                ACTION_MEDIA_PAUSED -> {
                    mediaPlaying = false
                    handler.removeCallbacks(mediaBeatRunnable ?: Runnable {})
                    mediaBeatRunnable = null
                }
                ACTION_AUDIO_BEAT -> if (prefs.getBoolean("musicEnabled", true)) {
                    mediaPlaying = true
                    beatPulse(true)
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
                "com.example.floatingpet.KEY_PULSE" -> {
                    if (prefs.getBoolean("typingEnabled", false)) {
                        val now = System.currentTimeMillis()
                        if (now - lastKeyPulse > 38L) { lastKeyPulse = now; typingPulse() }
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotification()
        showPet()
        val f = IntentFilter().apply { addAction(ACTION_SETTINGS); addAction(ACTION_MEDIA); addAction(ACTION_MEDIA_PAUSED); addAction(ACTION_AUDIO_BEAT); addAction(ACTION_TYPING); addAction("com.example.floatingpet.KEY_PULSE") }
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
        params = WindowManager.LayoutParams((size * 1.30f).toInt(), (size * 1.62f).toInt(), type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START; x = 40; y = 100 }
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
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setTextColor(Color.rgb(92, 83, 66))
            background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(20).toFloat(); setStroke(dp(2), Color.rgb(255, 190, 202)) }
            visibility = android.view.View.GONE
            maxLines = 2
        }
        frame.addView(body, FrameLayout.LayoutParams(size, size, Gravity.CENTER_HORIZONTAL or Gravity.TOP).apply { topMargin = (size * .52f).toInt() })
        frame.addView(head, FrameLayout.LayoutParams((size * .82f).toInt(), (size * .7f).toInt(), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = (size * .25f).toInt() })
        frame.addView(speech, FrameLayout.LayoutParams((size * 1.22f).toInt().coerceAtMost((size * 1.28f).toInt()), -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(2) })
        frame.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchDownX = e.rawX; touchDownY = e.rawY; startX = params.x; startY = params.y
                    moved = false; longPressing = false; chargeCancelledByMove = false; interactionAnimating = true
                    // Rotate around the lower-middle of the head so it stays visually attached to the torso.
                    head?.pivotX = (head?.width ?: size) / 2f
                    head?.pivotY = (head?.height ?: size) * .82f
                    pressStartedAt = System.currentTimeMillis()
                    pressRunnable = Runnable {
                        longPressing = true
                        if (!moveMode) { beginPressLoop(); speak("pat"); spawnEffect("♡", .58f) }
                    }
                    handler.postDelayed(pressRunnable!!, 420)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - touchDownX; val dy = e.rawY - touchDownY
                    if (abs(dx) > 7 || abs(dy) > 7) {
                        moved = true
                        handler.removeCallbacks(pressRunnable ?: Runnable {})
                        if (longPressing && !moveMode) {
                            // A stray finger movement cancels charging instead of mixing drag/pat animations.
                            longPressing = false
                            chargeCancelledByMove = true
                            handler.removeCallbacks(pressLoop ?: Runnable {})
                            pressLoop = null
                            head?.animate()?.cancel()
                            body?.animate()?.cancel()
                            head?.animate()?.translationY(0f)?.translationX(0f)?.rotation(0f)?.scaleX(1f)?.scaleY(1f)?.setDuration(120)?.start()
                            body?.animate()?.translationY(0f)?.scaleX(1f)?.scaleY(1f)?.setDuration(120)?.start()
                        } else if (moveMode) {
                            params.x = startX + dx.toInt(); params.y = startY + dy.toInt()
                            try { wm.updateViewLayout(frame, params) } catch (_: Exception) {}
                        } else {
                            // Restore the original head-follow gesture and use asymmetric,
                            // subtle torso compensation only: more on downward petting, very little upward.
                            val headX = (dx * .02f).coerceIn(-dp(3).toFloat(), dp(3).toFloat())
                            val headY = (dy * .015f).coerceIn(-dp(2).toFloat(), dp(2).toFloat())
                            head?.rotation = (dx / 18f).coerceIn(-7f, 7f)
                            head?.translationX = headX
                            head?.translationY = headY
                            body?.translationY = if (headY > 0f) headY * .35f else headY * .06f
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(pressRunnable ?: Runnable {})
                    handler.removeCallbacks(pressLoop ?: Runnable {})
                    pressLoop = null
                    val wasLongPress = longPressing
                    if (moveMode) {
                        prefs.edit().putInt("petX", params.x).putInt("petY", params.y).apply()
                        interactionAnimating = false
                    } else if (chargeCancelledByMove) {
                        head?.animate()?.translationY(0f)?.translationX(0f)?.rotation(0f)?.scaleX(1f)?.scaleY(1f)?.setDuration(140)?.start()
                        body?.animate()?.translationY(0f)?.scaleX(1f)?.scaleY(1f)?.setDuration(140)?.start()
                        interactionAnimating = false
                        startIdleAnimations()
                    } else if (wasLongPress) {
                        releasePat()
                    } else if (!moved) {
                        speak("tap"); pressHead(); spawnEffect(listOf("♡", "✦", "✧", "♥").random(), .72f)
                    } else {
                        head?.animate()?.translationY(0f)?.translationX(0f)?.rotation(0f)?.setDuration(140)?.start()
                        body?.animate()?.translationY(0f)?.translationX(0f)?.setDuration(140)?.start()
                        interactionAnimating = false
                    }
                    longPressing = false
                    head?.animate()?.rotation(0f)?.translationX(0f)?.setDuration(180)?.start()
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
        if (params.width != (size * 1.30f).toInt() || params.height != (size * 1.62f).toInt()) {
            params.width = (size * 1.30f).toInt(); params.height = (size * 1.62f).toInt()
            try { wm.updateViewLayout(frame, params) } catch (_: Exception) {}
        }
        if (prefs.contains("petX")) params.x = prefs.getInt("petX", params.x)
        if (prefs.contains("petY")) params.y = prefs.getInt("petY", params.y)
        body?.layoutParams = FrameLayout.LayoutParams(size, size, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = (size * .52f).toInt() }
        head?.layoutParams = FrameLayout.LayoutParams((size * .82f).toInt(), (size * .7f).toInt(), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = (size * .25f).toInt() }
        speech?.layoutParams = FrameLayout.LayoutParams((size * 1.22f).toInt(), -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(2) }
        body?.requestLayout(); head?.requestLayout()
        try { wm.updateViewLayout(frame, params) } catch (_: Exception) {}
        bodyBreathAnimator?.cancel(); headBreathAnimator?.cancel(); idleAnimator?.cancel()
        startIdleAnimations()
    }

    private fun startIdleAnimations() {
        val breath = prefs.getInt("breathAmpHalfDp", 6).coerceIn(1, 8) / 2f
        val bodyMs = (3300 - prefs.getInt("breathSpeed", 14) * 45).toLong().coerceIn(2400L, 3000L)
        bodyBreathAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = bodyMs; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE; interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { a ->
                val t = a.animatedValue as Float
                if (!interactionAnimating && !longPressing && !moved) {
                    body?.translationY = -(breath.coerceAtMost(3f) * .18f * resources.displayMetrics.density * t)
                    body?.scaleY = 1f + .003f * t
                    body?.scaleX = 1f + .002f * t
                }
            }
            start()
        }
        val headBreath = prefs.getInt("headBreathAmpHalfDp", 4).coerceIn(1, 8) / 2f
        val headMs = (2600 - prefs.getInt("headBreathSpeed", 20) * 45).toLong().coerceIn(1500L, 2300L)
        headBreathAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = headMs; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE; interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { a ->
                val t = a.animatedValue as Float
                if (!interactionAnimating && !longPressing && !moved) {
                    head?.translationY = -(headBreath * .65f * resources.displayMetrics.density * t)
                    head?.scaleY = 1f + .003f * t
                }
            }
            start()
        }
        // Natural idle motion is vertical breathing only; no side-to-side head wobble.
        idleAnimator = null
    }

    private fun beginPressLoop() {
        pressLoop = object : Runnable {
            override fun run() {
                if (longPressing && !moveMode) {
                    val elapsed = (System.currentTimeMillis() - pressStartedAt).coerceAtLeast(0L)
                    val charge = (elapsed / 1100f).coerceIn(0f, 1f)
                    // Fast, tight trembling builds as the hold continues; keep vertical travel tiny
                    // so the head stays visually connected to the body.
                    val tremble = kotlin.math.sin(elapsed / 18.0).toFloat()
                    val density = resources.displayMetrics.density
                    val patHeadDown = prefs.getInt("patHeadDownHalfDp", 4).coerceIn(0, 12) / 2f
                    val bodyDown = 0.4f + charge * 1.2f + tremble * (0.06f + charge * 0.1f)
                    val extraHeadDown = prefs.getInt("holdHeadDownExtraHalfDp", 3).coerceIn(0, 8) / 2f
                    head?.translationY = (bodyDown + extraHeadDown * charge + patHeadDown * charge + tremble * (0.02f + charge * 0.04f)) * density
                    head?.rotation = tremble * (0.25f + charge * 0.45f)
                    head?.scaleY = 1f - charge * .004f
                    head?.scaleX = 1f + charge * .002f
                    body?.translationY = bodyDown * density
                    handler.postDelayed(this, 16)
                }
            }
        }
        handler.post(pressLoop!!)
    }

    private fun pressHead() {
        interactionAnimating = true
        idleAnimator?.cancel()
        bodyBreathAnimator?.cancel(); headBreathAnimator?.cancel()
        head?.animate()?.cancel()
        body?.animate()?.cancel()
        // Move head and body together through the final bounce to avoid a visible gap.
        val headJump = -(prefs.getInt("clickHeadJumpHalfDp", 12) / 2f * resources.displayMetrics.density).toInt()
        val bodyJump = -(prefs.getInt("clickBodyJumpHalfDp", 10) / 2f * resources.displayMetrics.density).toInt()
        head?.animate()?.translationY(headJump.toFloat())?.rotation(0f)?.scaleY(.975f)?.setDuration(110)?.withEndAction {
            head?.animate()?.translationY(0f)?.scaleY(1f)?.setDuration(230)?.start()
        }?.start()
        body?.animate()?.translationY(bodyJump.toFloat())?.setDuration(110)?.withEndAction {
            body?.animate()?.translationY(0f)?.setDuration(230)?.start()
        }?.start()
        handler.postDelayed({ interactionAnimating = false; startIdleAnimations() }, 330)
    }

    private fun beatPulse(realBeat: Boolean = false) {
        head?.animate()?.cancel()
        val drop = if (realBeat) dp(3).toFloat() else dp(2).toFloat()
        head?.animate()?.translationY(drop)?.rotation(2.2f)?.setDuration(85)?.withEndAction {
            head?.animate()?.translationY(-dp(2).toFloat())?.rotation(-1.4f)?.setDuration(95)?.withEndAction {
                head?.animate()?.translationY(0f)?.rotation(0f)?.setDuration(110)?.start()
            }?.start()
        }?.start()
        body?.animate()?.scaleX(1.003f)?.scaleY(1.004f)?.setDuration(120)?.withEndAction {
            body?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(180)?.start()
        }?.start()
        val now = System.currentTimeMillis()
        if (now - lastBeatTalk > 6500L) { lastBeatTalk = now; speak("beat") }
    }

    private fun startEstimatedBeatLoop() {
        handler.removeCallbacks(mediaBeatRunnable ?: Runnable {})
        mediaBeatRunnable = object : Runnable {
            override fun run() {
                if (!mediaPlaying || !prefs.getBoolean("musicEnabled", true) || container == null) return
                beatPulse()
                if (Random.nextInt(4) == 0) spawnEffect(listOf("♪", "♫", "✦").random(), .48f)
                handler.postDelayed(this, Random.nextLong(470L, 760L))
            }
        }
        handler.post(mediaBeatRunnable!!)
    }

    private fun spawnEffect(symbol: String, horizontalPosition: Float) {
        val frame = container ?: return
        val size = dp(if (symbol.length > 1) 25 else 29)
        val effect = ImageView(this).apply {
            setImageResource(when (symbol) {
                "♡", "♥" -> R.drawable.pet_effect_heart
                "♪", "♫" -> R.drawable.pet_effect_note
                "✿", "·" -> R.drawable.pet_effect_flower
                else -> R.drawable.pet_effect_star
            })
            scaleType = ImageView.ScaleType.FIT_CENTER
            alpha = 0f
        }
        val safeWidth = frame.width.coerceAtLeast(dp(120))
        val safeHeight = frame.height.coerceAtLeast(dp(180))
        val x = (safeWidth * horizontalPosition).toInt().coerceIn(dp(4), (safeWidth - size - dp(4)).coerceAtLeast(dp(4)))
        val y = (safeHeight * Random.nextDouble(.34, .68)).toInt().coerceIn(dp(55), (safeHeight - size - dp(12)).coerceAtLeast(dp(60)))
        frame.addView(effect, FrameLayout.LayoutParams(size, size).apply { leftMargin = x; topMargin = y })
        effect.animate().alpha(1f).translationY(-dp(38).toFloat()).scaleX(1.18f).scaleY(1.18f).setDuration(180).withEndAction {
            effect.animate().alpha(0f).setDuration(360).withEndAction { frame.removeView(effect) }.start()
        }.start()
    }

    private fun typingPulse() {
        head?.animate()?.cancel()
        head?.animate()?.translationY(dp(2).toFloat())?.rotation(.4f)?.setDuration(70)?.withEndAction {
            head?.animate()?.translationY(0f)?.rotation(0f)?.setDuration(130)?.start()
        }?.start()
        body?.animate()?.translationY(-dp(1).toFloat())?.setDuration(90)?.withEndAction {
            body?.animate()?.translationY(0f)?.setDuration(150)?.start()
        }?.start()
    }

    private fun releasePat() {
        interactionAnimating = true
        handler.removeCallbacks(pressLoop ?: Runnable {})
        bodyBreathAnimator?.cancel(); headBreathAnimator?.cancel()
        val charge = ((System.currentTimeMillis() - pressStartedAt) / 1100f).coerceIn(0f, 1f)
        head?.animate()?.cancel()
        body?.animate()?.cancel()
        // A restrained rebound: head and body travel in the same direction together.
        val headBase = prefs.getInt("holdHeadJumpHalfDp", 20) / 2f
        val bodyBase = prefs.getInt("holdBodyJumpHalfDp", 18) / 2f
        val rebound = (headBase + charge * 2.0f).coerceAtMost(18f) * resources.displayMetrics.density
        val bodyRebound = (bodyBase + charge * 2.0f).coerceAtMost(18f) * resources.displayMetrics.density
        head?.animate()?.translationY(-rebound)?.rotation(0f)?.scaleY(1.02f)?.scaleX(.99f)?.setDuration(145)?.withEndAction {
            head?.animate()?.translationY(0f)?.scaleX(1f)?.scaleY(1f)?.setDuration(250)?.start()
        }?.start()
        body?.animate()?.translationY(-bodyRebound)?.setDuration(145)?.withEndAction {
            body?.animate()?.translationY(0f)?.setDuration(250)?.start()
        }?.start()
        spawnEffect(if (charge > .55f) "♥" else "✦", .64f)
        handler.postDelayed({ interactionAnimating = false; startIdleAnimations() }, 395)
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
        mediaPlaying = false
        mediaBeatRunnable = null
        idleRandomRunnable = null
        bodyBreathAnimator?.cancel(); headBreathAnimator?.cancel(); idleAnimator?.cancel()
        container?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        container = null
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}