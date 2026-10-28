package com.example.floatingpet

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Sends coarse interaction pulses only. Never reads event.text, node text, or typed content.
 * Soft-keyboard text-change events are used as an approximation of individual key presses.
 */
class TypingAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            eventTypes = eventTypes or AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or AccessibilityEvent.TYPE_VIEW_FOCUSED or AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            notificationTimeout = 35
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> sendBroadcast(Intent("com.example.floatingpet.KEY_PULSE").setPackage(packageName))
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> sendBroadcast(Intent(PetOverlayService.ACTION_TYPING).setPackage(packageName).putExtra("visible", true))
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> sendBroadcast(Intent(PetOverlayService.ACTION_TYPING).setPackage(packageName).putExtra("visible", false))
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            sendBroadcast(Intent("com.example.floatingpet.KEY_PULSE").setPackage(packageName))
        }
        return false
    }

    override fun onInterrupt() {}
}