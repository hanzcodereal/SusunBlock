package id.hanzdev.susunblock

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import id.hanzdev.susunblock.game.Themes
import id.hanzdev.susunblock.ui.LogoView
import id.hanzdev.susunblock.ui.UiKit

/** Layar pembuka: logo, nama aplikasi, indikator memuat, dan kredit developer. */
class SplashActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private var started = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val theme = Themes.all[0]

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(theme.background)
            setPadding(0, 0, 0, UiKit.dp(this@SplashActivity, 28f))
        }

        fun spacer() = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(1, 0, 1f)
        }

        val logoSize = UiKit.dp(this, 160f)
        val logo = LogoView(this)
        val title = UiKit.text(this, "Susun Block", 38f, theme.primaryText, bold = true)
        val subtitle = UiKit.text(this, "Game puzzle susun blok", 15f, theme.tertiaryText)

        val version = UiKit.text(this, "Versi ${versionName()}", 13f, theme.tertiaryText)

        root.addView(spacer())
        root.addView(
            logo,
            LinearLayout.LayoutParams(logoSize, logoSize).apply { gravity = Gravity.CENTER_HORIZONTAL }
        )
        root.addView(title, UiKit.params(this, 20f))
        root.addView(subtitle, UiKit.params(this, 4f))
        root.addView(spacer())
        root.addView(version, UiKit.params(this, 0f))

        setContentView(root)

        // Animasi masuk: logo membesar dengan pantulan, teks muncul perlahan
        logo.scaleX = 0.7f
        logo.scaleY = 0.7f
        logo.alpha = 0f
        logo.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(650)
            .setInterpolator(OvershootInterpolator(1.2f)).start()
        for ((i, v) in listOf<View>(title, subtitle, version).withIndex()) {
            v.alpha = 0f
            v.animate().alpha(1f).setStartDelay(250L + i * 90L).setDuration(500).start()
        }

        handler.postDelayed({ openMenu() }, SPLASH_MS)
    }

    private fun versionName(): String = try {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0.0"
    } catch (e: Exception) {
        "1.0.0"
    }

    @Suppress("DEPRECATION")
    private fun openMenu() {
        if (started || isFinishing) return
        started = true
        startActivity(Intent(this, MainActivity::class.java))
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private companion object {
        const val SPLASH_MS = 2200L
    }
}
