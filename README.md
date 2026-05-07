# RokidBrew

Community app store for [Rokid AR glasses](https://www.rokid.com). Browse, install, and update community-made apps — directly from your phone.

[![Platform](https://img.shields.io/badge/Android-3DDC84?logo=android)](https://github.com/Anezium/RokidBrew)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin)](https://kotlinlang.org/)
[![Compose](https://img.shields.io/badge/Jetpack_Compose-Material_3-4285F4?logo=jetpackcompose)](https://developer.android.com/compose)

<p align="center">
  <img src="RokidBrew_screenshot1.jpg" width="280" alt="RokidBrew store screen" />
  <img src="RokidBrew_screenshot2.jpg" width="280" alt="RokidBrew app detail" />
</p>

## Download

Get the latest APK from [GitHub Releases](https://github.com/Anezium/RokidBrew/releases/latest).

## What it does

- **Browse 31+ community apps** for Rokid glasses — AI assistants, navigation HUDs, games, launchers, translators, and more.
- **Install phone APKs** locally on your Android device.
- **Push glasses APKs** to your Rokid glasses via CXR-L / Hi Rokid authorization.
- **Combo apps** — apps that need both a phone and glasses APK get a single install flow.
- **Check install status** and detect available updates.
- **Self-updating** — the store checks for new versions on every refresh.

No desktop. No cloud relay. Everything stays between your phone and your glasses.

## How it works

```
┌─────────────────────┐          HTTP GET            ┌──────────────────────────┐
│   RokidBrew (Phone) │ ──────────────────────────► │ RokidBrew-Registry        │
│   Android App        │ ◄────────────────────────── │ dist/apps.v1.json         │
└─────────────────────┘          (JSON manifest)     └──────────────────────────┘
```

The app loads a cached manifest on startup, then refreshes from the hosted registry in the background. The registry is maintained in a separate repo — new apps can be added without shipping APK updates.

**[RokidBrew-Registry →](https://github.com/Anezium/RokidBrew-Registry)**

## Requirements

- Android 9+ (API 28)
- **Global Hi Rokid app** installed on your phone (for glasses-side APK installs)
- Bluetooth pairing between phone and glasses
- Phone Wi-Fi enabled during glasses installs

## Build

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-22'
.\gradlew assembleDebug
```

Output: `phone-app/build/outputs/apk/debug/phone-app-debug.apk`

## Project structure

```
RokidBrew/
├── phone-app/        Android phone store app (Kotlin + Jetpack Compose + CXR-L SDK)
├── docs/             Architecture docs
└── dist/             Built APKs
```

## Credits

Built on top of the Rokid CXR-L SDK (`client-l`). Registry powered by the community at [RokidBrew-Registry](https://github.com/Anezium/RokidBrew-Registry).
