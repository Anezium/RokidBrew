# RokidBrew Registry Architecture

RokidBrew uses a remote registry so apps can be added or updated without shipping a new phone APK.

## Load order

1. Load the cached remote manifest from app storage, if present.
2. Fall back to the bundled `phone-app/src/main/assets/apps.json`.
3. Refresh the remote registry in the background.
4. If refresh succeeds, replace the in-memory list and cache the manifest.

The UI always has an immediate local list, then updates itself when the remote registry is available.

## Registry endpoint

The phone app fetches:

```text
https://raw.githubusercontent.com/Anezium/RokidBrew-Registry/main/dist/apps.v1.json
```

## Registry repo

The registry lives in:

```text
https://github.com/Anezium/RokidBrew-Registry
```

Source files are one app per JSON file in `apps/`. The generated manifest is `dist/apps.v1.json`.

## Remote assets

The manifest supports:

- `iconUrl`
- `screenshotUrls`

The app caches remote icons/screenshots locally. If remote media is unavailable, it falls back to bundled assets or generated initials.
