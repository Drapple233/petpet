package com.example.floatingpet

import android.content.ComponentName
import android.content.Intent
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Uses playback state changes only; it does not record or analyze audio. */
class MediaListenerService : NotificationListenerService() {
    private val callbacks = mutableMapOf<MediaController, MediaController.Callback>()
    override fun onListenerConnected() {
        super.onListenerConnected()
        try {
            val manager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
            manager.getActiveSessions(ComponentName(this, MediaListenerService::class.java)).forEach { attach(it) }
        } catch (_: SecurityException) { }
    }
    private fun attach(controller: MediaController) {
        val callback = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) {
                val action = if (state?.state == PlaybackState.STATE_PLAYING) PetOverlayService.ACTION_MEDIA else PetOverlayService.ACTION_MEDIA_PAUSED
                sendBroadcast(Intent(action).setPackage(packageName))
            }
            override fun onMetadataChanged(metadata: android.media.MediaMetadata?) {
                if (controller.playbackState?.state == PlaybackState.STATE_PLAYING) sendBroadcast(Intent(PetOverlayService.ACTION_MEDIA).setPackage(packageName))
            }
        }
        try { controller.registerCallback(callback); callbacks[controller] = callback } catch (_: Exception) { }
    }
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        try { val manager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager; manager.getActiveSessions(ComponentName(this, MediaListenerService::class.java)).forEach { if (!callbacks.containsKey(it)) attach(it) } } catch (_: SecurityException) { }
    }
    override fun onListenerDisconnected() { callbacks.forEach { (controller, callback) -> try { controller.unregisterCallback(callback) } catch (_: Exception) {} }; callbacks.clear(); super.onListenerDisconnected() }
}
