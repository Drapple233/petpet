package com.example.floatingpet

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.os.Process
import kotlin.concurrent.thread
import kotlin.math.sqrt

/** Detects local audio onsets; raw audio is neither stored nor uploaded. */
class AudioBeatService : Service() {
    @Volatile private var running = false
    private var recorder: AudioRecord? = null
    private var projection: MediaProjection? = null
    private var worker: Thread? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        if (running) return START_STICKY
        createNotification(intent)
        val sampleRate = 16000
        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuffer <= 0) { stopSelf(); return START_NOT_STICKY }
        try {
            val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
            @Suppress("DEPRECATION")
            val resultData = if (Build.VERSION.SDK_INT >= 33) intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java) else intent?.getParcelableExtra(EXTRA_RESULT_DATA) as? Intent
            recorder = if (resultCode != 0 && resultData != null && Build.VERSION.SDK_INT >= 29) {
                val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                projection = manager.getMediaProjection(resultCode, resultData)
                val config = AudioPlaybackCaptureConfiguration.Builder(projection!!)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .build()
                AudioRecord.Builder()
                    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build())
                    .setBufferSizeInBytes(minBuffer * 2)
                    .setAudioPlaybackCaptureConfig(config)
                    .build()
            } else {
                AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuffer * 2)
            }
            if (recorder?.state != AudioRecord.STATE_INITIALIZED) { stopSelf(); return START_NOT_STICKY }
            running = true
            recorder?.startRecording()
            worker = thread(name = "FloatingPetAudioBeat") {
                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
                val samples = ShortArray((minBuffer / 2).coerceAtLeast(512))
                var noiseFloor = 350.0
                var lastBeat = 0L
                while (running && !Thread.currentThread().isInterrupted) {
                    val count = try { recorder?.read(samples, 0, samples.size) ?: -1 } catch (_: Exception) { -1 }
                    if (count <= 0) break
                    var sum = 0.0
                    for (i in 0 until count) { val v = samples[i].toDouble(); sum += v * v }
                    val rms = sqrt(sum / count)
                    val now = System.currentTimeMillis()
                    if (rms > maxOf(700.0, noiseFloor * 1.65) && now - lastBeat > 260L) {
                        lastBeat = now
                        sendBroadcast(Intent(PetOverlayService.ACTION_AUDIO_BEAT).setPackage(packageName))
                    }
                    noiseFloor = noiseFloor * .94 + minOf(rms, noiseFloor * 1.8) * .06
                }
                stopSelf()
            }
        } catch (_: SecurityException) { stopSelf(); return START_NOT_STICKY }
        catch (_: Exception) { stopSelf(); return START_NOT_STICKY }
        return START_STICKY
    }

    private fun createNotification(intent: Intent?) {
        val channel = "pet_audio_capture"
        if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(channel, "桌宠音频节拍", NotificationManager.IMPORTANCE_LOW))
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, channel) else Notification.Builder(this)
        val notification = builder.setSmallIcon(android.R.drawable.ic_media_play).setContentTitle("FloatingPet 音频节拍检测").setContentText("仅本机分析音频，不保存录音").build()
        if (Build.VERSION.SDK_INT >= 29) {
            val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
            val type = if (resultCode != 0) android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION else android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            startForeground(28, notification, type)
        } else startForeground(28, notification)
    }

    override fun onDestroy() {
        running = false
        try { recorder?.stop() } catch (_: Exception) {}
        try { recorder?.release() } catch (_: Exception) {}
        recorder = null
        try { projection?.stop() } catch (_: Exception) {}
        projection = null
        worker?.interrupt()
        worker = null
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP = "com.example.floatingpet.STOP_AUDIO"
        const val EXTRA_RESULT_CODE = "capture_result_code"
        const val EXTRA_RESULT_DATA = "capture_result_data"
    }
}