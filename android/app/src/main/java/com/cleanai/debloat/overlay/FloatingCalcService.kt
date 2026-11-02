package com.cleanai.debloat.overlay

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import com.cleanai.debloat.R
import com.cleanai.debloat.ui.IosCalc

/**
 * A small iPhone-style calculator that floats over other apps.
 * It runs only while the window is open: closing it (✕ or the notification) stops the service.
 */
class FloatingCalcService : Service() {
    private lateinit var wm: WindowManager
    private var root: LinearLayout? = null
    private val calc = IosCalc()
    private lateinit var display: TextView
    private lateinit var expression: TextView
    private lateinit var body: LinearLayout
    private val keys = mutableMapOf<String, TextView>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP || !Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification(),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
        if (root == null) show()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        root?.let { runCatching { wm.removeView(it) } }
        root = null
        super.onDestroy()
    }

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Floating calculator", NotificationManager.IMPORTANCE_LOW))
        }
        val stop = PendingIntent.getService(
            this, 0,
            Intent(this, FloatingCalcService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        @Suppress("DEPRECATION")
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else Notification.Builder(this)
        return b.setSmallIcon(R.drawable.ic_tool_calc)
            .setContentTitle("Floating calculator is open")
            .setContentText("Tap to close it.")
            .setContentIntent(stop)
            .setOngoing(true)
            .build()
    }

    // ---------------------------------------------------------------- window

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()

    private fun rounded(color: Int, radius: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun show() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val params = WindowManager.LayoutParams(
            dp(268f),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // Not focusable: the keyboard and the app underneath keep working.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(24f)
            y = dp(140f)
        }

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(0xF2000000.toInt(), 26f)
            setPadding(dp(12f), dp(6f), dp(12f), dp(12f))
            elevation = dp(12f).toFloat()
        }

        // Header: drag anywhere on it; – folds the keys away, ✕ closes.
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4f), 0, 0, 0)
        }
        header.addView(TextView(this).apply {
            text = "⋮⋮  Calculator"
            setTextColor(GREY)
            textSize = 13f
        }, LinearLayout.LayoutParams(0, dp(40f), 1f).apply { gravity = Gravity.CENTER_VERTICAL })
        header.addView(headerButton("–") {
            body.visibility = if (body.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        })
        header.addView(headerButton("✕") { stopSelf() })
        var startX = 0; var startY = 0; var touchX = 0f; var touchY = 0f
        header.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { startX = params.x; startY = params.y; touchX = e.rawX; touchY = e.rawY; true }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (e.rawX - touchX).toInt()
                    params.y = startY + (e.rawY - touchY).toInt()
                    root?.let { wm.updateViewLayout(it, params) }
                    true
                }
                else -> false
            }
        }
        panel.addView(header)

        expression = TextView(this).apply {
            setTextColor(GREY)
            textSize = 14f
            gravity = Gravity.END
            maxLines = 1
        }
        panel.addView(expression, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        display = TextView(this).apply {
            setTextColor(0xFFFFFFFF.toInt())
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            gravity = Gravity.END or Gravity.BOTTOM
            maxLines = 1
            // Long-press copies the number.
            setOnLongClickListener {
                getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Result", calc.display.replace(",", "")))
                Toast.makeText(this@FloatingCalcService, "Copied ${calc.display}", Toast.LENGTH_SHORT).show()
                true
            }
        }
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(display, 18, 48, 1, TypedValue.COMPLEX_UNIT_SP)
        panel.addView(display, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(60f)))

        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        ROWS.forEach { row ->
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.forEach { k ->
                val wide = k == "0"
                val key = TextView(this).apply {
                    text = k
                    gravity = if (wide) Gravity.CENTER_VERTICAL or Gravity.START else Gravity.CENTER
                    if (wide) setPadding(dp(18f), 0, 0, 0)
                    textSize = if (k in OPS || k == "=") 24f else 20f
                    setOnClickListener { v ->
                        v.performHapticFeedback(HapticFeedbackType)
                        calc.press(if (k == "AC") calc.clearLabel else k)
                        refresh()
                    }
                }
                keys[k] = key
                line.addView(key, LinearLayout.LayoutParams(0, dp(46f), if (wide) 2f else 1f).apply { setMargins(dp(4f), dp(4f), dp(4f), dp(4f)) })
            }
            body.addView(line)
        }
        panel.addView(body)

        root = panel
        wm.addView(panel, params)
        refresh()
    }

    private fun headerButton(label: String, onClick: () -> Unit) = TextView(this).apply {
        text = label
        setTextColor(0xFFFFFFFF.toInt())
        textSize = 16f
        gravity = Gravity.CENTER
        background = rounded(0xFF333333.toInt(), 16f)
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(dp(32f), dp(32f)).apply { marginStart = dp(8f) }
    }

    /** Redraws the number, the AC/C label and the lit operator after each key. */
    private fun refresh() {
        display.text = calc.display
        expression.text = calc.expression
        keys["AC"]?.text = calc.clearLabel
        keys.forEach { (k, v) ->
            val lit = k.length == 1 && k[0] == calc.selectedOp
            val (bg, fg) = when {
                k in OPS && lit -> 0xFFFFFFFF.toInt() to ORANGE
                k in OPS || k == "=" -> ORANGE to 0xFFFFFFFF.toInt()
                k == "AC" || k == "±" || k == "%" -> 0xFFA5A5A5.toInt() to 0xFF000000.toInt()
                else -> 0xFF333333.toInt() to 0xFFFFFFFF.toInt()
            }
            v.background = rounded(bg, 23f)
            v.setTextColor(fg)
        }
    }

    companion object {
        private const val CHANNEL = "floating_calc"
        private const val NOTIFICATION_ID = 7002
        private const val ACTION_STOP = "com.cleanai.debloat.STOP_FLOATING_CALC"
        private const val ORANGE = 0xFFFF9F0A.toInt()
        private const val GREY = 0xFFA5A5A5.toInt()
        private val HapticFeedbackType = HapticFeedbackConstants.KEYBOARD_TAP
        private val OPS = setOf("÷", "×", "−", "+")
        private val ROWS = listOf(
            listOf("AC", "±", "%", "÷"),
            listOf("7", "8", "9", "×"),
            listOf("4", "5", "6", "−"),
            listOf("1", "2", "3", "+"),
            listOf("0", ".", "="),
        )

        /**
         * Opens the floating calculator. The first time, Android asks the user to allow
         * "Display over other apps"; returns false while that permission is missing.
         */
        fun start(ctx: Context): Boolean {
            if (!Settings.canDrawOverlays(ctx)) {
                Toast.makeText(ctx, "Allow \"Display over other apps\" for Mobile Handler, then tap ⧉ again.", Toast.LENGTH_LONG).show()
                val ask = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { ctx.startActivity(ask) }
                return false
            }
            ContextCompat.startForegroundService(ctx, Intent(ctx, FloatingCalcService::class.java))
            return true
        }
    }
}
