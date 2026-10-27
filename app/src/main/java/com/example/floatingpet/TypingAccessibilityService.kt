package com.example.floatingpet

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/** Emits only coarse UI/keyboard-related events; never reads event.text or node content. */
class TypingAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val isUiFocus = event.eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED || event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED
        if (isUiFocus) sendBroadcast(Intent(PetOverlayService.ACTION_TYPING).setPackage(packageName).putExtra("visible", event.eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED))
    }
    override fun onInterrupt() {}
}
