package com.zya2.autoclicker

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var tvAccessibilityStatus: TextView
    private lateinit var tvOverlayStatus: TextView
    private lateinit var rgMode: RadioGroup
    private lateinit var etInterval: EditText
    private lateinit var etDuration: EditText
    private lateinit var etRepeats: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvAccessibilityStatus = findViewById(R.id.tvAccessibilityStatus)
        tvOverlayStatus = findViewById(R.id.tvOverlayStatus)
        rgMode = findViewById(R.id.rgMode)
        etInterval = findViewById(R.id.etInterval)
        etDuration = findViewById(R.id.etDuration)
        etRepeats = findViewById(R.id.etRepeats)

        ClickConfig.load(this)
        when (ClickConfig.mode) {
            ClickMode.SINGLE -> rgMode.check(R.id.rbSingle)
            ClickMode.MULTI -> rgMode.check(R.id.rbMulti)
            ClickMode.SWIPE -> rgMode.check(R.id.rbSwipe)
        }
        etInterval.setText(ClickConfig.intervalMs.toString())
        etDuration.setText(ClickConfig.swipeDurationMs.toString())
        etRepeats.setText(ClickConfig.repeats.toString())

        findViewById<Button>(R.id.btnAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.btnOverlay).setOnClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
        findViewById<Button>(R.id.btnSave).setOnClickListener {
            saveSettings()
            Toast.makeText(this, "保存しました", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnPanel).setOnClickListener {
            saveSettings()
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "先にオーバーレイ許可が必要です", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            startService(Intent(this, OverlayService::class.java))
            Toast.makeText(this, "操作パネルを表示しました", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        tvAccessibilityStatus.text =
            "アクセシビリティ: " + if (isAccessibilityEnabled()) "有効" else "無効（許可が必要です）"
        tvOverlayStatus.text =
            "オーバーレイ: " + if (Settings.canDrawOverlays(this)) "許可済み" else "未許可"
    }

    private fun saveSettings() {
        ClickConfig.mode = when (rgMode.checkedRadioButtonId) {
            R.id.rbMulti -> ClickMode.MULTI
            R.id.rbSwipe -> ClickMode.SWIPE
            else -> ClickMode.SINGLE
        }
        ClickConfig.intervalMs = etInterval.text.toString().toLongOrNull() ?: 100L
        ClickConfig.swipeDurationMs = etDuration.text.toString().toLongOrNull() ?: 300L
        ClickConfig.repeats = etRepeats.text.toString().toIntOrNull() ?: 0
        ClickConfig.save(this)
    }

    private fun isAccessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getInt(
            contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0
        )
        if (enabled != 1) return false
        val services = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val expected = "$packageName/.AutoClickService"
        return services.split(':').any { it.equals(expected, ignoreCase = true) }
    }
}
