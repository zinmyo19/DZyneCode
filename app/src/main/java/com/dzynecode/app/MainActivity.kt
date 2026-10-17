package com.dzynecode.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

class MainActivity : Activity() {
    private val bg = Color.rgb(16, 20, 23)
    private val panel = Color.rgb(23, 30, 35)
    private val panel2 = Color.rgb(31, 40, 46)
    private val mint = Color.rgb(114, 227, 177)
    private val white = Color.rgb(237, 244, 245)
    private val muted = Color.rgb(151, 166, 173)
    private val red = Color.rgb(255, 133, 133)

    private lateinit var workspace: File
    private lateinit var root: LinearLayout
    private lateinit var body: FrameLayout
    private lateinit var statusText: TextView
    private lateinit var headerPill: TextView
    private lateinit var drawerPanel: ViewGroup
    private lateinit var drawerDim: View
    private var drawerOpen = false
    private val tabLabels = mutableListOf<TextView>()
    private val tabLines = mutableListOf<View>()
    private val tabIds = mutableListOf<Int>()
    private lateinit var gutter: TextView
    private lateinit var editorWrap: LinearLayout
    private val consoleSpans = SpannableStringBuilder()
    private val highlightHandler = Handler(Looper.getMainLooper())
    private var highlightRunnable: Runnable? = null
    private var highlighting = false
    private lateinit var editor: EditText
    private lateinit var consoleView: TextView
    private lateinit var consoleScroll: ScrollView
    private lateinit var inputField: EditText
    private var currentTab = TAB_EDITOR
    private var currentUri: Uri? = null
    private var currentScriptName = "main.py"
    private var pendingAnswer: ((String) -> Unit)? = null
    private var runTask: Future<*>? = null
    @Volatile private var isRunning = false
    private val executor = Executors.newSingleThreadExecutor()
    private val outputLog = StringBuilder()
    private val localScriptFile by lazy { File(workspace, "main.py") }
    private val preferences by lazy { getSharedPreferences("workspace_settings", MODE_PRIVATE) }
    private var lastWorkspaceTab = TAB_EDITOR
    private val aiExecutor = Executors.newSingleThreadExecutor()
    private var aiBusy = false
    private val aiBackup by lazy { File(workspace, "before_ai_fix.py") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility = 0
        workspace = File(filesDir, "dzyne_workspace").apply { mkdirs() }
        if (!Python.isStarted()) Python.start(AndroidPlatform(this))
        buildShell()
        val remembered = File(workspace, "main.py")
        editor.setText(if (remembered.exists()) remembered.readText() else STARTER)
        applyPreferences()
        renderTab(TAB_EDITOR)
    }

