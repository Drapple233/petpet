package com.example.floatingpet

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 48, 36, 36)
        }
        root.addView(TextView(this).apply {
            text = "FloatingPet · 手机悬浮桌宠"
            textSize = 25f
        })
        root.addView(TextView(this).apply {
            text = "\n默认角色：黑衣红瞳猫耳小人\n\n基础动画：呼吸、待机摇头、触摸歪头、点击跳跃。\n音乐和键盘互动需要按需授权。"
            textSize = 16f
        })
        root.addView(Button(this).apply {
            text = "① 授予悬浮窗权限"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                } else Toast.makeText(this@MainActivity, "悬浮窗权限已开启", Toast.LENGTH_SHORT).show()
            }
        })
        root.addView(Button(this).apply {
            text = "② 开启桌宠"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    Toast.makeText(this@MainActivity, "请先开启悬浮窗权限", Toast.LENGTH_LONG).show()
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                } else {
                    val i = Intent(this@MainActivity, PetOverlayService::class.java)
                    if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
                    Toast.makeText(this@MainActivity, "桌宠已启动", Toast.LENGTH_SHORT).show()
                }
            }
        })
        root.addView(Button(this).apply {
            text = "③ 音乐识别授权说明"
            setOnClickListener {
                startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
            }
        })
        root.addView(Button(this).apply {
            text = "④ 打字互动（可选）"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        })
        root.addView(TextView(this).apply {
            text = "\n提示：打字互动仅用于判断输入界面状态，不读取或保存键入内容。不同系统和播放器的媒体信息支持程度可能不同。"
            textSize = 13f
        })
        setContentView(root)
    }
}
