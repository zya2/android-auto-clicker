package com.zya2.autoclicker

import android.content.Context

enum class ClickMode {
    SINGLE, MULTI, SWIPE
}

data class TapPoint(var x: Int, var y: Int)

/**
 * 設定とタップ位置を保持し、SharedPreferences に保存する。
 * MainActivity（設定画面）と OverlayService（マーカー）の両方から使う。
 */
object ClickConfig {

    private const val PREFS = "auto_clicker_config"
    private const val KEY_MODE = "mode"
    private const val KEY_INTERVAL = "interval_ms"
    private const val KEY_DURATION = "swipe_duration_ms"
    private const val KEY_REPEATS = "repeats"
    private const val KEY_POINTS = "points"

    var mode: ClickMode = ClickMode.SINGLE
    var intervalMs: Long = 100L
    var swipeDurationMs: Long = 300L

    /** 0 = 無制限 */
    var repeats: Int = 0

    val points = mutableListOf<TapPoint>()

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        mode = runCatching {
            ClickMode.valueOf(prefs.getString(KEY_MODE, ClickMode.SINGLE.name)!!)
        }.getOrDefault(ClickMode.SINGLE)
        intervalMs = prefs.getLong(KEY_INTERVAL, 100L)
        swipeDurationMs = prefs.getLong(KEY_DURATION, 300L)
        repeats = prefs.getInt(KEY_REPEATS, 0)
        points.clear()
        val raw = prefs.getString(KEY_POINTS, "") ?: ""
        if (raw.isNotEmpty()) {
            for (pair in raw.split(";")) {
                val xy = pair.split(",")
                if (xy.size == 2) {
                    val x = xy[0].toIntOrNull()
                    val y = xy[1].toIntOrNull()
                    if (x != null && y != null) points.add(TapPoint(x, y))
                }
            }
        }
    }

    fun save(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_MODE, mode.name)
            .putLong(KEY_INTERVAL, intervalMs)
            .putLong(KEY_DURATION, swipeDurationMs)
            .putInt(KEY_REPEATS, repeats)
            .putString(KEY_POINTS, points.joinToString(";") { "${it.x},${it.y}" })
            .apply()
    }
}
