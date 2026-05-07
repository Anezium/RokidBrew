# RokidBrew

Community homebrew store and installer for Rokid glasses.

This build is a phone-first Android app:

- `phone-app`: phone-side store and installer. It downloads APKs from the community index, installs phone APKs locally, and pushes glasses APKs through the CXR-L / Hi Rokid flow.

## Build Outputs

```text
dist/RokidBrew-phone-v0.1.1-debug.apk
```

## Store Model

The phone app embeds a starter index and refreshes from the hosted registry:

```text
phone-app/src/main/assets/apps.json
```

Each app entry declares:

- `type`: `glasses` or `combo`
- `phoneRequired`: whether a phone companion app is needed
- `artifacts`: APK URLs split by `target` (`glasses` / `phone`)

Remote registry endpoints live in `BrewIndex` and currently target `RokidBrew-Registry`.

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
- LAN/SPP fallback installer using the proven Rokid-APKs companion protocol
- release builds signed with a real RokidBrew key
