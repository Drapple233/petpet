package com.example.floatingpet

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("pet_settings", MODE_PRIVATE) }
    private lateinit var status: TextView
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(31, 25, 45)
        window.navigationBarColor = Color.rgb(31, 25, 45)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(24))
            setBackgroundColor(Color.rgb(247, 243, 252))
        }
        val scroll = ScrollView(this).apply { addView(root) }
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(22), dp(20), dp(22))
            setBackgroundColor(Color.rgb(57, 43, 78))
        }
        hero.addView(TextView(this).apply { text = "✦  FloatingPet"; textSize = 27f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD })
        hero.addView(TextView(this).apply { text = "你的桌面小伙伴 · 动作与联动控制台"; textSize = 14f; setTextColor(Color.rgb(226, 214, 242)); setPadding(0, dp(7), 0, 0) })
        root.addView(hero, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) })
        status = TextView(this).apply { textSize = 14f; setTextColor(Color.rgb(67, 53, 86)); setPadding(dp(2), dp(4), 0, dp(10)) }
        root.addView(status)
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; weightSum = 2f }
        actions.addView(actionButton("启动桌宠") { startPet() }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(6) })
        actions.addView(actionButton("关闭桌宠") { stopService(Intent(this@MainActivity, PetOverlayService::class.java)); refreshStatus() }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(6) })
        root.addView(actions)
        section(root, "桌宠外观")
        addSlider(root, "桌宠大小", 120, 320, prefs.getInt("size", 220), " px") { v -> save("size", v); updatePet() }
        addSwitch(root, "锁定当前大小", "锁定后仍可拖动桌宠，但不会误触尺寸调整", prefs.getBoolean("sizeLocked", false)) { save("sizeLocked", it); updatePet() }
        section(root, "待机动作")
        addSlider(root, "呼吸幅度", 1, 16, prefs.getInt("breathAmp", 7), "") { v -> save("breathAmp", v); updatePet() }
        addSlider(root, "呼吸速度", 4, 24, prefs.getInt("breathSpeed", 12), "") { v -> save("breathSpeed", v); updatePet() }
        addSlider(root, "头部晃动幅度", 2, 24, prefs.getInt("headAmp", 12), "°") { v -> save("headAmp", v); updatePet() }
        addSlider(root, "头部晃动频率", 4, 30, prefs.getInt("headSpeed", 15), "") { v -> save("headSpeed", v); updatePet() }
        section(root, "互动联动")
        addSwitch(root, "音乐节奏联动", "根据系统媒体播放状态触发轻微节奏动作", prefs.getBoolean("musicEnabled", true)) { save("musicEnabled", it); if (it) openMediaSettingsIfNeeded(); updatePet() }
        val media = actionButton("开启音乐监听授权") { startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")) }
        root.addView(media, fullWidth())
        addSwitch(root, "键盘联动", "检测输入界面事件，不读取、不保存键入内容", prefs.getBoolean("typingEnabled", false)) { save("typingEnabled", it); if (it) startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); updatePet() }
        root.addView(TextView(this).apply {
            text = "互动方式：轻点头部会弹一下；长按会持续按头，松手后弹回；触摸/拖动时头部会跟随指尖。首次使用需授予悬浮窗权限。\n\n说明：音乐节拍效果受播放器和 Android 系统支持限制；键盘联动需在辅助功能中手动开启。"
            textSize = 12.5f; setTextColor(Color.rgb(102, 91, 117)); setPadding(dp(4), dp(16), dp(4), dp(6))
        })
        setContentView(scroll)
        refreshStatus()
    }
    private fun section(root: LinearLayout, title: String) {
        root.addView(TextView(this).apply { text = title; textSize = 18f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.rgb(57, 43, 78)); setPadding(dp(2), dp(20), 0, dp(8)) })
    }
    private fun actionButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 14f; setTextColor(Color.WHITE); setBackgroundColor(Color.rgb(107, 79, 143)); setOnClickListener { action() }
    }
    private fun fullWidth() = LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(6) }
    private fun addSwitch(root: LinearLayout, title: String, subtitle: String, checked: Boolean, changed: (Boolean) -> Unit) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(10), dp(10), dp(8)); setBackgroundColor(Color.WHITE) }
        val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        line.addView(TextView(this).apply { text = title; textSize = 15f; setTextColor(Color.rgb(45, 38, 55)) }, LinearLayout.LayoutParams(0, -2, 1f))
        val sw = Switch(this).apply { isChecked = checked; setOnCheckedChangeListener { _, b -> changed(b) } }
        line.addView(sw); box.addView(line)
        box.addView(TextView(this).apply { text = subtitle; textSize = 12f; setTextColor(Color.GRAY); setPadding(0, dp(3), 0, 0) })
        root.addView(box, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(7) })
    }
    private fun addSlider(root: LinearLayout, title: String, min: Int, max: Int, initial: Int, suffix: String, changed: (Int) -> Unit) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(8), dp(14), dp(6)); setBackgroundColor(Color.WHITE) }
        val label = TextView(this).apply { textSize = 14f; setTextColor(Color.rgb(55, 45, 68)) }
        val seek = SeekBar(this).apply { this.max = max - min; progress = (initial - min).coerceIn(0, max - min) }
        fun updateLabel(v: Int) { label.text = "$title  $v$suffix" }
        updateLabel(min + seek.progress)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { val v = min + p; updateLabel(v); if (fromUser) changed(v) }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        box.addView(label); box.addView(seek)
        root.addView(box, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
    }
    private fun save(k: String, v: Int) { prefs.edit().putInt(k, v).apply() }
    private fun save(k: String, v: Boolean) { prefs.edit().putBoolean(k, v).apply() }
    private fun startPet() {
        if (!Settings.canDrawOverlays(this)) { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))); Toast.makeText(this, "请先允许显示在其他应用上层", Toast.LENGTH_LONG).show(); return }
        val i = Intent(this, PetOverlayService::class.java).setAction("START")
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
        updatePet(); refreshStatus()
    }
    private fun updatePet() { sendBroadcast(Intent(PetOverlayService.ACTION_SETTINGS).setPackage(packageName)) }
    private fun openMediaSettingsIfNeeded() { /* permission is granted by the explicit button below */ }
    private fun refreshStatus() { status.text = if (Settings.canDrawOverlays(this)) "● 悬浮窗权限已开启 · 设置会自动应用" else "○ 还未开启悬浮窗权限" }
}
