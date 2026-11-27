package com.zya2.autoclicker

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * ジェスチャ実行専用のアクセシビリティサービス。
 * 画面の読み取りは行わず、Engine からの dispatchGesture だけを行う。
 */
class AutoClickService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        Engine.service = this
    }

    override fun onDestroy() {
        Engine.stop()
        if (Engine.service == this) Engine.service = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // イベント監視はしない
    }

    override fun onInterrupt() {
        // 何もしない
    }
}
