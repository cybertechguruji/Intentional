# Intentional

An Android app that helps you open selected social apps with a purpose and a time limit.

## Open the project

Open this folder in Android Studio. The project uses Android 7.0 (API 24) as its minimum and Jetpack Compose with Material 3.

## Current starting point

The app includes a Today / Week / Month dashboard with planned time, actual time, extra time, and per-app breakdowns. The app picker suggests likely social apps and keeps enabled apps at the top. An Accessibility service places both the first purpose-and-time check-in and the time-up choices over the selected app, without switching to a separate screen. The last chosen session length is preselected, but the user confirms each new session. A one-minute heads-up appears before the session ends. At expiry, users can finish and return Home, continue for five minutes, or choose a custom extension. The prompt waits for the user; there is no automatic timeout. Appearance can follow the phone or be set to Light or Dark. A short Settings note explains how to prevent YouTube picture-in-picture after returning Home. This is an early build and still needs phone testing before everyday use.

The app explains Accessibility access before opening Android Settings. The service watches app-window changes only; it does not read on-screen text or message content. The installed-app visibility permission lets people choose apps from their phone. Session times stay on the device. Reasons are used only on the check-in screen and are not saved.

## Build

Open in Android Studio and let it sync the Gradle project, then run the `app` configuration on a phone or emulator.

To share an installable APK, choose **Build > Build APK(s)**. The APK appears at `app/build/outputs/apk/debug/app-debug.apk`. If Android says the app is not installed while an older Intentional build is already on the phone, uninstall the older build first; builds signed with different local debug keys cannot replace one another. Then install the newly built APK.
