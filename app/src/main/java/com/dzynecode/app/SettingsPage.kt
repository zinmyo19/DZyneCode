package com.dzynecode.app

import android.app.Activity
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*

/** App-private preferences; changes apply immediately and survive relaunch. */
class SettingsPage(
    private val activity: Activity,
    private val prefs: SharedPreferences,
    private val onChanged: () -> Unit
) {
    private val bg = Color.rgb(16, 20, 23)
    private val panel = Color.rgb(23, 30, 35)
    private val panel2 = Color.rgb(31, 40, 46)
    private val mint = Color.rgb(114, 227, 177)
    private val white = Color.rgb(237, 244, 245)
    private val muted = Color.rgb(151, 166, 173)
    private fun dp(n: Int) = (n * activity.resources.displayMetrics.density + .5f).toInt()
    private fun text(value: String, size: Float = 14f, color: Int = white, bold: Boolean = false) = TextView(activity).apply {
        text = value; textSize = size; setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }
    private fun rounded(fill: Int) = GradientDrawable().apply {
        setColor(fill); cornerRadius = dp(16).toFloat(); setStroke(dp(1), panel2)
    }
    private fun card() = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(panel)
        setPadding(dp(16), dp(10), dp(16), dp(10))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
    }
    private fun section(list: LinearLayout, title: String) {
        list.addView(text(title, 11f, mint, true).apply { setPadding(dp(4), dp(14), 0, dp(10)) })
    }
    private fun toggle(group: LinearLayout, title: String, detail: String, key: String, default: Boolean) {
        val row = LinearLayout(activity).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, dp(10))
            minimumHeight = dp(64)
        }
        val labels = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        labels.addView(text(title, 14f, white, true))
        labels.addView(text(detail, 11f, muted).apply { setPadding(0, dp(4), dp(8), 0) })
        row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        val control = Switch(activity).apply {
            contentDescription = title
            isChecked = prefs.getBoolean(key, default)
            minimumWidth = dp(48); minimumHeight = dp(48)
            thumbTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(mint, muted))
            trackTintList = ColorStateList.valueOf(panel2)
            setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean(key, checked).apply(); onChanged() }
        }
        row.addView(control)
        row.setOnClickListener { control.isChecked = !control.isChecked }
        group.addView(row)
    }
    private fun size(group: LinearLayout, title: String, key: String, default: Int, min: Int, max: Int) {
        val value = prefs.getInt(key, default).coerceIn(min, max)
        val heading = text("$title · $value sp", 14f, white, true).apply { setPadding(0, dp(12), 0, dp(4)) }
        group.addView(heading)
        group.addView(SeekBar(activity).apply {
            contentDescription = title
            minimumHeight = dp(48)
            this.max = max - min
            progress = value - min
            progressTintList = ColorStateList.valueOf(mint)
            thumbTintList = ColorStateList.valueOf(mint)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    val selected = progress + min
                    heading.text = "$title · $selected sp"
                    prefs.edit().putInt(key, selected).apply()
                    onChanged()
                }
            })
        }, LinearLayout.LayoutParams(-1, dp(48)))
    }
    fun build(onBack: () -> Unit, onAbout: () -> Unit, onAi: () -> Unit = {}): View {
        val list = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), dp(6), dp(2), dp(24))
        }
        list.addView(text("‹  Back to workspace", 13f, mint, true).apply {
            gravity = Gravity.CENTER_VERTICAL; minimumHeight = dp(48)
            setPadding(dp(4), 0, dp(4), 0); setOnClickListener { onBack() }
        })
        list.addView(text("Settings", 27f, white, true))
        list.addView(text("Make this workspace yours.", 12f, muted).apply { setPadding(0, dp(5), 0, dp(12)) })
        section(list, "APPEARANCE")
        list.addView(card().apply {
            addView(text("MIDNIGHT / MINT", 12f, mint, true).apply { setPadding(0, dp(7), 0, dp(5)) })
            addView(text("A focused dark workspace with soft mint accents.", 12f, muted).apply { setPadding(0, 0, 0, dp(7)) })
        })
        section(list, "EDITOR")
        list.addView(card().apply {
            size(this, "Editor font size", "editor_font", 14, 12, 24)
            toggle(this, "Line numbers", "Show the line-number gutter", "line_numbers", true)
            toggle(this, "Syntax highlighting", "Color Python keywords, strings and comments", "syntax_highlight", true)
            toggle(this, "Word wrap", "Fit long lines to the editor width", "word_wrap", false)
        })
        section(list, "CONSOLE & WORKSPACE")
        list.addView(card().apply {
            size(this, "Console font size", "console_font", 13, 11, 22)
            toggle(this, "Auto-scroll output", "Follow new console output", "auto_scroll", true)
            toggle(this, "Keep screen awake", "Keep the display on while this app is open", "keep_awake", false)
        })
        section(list, "AI FIX")
        list.addView(card().apply {
            minimumHeight = dp(76)
            addView(text("AI connections & agents  ›", 16f, white, true))
            addView(text("OpenCode • OpenAI-compatible services • custom servers", 12f, muted))
            contentDescription = "AI connections and agents"
            setOnClickListener { onAi() }
        })
        section(list, "ABOUT")
        list.addView(card().apply {
            minimumHeight = dp(76)
            gravity = Gravity.CENTER_VERTICAL
            addView(text("About DZyne Code  ›", 16f, white, true))
            addView(text("App info, DZyneLabs & contact links", 12f, muted).apply { setPadding(0, dp(6), 0, 0) })
            contentDescription = "About DZyne Code and contact links"
            isFocusable = true
            setOnClickListener { onAbout() }
        })
        list.addView(text("Changes are saved automatically on this device.", 11f, muted).apply {
            gravity = Gravity.CENTER; setPadding(dp(8), dp(12), dp(8), dp(12))
        })
        return ScrollView(activity).apply { setBackgroundColor(bg); isFillViewport = true; addView(list) }
    }
}
