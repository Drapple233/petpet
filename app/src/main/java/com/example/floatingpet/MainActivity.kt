package com.example.floatingpet

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.content.res.ColorStateList
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.*
import android.graphics.drawable.GradientDrawable

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("pet_settings", MODE_PRIVATE) }
    private lateinit var status: TextView
    private val ink = Color.rgb(42, 45, 49)
    private val mint = Color.rgb(190, 194, 198)
    private val blue = Color.rgb(175, 180, 185)
    private val pink = Color.rgb(205, 208, 212)
    private val butter = Color.rgb(220, 222, 225)
    private val cream = Color.rgb(245, 246, 247)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(239, 240, 242)
        window.navigationBarColor = Color.rgb(239, 240, 242)
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(28))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.rgb(242, 243, 245), Color.rgb(229, 231, 234), Color.rgb(247, 247, 248)))
        }
        val scroll = ScrollView(this).apply { clipToPadding = false; addView(root) }
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(22))
            background = rounded(Color.WHITE, pink, 1, 28)
            elevation = dp(5).toFloat()
        }
        hero.addView(TextView(this).apply { text = "FloatingPet"; textSize = 29f; setTextColor(Color.rgb(38, 40, 43)); typeface = Typeface.create("sans-serif", Typeface.NORMAL); letterSpacing = 0.015f })
        hero.addView(TextView(this).apply { text = ""; textSize = 14f; setTextColor(ink); setPadding(0, 0, 0, 0) })
        
        root.addView(hero, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })

        status = TextView(this).apply { textSize = 13f; setTextColor(ink); setPadding(dp(16), dp(13), dp(16), dp(13)); background = rounded(Color.WHITE, mint, 1, 22); elevation = dp(2).toFloat() }
        root.addView(status, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; weightSum = 2f }
        actions.addView(button("开始陪伴  ♡", mint) { startPet() }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(5) })
        actions.addView(button("收起桌宠", pink) { stopService(Intent(this@MainActivity, PetOverlayService::class.java)); refreshStatus() }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(5) })
        root.addView(actions)

        section(root, "✧  桌宠外观与位置", blue)
        addSlider(root, "桌宠大小", 120, 500, prefs.getInt("size", 220).coerceAtMost(500), " px", blue) { save("size", it); updatePet() }
        addSwitch(root, "位置调整模式", "开启后拖动桌宠移动位置；关闭后触摸用于互动", prefs.getBoolean("moveMode", false), mint) { save("moveMode", it); updatePet() }
        addSwitch(root, "锁定当前大小", "避免误操作调整尺寸", prefs.getBoolean("sizeLocked", false), pink) { save("sizeLocked", it); updatePet() }

        section(root, "♡  互动与声音", pink)
        addSwitch(root, "音乐联动", "自动跟随播放状态；未开启音频分析时使用柔和估算节奏", prefs.getBoolean("musicEnabled", true), blue) { save("musicEnabled", it); updatePet(); if (it) openMediaSettings() }
        root.addView(button("开启系统音频节拍捕获  ♫", blue) { requestSystemAudioCapture() }, fullWidth())
        root.addView(button("开启麦克风节拍检测  ♡", mint) { requestMicPermissionAndStart() }, fullWidth())
        root.addView(button("停止音频节拍检测", pink) { stopService(Intent(this, AudioBeatService::class.java)) }, fullWidth())
        addSwitch(root, "逐键打字联动", "辅助功能开启后，尝试每次按键触发一次轻点；不读取或保存键入内容", prefs.getBoolean("typingEnabled", false), butter) {
            save("typingEnabled", it)
            if (it) startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            updatePet()
        }
        root.addView(button("开启音乐播放监听授权", butter) { openMediaSettings() }, fullWidth())

        section(root, "✿  温柔的小提示", butter)
        root.addView(TextView(this).apply {
            text = "系统音频捕获需要 Android 的系统确认框，且音乐 App 必须允许被捕获；若捕获不到声音，可改用麦克风模式。麦克风模式只在本机分析音量变化，不保存录音。逐键反馈受输入法和系统版本限制。"
            textSize = 12.5f; setTextColor(ink); setPadding(dp(16), dp(16), dp(16), dp(16)); background = rounded(Color.WHITE, butter, 1, 22); elevation = dp(2).toFloat()
        })
        root.addView(TextView(this).apply { text = "made with little stars, clouds and love"; gravity = Gravity.CENTER; textSize = 11.5f; setTextColor(Color.rgb(135, 138, 143)); setPadding(0, dp(18), 0, dp(4)) })
        setContentView(scroll)
        refreshStatus()
    }

    private fun rounded(fill: Int, stroke: Int, strokeDp: Int, radius: Int) = GradientDrawable().apply {
        // Translucent white surfaces over a soft gray background create a restrained glass-card effect.
        setColor(Color.argb(218, 255, 255, 255))
        cornerRadius = dp(radius).toFloat()
        setStroke(dp(strokeDp.coerceAtMost(1)), Color.argb(210, 35, 37, 40))
    }
    private fun button(label: String, color: Int, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 13.5f
        setTextColor(ink)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        background = rounded(Color.WHITE, color, 1, 22)
        elevation = dp(2).toFloat()
        setPadding(dp(8), 0, dp(8), 0)
        setOnClickListener { action() }
    }
    private fun fullWidth() = LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(7) }
    private fun section(root: LinearLayout, title: String, color: Int) {
        root.addView(TextView(this).apply {
            text = title.replace("✧", "◇").replace("✿", "○")
            textSize = 18f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(Color.rgb(48, 51, 55))
            setPadding(dp(4), dp(23), 0, dp(10))
            letterSpacing = 0.01f
        })
        root.addView(LinearLayout(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.rgb(105, 109, 114))
                cornerRadius = dp(2).toFloat()
            }
        }, LinearLayout.LayoutParams(dp(34), dp(2)).apply { bottomMargin = dp(12); marginStart = dp(4) })
    }
    private fun addSwitch(root: LinearLayout, title: String, subtitle: String, checked: Boolean, accent: Int, changed: (Boolean) -> Unit) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(14), dp(12))
            background = rounded(Color.WHITE, accent, 1, 22)
            elevation = dp(2).toFloat()
        }
        val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        line.addView(TextView(this).apply {
            text = title
            textSize = 15f
            setTextColor(ink)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        line.addView(Switch(this).apply {
            isChecked = checked
            thumbTintList = ColorStateList.valueOf(if (checked) Color.rgb(65, 68, 73) else Color.rgb(238, 239, 241))
            trackTintList = ColorStateList.valueOf(if (checked) Color.rgb(175, 178, 183) else Color.rgb(205, 208, 212))
            setOnCheckedChangeListener { _, v ->
                thumbTintList = ColorStateList.valueOf(if (v) Color.rgb(65, 68, 73) else Color.rgb(238, 239, 241))
                trackTintList = ColorStateList.valueOf(if (v) Color.rgb(175, 178, 183) else Color.rgb(205, 208, 212))
                changed(v)
            }
        })
        box.addView(line)
        box.addView(TextView(this).apply {
            text = subtitle
            textSize = 11.5f
            setTextColor(Color.rgb(125, 129, 134))
            setPadding(0, dp(4), 0, 0)
        })
        root.addView(box, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })
    }
    private fun addHalfDpSlider(root: LinearLayout, title: String, initialHalfDp: Int, accent: Int, changed: (Int) -> Unit) {
        val minHalfDp = when {
            title.startsWith("点击：") -> 4
            title.startsWith("长按松手：") -> 4
            else -> 0
        }
        val maxHalfDp = when {
            title.startsWith("点击：") -> 24
            title.startsWith("长按松手：") -> 28
            title == "摸头：头部下压幅度" -> 12
            else -> 8
        }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(10)); background = rounded(Color.WHITE, accent, 1, 22); elevation = dp(2).toFloat() }
        val label = TextView(this).apply { textSize = 13.5f; setTextColor(ink) }
        val seek = SeekBar(this).apply { max = maxHalfDp - minHalfDp; progress = (initialHalfDp - minHalfDp).coerceIn(0, maxHalfDp - minHalfDp); progressTintList = ColorStateList.valueOf(Color.rgb(75, 79, 84)); thumbTintList = ColorStateList.valueOf(Color.rgb(55, 59, 64)); progressBackgroundTintList = ColorStateList.valueOf(Color.rgb(211, 214, 218)) }
        fun update(v: Int) { label.text = "$title   ${"%.1f".format(v / 2f)} dp" }
        update(minHalfDp + seek.progress)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { val v = minHalfDp + p; update(v); if (fromUser) changed(v) }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        box.addView(label)
        box.addView(seek)
        root.addView(box, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(7) })
    }
    private fun addSlider(root: LinearLayout, title: String, min: Int, max: Int, initial: Int, suffix: String, accent: Int, changed: (Int) -> Unit) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(8), dp(14), dp(6)); background = rounded(Color.WHITE, accent, 1, 17) }
        val label = TextView(this).apply { textSize = 13.5f; setTextColor(ink) }
        val seek = SeekBar(this).apply { this.max = max - min; progress = (initial - min).coerceIn(0, max - min); progressTintList = ColorStateList.valueOf(Color.rgb(75, 79, 84)); thumbTintList = ColorStateList.valueOf(Color.rgb(55, 59, 64)); progressBackgroundTintList = ColorStateList.valueOf(Color.rgb(211, 214, 218)) }
        fun update(v: Int) { label.text = "$title   $v$suffix" }
        update(min + seek.progress)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { val v = min + p; update(v); if (fromUser) changed(v) }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        box.addView(label); box.addView(seek)
        root.addView(box, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
    }
    private fun save(k: String, v: Int) { prefs.edit().putInt(k, v).apply() }
    private fun save(k: String, v: Boolean) { prefs.edit().putBoolean(k, v).apply() }
    private fun startPet() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            Toast.makeText(this, "请先允许显示在其他应用上层", Toast.LENGTH_LONG).show(); return
        }
        val i = Intent(this, PetOverlayService::class.java).setAction("START")
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
        updatePet(); refreshStatus()
    }
    private fun updatePet() { sendBroadcast(Intent(PetOverlayService.ACTION_SETTINGS).setPackage(packageName)) }
    private fun openMediaSettings() { startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")) }
    private fun requestMicPermissionAndStart() {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 2201)
        } else startAudioService(null)
    }
    private fun requestSystemAudioCapture() {
        if (Build.VERSION.SDK_INT < 29) {
            Toast.makeText(this, "系统音频捕获需要 Android 10 或更高版本，请使用麦克风模式", Toast.LENGTH_LONG).show(); return
        }
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(manager.createScreenCaptureIntent(), 2202)
    }
    @Deprecated("Deprecated in Android API; kept for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 2202 && resultCode == RESULT_OK && data != null) startAudioService(data, resultCode)
        else if (requestCode == 2202) Toast.makeText(this, "未授权系统音频捕获，可尝试麦克风模式", Toast.LENGTH_LONG).show()
    }
    private fun startAudioService(data: Intent?, resultCode: Int = 0) {
        val i = Intent(this, AudioBeatService::class.java)
        if (data != null) { i.putExtra(AudioBeatService.EXTRA_RESULT_CODE, resultCode); i.putExtra(AudioBeatService.EXTRA_RESULT_DATA, data) }
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
        Toast.makeText(this, if (data == null) "麦克风节拍检测已启动" else "系统音频节拍检测已启动", Toast.LENGTH_SHORT).show()
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 2201 && grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) startAudioService(null)
        else if (requestCode == 2201) Toast.makeText(this, "需要麦克风权限才能分析节拍", Toast.LENGTH_LONG).show()
    }
    private fun refreshStatus() {
        status.text = if (Settings.canDrawOverlays(this)) "☁  悬浮窗已就绪  ·  马卡龙模式已启用  ♡" else "♡  还差一步：请允许桌宠显示在其他应用上层"
    }
}