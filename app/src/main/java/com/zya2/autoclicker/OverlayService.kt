package com.zya2.autoclicker

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * フローティング操作パネルと、ドラッグで動かせるタップ位置マーカーを
 * 他のアプリの上に表示するサービス。
 */
class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var panelView: LinearLayout? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var runButton: Button? = null

    private class Marker(
        val view: TextView,
        val params: WindowManager.LayoutParams,
        val point: TapPoint
    )

    private val markers = mutableListOf<Marker>()

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        ClickConfig.load(this)
        showPanel()
        // 保存済みの位置があればマーカーを復元する
        for (p in ClickConfig.points) addMarker(p.x, p.y, restore = true)
        if (markers.isEmpty()) {
            val dm = resources.displayMetrics
            addMarker(dm.widthPixels / 2, dm.heightPixels / 3, restore = false)
        }
        Engine.onStateChanged = { running ->
            runButton?.text = if (running) "停止" else "実行"
        }
    }

    override fun onDestroy() {
        Engine.stop()
        Engine.onStateChanged = null
        for (m in markers) runCatching { windowManager.removeView(m.view) }
        markers.clear()
        panelView?.let { runCatching { windowManager.removeView(it) } }
        panelView = null
        super.onDestroy()
    }

    private fun overlayParams(width: Int, height: Int, x: Int, y: Int): WindowManager.LayoutParams {
        val params = WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = x
        params.y = y
        return params
    }

    private fun showPanel() {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.argb(225, 30, 34, 44))
                cornerRadius = dp(14).toFloat()
            }
        }

        val title = TextView(this).apply {
            text = "オートクリッカー（ドラッグで移動）"
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(dp(2), dp(2), dp(2), dp(6))
        }
        panel.addView(title)

        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val addButton = Button(this).apply { text = "＋ 位置を追加" }
        val removeButton = Button(this).apply { text = "－ 削除" }
        row1.addView(addButton)
        row1.addView(removeButton)
        panel.addView(row1)

        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val runBtn = Button(this).apply { text = "実行" }
        val closeButton = Button(this).apply { text = "閉じる" }
        row2.addView(runBtn)
        row2.addView(closeButton)
        panel.addView(row2)
        runButton = runBtn

        addButton.setOnClickListener {
            val dm = resources.displayMetrics
            val offset = markers.size * dp(24)
            addMarker(dm.widthPixels / 2 + offset, dm.heightPixels / 3 + offset, restore = false)
            syncPoints()
        }
        removeButton.setOnClickListener {
            if (markers.isNotEmpty()) {
                val m = markers.removeAt(markers.size - 1)
                runCatching { windowManager.removeView(m.view) }
                renumberMarkers()
                syncPoints()
            }
        }
        runBtn.setOnClickListener {
            if (Engine.running) {
                Engine.stop()
            } else {
                syncPoints()
                ClickConfig.save(this)
                if (Engine.service == null) {
                    Toast.makeText(
                        this,
                        "先にアプリ画面からアクセシビリティを有効にしてください",
                        Toast.LENGTH_LONG
                    ).show()
                } else if (ClickConfig.points.isEmpty()) {
                    Toast.makeText(this, "タップ位置がありません", Toast.LENGTH_SHORT).show()
                } else {
                    Engine.start()
                }
            }
        }
        closeButton.setOnClickListener { stopSelf() }

        val params = overlayParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            dp(16), dp(120)
        )
        panelParams = params
        makeDraggable(title, params) { panel }
        windowManager.addView(panel, params)
        panelView = panel
    }

    private fun addMarker(centerX: Int, centerY: Int, restore: Boolean) {
        val size = dp(52)
        val number = markers.size + 1
        val view = TextView(this).apply {
            text = number.toString()
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(190, 0, 170, 60))
                setStroke(dp(2), Color.WHITE)
            }
        }
        val point = TapPoint(centerX, centerY)
        val params = overlayParams(size, size, centerX - size / 2, centerY - size / 2)
        val marker = Marker(view, params, point)
        makeDraggable(view, params) {
            point.x = params.x + size / 2
            point.y = params.y + size / 2
            view
        }
        windowManager.addView(view, params)
        markers.add(marker)
        if (!restore) syncPoints()
    }

    private fun renumberMarkers() {
        markers.forEachIndexed { index, marker -> marker.view.text = (index + 1).toString() }
    }

    /** マーカーの中心座標を設定オブジェクトへ反映する */
    private fun syncPoints() {
        ClickConfig.points.clear()
        for (m in markers) ClickConfig.points.add(TapPoint(m.point.x, m.point.y))
    }

    /**
     * ビューをドラッグで動かせるようにする。
     * onMoved はドラッグ終了時に呼ばれる。
     */
    private fun makeDraggable(
        view: View,
        params: WindowManager.LayoutParams,
        onMoved: () -> View
    ) {
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (event.rawX - touchX).toInt()
                    params.y = startY + (event.rawY - touchY).toInt()
                    runCatching { windowManager.updateViewLayout(v, params) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    onMoved()
                    syncPoints()
                    ClickConfig.save(this@OverlayService)
                    true
                }
                else -> false
            }
        }
    }
}
