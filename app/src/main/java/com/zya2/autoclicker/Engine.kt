package com.zya2.autoclicker

import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper

/**
 * 設定からジェスチャ列を組み立て、アクセシビリティサービス経由で
 * 一定間隔で繰り返し実行するエンジン。
 */
object Engine {

    /** 接続中のアクセシビリティサービス（未接続なら null） */
    var service: AutoClickService? = null

    var running: Boolean = false
        private set

    /** 実行状態が変わったときに呼ばれる（オーバーレイのボタン表示更新用） */
    var onStateChanged: ((Boolean) -> Unit)? = null

    private val handler = Handler(Looper.getMainLooper())
    private var actions: List<GestureDescription> = emptyList()
    private var stepIndex = 0
    private var loopsDone = 0

    private fun buildActions(): List<GestureDescription> {
        val pts = ClickConfig.points
        if (pts.isEmpty()) return emptyList()
        return when (ClickConfig.mode) {
            ClickMode.SINGLE -> listOf(tapGesture(pts[0]))
            ClickMode.MULTI -> pts.map { tapGesture(it) }
            ClickMode.SWIPE -> {
                if (pts.size < 2) listOf(tapGesture(pts[0]))
                else listOf(swipeGesture(pts[0], pts[1]))
            }
        }
    }

    private fun tapGesture(p: TapPoint): GestureDescription {
        val path = Path()
        path.moveTo(p.x.toFloat(), p.y.toFloat())
        val stroke = GestureDescription.StrokeDescription(path, 0L, 50L)
        return GestureDescription.Builder().addStroke(stroke).build()
    }

    private fun swipeGesture(from: TapPoint, to: TapPoint): GestureDescription {
        val path = Path()
        path.moveTo(from.x.toFloat(), from.y.toFloat())
        path.lineTo(to.x.toFloat(), to.y.toFloat())
        val duration = ClickConfig.swipeDurationMs.coerceIn(50L, 5000L)
        val stroke = GestureDescription.StrokeDescription(path, 0L, duration)
        return GestureDescription.Builder().addStroke(stroke).build()
    }

    fun start() {
        val svc = service ?: return
        if (running) return
        actions = buildActions()
        if (actions.isEmpty()) return
        stepIndex = 0
        loopsDone = 0
        running = true
        onStateChanged?.invoke(true)
        handler.post { runStep(svc) }
    }

    fun stop() {
        if (!running) return
        running = false
        handler.removeCallbacksAndMessages(null)
        onStateChanged?.invoke(false)
    }

    private fun runStep(svc: AutoClickService) {
        if (!running) return
        if (stepIndex >= actions.size) {
            stepIndex = 0
            loopsDone++
            if (ClickConfig.repeats > 0 && loopsDone >= ClickConfig.repeats) {
                stop()
                return
            }
        }
        svc.dispatchGesture(actions[stepIndex], null, null)
        stepIndex++
        val interval = ClickConfig.intervalMs.coerceIn(10L, 60000L)
        handler.postDelayed({ runStep(svc) }, interval)
    }
}
