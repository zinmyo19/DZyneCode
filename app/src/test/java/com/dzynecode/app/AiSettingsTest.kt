package com.dzynecode.app

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class AiSettingsTest {
    private lateinit var activity: Activity
    private fun all(view: View): List<View> = listOf(view) + if (view is ViewGroup) (0 until view.childCount).flatMap { all(view.getChildAt(it)) } else emptyList()
    @Before fun setup() {
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.getSharedPreferences("ai_settings", Context.MODE_PRIVATE).edit().clear().commit()
    }
    @Test fun settingsAiNavigationCallback() {
        var opened = false
        val settings = SettingsPage(activity, activity.getSharedPreferences("test", Context.MODE_PRIVATE)) {}.build({}, {}, { opened = true })
        all(settings).first { it.contentDescription == "AI connections and agents" }.performClick()
        assertTrue(opened)
    }
    @Test fun hasBothConnectionTypesAndNoHardcodedCredentials() {
        val page = AiSettingsPage(activity).build {}
        val inputs = all(page).filterIsInstance<EditText>()
        assertEquals("", inputs.first { it.contentDescription == "OpenCode server URL" }.text.toString())
        assertEquals("opencode", inputs.first { it.contentDescription == "Basic auth username" }.text.toString())
        assertEquals("", inputs.first { it.contentDescription == "API key (optional for local servers)" }.text.toString())
        val selector = all(page).filterIsInstance<Spinner>().single()
        assertEquals(2, selector.count)
        val help = all(page).filterIsInstance<TextView>().joinToString("\n") { it.text.toString() }
        assertTrue(help.contains("Google AI Studio / Gemini"))
        assertTrue(help.contains("official OpenAI-compatible endpoint"))
    }
    @Test fun compatibleSettingsSaveWithoutKeyForLocalServicesAndPersist() {
        val prefs = activity.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)
        prefs.edit().putString("mode", "compatible").commit()
        val page = AiSettingsPage(activity).build {}
        val inputs = all(page).filterIsInstance<EditText>()
        inputs.first { it.contentDescription == "API base URL (include /v1 when required)" }.setText("https://example.com/v1/")
        inputs.first { it.contentDescription == "Model ID (required)" }.setText("my-model")
        all(page).filterIsInstance<TextView>().first { it.text.toString() == "Save AI settings" }.performClick()
        assertEquals("https://example.com/v1", prefs.getString("api_url", ""))
        assertEquals("my-model", prefs.getString("api_model", ""))
        assertEquals("compatible", prefs.getString("mode", ""))
        assertEquals("", AiConfigStore(activity).secret("api_key"))
    }
    @Test fun geminiPresetSetsOfficialEndpointEditableDefaultAndClearsCredentials() {
        activity.getSharedPreferences("ai_settings", Context.MODE_PRIVATE).edit()
            .putString("mode", "compatible").commit()
        val page = AiSettingsPage(activity).build {}
        val inputs = all(page).filterIsInstance<EditText>()
        val apiUrl = inputs.first { it.contentDescription == "API base URL (include /v1 when required)" }
        val apiKey = inputs.first { it.contentDescription == "API key (optional for local servers)" }
        val apiModel = inputs.first { it.contentDescription == "Model ID (required)" }
        apiUrl.setText("https://old.example/v1/")
        apiKey.setText("must-be-cleared")
        apiModel.setText("old-model")

        all(page).filterIsInstance<TextView>().first { it.text.toString() == "Choose URL preset" }.performClick()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        val list = dialog.listView
        assertEquals("OpenAI-compatible URL presets", org.robolectric.Shadows.shadowOf(dialog).title)
        assertEquals("Google AI Studio / Gemini", list.adapter.getItem(1))
        list.performItemClick(list.getChildAt(1), 1, list.adapter.getItemId(1))

        assertEquals("https://generativelanguage.googleapis.com/v1beta/openai/", apiUrl.text.toString())
        assertEquals("gemini-3.8-flash", apiModel.text.toString())
        assertEquals("", apiKey.text.toString())
        apiModel.setText("gemini-custom-model")
        assertEquals("gemini-custom-model", apiModel.text.toString())
    }
    @Test fun geminiPresetSavesNormalizedUrlAndDefaultModelWithoutKey() {
        val prefs = activity.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)
        prefs.edit().putString("mode", "compatible").commit()
        val page = AiSettingsPage(activity).build {}
        all(page).filterIsInstance<TextView>().first { it.text.toString() == "Choose URL preset" }.performClick()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        val list = dialog.listView
        list.performItemClick(list.getChildAt(1), 1, list.adapter.getItemId(1))
        all(page).filterIsInstance<TextView>().first { it.text.toString() == "Save AI settings" }.performClick()

        assertEquals("https://generativelanguage.googleapis.com/v1beta/openai", prefs.getString("api_url", ""))
        assertEquals("gemini-3.8-flash", prefs.getString("api_model", ""))
        assertEquals("", AiConfigStore(activity).secret("api_key"))
    }
    @Test fun nonGeminiPresetsKeepModelAndKeyBlank() {
        activity.getSharedPreferences("ai_settings", Context.MODE_PRIVATE).edit()
            .putString("mode", "compatible").commit()
        val page = AiSettingsPage(activity).build {}
        val inputs = all(page).filterIsInstance<EditText>()
        val apiKey = inputs.first { it.contentDescription == "API key (optional for local servers)" }
        val apiModel = inputs.first { it.contentDescription == "Model ID (required)" }
        for (index in listOf(0, 2, 3, 4)) {
            apiKey.setText("old-key")
            apiModel.setText("old-model")
            all(page).filterIsInstance<TextView>().first { it.text.toString() == "Choose URL preset" }.performClick()
            val dialog = ShadowAlertDialog.getLatestAlertDialog()
            val list = dialog.listView
            list.performItemClick(list.getChildAt(index), index, list.adapter.getItemId(index))
            assertEquals("", apiModel.text.toString())
            assertEquals("", apiKey.text.toString())
        }
    }
    @Test fun backWorksAndPageFitsNarrowPhone() {
        var back = false
        val page = AiSettingsPage(activity).build { back = true }
        all(page).filterIsInstance<TextView>().first { it.text.toString() == "‹  Back to Settings" }.performClick()
        assertTrue(back)
        page.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY))
        page.layout(0, 0, 320, 640)
        assertEquals(320, page.measuredWidth)
        assertTrue((page as ViewGroup).getChildAt(0).measuredHeight > 640)
    }
}
