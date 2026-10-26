# Needle Pocket for Android

Native Android ARM64 app using the official Needle 3 engine. It routes natural-language commands into fixed local tools: tasks, notes, workout records, habit status, food logs, task completion and an in-app focus countdown.

First launch downloads the Needle 3 model (about 35 MB) from the official Cactus Compute Hugging Face repository into private app storage. After that, inference is on-device/offline. User records are kept in SharedPreferences on the phone. No cloud AI API key or account is used.

The app targets arm64-v8a. The workflow downloads the published native Android engine and builds a debug APK. Open Actions and download the artifact named needle-pocket-debug-apk.

Upstream model: https://huggingface.co/Cactus-Compute/needle3
Runtime source: https://github.com/cactus-compute/needle
Model license: Apache-2.0. Preserve upstream notices when redistributing.

Limitations: Needle 3 is designed for tool calling and structured actions, not general conversation. This app only executes the listed local tools; it does not control other Android apps. The focus timer is in-app, not an Android system alarm.