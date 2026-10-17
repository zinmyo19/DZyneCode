package com.dzynecode.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Builds the app's self-contained About screen without external UI dependencies. */
class AboutPage(private val activity: Activity) {
    private val bg = Color.rgb(16, 20, 23)
    private val panel = Color.rgb(23, 30, 35)
    private val panel2 = Color.rgb(31, 40, 46)
    private val mint = Color.rgb(114, 227, 177)
    private val white = Color.rgb(237, 244, 245)
    private val muted = Color.rgb(151, 166, 173)

    fun build(onBack: () -> Unit): View {
        val scroll = ScrollView(activity).apply {
            setBackgroundColor(bg)
            isFillViewport = true
            contentDescription = "About DZyne Code"
        }
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(28))
        }
        scroll.addView(content, ViewGroup.LayoutParams(-1, -2))

        val back = TextView(activity).apply {
            text = "‹  BACK TO SETTINGS"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(mint)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), 0, dp(10), 0)
            minHeight = dp(48)
            contentDescription = "Back to settings"
            setOnClickListener { onBack() }
        }
        content.addView(back, LinearLayout.LayoutParams(-1, dp(52)))

        val hero = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(22))
            background = rounded(panel, 20)
            contentDescription = "DZyne Code app information"
        }
        val mark = TextView(activity).apply {
            text = "</>"
            textSize = 27f
            typeface = Typeface.MONOSPACE
            setTextColor(mint)
            setPadding(0, 0, 0, dp(4))
        }
        hero.addView(mark, LinearLayout.LayoutParams(-1, -2))
        hero.addView(text("DZYNE CODE", 25f, white, true))
        hero.addView(text("PYTHON WORKSPACE  •  CPYTHON 3.11", 11f, mint, true).apply {
            setPadding(0, dp(7), 0, 0)
        })
        val version = try {
            @Suppress("DEPRECATION")
            activity.packageManager.getPackageInfo(activity.packageName, 0).versionName ?: "Unknown"
        } catch (_: Exception) {
            "Unknown"
        }
        hero.addView(text("Version $version", 12f, muted, false).apply {
            setPadding(0, dp(9), 0, 0)
        })
        hero.addView(text(
            "A focused mobile Python workspace for writing and running scripts with CPython 3.11 on your device.",
            15f, white, false
        ).apply {
            setPadding(0, dp(16), 0, 0)
            setLineSpacing(0f, 1.12f)
        })
        content.addView(hero, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(24) })

        content.addView(text("WHAT'S INSIDE", 12f, muted, true).apply {
            setPadding(dp(4), 0, 0, dp(10))
        })
        val features = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(panel, 16)
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        listOf(
            "Write and edit Python scripts on mobile",
            "Run code locally with an on-device console",
            "Preview web output and manage workspace files"
        ).forEach { feature ->
            features.addView(text("•  $feature", 14f, white, false).apply {
                setPadding(0, dp(7), 0, dp(7))
                setLineSpacing(0f, 1.1f)
            })
        }
        content.addView(features, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(24) })

        content.addView(text("CONTACT DZYNELABS", 12f, muted, true).apply {
            setPadding(dp(4), 0, 0, dp(10))
        })
        addContact(content, "Telegram Bot", "@Dominic_aiBot", "https://t.me/Dominic_aiBot")
        addContact(content, "Telegram Group", "ZyneLabs updates", "https://t.me/+7I8oPYJp-fE3ZTY1")
        addContact(content, "GitHub", "zinmyo19", "https://github.com/zinmyo19")
        addContact(content, "Website", "zynelabs.is-a.dev", "https://dzinlabs-site.zynelabs.workers.dev/")

        content.addView(text("Made by DZyneLabs", 13f, muted, false).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(22), 0, 0)
        }, LinearLayout.LayoutParams(-1, -2))
        return scroll
    }

    private fun addContact(parent: LinearLayout, title: String, label: String, url: String) {
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(9), dp(8), dp(9))
            background = rounded(panel2, 16)
            isClickable = true
            isFocusable = true
            minimumHeight = dp(72)
            contentDescription = "$title, $label. Opens external link."
            setOnClickListener { openUrl(url) }
        }
        val copy = TextView(activity).apply {
            text = "COPY"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(mint)
            gravity = Gravity.CENTER
            setPadding(dp(10), 0, dp(10), 0)
            minHeight = dp(48)
            minWidth = dp(64)
            contentDescription = "Copy $title link"
            setOnClickListener {
                copyLink(url)
                it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            }
        }
        val details = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        details.addView(text(title, 15f, white, true))
        details.addView(text(label, 13f, muted, false).apply {
            setPadding(0, dp(3), 0, 0)
            setLineSpacing(0f, 1.08f)
        })
        card.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(copy, LinearLayout.LayoutParams(dp(72), -1))
        parent.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
    }

    private fun openUrl(url: String) {
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(activity, "No app is available to open this link.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyLink(url: String) {
        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Link", url))
        Toast.makeText(activity, "Link copied", Toast.LENGTH_SHORT).show()
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean): TextView = TextView(activity).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        setLineSpacing(0f, 1.05f)
    }

    private fun rounded(color: Int, radius: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density + 0.5f).toInt()
}
