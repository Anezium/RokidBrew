# Registry Store Listings

RokidBrew should keep the current `apps.v1.json` fields for compatibility, then add optional store-listing fields that richer clients can display. Existing apps continue to work with `summary` and `description`; the redesigned app reads the richer fields only when they are present.

## App JSON shape

Add optional `listing` and `releases` fields to each `apps/<app-id>.json` entry:

```json
{
  "id": "rokid-scribe",
  "name": "Rokid-Scribe",
  "category": "Utility",
  "type": "combo",
  "version": "1.1.2",
  "summary": "Capture voice notes on glasses, transcribe on phone, export as TXT or PDF.",
  "description": "Short fallback description for old clients.",
  "listing": {
    "about": "Long product description written for RokidBrew. This should explain what the app does, how it behaves on phone and glasses, and what the user should expect before installing.",
    "descriptionMarkdown": "Optional markdown source. RokidBrew may render it as plain text until markdown support exists."
  },
  "releases": [
    {
      "version": "1.1.2",
      "date": "2026-05-26T17:10:49Z",
      "sourceReleaseUrl": "https://github.com/Anezium/Rokid-Scribe/releases/tag/v1.1.2",
      "notes": "Release notes summarized for the store listing.",
      "changes": [
        "Improved phone import flow.",
        "Added export fixes.",
        "Updated glasses APK metadata."
      ]
    }
  ],
  "artifacts": []
}
```

## Display rules

The app should use this priority order:

1. `listing.about`
2. `listing.descriptionMarkdown`
3. legacy `description`
4. legacy `summary`

For changelogs, the app shows the first item in `releases` as the latest release. The registry builder should sort newest first. If no release data exists, the app can show a quiet placeholder or omit the section.

## Content workflow

The registry-side workflow should be review-first:

1. Start from manual text when provided by the maintainer.
2. Otherwise fetch the upstream GitHub README and latest GitHub Releases.
3. Generate a concise `listing.about` and `releases[].changes`.
4. Keep `summary` short and stable for old clients.
5. Open a registry diff for review before publishing.

The future `RokidBrew Registry` skill should be responsible for steps 2-4. It should not publish automatically. It should return the proposed JSON patch and source URLs used, so the maintainer can approve the tone and factual claims.

## GitHub release import

For GitHub release artifacts, the registry can usually infer:

- `sourceReleaseUrl` from the artifact release tag.
- `date` from GitHub `published_at`.
- `version` from artifact metadata, asset filename, or tag name.
- `notes` from release body, summarized for the store.
- `changes` from release body bullets, normalized into short user-facing items.

For raw GitHub APK URLs, release data may be unavailable. Keep `releases` empty unless a specific release URL is configured.

## Compatibility with current registry

Do not make these fields required yet. Current `dist/apps.v1.json` can include them without breaking RokidBrew because old clients ignore unknown fields. A later `dist/apps.v2.json` can formalize richer media, localization, permissions, and host app metadata.

Recommended later fields, not needed for this redesign:

```json
{
  "media": [
    {
      "type": "screenshot",
      "target": "glasses",
      "asset": "rokid-scribe/glasses-recording.jpg",
      "caption": "Recording HUD on the glasses"
    }
  ],
  "hostApps": [
    {
      "region": "global",
      "name": "Hi Rokid",
      "packageName": "com.rokid.sprite.global.aiapp"
    },
    {
      "region": "china",
      "name": "Rokid AI CN",
      "packageName": "com.rokid.sprite.aiapp"
    }
  ]
}
```
