# Intentional 🧘📱

> An open-source, friction-based companion for Android that helps you open social media with clarity, intention, and set boundaries—without guilt or anxiety.

---

## 🌟 About Intentional

Most screen-time apps lock you out aggressively or shame you with red alerts and streak counters. **Intentional** takes a mindful approach:

* **The Pause:** Whenever you launch a chosen social app, a clean prompt asks: *"What brings you here?"* and *"How long do you plan to stay?"*
* **Gentle Heads-Up:** One minute before your chosen session expires, a subtle banner reminds you to start wrapping up.
* **Choice at Expiry:** When time is up, you decide: return Home, add 5 more minutes, or set a custom extension.
* **No Scores, No Guilt:** A clean dashboard gives you visibility over your Today, Week, and Month usage without penalties or judgments.
* **100% Private & On-Device:** Reasons you type are never stored. Your session data never leaves your device.

---

## 🎁 100% Free & Open Source

Intentional is **completely free to use for all Android users**.
- No subscriptions
- No ads or paywalls
- No tracking or analytics
- Fully open-source under the permissive **MIT License**


To share an installable APK, choose **Build > Build APK(s)**. The APK appears at `app/build/outputs/apk/debug/app-debug.apk`. If Android says the app is not installed while an older Intentional build is already on the phone, uninstall the older build first; builds signed with different local debug keys cannot replace one another. Then install the newly built APK.

## Download and install

[Download the latest APK](https://github.com/cybertechguruji/Intentional/releases/latest)

After downloading, tap the APK file on your phone and follow Android’s prompts to install it.

---

## ⚡ Built With "Vibe Coding"

Transparency first: this project was created through **vibe coding**—pairing human intent and ideas with modern AI programming assistants. 

Whether you are a seasoned Android engineer, a hobbyist, or someone who loves building through AI and vibe coding: **you are welcome here!** The codebase is kept clean, understandable, and open to continuous improvement.

---

## 🛡️ Modern Architecture (GPay & Banking Friendly)

Unlike early prototypes that relied on invasive Accessibility Services, Intentional uses standard Android APIs:

1. **Usage Access:** Detects when your selected social apps open.
2. **Display Over Other Apps:** Renders the check-in and time-up overlays smoothly over the target app.
3. **Foreground Service:** Keeps session timers reliable without being killed by background memory managers.

✨ **Banking App Friendly:** Because Intentional does **not** run an Accessibility Service, payment and banking apps (like Google Pay, PhonePe, and banking portals) run without security warnings or blocks.

---

## 📲 Download & Installation

### Option 1: Direct APK Download (Easiest)
Grab the latest ready-to-install `.apk` directly from our [GitHub Releases](../../releases) section:
1. Download `app-debug.apk` onto your phone.
2. Tap the file to install (allow "Install unknown apps" if prompted).
3. Open Intentional and tap **Turn on app check-ins** to grant:
   - **Usage Access**
   - **Display over other apps**
4. Select which apps you want to monitor in the **Apps** tab.

### Option 2: Build From Source
If you are a developer and want to inspect or modify the code:
1. Clone this repository:
   ```bash
   git clone https://github.com/your-username/intentional-social-sessions.git
   ```
2. Open the project in **Android Studio** (Ladybug / Hedgehog or newer).
3. Let Gradle sync dependencies.
4. Run the `app` configuration on a connected phone or emulator (Android 7.0+ / API 24+).
5. Or build the APK via command line:
   ```bash
   ./gradlew assembleDebug
   ```
   The APK output will be in `app/build/outputs/apk/debug/app-debug.apk`.

---

## 🤝 Contributing

We love community contributions!

To keep things organized and collaborative:
1. **Start with an Issue:** Before submitting a Pull Request, please open a **GitHub Issue** to share what you'd like to build, fix, or improve. This allows us to align on ideas before writing code.
2. **Vibe Coding Welcome:** PRs written with the help of AI and vibe coding tools are enthusiastically welcome! All we ask is that you verify your changes compile cleanly and test them on an actual Android device or emulator.
3. **Code Style:** Keep Jetpack Compose layouts clean, readable, and respectful of user battery and memory.

---

## 📄 License

This project is licensed under the **MIT License**. You are free to use, modify, distribute, and adapt this project for personal or commercial purposes.
 (fixing apk installation error)
