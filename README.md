# DZyne Code

A fresh Android Python workspace with an embedded CPython 3.11 runtime (Chaquopy). It can open and edit `.py` files, run scripts in-app, collect `input()` interactively in the console, preview generated HTML/images, and export console output.

## Build

Requirements: JDK 17+, Android SDK platform 34/build-tools 34.0.0, host Python 3.11 on `PATH` (Chaquopy build requirement), and network access for Gradle/PyPI dependencies. The APK targets Android API 24+ and bundles `arm64-v8a` plus `armeabi-v7a`.

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

## Python packages

The runtime includes the Python 3.11 standard library and curated packages bundled at build time: `requests`, `beautifulsoup4` (`bs4`), `rich`, `sympy`, and `packaging`. The Libraries screen can also fetch pure-Python wheels and their compatible dependencies from PyPI into the app-private workspace. This is a constrained installer, not full `pip`: native/platform-specific wheels, source builds, VCS links, extras, and all possible dependency conflicts are not supported. Packages requiring native extensions (for example, many SciPy/OpenCV builds) need Android-compatible binaries and cannot be promised as installable. Internet access is needed to install packages.

## Usage

- **Open**: select a `.py` file through Android's document picker.
- **Run**: execute the editor contents in the embedded Python runtime. `input()` prompts appear in the Console tab.
- **Preview**: scripts can write `preview.html`, `index.html`, or an image into the workspace; the Preview tab renders supported output files.
- **Export**: save the captured console log as a text file using Android's document picker.

Files created by scripts are stored in the app's private workspace. Use Android's file picker to open/export files; the app does not request broad storage access. The Console is an interactive Python output/input pane, not a general Linux/bash shell. Desktop GUI toolkits and arbitrary subprocess-based workflows are outside this Android runtime's scope.

## AI Fix (1.2.3)

In **Settings → AI connections & agents**, choose **OpenCode agents** or **OpenAI-compatible API**. OpenCode needs your reachable server URL, optional HTTP Basic username/password, and a working provider/model configured on that server. Use **Test connection & choose agent** to load server agents, including custom agents. An optional model override uses `provider/model`.

Compatible mode needs a base URL, your API key (optional for local services), and an exact model ID. URL presets cover OpenAI, Google AI Studio / Gemini, DeepSeek, Groq and Ollama; these do not include API access or keys. For Gemini, choose **Choose URL preset → Google AI Studio / Gemini**, which fills `https://generativelanguage.googleapis.com/v1beta/openai/` and the editable example model `gemini-3.8-flash`. Enter your Google AI Studio API key, choose a model available to your key, and save. Switching presets clears the previous API key to avoid sending it to another provider. Gemini uses Google's official OpenAI-compatible endpoint, not its native request format. Native Claude APIs require OpenCode or a compatible gateway. Arbitrary incompatible agent APIs are not automatically supported.

Use **Menu → AI FIX**, review what will be sent, then tap **Send**. Console output is optional and off by default. AI output is syntax-checked without execution, shown as before/after code, and only applied after review. Edits made while waiting are protected from replacement. **UNDO AI FIX** restores the most recent pre-fix backup after confirmation. Use **Save** to export changes. Valid syntax does not guarantee correctness.

Use HTTPS remotely. HTTP exposes code and credentials in transit and is warned about in the app; use it only on trusted networks. On a phone, `localhost` is the phone, not your computer. OpenCode usually listens on port 4096; bind it to a reachable LAN interface and enable `OPENCODE_SERVER_PASSWORD` rather than exposing an unauthenticated server. Credentials are AES-GCM encrypted with Android Keystore and excluded from backups. No developer API key is bundled. The OpenCode client disables all discovered tools and requests textual code rather than shell commands or server-side edits.

API references: [OpenCode](https://opencode.ai/docs/server/), [Chat Completions](https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create/), [Gemini OpenAI compatibility](https://ai.google.dev/gemini-api/docs/openai), [Ollama compatibility](https://docs.ollama.com/api/openai-compatibility).

## License

License: GPL-3.0 — see LICENSE.
