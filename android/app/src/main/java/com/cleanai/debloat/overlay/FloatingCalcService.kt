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
import kotlin.math.abs
import com.cleanai.debloat.MainActivity
import com.cleanai.debloat.ui.CalcMemory

/**
 * A small iPhone-style calculator that floats over other apps.
 * It runs only while the window is open: closing it (✕ or the notification) stops the service.
 */
class FloatingCalcService : Service() {
    private lateinit var wm: WindowManager
    private var root: LinearLayout? = null
    // The same calculator as the app's Basic tab, so a sum carries over both ways.
    private val calc = CalcMemory.basic
    private lateinit var display: TextView
    private lateinit var expression: TextView
    private lateinit var body: LinearLayout
    private val keys = mutableMapOf<String, TextView>()

    // Remembered while the window is open, so resizing keeps it where the user put it.
    private var posX = -1
    private var posY = -1
    private var size = 1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        com.cleanai.debloat.ui.CalcHistory.init(this)
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

    /** Like [dp], scaled by the chosen window size (small, medium, large). */
    private fun sp(v: Float) = dp(v * SIZES[size])

    private fun rounded(color: Int, radius: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun show() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val params = WindowManager.LayoutParams(
            sp(268f),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // Not focusable: the keyboard and the app underneath keep working.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = if (posX >= 0) posX else dp(24f)
            y = if (posY >= 0) posY else dp(140f)
        }

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            // A thin light edge keeps the black window visible over dark screens.
            background = rounded(0xF2000000.toInt(), 26f).apply { setStroke(dp(1f), 0x55FFFFFF) }
            setPadding(sp(12f), sp(6f), sp(12f), sp(12f))
            elevation = dp(12f).toFloat()
        }

