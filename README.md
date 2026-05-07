# RokidBrew

Community homebrew store and installer for Rokid glasses.

This first build is a working MVP with two Android apps:

- `glasses-app`: AR-first store that runs directly on Rokid glasses. It uses a black background, D-pad navigation, direct APK downloads, and Android `PackageInstaller` confirmation on the glasses.
- `phone-app`: phone-side store and installer. It downloads APKs from the same index, installs phone APKs locally, and pushes glasses APKs through the CXR-L / Hi Rokid flow.

## Build Outputs

```text
dist/RokidBrew-glasses-v0.1.0-debug.apk
dist/RokidBrew-phone-v0.1.0-debug.apk
```

## Store Model

Both apps currently embed the same starter index:

```text
phone-app/src/main/assets/apps.json
glasses-app/src/main/assets/apps.json
```

Each app entry declares:

- `type`: `glasses` or `combo`
- `phoneRequired`: whether a phone companion app is needed
- `artifacts`: APK URLs split by `target` (`glasses` / `phone`)

The next step is to move this embedded JSON to a hosted `rokidbrew-index` repo and let both apps refresh it over HTTPS.

## Glasses UX

The glasses app is intentionally AR-style:

- pure black background so the view stays transparent-feeling on Rokid displays
- high-contrast text only
- D-pad up/down to browse
- D-pad center / enter to download and install
- left/right jumps by three apps
- combo apps clearly show `Phone app required`

Install is not silent. The app downloads the APK, then opens Android `PackageInstaller`, so the user confirms the install on the glasses.

## Phone UX

The phone app supports:

- browse the same app index
- install phone APK locally
- authorize Hi Rokid
- install glasses APK via CXR-L
- combo flow button for apps with both artifacts

CXR-L requires the global Hi Rokid app, Bluetooth-connected glasses, and phone Wi-Fi enabled.

## Roadmap

- remote index refresh with signatures/checksums
- package/version detection for update badges
- richer EUNG SOFT + GitHub scraper
- QR/deep-link handoff from glasses to phone for phone-required apps
- LAN/SPP fallback installer using the proven Rokid-APKs companion protocol
- release builds signed with a real RokidBrew key
