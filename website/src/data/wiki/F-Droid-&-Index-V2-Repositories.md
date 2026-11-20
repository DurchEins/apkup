# 📦 F-Droid & Index V2 Repositories in ApkUp

← Back to [Wiki Home](Home) · [Troubleshooting Source Errors](Troubleshooting-Source-Errors#8-f-droid--izzyondroid--codeberg--forgejo-issues)

Starting with **v1.40.0+**, ApkUp upgrades its F-Droid and community repository processing to the modern **F-Droid Index V2** specification (`index-v2.json`). This brings dramatic improvements in network efficiency, memory usage, and update accuracy.

---

## 📑 Quick Navigation

* [1. What is F-Droid Index V2?](#1-what-is-f-droid-index-v2)
* [2. Why the Upgrade from Index V1 (`index.xml`)?](#2-why-the-upgrade-from-index-v1-indexxml)
* [3. Supported Community Repositories](#3-supported-community-repositories)
* [4. Managing Built-in & Custom F-Droid Repositories](#4-managing-built-in--custom-f-droid-repositories-v1431)
* [5. Security, Signing Keys & Fingerprint Verification](#5-security-signing-keys--fingerprint-verification)

---

## 1. What is F-Droid Index V2?

The F-Droid Index V2 format is the next-generation metadata catalog for Android open-source repositories. Instead of relying on monolithic, memory-intensive XML archives, Index V2 distributes repository manifests formatted as streamlined JSON objects (`index-v2.json`), partitioned by application packages and cryptographic release signatures.

---

## 2. Why the Upgrade from Index V1 (`index.xml`)?

Prior to v1.40, repository metadata was parsed from legacy `index.jar` / `index.xml` feeds. This came with significant disadvantages on mobile devices:
* ⚠️ **High RAM & CPU Consumption:** Parsing a 30+ MB uncompressed XML file required tens of megabytes of heap allocation and could cause stutter on mid-range devices.
* ⚠️ **Slow Cold Checks:** Reading XML tags sequentially slowed down background checks.
* ⚠️ **Bandwidth Waste:** Entire catalogues had to be parsed even if only a few packages changed.

### The Index V2 Advantages in ApkUp:
* ⚡ **Streaming JSON Parser:** ApkUp streams `index-v2.json` directly from the network without allocating huge string buffers into RAM.
* ⚡ **Incremental Index-V2 Diffs & Caching:** Uses `ETag`, `Last-Modified`, and signed F-Droid Index-V2 diffs (`RFC 7396` JSON Merge Patch with mandatory SHA-256 verification) to download only lightweight incremental changes when your local cache is up to date, automatically falling back to the full `index-v2.json` when needed.
* ⚡ **Instant Package Matching:** Direct hashmap lookup matching your device's installed package names in milliseconds.

---

## 3. Supported Community Repositories

ApkUp connects natively with major verified repositories inside a single unified repository manager:
1. **IzzyOnDroid (`apt.izzysoft.de`):** The premier curated repository for open-source applications built directly from developer repositories with original developer signatures.
2. **Official F-Droid Repository (`f-droid.org`):** The global repository for free and open-source software built from source on F-Droid build servers.
3. **Built-in Verified Presets:** Ready-to-enable profiles for **NewPipe Repo (`archive.newpipe.net`)**, **microG Repo (`microg.org`)**, and **Guardian Project (`guardianproject.info`)** with pre-pinned SHA-256 signing fingerprints.
4. **Custom Third-Party HTTPS Repositories:** Any repository implementing the F-Droid Index V2 (`entry.jar` / `index-v2.json`) or Index V1 (`index-v1.jar`) standard.

---

## 4. Managing Built-in & Custom F-Droid Repositories (v1.43.1+)

Starting with **v1.43.1**, all built-in and custom F-Droid repositories are managed in one unified bottom sheet in the Setup tab (alongside automatic detection during Obtainium imports):
1. In ApkUp, navigate to **Setup ➔ Update Sources ➔ F-Droid / IzzyOnDroid**.
2. Toggle the source on and tap the row to open the unified **F-Droid Repositories** bottom sheet (`FDroidReposBottomSheet`).
3. Toggle any of the **Built-in Repositories** (*IzzyOnDroid*, *Official F-Droid*, *microG Repo*, *Guardian Project*, or *NewPipe Repo*) or add your own custom HTTPS repository:
   * **Display Name:** Label shown inside ApkUp
   * **Repository URL:** `https://...` endpoint (supports `/repo`, `/archive`, `/fdroid/archive`, or `fdroidrepo://` links with `?fingerprint=…`)
   * **SHA-256 Fingerprint (Optional):** 64-character hex signing fingerprint of the repository index certificate
4. Tap **Add Repository**. ApkUp validates the `https://` host and includes the repository in your next update scan.

---

## 5. Security, Signing Keys & Fingerprint Verification

### Cryptographic JAR Verification & Optional TOFU Pinning
When fetching `entry.jar` (Index V2) or `index-v1.jar` (Index V1 fallback), ApkUp cryptographically verifies the JAR signature (`META-INF/*`) and enforces anti-rollback timestamp checks:
* **Explicitly Pinned Repositories:** Built-in presets and custom repositories with a 64-character SHA-256 fingerprint strictly enforce that exact X.509 signing certificate (fail-closed).
* **Trust-on-First-Use (TOFU) Pinning:** If you add a custom HTTPS repository without specifying a SHA-256 fingerprint, ApkUp verifies the signed `entry.jar` / `index-v1.jar` on the first successful sync, automatically pins its X.509 certificate fingerprint for all future checks, and displays a **TOFU-Pinned** badge in the repository sheet.

### Package Signature Isolation
F-Droid builds packages with its own official signing keys, while developers signing their own releases (or packages from IzzyOnDroid / GitHub) use developer private keys. ApkUp enforces **cryptographic signature matching** before recommending or installing any update to ensure that Android does not reject the package with an `INSTALL_FAILED_UPDATE_INCOMPATIBLE` error.

---

← Back to [Wiki Home](Home) | Related: [Update Sources, Repository Linking & Signature Protection](Update-Sources,-Repository-Linking-&-Signature-Protection) · [Troubleshooting Source Errors](Troubleshooting-Source-Errors#8-f-droid--izzyondroid--codeberg--forgejo-issues)
