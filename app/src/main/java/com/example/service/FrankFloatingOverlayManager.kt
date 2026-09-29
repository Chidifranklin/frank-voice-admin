package com.example.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Manages Frank's Floating Super Admin Overlay HUD that appears over other active apps.
 * Utilizes SYSTEM_ALERT_WINDOW (TYPE_APPLICATION_OVERLAY) to provide immediate visual feedback,
 * live voice transcription, and action confirmation when commands are uttered from any app.
 */
class FrankFloatingOverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var overlayView: View? = null
    private var isOverlayAttached = false

    private var tvBadge: TextView? = null
    private var tvUserText: TextView? = null
    private var tvReplyText: TextView? = null
    private var ivIcon: ImageView? = null

    private val autoDismissRunnable = Runnable {
        showIdleMicBadge()
    }

    /**
     * Checks if SYSTEM_ALERT_WINDOW / Display over other apps is granted.
     */
    fun canDrawOverlay(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    /**
     * Shows a compact floating microphone icon when Frank is idle and running in the background.
     */
    fun showIdleMicBadge() {
        mainHandler.post {
            ensureOverlayCreated()
            if (!isOverlayAttached) return@post

            tvBadge?.text = "IDLE • BACKGROUND"
            tvBadge?.setTextColor(Color.parseColor("#38BDF8")) // Sky blue
            tvUserText?.text = "Frank Microphone Active"
            tvReplyText?.text = "Say \"Hey Frank\" anytime to activate"
            ivIcon?.setImageResource(com.example.R.drawable.ic_microphone)
            ivIcon?.setColorFilter(Color.parseColor("#00E5FF")) // CyberCyan
        }
    }

    /**
     * Shows or updates the floating overlay with active listening status.
     */
    fun showListening(heardPartial: String = "") {
        mainHandler.post {
            ensureOverlayCreated()
            if (!isOverlayAttached) return@post

            tvBadge?.text = "FRANK LISTENING"
            tvBadge?.setTextColor(Color.parseColor("#00E5FF")) // CyberCyan
            tvUserText?.text = if (heardPartial.isNotBlank()) "\"$heardPartial\"" else "Listening for wake word..."
            tvReplyText?.text = "Say \"Hey Frank\" • Zero beeps active"
            ivIcon?.setImageResource(com.example.R.drawable.ic_microphone)
            ivIcon?.setColorFilter(Color.parseColor("#00E5FF"))

            resetAutoDismissTimer(6000)
        }
    }

    /**
     * Shows command execution and result on the floating overlay over other apps.
     */
    fun showExecution(command: String, result: String, badge: String = "SUPER ADMIN") {
        mainHandler.post {
            ensureOverlayCreated()
            if (!isOverlayAttached) return@post

            tvBadge?.text = badge.uppercase()
            tvBadge?.setTextColor(Color.parseColor("#10B981")) // CyberEmerald
            tvUserText?.text = "\"$command\""
            tvReplyText?.text = result
            ivIcon?.setImageResource(com.example.R.drawable.ic_microphone)
            ivIcon?.setColorFilter(Color.parseColor("#10B981"))

            resetAutoDismissTimer(4500)
        }
    }

    /**
     * Hides and detaches the floating overlay from the screen.
     */
    fun hideOverlay() {
        mainHandler.post {
            mainHandler.removeCallbacks(autoDismissRunnable)
            if (isOverlayAttached && overlayView != null && windowManager != null) {
                try {
                    windowManager.removeView(overlayView)
                    Log.d(TAG, "Floating overlay detached")
                } catch (e: Exception) {
                    Log.w(TAG, "Error detaching floating overlay: ${e.message}")
                } finally {
                    isOverlayAttached = false
                }
            }
        }
    }

    private fun resetAutoDismissTimer(delayMillis: Long) {
        mainHandler.removeCallbacks(autoDismissRunnable)
        mainHandler.postDelayed(autoDismissRunnable, delayMillis)
    }

    @SuppressLint("InflateParams")
    private fun ensureOverlayCreated() {
        if (!canDrawOverlay() || windowManager == null) {
            return
        }

        if (overlayView == null) {
            overlayView = createOverlayView()
        }

        if (!isOverlayAttached && overlayView != null) {
            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = dpToPx(36)
            }

            try {
                windowManager.addView(overlayView, params)
                isOverlayAttached = true
                Log.d(TAG, "Floating overlay attached successfully over other apps")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to attach floating overlay: ${e.message}", e)
                isOverlayAttached = false
            }
        }
    }

    private fun createOverlayView(): View {
        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
        }

        val cardLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(14), dpToPx(10), dpToPx(14), dpToPx(10))

            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#F00D1424")) // 95% opacity dark cyber navy
                cornerRadius = dpToPx(16).toFloat()
                setStroke(dpToPx(1), Color.parseColor("#38BDF8")) // CyberCyan border
            }
            background = bg
            elevation = dpToPx(10).toFloat()
        }

        val icon = ImageView(context).apply {
            setImageResource(com.example.R.drawable.ic_microphone)
            setColorFilter(Color.parseColor("#00E5FF"))
            layoutParams = LinearLayout.LayoutParams(dpToPx(28), dpToPx(28)).apply {
                marginEnd = dpToPx(10)
            }
        }
        ivIcon = icon

        val textColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
        }

        val badgeView = TextView(context).apply {
            text = "FRANK ACTIVE"
            textSize = 9f
            setTextColor(Color.parseColor("#00E5FF"))
            paint.isFakeBoldText = true
        }
        tvBadge = badgeView

        val userTextView = TextView(context).apply {
            text = "Listening for command..."
            textSize = 12f
            setTextColor(Color.WHITE)
            paint.isFakeBoldText = true
            maxLines = 1
        }
        tvUserText = userTextView

        val replyTextView = TextView(context).apply {
            text = "Overriding other apps"
            textSize = 11f
            setTextColor(Color.parseColor("#94A3B8")) // Slate muted
            maxLines = 2
        }
        tvReplyText = replyTextView

        textColumn.addView(badgeView)
        textColumn.addView(userTextView)
        textColumn.addView(replyTextView)

        val closeButton = ImageView(context).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setColorFilter(Color.parseColor("#64748B"))
            layoutParams = LinearLayout.LayoutParams(dpToPx(22), dpToPx(22)).apply {
                marginStart = dpToPx(8)
            }
            setOnClickListener {
                hideOverlay()
            }
        }

        // Tap card to bring Frank's full UI to foreground
        cardLayout.setOnClickListener {
            val deviceManager = SuperAdminDeviceManager(context)
            deviceManager.bringFrankToForeground()
            hideOverlay()
        }

        cardLayout.addView(icon)
        cardLayout.addView(textColumn)
        cardLayout.addView(closeButton)

        rootLayout.addView(cardLayout)
        return rootLayout
    }

    private fun dpToPx(dp: Int): Int {
        val density = context.resources.displayMetrics.density
        return (dp * density).toInt()
    }

    companion object {
        private const val TAG = "FrankOverlayManager"
    }
}
