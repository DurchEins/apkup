---
description: "Direct fixes for DNS errors, HTTP 401/403/429 rate limits, APKMirror Cloudflare challenges, signature mismatches, and repo filters."
icon: triangle-exclamation
---

# 🚨 Troubleshooting Source Errors in ApkUp

When checking for updates or downloading packages, ApkUp communicates with multiple independent providers (Google Play Store, APKMirror, F-Droid / IzzyOnDroid, GitHub, GitLab, and Codeberg / Forgejo). If a source check or installation fails, ApkUp links directly to the matching section below.

---

## 📑 Quick Navigation

* [1. Understanding the Error Types](#understanding-error-types)
* [2. Network & Connectivity (`DNS` & `Timeout`)](#network-connectivity)
* [3. Authentication & Rate Limits (`HTTP 401`, `403`, `429`)](#auth-and-rate-limits)
* [4. Server & Availability Errors (`HTTP 404`, `5xx`)](#server-errors)
* [5. APKMirror Cloudflare Challenges & Browser Fallback](#apkmirror-cloudflare)
* [6. Installation & Signature Conflicts (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`)](#signature-conflicts)
* [7. Custom Repository Linking & Asset Matching](#custom-repo-linking)
* [8. F-Droid / IzzyOnDroid & Codeberg / Forgejo Issues](#fdroid-and-codeberg)
* [9. Quick Diagnostic Checklist](#diagnostic-checklist)

---

<a id="understanding-error-types"></a>
## 1. Understanding the Error Types

1. **Network & Source Check Failures:** The remote server was unreachable, rate-limited the request, or returned an HTTP error.
2. **Package & Installation Failures:** The download completed, but Android's `PackageManager`, ApkUp's pre-install compatibility check (`minSdk` / `ABI`), or cryptographic signature verification rejected the package.

---

<a id="network-connectivity"></a>
<a id="dns-resolution-failed"></a>
<a id="2-network--connectivity-issues-dns--timeouts"></a>
## 2. Network & Connectivity (`DNS` & `Timeout`)

<a id="41-dns-error--server-unreachable"></a>
### DNS Resolution Failed (`UnknownHostException`)
* **Symptom:** `UnknownHostException`, `dns lookup failed`, or `Unable to resolve host`.
* **Cause:** Your device could not resolve the server hostname (e.g., `api.github.com`, `gitlab.com`, `codeberg.org`, or `apt.izzysoft.de`). Common culprits are Private DNS (DoH/DoT), VPN ad-blockers (AdGuard, NextDNS, Pi-hole), or switching networks.
* **Solution:**
  1. Confirm general internet connectivity in your browser.
  2. Temporarily set **Android Settings ➔ Network & Internet ➔ Private DNS** to *Automatic*.
  3. Allowlist repository domains in any ad-blocking VPN (`github.com`, `gitlab.com`, `codeberg.org`, `apkmirror.com`, `apt.izzysoft.de`, `f-droid.org`).

<a id="connection-timeout"></a>
<a id="42-network-timeout--connection-timed-out"></a>
### Connection Timeout (`SocketTimeoutException`)
* **Symptom:** `SocketTimeoutException` or `Read timed out`.
* **Cause:** The remote server took too long to respond (peak traffic or high-latency mobile connection).
* **Solution:**
  1. Pull down on the **Updates** tab to retry.
  2. If one specific source times out repeatedly, open **Setup ➔ Source Health & Cooldowns** to inspect cooldowns, or temporarily toggle that source off in **Setup**.

---

<a id="auth-and-rate-limits"></a>
<a id="43-auth-error--rate-limit-http-401--403--429"></a>
## 3. Authentication & Rate Limits (`HTTP 401`, `403`, `429`)

<a id="github-rate-limit"></a>
<a id="310-common-error-github-updates-are-temporarily-not-found-http-403-rate-limit"></a>
### GitHub Rate Limit Exceeded (`HTTP 403`)
* **Symptom:** `HTTP 403: API rate limit exceeded`.
* **Cause:** GitHub limits unauthenticated requests to **60 requests per hour per IP**. Tracking many GitHub apps or sharing a VPN/public Wi-Fi IP exhausts this quickly.
* **Solution:**
  * Add a free **GitHub Personal Access Token** (raises the limit to **5,000 requests/hour** and enables batched GraphQL queries).
  * Follow the [Token Setup Guide](tokens.md), then paste it under **Setup ➔ GitHub ➔ Test Token**.

<a id="play-store-rate-limit"></a>
<a id="39-common-error-google-play-store-download-fails-http-429-or-paid-apps"></a>
### Google Play Store Rate Limit (`HTTP 429`)
* **Symptom:** `HTTP 429: Too Many Requests` or Play Store download failure.
* **Cause:** Anonymous Play Store guest sessions (`gplayapi 4.0.0`) are temporarily rate-limited by Google, or the app is a paid/region-locked app requiring a personal license.
* **Solution:**
  1. Wait 5–10 minutes — ApkUp automatically rotates anonymous sessions with synchronized cooldown protection.
  2. Open the update card and switch to **APKMirror**, **F-Droid**, or **GitHub/GitLab** under *Alternative Sources* if available.

<a id="gitlab-auth-error"></a>
### GitLab / Codeberg Authentication Error (`HTTP 401` / `403`)
* **Symptom:** `HTTP 401 Unauthorized` or `HTTP 403 Forbidden`.
* **Cause:** The linked project is private, belongs to an internal group, or your stored Personal Access Token expired/lacks scope (`read_api` for GitLab, `read:repository` for Codeberg/Forgejo).
* **Solution:**
  * Open **Setup ➔ GitLab** (or **Codeberg**), update your token, and tap **Test Token**.

---

<a id="server-errors"></a>
<a id="44-server-error--not-found-http-404--500--502--503"></a>
## 4. Server & Availability Errors (`HTTP 404`, `5xx`)

<a id="apkmirror-404"></a>
<a id="38-common-error-apkmirror-shows-an-update-but-the-webpage-says-404-not-found"></a>
### APKMirror `404 Not Found`
* **Symptom:** ApkUp detects a new APKMirror version, but downloading fails with `HTTP 404`.
* **Cause:** APKMirror's RSS/API feed occasionally publishes a release before all architecture variants finish propagating across CDN nodes.
* **Solution:**
  * Wait 15–30 minutes and retry.
  * Tap the app card and choose **Open in Browser** to inspect the variant page directly.

### Upstream Server Errors (`HTTP 500`, `502`, `503`, `504`)
* **Symptom:** `502 Bad Gateway`, `503 Service Unavailable`, or `504 Gateway Timeout`.
* **Cause:** Temporary outage or maintenance at the upstream provider.
* **Solution:** No action needed — ApkUp's *Source Health* tracker handles temporary outages automatically and retries on your next check.

---

<a id="apkmirror-cloudflare"></a>
<a id="45-apkmirror-cloudflare-protection--browser-fallback"></a>
## 5. APKMirror Cloudflare Challenges & Browser Fallback

* **Symptom:** APKMirror prompts for verification or opens your browser.
* **Cause:** Cloudflare bot protection flagged automated traffic or your VPN/proxy IP.
* **Solution:**
  1. When Cloudflare triggers a challenge during a foreground download, ApkUp opens an **in-app WebView** so you can complete the check without leaving the app.
  2. If it runs in the background or still fails, ApkUp automatically opens your device browser as a seamless fallback and enforces a 10-minute per-app cooldown to prevent repeated blocks.

---

<a id="signature-conflicts"></a>
<a id="46-installation-failed--signature-conflicts"></a>
<a id="31-why-does-apkup-sometimes-show-a-warning-icon-for-a-signature-conflict"></a>
## 6. Installation & Signature Conflicts (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`)

* **Symptom:** Android rejects installation with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, or ApkUp flags a **Signature Mismatch** warning.
* **Cause:** Every Android app update must be signed with the **exact same private cryptographic key** as the installed app. Cross-updating between different signers (e.g., Google Play vs. F-Droid build servers vs. developer GitHub release) is blocked by Android and ApkUp's pre-install verifier.
* **How to Resolve:**
  1. Open the update card and pick a matching source under **Alternative Sources** (or pin the app to its original source).
  2. If you intentionally want to switch signers (e.g., Play Store ➔ GitHub), **uninstall the existing app first** (back up its data beforehand!) and install the new build cleanly.
  3. **Exporting APKs (`Save APK`):** Saving an `.apk` locally still works even if the installed app uses a different signature — ApkUp verifies file/archive/SHA-256 integrity without blocking local export.

---

<a id="custom-repo-linking"></a>
<a id="47-repository-linking--no-matching-apk-asset"></a>
## 7. Custom Repository Linking & Asset Matching

* **Symptom:** *"No matching release asset found"* or repository lookup error.
* **Cause:** The release only contains source archives (`.zip` / `.tar.gz`), desktop binaries, an incompatible CPU architecture (`ABI`) / `minSdk`, or a custom asset filename that doesn't match your filter.
* **Solution:**
  1. Verify on the repository's Releases page that a compiled Android `.apk` exists.
  2. Check your repository slug format in **Apps ➔ Link Repository**:
     * GitHub: `owner/repository`
     * GitLab: `group/project` or nested `group/subgroup/project`
     * Codeberg / Forgejo: `owner/repository` (plus custom host if self-hosted)
  3. If the release ships multiple APK variants (e.g., FOSS vs. Play or architecture splits), add an optional **APK Asset Regex** and test it live in the repository sheet.

---

<a id="fdroid-and-codeberg"></a>
## 8. F-Droid / IzzyOnDroid & Codeberg / Forgejo Issues

* **F-Droid / IzzyOnDroid:** If a custom repository fails certificate verification (`TOFU` or fingerprint mismatch), open **Setup ➔ F-Droid / IzzyOnDroid**, verify the HTTPS URL and 64-character SHA-256 fingerprint, and see the [F-Droid & Index V2 Guide](fdroid-index-v2.md).
* **Codeberg / Forgejo:** Self-hosted Forgejo instances must use `https://` and expose the standard `/api/v1/repos/.../releases` endpoint. See [Token Configuration](tokens.md) for private repos.

---

<a id="diagnostic-checklist"></a>
<a id="-part-4-common-error-messages--self-help-troubleshooting"></a>
## 9. Quick Diagnostic Checklist

1. **Pull to Refresh** on the **Updates** tab.
2. Check **Setup ➔ Source Health & Cooldowns** to see if a source is temporarily cooled down.
3. Temporarily test without VPN / Private DNS if DNS or Cloudflare errors persist.
4. Add a free **GitHub / GitLab / Codeberg Token** for heavy repo lists.
5. Need help? Export a sanitized report via **Setup ➔ Diagnostics ➔ Export / Share** and attach it to a [GitHub Issue](https://github.com/DurchEins/apkup/issues).
