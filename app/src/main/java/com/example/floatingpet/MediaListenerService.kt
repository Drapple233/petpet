package com.example.floatingpet

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.content.ComponentName

/**
 * Optional media-session access via Notification Listener permission.
 * Android/player support varies. This starter detects play/pause state; robust beat detection
 * requires a permitted audio analysis source and is not guaranteed for arbitrary third-party apps.
 */
class MediaListenerService : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        try {
            val msm = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
            msm.getActiveSessions(ComponentName(this, MediaListenerService::class.java))
                .forEach { controller ->
                    controller.registerCallback(object : android.media.session.MediaController.Callback() {
                        override fun onPlaybackStateChanged(state: PlaybackState?) {
                            // Future hook: broadcast state to PetOverlayService for animation mode.
                        }
                    })
                }
        } catch (_: SecurityException) { }
    }
    override fun onNotificationPosted(sbn: StatusBarNotification?) { }
}
