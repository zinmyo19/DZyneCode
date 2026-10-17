package com.dzynecode.app

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class SettingsAboutTest {
    private lateinit var activity: Activity
    @Before fun setup() {
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.getSharedPreferences("test", Context.MODE_PRIVATE).edit().clear().commit()
    }
    private fun all(root: View): List<View> = listOf(root) +
        if (root is ViewGroup) (0 until root.childCount).flatMap { all(root.getChildAt(it)) } else emptyList()
    private fun named(root: View, title: String): View = all(root).first { it is TextView && it.text.toString() == title }

    @Test fun settingsPersistAndNotify() {
        val prefs = activity.getSharedPreferences("test", Context.MODE_PRIVATE)
        var changed = 0
        val page = SettingsPage(activity, prefs) { changed++ }.build({}, {})
        val controls = all(page).filterIsInstance<Switch>()
        assertEquals(5, controls.size)
        val lines = controls.first { it.contentDescription == "Line numbers" }
        assertTrue(lines.isChecked)
        lines.isChecked = false
        assertFalse(prefs.getBoolean("line_numbers", true))
        assertEquals(1, changed)
        val recreated = SettingsPage(activity, prefs) {}.build({}, {})
        assertFalse(all(recreated).filterIsInstance<Switch>().first { it.contentDescription == "Line numbers" }.isChecked)
        val awake = controls.first { it.contentDescription == "Keep screen awake" }
        assertFalse(awake.isChecked)
        awake.isChecked = true
        assertTrue(prefs.getBoolean("keep_awake", false))
        assertEquals(2, all(page).filterIsInstance<SeekBar>().size)
    }
    @Test fun navigationCallbacksWork() {
        val prefs = activity.getSharedPreferences("test", Context.MODE_PRIVATE)
        var back = false; var about = false
        val settings = SettingsPage(activity, prefs) {}.build({ back = true }, { about = true })
        named(settings, "‹  Back to workspace").performClick()
        assertTrue(back)
        all(settings).first { it.contentDescription == "About DZyne Code and contact links" }.performClick()
        assertTrue(about)
        var backToSettings = false
        val page = AboutPage(activity).build { backToSettings = true }
        named(page, "‹  BACK TO SETTINGS").performClick()
        assertTrue(backToSettings)
    }
    @Test fun allFourCardsOpenExactUserUrlsAndCopy() {
        val page = AboutPage(activity).build {}
        val urls = linkedMapOf(
            "Telegram Bot" to "https://t.me/Dominic_aiBot",
            "Telegram Group" to "https://t.me/+7I8oPYJp-fE3ZTY1",
            "GitHub" to "https://github.com/zinmyo19",
            "Website" to "https://dzinlabs-site.zynelabs.workers.dev/"
        )
        for ((name, url) in urls) {
            val card = all(page).first { it.contentDescription?.toString()?.startsWith("$name,") == true }
            card.performClick()
            val intent = shadowOf(activity).nextStartedActivity
            assertEquals(Intent.ACTION_VIEW, intent.action)
            assertEquals(url, intent.data.toString())
            all(page).first { it.contentDescription == "Copy $name link" }.performClick()
            val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            assertEquals(url, clipboard.primaryClip!!.getItemAt(0).text.toString())
        }
        assertNotNull(named(page, "zynelabs.is-a.dev"))
        assertNotNull(named(page, "Made by DZyneLabs"))
    }
    @Test fun screensMeasureAtNarrowPhoneWidth() {
        val prefs = activity.getSharedPreferences("test", Context.MODE_PRIVATE)
        val pages = listOf(SettingsPage(activity, prefs) {}.build({}, {}), AboutPage(activity).build {})
        for (page in pages) {
            page.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY))
            page.layout(0, 0, 320, 640)
            assertEquals(320, page.measuredWidth)
            assertTrue((page as ViewGroup).getChildAt(0).measuredHeight > 640)
        }
    }
}