    private fun buildShell() {
        // Root is the content column; a FrameLayout wraps it so the left
        // drawer can overlay the content.
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(16), dp(10), dp(16), dp(8))
        }
        // ---- header: burger + brand + status pill ----
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(10))
        }
        val burger = TextView(this).apply {
            text = "\u2630"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(white)
            setBackgroundColor(panel)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setOnClickListener { toggleDrawer() }
        }
        header.addView(burger, LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(10) })
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        brand.addView(label("DZYNE CODE", 20f, white, true))
        brand.addView(label("PYTHON WORKSPACE  \u2022  CPYTHON 3.11", 10f, muted, true))
        header.addView(brand, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        headerPill = TextView(this).apply {
            text = "\u25cf Ready"
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(7), dp(12), dp(7))
        }
        setPill(false)
        header.addView(headerPill)
        root.addView(header)

        // ---- tabs: label + mint underline on the active tab ----
        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(panel)
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        listOf("EDITOR" to TAB_EDITOR, "CONSOLE" to TAB_CONSOLE, "PREVIEW" to TAB_PREVIEW, "LIBRARIES" to TAB_LIBRARIES).forEach { (title, tab) ->
            val tabLabel = TextView(this).apply {
                text = title
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(9), dp(4), dp(7))
                setTextColor(muted)
            }
            val underline = View(this).apply { setBackgroundColor(Color.TRANSPARENT) }
            val tabBox = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setOnClickListener { renderTab(tab) }
            }
            tabBox.addView(tabLabel, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            tabBox.addView(underline, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(2)))
            tabLabels.add(tabLabel)
            tabLines.add(underline)
            tabIds.add(tab)
            tabs.addView(tabBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        root.addView(tabs)
        statusText = label("Ready \u2022 Python runs on this device", 11f, muted, false).apply {
            setPadding(dp(2), dp(9), dp(2), dp(7))
        }
        root.addView(statusText)
        body = FrameLayout(this)
        root.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // ---- left drawer: vertical actions, hidden until the burger is tapped ----
        drawerDim = View(this).apply {
            setBackgroundColor(Color.argb(115, 0, 0, 0))
            visibility = View.GONE
            setOnClickListener { closeDrawer() }
        }
        drawerPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(panel)
            setPadding(dp(10), dp(12), dp(10), dp(12))
            visibility = View.GONE
        }
        drawerPanel.addView(label("MENU", 10f, muted, true).apply { setPadding(dp(6), dp(4), dp(6), dp(10)) })
        drawerPanel.addView(drawerButton("\u25b6 RUN", { closeDrawer(); startRun() }, mint, bg))
        drawerPanel.addView(drawerButton("AI FIX", { closeDrawer(); requestAiFix() }, panel2, mint))
        drawerPanel.addView(drawerButton("UNDO AI FIX", { closeDrawer(); undoAiFix() }))
        drawerPanel.addView(drawerButton("OPEN", { closeDrawer(); openScript() }))
        drawerPanel.addView(drawerButton("SAVE", { closeDrawer(); saveScript() }))
        drawerPanel.addView(drawerButton("STOP", { closeDrawer(); stopRun() }, panel2, red))
        drawerPanel.addView(drawerButton("OUT", { closeDrawer(); exportOutput() }))
        drawerPanel.addView(View(this).apply { setBackgroundColor(panel2) }, LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(12); bottomMargin = dp(12) })
        drawerPanel.addView(drawerButton("SETTINGS", { closeDrawer(); renderTab(TAB_SETTINGS) }))
        drawerPanel.addView(drawerButton("ABOUT / CONTACT", { closeDrawer(); renderTab(TAB_ABOUT) }))
        drawerPanel.addView(label("Made by DZyneLabs", 10f, muted, false).apply { setPadding(dp(6), dp(16), dp(6), 0) })
        val drawerContent = drawerPanel.apply { visibility = View.VISIBLE }
        drawerPanel = ScrollView(this).apply {
            setBackgroundColor(panel)
            addView(drawerContent, ViewGroup.LayoutParams(-1, -2))
            visibility = View.GONE
        }

        val frame = FrameLayout(this).apply {
            setBackgroundColor(bg)
            addView(root, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(drawerDim, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(drawerPanel, FrameLayout.LayoutParams(dp(200), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START))
        }
        setContentView(frame)

        // ---- editor + line-number gutter ----
        gutter = TextView(this).apply {
            text = "1"
            setTextColor(Color.rgb(70, 86, 94))
            textSize = 14f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.TOP or Gravity.END
            setPadding(dp(6), dp(14), dp(6), dp(18))
            setBackgroundColor(panel)
        }
        editor = EditText(this).apply {
            setTextColor(white)
            setHintTextColor(muted)
            textSize = 14f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.TOP or Gravity.START
            setPadding(dp(14), dp(14), dp(14), dp(18))
            setBackgroundColor(panel)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            isSingleLine = false
            hint = "Write Python code here\u2026"
            setHorizontallyScrolling(true)
            setTextIsSelectable(false)
        }
        editorWrap = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(panel)
            addView(gutter, LinearLayout.LayoutParams(dp(38), ViewGroup.LayoutParams.MATCH_PARENT))
            addView(editor, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        }
        editor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateGutter()
                highlightRunnable?.let { highlightHandler.removeCallbacks(it) }
                highlightRunnable = Runnable { applyHighlight() }
                highlightHandler.postDelayed(highlightRunnable!!, 300)
            }
        })
        editor.setOnScrollChangeListener { _, _, scrollY, _, _ -> gutter.scrollY = scrollY }
        consoleView = TextView(this).apply {
            text = "DZyne Code console ready.\n"
            setTextColor(white)
            textSize = 13f
            typeface = Typeface.MONOSPACE
            setPadding(dp(13), dp(13), dp(13), dp(14))
        }
        consoleScroll = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(11, 15, 18))
            addView(consoleView)
        }
        inputField = EditText(this).apply {
            hint = "Type input() value\u2026"
            setHintTextColor(muted)
            setTextColor(white)
            textSize = 14f
            isSingleLine = true
            setPadding(dp(12), 0, dp(12), 0)
            setBackgroundColor(panel2)
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEND
            setOnEditorActionListener { _, _, _ -> sendInput(); true }
        }
    }

    private fun toggleDrawer() { if (drawerOpen) closeDrawer() else openDrawer() }

    private fun openDrawer() {
        drawerOpen = true
        drawerPanel.visibility = View.VISIBLE
        drawerDim.visibility = View.VISIBLE
    }

    private fun closeDrawer() {
        drawerOpen = false
        drawerPanel.visibility = View.GONE
        drawerDim.visibility = View.GONE
    }

    private fun setPill(running: Boolean) {
        val pillBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(20).toFloat()
            setColor(if (running) mint else panel2)
        }
        headerPill.background = pillBg
        headerPill.setTextColor(if (running) bg else muted)
        headerPill.text = if (running) "\u25cf Running" else "\u25cf Ready"
    }

    private fun updateGutter() {
        val lines = editor.text.count { it == '\n' } + 1
        gutter.text = (1..lines).joinToString("\n")
    }

    private fun applyPreferences() {
        val editorSize = preferences.getInt("editor_font", 14).coerceIn(12, 24).toFloat()
        editor.textSize = editorSize
        gutter.textSize = editorSize
        gutter.visibility = if (preferences.getBoolean("line_numbers", true)) View.VISIBLE else View.GONE
        editor.setHorizontallyScrolling(!preferences.getBoolean("word_wrap", false))
        consoleView.textSize = preferences.getInt("console_font", 13).coerceIn(11, 22).toFloat()
        if (preferences.getBoolean("keep_awake", false)) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        applyHighlight()
    }

    /** Lightweight Python syntax tint. Spans only — never touches text, so typing is unaffected. */
    private fun applyHighlight() {
        try {
            val text = editor.text
            if (highlighting) return
            highlighting = true
            val selStart = editor.selectionStart
            val selEnd = editor.selectionEnd
            for (span in text.getSpans(0, text.length, ForegroundColorSpan::class.java)) {
                text.removeSpan(span)
            }
            if (!preferences.getBoolean("syntax_highlight", true) || text.length > 20000) {
                highlighting = false
                return
            }
            var i = 0
            val n = text.length
            while (i < n) {
                val c = text[i]
                if (c == '#') {
                    var j = i
                    while (j < n && text[j] != '\n') j++
                    text.setSpan(ForegroundColorSpan(Color.rgb(95, 115, 124)), i, j, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    i = j
                } else if (c == '"' || c == '\'') {
                    val quote = c
                    var j = i + 1
                    while (j < n) {
                        if (text[j] == '\\' && j + 1 < n) { j += 2; continue }
                        if (text[j] == quote) { j++; break }
                        if (text[j] == '\n') break
                        j++
                    }
                    text.setSpan(ForegroundColorSpan(Color.rgb(242, 198, 109)), i, j, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    i = j
                } else if (c.isLetter() || c == '_') {
                    var j = i
                    while (j < n && (text[j].isLetterOrDigit() || text[j] == '_')) j++
                    val word = text.substring(i, j)
                    if (word in PY_KEYWORDS) {
                        text.setSpan(ForegroundColorSpan(mint), i, j, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    i = j
                } else i++
            }
            editor.setSelection(minOf(selStart, text.length).coerceAtLeast(0), minOf(selEnd, text.length).coerceAtLeast(0))
            highlighting = false
        } catch (_: Exception) {
            highlighting = false
        }
    }

    private fun drawerButton(text: String, click: () -> Unit, fill: Int = panel2, ink: Int = white): TextView = TextView(this).apply {
        this.text = text
        textSize = 11f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setTextColor(ink)
        setPadding(dp(10), dp(11), dp(10), dp(11))
        setBackgroundColor(fill)
        setOnClickListener { click() }
    }.also {
        it.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(6) }
    }

    private fun renderTab(tab: Int) {
        currentTab = tab
        if (tab in TAB_EDITOR..TAB_LIBRARIES) lastWorkspaceTab = tab
        (getSystemService(INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager)
            ?.hideSoftInputFromWindow(body.windowToken, 0)
        // Detach the retained editor even if its previous page was not the editor.
        (editorWrap.parent as? ViewGroup)?.removeView(editorWrap)
        for (i in tabLabels.indices) {
            val selected = (tabIds[i] == tab)
            tabLabels[i].setTextColor(if (selected) mint else muted)
            tabLines[i].setBackgroundColor(if (selected) mint else Color.TRANSPARENT)
        }
        body.removeAllViews()
        when (tab) {
            TAB_EDITOR -> {
                body.addView(editorWrap, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }
            TAB_CONSOLE -> body.addView(buildConsolePage(), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            TAB_PREVIEW -> body.addView(buildPreviewPage(), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            TAB_SETTINGS -> body.addView(SettingsPage(this, preferences, ::applyPreferences).build(
                onBack = { renderTab(lastWorkspaceTab) }, onAbout = { renderTab(TAB_ABOUT) }, onAi = { renderTab(TAB_AI_SETTINGS) }
            ), FrameLayout.LayoutParams(-1, -1))
            TAB_AI_SETTINGS -> body.addView(AiSettingsPage(this).build { renderTab(TAB_SETTINGS) }, FrameLayout.LayoutParams(-1, -1))
            TAB_ABOUT -> body.addView(AboutPage(this).build { renderTab(TAB_SETTINGS) }, FrameLayout.LayoutParams(-1, -1))
            else -> body.addView(buildLibrariesPage(), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
    }

    private fun buildConsolePage(): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        (consoleScroll.parent as? ViewGroup)?.removeView(consoleScroll)
        (inputField.parent as? ViewGroup)?.removeView(inputField)
        box.addView(consoleScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(2))
        }
        inputRow.addView(inputField, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(8) })
        inputRow.addView(actionButton("SEND", { sendInput() }, mint, bg), LinearLayout.LayoutParams(dp(76), dp(46)))
        box.addView(inputRow)
        return box
    }

    private fun buildPreviewPage(): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val note = label("Preview • writes to the app workspace appear here", 11f, muted, false)
        note.setPadding(dp(4), dp(5), dp(4), dp(9))
        box.addView(note)
        val web = WebView(this).apply {
            setBackgroundColor(Color.WHITE)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            webViewClient = WebViewClient()
        }
        val candidates = workspace.listFiles()?.filter { it.isFile && it.extension.lowercase() in setOf("html", "htm", "png", "jpg", "jpeg", "gif", "svg") }
            ?.sortedByDescending { it.lastModified() }.orEmpty()
        val preferred = candidates.firstOrNull { it.name.equals("preview.html", true) }
            ?: candidates.firstOrNull { it.name.equals("index.html", true) }
            ?: candidates.firstOrNull()
        if (preferred == null) {
            web.loadDataWithBaseURL(null, PREVIEW_HELP, "text/html", "UTF-8", null)
        } else {
            web.loadUrl(Uri.fromFile(preferred).toString())
        }
        box.addView(web, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        return box
    }

    private fun buildLibrariesPage(): View {
        val scroll = ScrollView(this).apply { setBackgroundColor(bg) }
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), dp(3), dp(2), dp(16))
        }
        list.addView(label("BUILT IN", 11f, mint, true).apply { setPadding(dp(4), dp(8), dp(4), dp(8)) })
        addPackageCard(list, "Python 3.11 standard library", "json, math, re, pathlib, sqlite3, datetime, statistics, asyncio, unittest, urllib and more")
        list.addView(label("BUNDLED PACKAGES", 11f, mint, true).apply { setPadding(dp(4), dp(13), dp(4), dp(8)) })
        addPackageCard(list, "requests", "HTTP requests • import requests")
        addPackageCard(list, "Beautiful Soup 4", "HTML/XML parsing • from bs4 import BeautifulSoup")
        addPackageCard(list, "Rich", "Terminal formatting and tables • from rich import print")
        addPackageCard(list, "SymPy", "Symbolic mathematics • import sympy")
        list.addView(label("ADD A PURE-PYTHON PACKAGE", 11f, mint, true).apply { setPadding(dp(4), dp(14), dp(4), dp(8)) })
        val packageName = EditText(this).apply {
            hint = "Package name (e.g. textblob)"
            setHintTextColor(muted)
            setTextColor(white)
            textSize = 13f
            isSingleLine = true
            setPadding(dp(12), 0, dp(12), 0)
            setBackgroundColor(panel2)
        }
        val packageRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        packageRow.addView(packageName, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(8) })
        packageRow.addView(actionButton("INSTALL", { installPackage(packageName.text.toString()) }, mint, bg), LinearLayout.LayoutParams(dp(92), dp(46)))
        list.addView(packageRow)
        list.addView(label("Supports compatible pure-Python wheels and dependencies from PyPI. Internet required.", 10f, muted, false).apply { setPadding(dp(4), dp(7), dp(4), dp(7)) })
        val warning = TextView(this).apply {
            text = "Pure-Python wheels from PyPI only. Native wheels, source builds and VCS URLs are not supported."
            setTextColor(muted)
            textSize = 12f
            setPadding(dp(13), dp(14), dp(13), dp(14))
            setBackgroundColor(panel)
        }
        list.addView(warning, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
        scroll.addView(list)
        return scroll
    }

    private fun addPackageCard(list: LinearLayout, name: String, desc: String) {
        val accent = View(this).apply { setBackgroundColor(mint) }
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(label(name, 14f, white, true))
        texts.addView(label(desc, 11f, muted, false).apply { setPadding(0, dp(4), 0, 0) })
        val tag = TextView(this).apply {
            text = "bundled"
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(mint)
            setBackgroundColor(panel2)
            setPadding(dp(8), dp(5), dp(8), dp(5))
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(panel)
            setPadding(dp(2), dp(11), dp(13), dp(11))
        }
        card.addView(accent, LinearLayout.LayoutParams(dp(3), ViewGroup.LayoutParams.MATCH_PARENT))
        card.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(11) })
        card.addView(tag)
        list.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(7) })
    }

    private fun requestAiFix() {
        if (aiBusy || isRunning) { statusText.text = "Wait for the current task to finish"; return }
        val source = editor.text.toString()
        if (source.isBlank()) { statusText.text = "Write or open Python code first"; return }
        if (source.length > 100_000) { statusText.text = "AI Fix supports up to 100,000 code characters"; return }
        val store = AiConfigStore(this)
        try {
            OpenCodeClient.normalizeUrl(if (store.mode == "compatible") store.value("api_url") else store.value("oc_url"))
        } catch (_: Exception) {
            AlertDialog.Builder(this).setTitle("Set up AI Fix")
                .setMessage("Add your OpenCode server or compatible API URL, credentials and model in Settings → AI connections.")
                .setPositiveButton("AI settings") { _, _ -> renderTab(TAB_AI_SETTINGS) }.setNegativeButton("Cancel", null).show()
            return
        }
        val fileName = currentScriptName
        val uri = currentUri
        val include = CheckBox(this).apply { text = "Include recent console output (may contain secrets)"; isChecked = false }
        val settingsMode = store.mode
        val endpoint = if (settingsMode == "compatible") store.value("api_url") else store.value("oc_url")
        val detail = if (settingsMode == "compatible") "Model: ${store.value("api_model")}" else "Agent: ${store.value("oc_agent").ifBlank { "server default" }}"
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(4), dp(18), dp(4))
            addView(TextView(this@MainActivity).apply {
                text = "Send the full current script ($fileName) to:\n$endpoint\n$detail\n\nReview code for secrets first. AI suggestions can be wrong. Changes are shown before applying and never run automatically." +
                    if (endpoint.startsWith("http://")) "\n\nWarning: HTTP is unencrypted." else ""
            })
            addView(include)
        }
        AlertDialog.Builder(this).setTitle("AI Fix · review before sending").setView(box)
            .setPositiveButton("Send") { _, _ ->
                // Snapshot credentials and output on the UI thread, not mutable settings later.
                try {
                    val ocConfig = if (settingsMode != "compatible") store.openCode() else null
                    val apiConfig = if (settingsMode == "compatible") store.compatible() else null
                    val diagnostics = if (include.isChecked) outputLog.toString().takeLast(12_000) else "Console output not shared."
                    aiBusy = true; statusText.text = "AI Fix is working… You can keep editing."
                    aiExecutor.execute {
                        try {
                            val fixed = if (apiConfig != null) CompatibleAiClient(apiConfig).fix(source, diagnostics, fileName)
                                else OpenCodeClient(ocConfig!!).fix(source, diagnostics, fileName)
                            // Parse only. Never execute an AI response.
                            Python.getInstance().getModule("ast").callAttr("parse", fixed)
                            runOnUiThread {
                                if (!isDestroyed) {
                                    aiBusy = false
                                    if (fixed.trimEnd() == source.trimEnd()) statusText.text = "AI returned no code changes"
                                    else showAiReview(source, fixed, fileName, uri)
                                }
                            }
                        } catch (e: Exception) {
                            runOnUiThread {
                                if (!isDestroyed) {
                                    aiBusy = false; statusText.text = "AI Fix failed · original code is unchanged"
                                    val message = if (e is com.chaquo.python.PyException) "AI returned invalid Python syntax. Original code was kept. Try another model or agent." else e.message ?: "Could not reach the AI service"
                                    AlertDialog.Builder(this).setTitle("AI Fix could not finish").setMessage(message)
                                        .setPositiveButton("OK", null).setNeutralButton("AI settings") { _, _ -> renderTab(TAB_AI_SETTINGS) }.show()
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    aiBusy = false
                    AlertDialog.Builder(this).setTitle("Check AI settings").setMessage(e.message).setPositiveButton("OK", null).show()
                }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun showAiReview(original: String, fixed: String, fileName: String, uri: Uri?) {
        statusText.text = "AI suggestion ready · review before applying"
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, dp(12), 0) }
        content.addView(TextView(this).apply { text = "Review the complete replacement below. Syntax checked only; correctness is not guaranteed. Use Save to export the applied edit." })
        fun code(title: String, source: String) {
            content.addView(TextView(this).apply { text = title; setPadding(0, dp(10), 0, dp(6)); setTypeface(null, Typeface.BOLD) })
            val text = TextView(this).apply { this.text = source; typeface = Typeface.MONOSPACE; textSize = 12f; setTextIsSelectable(true) }
            content.addView(ScrollView(this).apply { addView(text) }, LinearLayout.LayoutParams(-1, dp(160)))
        }
        code("BEFORE", original); code("AI SUGGESTION", fixed)
        AlertDialog.Builder(this).setTitle("Review AI Fix · $fileName").setView(ScrollView(this).apply { addView(content) })
            .setNegativeButton("Discard") { _, _ -> statusText.text = "AI suggestion discarded" }
            .setPositiveButton("Apply to editor") { _, _ ->
                if (isRunning || editor.text.toString() != original || currentUri != uri || currentScriptName != fileName) {
                    statusText.text = "Editor changed or script is running · request a new AI Fix"
                    Toast.makeText(this, "Original snapshot changed. Your edits were kept.", Toast.LENGTH_LONG).show()
                } else {
                    try {
                        aiBackup.writeText(original)
                        localScriptFile.writeText(fixed)
                        editor.setText(fixed); renderTab(TAB_EDITOR)
                        statusText.text = "AI Fix applied · Undo AI Fix restores previous code · Save to export"
                    } catch (_: Exception) { statusText.text = "Could not save AI backup · original editor kept" }
                }
            }.show()
    }

    private fun undoAiFix() {
        if (isRunning || aiBusy) { statusText.text = "Wait for the current task to finish"; return }
        if (!aiBackup.exists()) { statusText.text = "No AI Fix backup yet"; return }
        AlertDialog.Builder(this).setTitle("Restore pre-AI code?")
            .setMessage("Replace the current editor with the most recent pre-AI backup? Any newer editor changes will be replaced. The backup is from the last applied AI Fix, not necessarily this file.")
            .setPositiveButton("Restore") { _, _ ->
                try {
                    val previous = aiBackup.readText()
                    localScriptFile.writeText(previous); editor.setText(previous); renderTab(TAB_EDITOR)
                    statusText.text = "Pre-AI backup restored · Save to export"
                } catch (_: Exception) { statusText.text = "Could not restore AI backup" }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun installPackage(packageSpec: String) {
        if (isRunning) {
            statusText.text = "Wait for the current run/install to finish"
            return
        }
        val spec = packageSpec.trim()
        if (spec.isEmpty()) {
            statusText.text = "Enter a package name first"
            return
        }
        outputLog.setLength(0)
        consoleSpans.clear()
        consoleView.text = ""
        appendConsole("[DZyne Code] Installing compatible wheel: $spec\n", CON_SYS)
        isRunning = true
        statusText.text = "Installing pure-Python package • see Console for progress"
        setPill(true)
        runTask = executor.submit {
            PythonBridge.outputHandler = { text, error -> runOnUiThread { appendConsole(text, if (error) CON_ERR else CON_OUT) } }
            try {
                val module = Python.getInstance().getModule("runtime")
                val result = module.callAttr("install_package", spec, workspace.absolutePath).toString()
                runOnUiThread {
                    appendConsole("\n$result\n", CON_OUT)
                    statusText.text = "Package install finished • test imports in a new run"
                }
            } catch (t: Throwable) {
                runOnUiThread {
                    appendConsole("\n[Install failed] ${t.message ?: t.javaClass.simpleName}\n", CON_ERR)
                    statusText.text = "Package install failed • open Console for details"
                }
            } finally {
                PythonBridge.outputHandler = null
                runOnUiThread { isRunning = false; setPill(false) }
            }
        }
    }

    private fun startRun() {
        if (aiBusy) { statusText.text = "Wait for AI Fix to finish before running"; return }
        if (isRunning) {
            statusText.text = "A script is already running"
            return
        }
        val source = editor.text.toString()
        currentScriptName = currentScriptName.ifBlank { "main.py" }
        localScriptFile.writeText(source)
        outputLog.setLength(0)
        consoleSpans.clear()
        consoleView.text = ""
        appendConsole("[DZyne Code] Running $currentScriptName with Python 3.11…\n", CON_SYS)
        isRunning = true
        statusText.text = "Running • input() prompts appear in Console"
        setPill(true)
        renderTab(TAB_CONSOLE)
        runTask = executor.submit {
            PythonBridge.outputHandler = { text, error -> runOnUiThread { appendConsole(text, if (error) CON_ERR else CON_OUT) } }
            PythonBridge.inputHandler = { prompt ->
                val latch = CountDownLatch(1)
                var answer = ""
                runOnUiThread {
                    if (prompt.isNotEmpty()) appendConsole(prompt, CON_ECHO)
                    pendingAnswer = { value -> answer = value; latch.countDown(); pendingAnswer = null }
                    renderTab(TAB_CONSOLE)
                    inputField.requestFocus()
                }
                try {
                    latch.await(30, TimeUnit.MINUTES)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
                answer
            }
            try {
                val module = Python.getInstance().getModule("runtime")
                module.callAttr("run_script", source, currentScriptName, workspace.absolutePath)
            } catch (t: Throwable) {
                runOnUiThread {
                    appendConsole("\n[Runtime error] ${t.message ?: t.javaClass.simpleName}\n", CON_ERR)
                }
            } finally {
                PythonBridge.inputHandler = null
                PythonBridge.outputHandler = null
                runOnUiThread {
                    isRunning = false
                    pendingAnswer = null
                    statusText.text = "Finished • open Preview to view generated files"
                    setPill(false)
                }
            }
        }
    }

    private fun stopRun() {
        if (!isRunning) {
            statusText.text = "Nothing is running"
            return
        }
        pendingAnswer?.invoke("")
        runTask?.cancel(true)
        statusText.text = "Stop requested"
        appendConsole("\n[Stop requested]\n", CON_ERR)
    }

    private fun sendInput() {
        val value = inputField.text.toString()
        inputField.setText("")
        appendConsole("$value\n", CON_ECHO)
        pendingAnswer?.invoke(value)
    }

    private fun appendConsole(text: String, kind: Int) {
        if (outputLog.length > 350_000) outputLog.delete(0, outputLog.length - 250_000)
        outputLog.append(text)
        val color = when (kind) {
            CON_SYS -> muted
            CON_ERR -> red
            CON_ECHO -> mint
            else -> white
        }
        val start = consoleSpans.length
        consoleSpans.append(text)
        consoleSpans.setSpan(ForegroundColorSpan(color), start, consoleSpans.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (consoleSpans.length > 350_000) consoleSpans.delete(0, consoleSpans.length - 250_000)
        consoleView.text = consoleSpans
        if (preferences.getBoolean("auto_scroll", true)) {
            consoleScroll.post { consoleScroll.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun openScript() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/x-python", "text/plain", "application/octet-stream"))
        }
        startActivityForResult(intent, REQ_OPEN)
    }

    private fun saveScript() {
        val uri = currentUri
        if (uri == null) {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/x-python"
                putExtra(Intent.EXTRA_TITLE, currentScriptName.ifBlank { "main.py" })
            }
            startActivityForResult(intent, REQ_SAVE)
        } else {
            try {
                contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(editor.text.toString()) }
                localScriptFile.writeText(editor.text.toString())
                statusText.text = "Saved • $currentScriptName"
            } catch (e: Exception) {
                statusText.text = "Save failed: ${e.message ?: "cannot write file"}"
            }
        }
    }

    private fun exportOutput() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, "dzyne-output.txt")
        }
        startActivityForResult(intent, REQ_EXPORT)
    }

    @Deprecated("Using the framework activity result for broad API 24 compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            when (requestCode) {
                REQ_OPEN -> {
                    val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                    currentUri = uri
                    currentScriptName = queryDisplayName(uri) ?: "script.py"
                    editor.setText(text)
                    localScriptFile.writeText(text)
                    statusText.text = "Opened • $currentScriptName"
                    renderTab(TAB_EDITOR)
                }
                REQ_SAVE -> {
                    contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(editor.text.toString()) }
                    currentUri = uri
                    currentScriptName = queryDisplayName(uri) ?: currentScriptName
                    localScriptFile.writeText(editor.text.toString())
                    statusText.text = "Saved • $currentScriptName"
                }
                REQ_EXPORT -> {
                    contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use {
                        it.write(outputLog.toString())
                        it.write("\n--- Exported from DZyne Code ---\n")
                    }
                    statusText.text = "Console output exported"
                }
            }
        } catch (e: Exception) {
            statusText.text = "File error: ${e.message ?: "could not access file"}"
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (_: Exception) { null }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (drawerOpen) { closeDrawer(); return }
        if (currentTab == TAB_AI_SETTINGS) { renderTab(TAB_SETTINGS); return }
        if (currentTab == TAB_ABOUT) { renderTab(TAB_SETTINGS); return }
        if (currentTab == TAB_SETTINGS) { renderTab(lastWorkspaceTab); return }
        super.onBackPressed()
    }

    override fun onDestroy() {
        highlightRunnable?.let { highlightHandler.removeCallbacks(it) }
        PythonBridge.inputHandler = null
        PythonBridge.outputHandler = null
        executor.shutdownNow()
        aiExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun label(text: String, size: Float, color: Int, bold: Boolean): TextView = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun actionButton(text: String, click: () -> Unit, fill: Int = panel2, ink: Int = white): TextView = TextView(this).apply {
        this.text = text
        textSize = 10f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setTextColor(ink)
        setPadding(dp(3), dp(11), dp(3), dp(11))
        setBackgroundColor(fill)
        setOnClickListener { click() }
    }

    private fun weightParams() = LinearLayout.LayoutParams(0, dp(42), 1f).apply {
        marginEnd = dp(5)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    companion object {
        private const val TAB_EDITOR = 0
        private const val TAB_CONSOLE = 1
        private const val TAB_PREVIEW = 2
        private const val TAB_LIBRARIES = 3
        private const val TAB_SETTINGS = 4
        private const val TAB_ABOUT = 5
        private const val TAB_AI_SETTINGS = 6
        private const val CON_SYS = 0
        private const val CON_OUT = 1
        private const val CON_ERR = 2
        private const val CON_ECHO = 3
        private val PY_KEYWORDS = setOf("import", "from", "def", "class", "return", "if", "elif", "else", "for", "while", "in", "not", "and", "or", "True", "False", "None", "with", "as", "try", "except", "finally", "pass", "break", "continue", "lambda", "global", "nonlocal", "assert", "del", "yield", "raise", "print")
        private const val REQ_OPEN = 1
        private const val REQ_SAVE = 2
        private const val REQ_EXPORT = 3
        private const val STARTER = "# DZyne Code • Python 3.11\n# Press RUN to execute this script.\n\nimport sys\n\nprint(\"Hello from DZyne Code!\")\nprint(\"Python\", sys.version.split()[0])\n"
        private const val PREVIEW_HELP = "<html><meta name='viewport' content='width=device-width, initial-scale=1'><body style='font-family:sans-serif;padding:24px;background:#181f24;color:#edf4f5'><h2>Nothing to preview yet</h2><p>Run a script which writes <code style='color:#f2c66d'>preview.html</code>, <code style='color:#f2c66d'>index.html</code>, or an image file into the app workspace. Then open Preview again.</p><pre style='color:#8fa1a8'>from pathlib import Path\nPath('preview.html').write_text('&lt;h1&gt;Hello!&lt;/h1&gt;')</pre><p>Files are stored in DZyne Code's private workspace.</p></body></html>"
    }
}
