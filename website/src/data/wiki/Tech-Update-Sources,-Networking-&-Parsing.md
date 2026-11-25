# 🌐 Technical Overview: Update Sources, Networking & Parsing

This page documents ApkUp's multi-source update engine, HTTP transport, rate-limit resilience, and repository parsing architecture.

---

## 1. Parallel Multi-Source Aggregation & Priority

- **Concurrent Source Queries:** Update checks run concurrently across enabled sources (`Google Play`, `APKMirror`, `F-Droid` / `IzzyOnDroid` / custom F-Droid feeds, `GitHub`, `GitLab`, `Codeberg` / `Forgejo`) and aggregate candidate `AppUpdate` models per `packageName`.
- **Source Priority & Per-App Pinning:** Global source ordering is configurable in Setup and stored in `Prefs`. Per-app pinning locks an app to a preferred source when an update is available from that source, with automatic fallback if the pinned source returns no newer build.
- **Alternative Variants (`alternativeUpdates`):** When multiple sources provide a newer `versionCode`, ApkUp retains alternative variants inside `AppUpdate.alternativeUpdates` so users can inspect and switch sources inside `UpdateDetailsSheet`.
- **Pre-Release Filtering:** Global and per-app pre-release channels (`Beta`, `RC`, `Alpha`, `Nightly`) filter tag/version strings before candidate selection.

---

## 2. Google Play Store (`gplayapi`)

- **On-Device Session & Delta Queries:** Uses `gplayapi` directly on-device without relying on an external dispenser server, minimizing third-party exposure.
- **HTTP 429 Recovery & Profiles:** Automatically recovers from Google Play rate limits (`HTTP 429`) and uses privacy-minimized device compatibility profiles.
- **Split-APK & Missing Pre-Download Hash:** Play Store responses do not always supply a pre-download SHA-256 digest in `Link.Url.expectedSha256`. When `expectedSha256` is absent, ApkUp displays `MissingPreDownloadHashBadge` next to the target version and relies on Android's APK Signature Scheme v2/v3 verification against the installed package before installation.

---

## 3. F-Droid, IzzyOnDroid & Custom F-Droid Repositories

- **Index-V2 & Index-V1 Support:** Supports official F-Droid, IzzyOnDroid, and custom HTTPS repositories (`?fingerprint=...`) via `IzzyOnDroidRepository`.
- **Cryptographic Index Verification:**
  - `index-v1.jar` / `entry.jar` signatures are verified against pinned 64-hex SHA-256 X.509 certificate fingerprints (including Trust-On-First-Use pinning for custom presets).
  - Incremental Index-V2 diffs (`RFC 7396`) validate per-diff SHA-256 checksums (`ApkIntegrityVerifier.verifyFileSha256`) before applying state updates.
- **Per-APK Checksums:** Every F-Droid / IzzyOnDroid package download includes `expectedSha256` for mandatory pre-install SHA-256 verification.

---

## 4. APKMirror Engine & Cloudflare Resilience

- **In-App Download & `.apkm` Split Bundles:** Resolves APKMirror release/variant pages, downloads single APKs or `.apkm` split-bundle archives, and extracts split parts in private internal storage with strict Zip-Slip and decompression-bomb limits.
- **Cooldown & Concurrency Throttling:** `SourceHealthTracker` enforces a per-app cooldown and intelligent concurrency throttling to avoid `HTTP 429` / `403` bursts.
- **Cloudflare WebView & Browser Fallback:** When anti-bot challenges block headless HTTP requests, ApkUp automatically routes resolution through an in-app Cloudflare-capable WebView or offers a seamless browser fallback (`isBrowserOnlyUpdate`).

---

## 5. GitHub, GitLab & Codeberg / Forgejo

- **3-Tier Repository Binding:** Supports built-in mappings for popular FOSS apps, `owner/repo` bindings, and self-hosted GitLab/Forgejo instances (including multi-level subgroup paths).
- **Batched GraphQL & Token Isolation:** When a GitHub Personal Access Token is configured, ApkUp batches release lookups via GraphQL to raise limits from 60 to 5,000 requests/hour. Tokens are stored per-host and stripped on cross-host redirects.
- **Asset Filtering & Digests:** Supports optional asset regex filtering, FOSS flavor preference, and `expectedSha256` extraction from release digests when provided (e.g. `CodebergRepository`, `GitHubRepository`).

---

> 📖 **User-friendly guide:** [Update Sources, Repository Linking & Signature Protection](https://github.com/DurchEins/apkup/wiki/Update-Sources,-Repository-Linking-&-Signature-Protection)
