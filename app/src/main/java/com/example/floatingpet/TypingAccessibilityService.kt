package com.example.floatingpet

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Optional and user-enabled. Uses event types only; does not retrieve window content or text.
 * On different Android skins keyboard visibility events may vary.
 */
class TypingAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Hook for future keyboard-visible state broadcast. Never reads event.text or node content.
    }
    override fun onInterrupt() {}
}