        // Header: drag anywhere on it; – folds the keys away, ✕ closes.
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4f), 0, 0, 0)
        }
        header.addView(TextView(this).apply {
            text = "⋮⋮  Calculator  ↗"
            setTextColor(GREY)
            textSize = 13f
            contentDescription = "Drag to move. Tap to open the full calculator."
        }, LinearLayout.LayoutParams(0, dp(40f), 1f).apply { gravity = Gravity.CENTER_VERTICAL })
        // ⤡ cycles small → medium → large.
        header.addView(headerButton("⤡") {
            size = (size + 1) % SIZES.size
            rebuild()
        })
        header.addView(headerButton("–") {
            body.visibility = if (body.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        })
        header.addView(headerButton("✕") { stopSelf() })
        var startX = 0; var startY = 0; var touchX = 0f; var touchY = 0f
        header.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { startX = params.x; startY = params.y; touchX = e.rawX; touchY = e.rawY; true }
                MotionEvent.ACTION_UP -> {
                    // A tap (not a drag) on the title goes back to the full calculator in the app.
                    val slop = dp(8f)
                    if (abs(e.rawX - touchX) < slop && abs(e.rawY - touchY) < slop) openApp()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (e.rawX - touchX).toInt()
                    params.y = startY + (e.rawY - touchY).toInt()
                    posX = params.x; posY = params.y
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
        panel.addView(display, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, sp(60f)))

        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        ROWS.forEach { row ->
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.forEach { k ->
                val wide = k == "0"
                val key = TextView(this).apply {
                    text = k
                    gravity = if (wide) Gravity.CENTER_VERTICAL or Gravity.START else Gravity.CENTER
                    if (wide) setPadding(dp(18f), 0, 0, 0)
                    textSize = (if (k in OPS || k == "=") 24f else 20f) * SIZES[size]
                    setOnClickListener { v ->
                        v.performHapticFeedback(HapticFeedbackType)
                        calc.press(if (k == "AC") calc.clearLabel else k)
                        refresh()
                    }
                }
                keys[k] = key
                line.addView(key, LinearLayout.LayoutParams(0, sp(46f), if (wide) 2f else 1f).apply { setMargins(sp(4f), sp(4f), sp(4f), sp(4f)) })
            }
            body.addView(line)
        }
        panel.addView(body)

        root = panel
        // Some phones refuse the window even with the permission on; say so instead of failing silently.
        val added = runCatching { wm.addView(panel, params) }
        if (added.isFailure) {
            root = null
            Toast.makeText(this, "Couldn't show the floating calculator: ${added.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }
        refresh()
    }

    /** Brings the app back on the calculator screen and closes the floating window, like expanding picture-in-picture. */
    private fun openApp() {
        val open = Intent(this, MainActivity::class.java)
            .putExtra(EXTRA_OPEN, "calculator")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        runCatching { startActivity(open) }
        stopSelf()
    }

    /** Draws the window again at the new size, keeping the sum and the position. */
    private fun rebuild() {
        val folded = body.visibility != View.VISIBLE
        root?.let { runCatching { wm.removeView(it) } }
        root = null
        keys.clear()
        show()
        if (folded) body.visibility = View.GONE
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
        // The sum so far, with its answer as you type ("12+3×4  = 24").
        expression.text = calc.expression + (calc.preview?.let { "  = $it" } ?: "")
        keys["AC"]?.text = calc.clearLabel
        keys.forEach { (k, v) ->
            val lit = k.length == 1 && k[0] == calc.selectedOp
            val (bg, fg) = when {
                k in OPS && lit -> 0xFFFFFFFF.toInt() to ORANGE
                k in OPS || k == "=" -> ORANGE to 0xFFFFFFFF.toInt()
                k == "AC" || k == "±" || k == "%" -> 0xFFA5A5A5.toInt() to 0xFF000000.toInt()
                else -> 0xFF333333.toInt() to 0xFFFFFFFF.toInt()
            }
            v.background = rounded(bg, 23f * SIZES[size])
            v.setTextColor(fg)
        }
    }

    companion object {
        private const val CHANNEL = "floating_calc"
        private const val NOTIFICATION_ID = 7002
        const val EXTRA_OPEN = "com.cleanai.debloat.OPEN_SCREEN"
        private const val ACTION_STOP = "com.cleanai.debloat.STOP_FLOATING_CALC"
        private const val ORANGE = 0xFFFF9F0A.toInt()
        private const val GREY = 0xFFA5A5A5.toInt()
        private val HapticFeedbackType = HapticFeedbackConstants.KEYBOARD_TAP
        private val OPS = setOf("÷", "×", "−", "+")
        private val SIZES = floatArrayOf(0.8f, 1f, 1.2f)
        private val ROWS = listOf(
            listOf("AC", "±", "%", "÷"),
            listOf("7", "8", "9", "×"),
            listOf("4", "5", "6", "−"),
            listOf("1", "2", "3", "+"),
            listOf("0", ".", "="),
        )

        /** Set while the user is in Settings allowing "Display over other apps"; the app opens the calculator on return. */
        @Volatile private var waitingForPermission = false

        fun canShow(ctx: Context) = Settings.canDrawOverlays(ctx)

        /**
         * Opens the floating calculator. Without "Display over other apps" it opens that Settings page instead,
         * and [resumeIfAllowed] opens the calculator as soon as the user comes back with it switched on.
         */
        fun start(ctx: Context): Boolean {
            if (!Settings.canDrawOverlays(ctx)) {
                waitingForPermission = true
                Toast.makeText(ctx, "Turn on Mobile Handler here, then come back.", Toast.LENGTH_LONG).show()
                val ask = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                // Some phones have no per-app page; fall back to the full list.
                runCatching { ctx.startActivity(ask) }.onFailure {
                    runCatching { ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
                return false
            }
            waitingForPermission = false
            ContextCompat.startForegroundService(ctx, Intent(ctx, FloatingCalcService::class.java))
            // Like picture-in-picture: the app steps aside and the calculator floats over the home screen.
            (ctx as? android.app.Activity)?.moveTaskToBack(true)
            return true
        }

        /** Called when the app comes back to the front: finishes a start that was waiting for the permission. */
        fun resumeIfAllowed(ctx: Context) {
            if (!waitingForPermission) return
            waitingForPermission = false
            if (Settings.canDrawOverlays(ctx)) {
                Toast.makeText(ctx, "Floating calculator is on. Tap its title to come back here.", Toast.LENGTH_LONG).show()
                start(ctx)
            } else {
                Toast.makeText(ctx, "\"Display over other apps\" is still off, so the floating calculator can't open.", Toast.LENGTH_LONG).show()
            }
        }
    }
}
