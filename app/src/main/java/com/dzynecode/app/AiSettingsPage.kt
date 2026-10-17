package com.dzynecode.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.text.InputType
import android.util.Base64
import android.view.View
import android.widget.*
import java.security.KeyStore
import java.util.concurrent.Executors
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Keys stay on this device, encrypted with an Android Keystore key. */
class AiConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)
    var mode: String
        get() = prefs.getString("mode", "opencode")!!
        set(value) { prefs.edit().putString("mode", value).apply() }
    fun value(key: String, default: String = "") = prefs.getString(key, default) ?: default
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("dzyne_ai_credentials", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("dzyne_ai_credentials", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun secret(name: String): String {
        val saved = value(name)
        if (saved.isEmpty()) return ""
        return try {
            val pieces = saved.split(":")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(pieces[0], Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(pieces[1], Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (_: Exception) { throw IllegalStateException("Saved credential is unavailable. Re-enter it in AI settings.") }
    }
    fun save(values: Map<String, String>, secrets: Map<String, String>) {
        val encrypted = secrets.mapValues { (_, plain) ->
            if (plain.isEmpty()) "" else {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, key())
                Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(cipher.doFinal(plain.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
            }
        }
        prefs.edit().apply { (values + encrypted).forEach { (k, v) -> putString(k, v) } }.apply()
    }
    fun openCode() = OpenCodeConfig(value("oc_url"), value("oc_user", "opencode"), secret("oc_password"), value("oc_agent"), value("oc_model"))
    fun compatible() = CompatibleAiConfig(value("api_url"), secret("api_key"), value("api_model"))
}

class AiSettingsPage(private val activity: Activity) {
    private val store = AiConfigStore(activity)
    private val mint = Color.rgb(114, 227, 177)
    private val white = Color.rgb(237, 244, 245)
    private val muted = Color.rgb(151, 166, 173)
    private fun dp(v: Int) = (v * activity.resources.displayMetrics.density).toInt()
    private fun text(s: String, size: Float = 13f) = TextView(activity).apply { text = s; textSize = size; setTextColor(white); setPadding(0, dp(8), 0, dp(8)) }
    private fun field(box: LinearLayout, title: String, initial: String, secret: Boolean = false): EditText {
        box.addView(text(title))
        return EditText(activity).apply {
            contentDescription = title; setTextColor(white); setHintTextColor(muted); textSize = 14f
            setSingleLine(true); inputType = if (secret) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setText(initial); minimumHeight = dp(48); box.addView(this, LinearLayout.LayoutParams(-1, -2))
        }
    }
    fun build(onBack: () -> Unit): View {
        val list = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(8), dp(8), dp(28)) }
        list.addView(text("‹  Back to Settings").apply { setTextColor(mint); minHeight = dp(48); setOnClickListener { onBack() } })
        list.addView(text("AI Fix connections", 25f))
        list.addView(text("Choose OpenCode agents or an OpenAI-compatible service. AI Fix sends your current code only after you tap Send. Console output is optional. Review before applying; nothing runs automatically."))
        val mode = Spinner(activity)
        mode.adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, listOf("OpenCode agents", "OpenAI-compatible API"))
        mode.setSelection(if (store.mode == "compatible") 1 else 0)
        list.addView(mode, LinearLayout.LayoutParams(-1, dp(52)))
        val oc = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val api = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        list.addView(oc); list.addView(api)
        val ocUrl = field(oc, "OpenCode server URL", store.value("oc_url"))
        ocUrl.hint = "https://your-server.example.com"
        val user = field(oc, "Basic auth username", store.value("oc_user", "opencode"))
        fun readSecret(name: String) = try { store.secret(name) } catch (_: Exception) { "" }
        val password = field(oc, "Basic auth password (optional)", readSecret("oc_password"), true)
        val agent = field(oc, "Agent name (blank = server default)", store.value("oc_agent"))
        val model = field(oc, "Model override (optional: provider/model)", store.value("oc_model"))
        val result = text("")
        fun currentOc() = OpenCodeConfig(ocUrl.text.toString(), user.text.toString(), password.text.toString(), agent.text.toString().trim(), model.text.toString().trim())
        val fetch = Button(activity).apply {
            text = "Test connection & choose agent"
            setOnClickListener {
                isEnabled = false; result.text = "Connecting…"
                val config = currentOc()
                val worker = Executors.newSingleThreadExecutor()
                worker.execute {
                    try {
                        val client = OpenCodeClient(config)
                        val version = client.health()
                        val agents = client.agents()
                        activity.runOnUiThread {
                            if (!activity.isDestroyed) {
                                result.text = "Connected · $version · ${agents.size} agents"
                                if (agents.isNotEmpty()) AlertDialog.Builder(activity).setTitle("Choose server agent")
                                    .setItems((listOf("Server default") + agents).toTypedArray()) { _, i -> agent.setText(if (i == 0) "" else agents[i - 1]) }.setNegativeButton("Cancel", null).show()
                            }
                        }
                    } catch (e: Exception) { activity.runOnUiThread { if (!activity.isDestroyed) result.text = e.message ?: "Connection failed" } }
                    finally { activity.runOnUiThread { isEnabled = true }; worker.shutdown() }
                }
            }
        }
        oc.addView(fetch); oc.addView(result)
        oc.addView(text("Create custom agents in OpenCode on your server, then refresh this list. Provider credentials and the default model are configured on the server. This app does not enable server tools."))
        val apiUrl = field(api, "API base URL (include /v1 when required)", store.value("api_url"))
        val apiKey = field(api, "API key (optional for local servers)", readSecret("api_key"), true)
        val apiModel = field(api, "Model ID (required)", store.value("api_model"))
        api.addView(Button(activity).apply {
            text = "Choose URL preset"
            setOnClickListener {
                val names = arrayOf("OpenAI", "Google AI Studio / Gemini", "DeepSeek", "Groq", "Ollama (change LAN IP)")
                val urls = arrayOf("https://api.openai.com/v1", "https://generativelanguage.googleapis.com/v1beta/openai/", "https://api.deepseek.com", "https://api.groq.com/openai/v1", "http://YOUR-LAN-IP:11434/v1")
                val models = arrayOf("", "gemini-3.8-flash", "", "", "")
                AlertDialog.Builder(activity).setTitle("OpenAI-compatible URL presets").setItems(names) { _, i ->
                    apiUrl.setText(urls[i]); apiKey.setText(""); apiModel.setText(models[i])
                }.show()
            }
        })
        api.addView(text("Google AI Studio / Gemini works with its official OpenAI-compatible endpoint. Select its preset, enter your Google AI Studio API key, and use a Gemini model available to your key. The suggested model ID is editable. Use a model ID offered by your provider; other compatible servers can use a custom URL. Native Claude APIs require an OpenCode provider or compatible gateway."))
        mode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                oc.visibility = if (position == 0) View.VISIBLE else View.GONE
                api.visibility = if (position == 1) View.VISIBLE else View.GONE
            }
        }
        oc.visibility = if (mode.selectedItemPosition == 0) View.VISIBLE else View.GONE
        api.visibility = if (mode.selectedItemPosition == 1) View.VISIBLE else View.GONE
        list.addView(text("Use HTTPS for remote servers. Plain HTTP sends code and credentials unencrypted; only use it on a trusted local network. localhost on this phone is not your computer. Credentials are encrypted on this device."))
        list.addView(Button(activity).apply {
            text = "Save AI settings"
            setOnClickListener {
                try {
                    val selectedOc = mode.selectedItemPosition == 0
                    val url = OpenCodeClient.normalizeUrl(if (selectedOc) ocUrl.text.toString() else apiUrl.text.toString())
                    if (!selectedOc && apiModel.text.isBlank()) throw IllegalArgumentException("Enter a model ID")
                    if (selectedOc && model.text.isNotBlank() && !model.text.contains('/')) throw IllegalArgumentException("Use provider/model for the model override")
                    fun save() {
                        try {
                            store.save(mapOf("mode" to if (selectedOc) "opencode" else "compatible", "oc_url" to if (selectedOc) url else ocUrl.text.toString().trim(), "oc_user" to user.text.toString(), "oc_agent" to agent.text.toString().trim(), "oc_model" to model.text.toString().trim(), "api_url" to if (!selectedOc) url else apiUrl.text.toString().trim(), "api_model" to apiModel.text.toString().trim()), mapOf("oc_password" to password.text.toString(), "api_key" to apiKey.text.toString()))
                            Toast.makeText(activity, "AI settings saved", Toast.LENGTH_SHORT).show()
                        } catch (_: Exception) { Toast.makeText(activity, "Cannot securely save credentials on this device", Toast.LENGTH_LONG).show() }
                    }
                    if (url.startsWith("http://")) AlertDialog.Builder(activity).setTitle("Unencrypted HTTP connection")
                        .setMessage("Code and credentials will travel unencrypted. Use only a trusted local network.")
                        .setPositiveButton("Save anyway") { _, _ -> save() }.setNegativeButton("Cancel", null).show()
                    else save()
                } catch (e: Exception) { Toast.makeText(activity, e.message ?: "Check AI settings", Toast.LENGTH_LONG).show() }
            }
        })
        list.addView(text("Save after selecting an agent. A successful connection test does not mean a model/provider is ready; AI Fix reports provider errors when sending."))
        return ScrollView(activity).apply { setBackgroundColor(Color.rgb(16, 20, 23)); addView(list) }
    }
}
