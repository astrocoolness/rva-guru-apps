# Colibri 5G · Galaxy S24 companion APK

This is a **native Android companion UI prototype**, not a compiled JustVugg/colibri engine or an embedded GGUF inference runtime. It offers live Hugging Face discovery and chat using either explicitly selected Featherless cloud inference or a **separately running** llama.cpp server in Termux at 127.0.0.1:8080. Chat is not available offline until a local server is installed and started.

## Build
From this directory with Android SDK API 35, JDK 17 and Gradle 8.10.2: `gradle :app:assembleDebug`. Output: `app/build/outputs/apk/debug/app-debug.apk`. The GitHub Actions workflow uploads this as an APK artifact. It is a debug-signed sideload build, not a production Play Store release.

## Use
- Choose "Featherless cloud" and enter your own API key (app-private SharedPreferences). A cloud chat sends the message to Featherless and may incur charges. The app does not contain bundled API keys.
- Choose "Termux local" only if the earlier Colibri S24 starter has already started `llama-server` on localhost:8080. No model is downloaded, installed or loaded by this APK.
- Hugging Face discovery shows trending models, repo metadata and links; it does not guarantee runnable compatibility.
- The 5 GB RAM target concerns the external model runtime, **not** this app. The previous Termux setup uses conservative profiles, but Android can terminate it earlier. No background service or wake lock is used.

Security: The debug APK grants INTERNET permission and is permitted cleartext HTTP for localhost connectivity. Do not expose your Termux model server to the public internet. Store keys only within the app settings, and do not share screenshots including secrets.
