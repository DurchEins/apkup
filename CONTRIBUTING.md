<a id="top"></a>

# Contributing to ApkUp

Thank you for your interest in contributing to **ApkUp**! Whether you are reporting a bug, translating strings, suggesting a repository mapping, or submitting a Pull Request, your contribution helps make Android app updates cleaner and safer for everyone.

## 🧭 Choose Your Path

<table>
  <tr>
    <td width="50%" valign="top">
      <b>🐛 Report a Bug</b><br>
      Found a crash, broken source, or UI glitch?<br><br>
      <a href="https://github.com/DurchEins/apkup/issues/new?template=bug_report.yml"><img src="https://img.shields.io/badge/Open-Bug%20Report-DC2626?style=flat&logo=github" alt="Open Bug Report" /></a>
    </td>
    <td width="50%" valign="top">
      <b>✨ Request a Feature</b><br>
      Have an idea for a new source, toggle, or UX improvement?<br><br>
      <a href="https://github.com/DurchEins/apkup/issues/new?template=feature_request.yml"><img src="https://img.shields.io/badge/Feature-Request-A855F7?style=flat&logo=github" alt="Request Feature" /></a>
    </td>
  </tr>
  <tr>
    <td width="50%" valign="top">
      <b>🔗 Suggest Repo Mapping</b><br>
      Know the official Git repo for an app not auto-detected?<br><br>
      <a href="https://github.com/DurchEins/apkup/issues/new?template=repo_mapping_request.yml"><img src="https://img.shields.io/badge/Suggest-Repo%20Mapping-0EA5E9?style=flat&logo=github" alt="Suggest Repo Mapping" /></a>
    </td>
    <td width="50%" valign="top">
      <b>💻 Code Pull Request</b><br>
      Ready to fix a bug or add a feature in Kotlin &amp; Compose?<br><br>
      <a href="https://github.com/DurchEins/apkup/compare"><img src="https://img.shields.io/badge/Create-Pull%20Request-16A34A?style=flat&logo=github" alt="Create Pull Request" /></a>
    </td>
  </tr>
  <tr>
    <td width="50%" valign="top">
      <b>🌍 Translate on Weblate</b><br>
      Help translate the app &amp; website in 9+ languages.<br><br>
      <a href="https://hosted.weblate.org/engage/apkup/"><img src="https://img.shields.io/badge/Translate-Weblate-144D3F?style=flat&logo=weblate" alt="Translate on Weblate" /></a>
    </td>
    <td width="50%" valign="top">
      <b>💬 Translation Issue</b><br>
      Report a typo, context mismatch, or request a new language.<br><br>
      <a href="https://github.com/DurchEins/apkup/issues/new?template=translation.yml"><img src="https://img.shields.io/badge/Translation-Issue-38BDF8?style=flat&logo=github" alt="Open Translation Issue" /></a>
    </td>
  </tr>
</table>

> [!NOTE]
> **Good First Ways to Help (No Coding Required):**
> - **Verify & triage issues** or attach reproducible logs (`Setup ➔ Diagnostics`).
> - **Polish translations** in your native language directly via Weblate in your browser.
> - **Submit verified repository mappings** so more open-source apps resolve automatically.
> - **Share before/after screenshots** or troubleshooting tips to improve the English [GitHub Wiki](https://github.com/DurchEins/apkup/wiki).

---

## 🌍 Translations (No Git Required)

ApkUp's **app and website UI** are available in **9 languages** (`en`, `de`, `es`, `fr`, `it`, `ja`, `pt`, `ru`, `zh-cn`), while the **Wiki guides** are primarily maintained in English.

- **Preferred method:** Translate directly in your browser on **[Hosted Weblate](https://hosted.weblate.org/engage/apkup/)**.
- **Quick typos or new language requests:** Open a **[Translation Issue](https://github.com/DurchEins/apkup/issues/new?template=translation.yml)**.

---

## 🐛 Bugs, Features & Repo Mappings

Before opening a new issue, please check **[existing issues](https://github.com/DurchEins/apkup/issues)** and the **[Troubleshooting Wiki](https://github.com/DurchEins/apkup/wiki/Troubleshooting-Source-Errors)**.

- **[Report a Bug](https://github.com/DurchEins/apkup/issues/new?template=bug_report.yml):** Include your Android version, ApkUp version, update source, and steps to reproduce.
- **[Request a Feature](https://github.com/DurchEins/apkup/issues/new?template=feature_request.yml):** Describe the use case and expected behavior.
- **[Suggest a Repo Mapping](https://github.com/DurchEins/apkup/issues/new?template=repo_mapping_request.yml):** Link the verified package name and upstream GitHub/GitLab/Codeberg repository.
- **Security vulnerabilities:** Do **not** open a public issue. Follow **[SECURITY.md](SECURITY.md)** and use GitHub Private Vulnerability Reporting.

---

## 🛠️ Development & Pull Requests

### Prerequisites
- **Android Studio** (or JDK 17+) with **Android SDK 37** (`minSdk = 29`, `targetSdk = 37`)
- **Gradle** wrapper included (`./gradlew`)

### Local Build & Verification
```bash
git clone https://github.com/DurchEins/apkup.git
cd apkup
./gradlew lintDebug testDebugUnitTest assembleDebug
```

> [!IMPORTANT]
> **Privacy & Security Golden Rules:** Every code change **must** preserve ApkUp's zero-trust and privacy guarantees:
> - **Explicit Consent:** Never perform background or unconsented network requests outside sources enabled by the user.
> - **Strict Host Isolation:** Never forward Personal Access Tokens (`Authorization` headers) across cross-host redirects.
> - **Zero Trackers:** Never introduce analytics SDKs, crash reporters, ads, or hardware identifiers.
> - **Cryptographic Integrity:** Never bypass `ApkIntegrityVerifier` certificate checks, downgrade guards, or archive safety limits.

### PR & Commit Guidelines
1. **Branch from `main`** with a descriptive name and use clear Conventional Commit-style titles:
   - `feat(sources): add support for custom Forgejo port`
   - `fix(installer): prevent double prompt on Shizuku reconnect`
   - `docs(wiki): clarify F-Droid Index V2 setup steps`
2. **Keep PRs reviewable:** Describe all included changes clearly in the PR summary so they are easy to review and test.
3. **Verify locally:** Ensure `lintDebug` and `testDebugUnitTest` pass before submitting.
4. **Match project conventions:** Follow Kotlin, Jetpack Compose, and Material 3 Expressive patterns used in the codebase.
5. **UI & Docs synchronization:** Include **before/after screenshots** for UI changes, and remember to update the relevant English **[GitHub Wiki](https://github.com/DurchEins/apkup/wiki)** guide whenever you modify update sources or user-facing settings.

<details>
<summary><b>📱 Local APK Debugging &amp; ADB Cheat Sheet</b></summary>
<br>

Debug builds automatically use `applicationIdSuffix = ".debug"` (**ApkUp Debug** / `com.apkup.debug`), so you can test your changes side-by-side next to your regular **ApkUp** installation without losing settings:

```bash
# Run lint, unit tests, and build the debug APK in one command
./gradlew lintDebug testDebugUnitTest assembleDebug

# Install or update ApkUp Debug on a connected device via ADB
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

</details>

<details>
<summary><b>🌱 First-Time Contributor? Fork &amp; Pull Request Guide</b></summary>
<br>

1. Click **Fork** in the top-right corner of **[DurchEins/apkup](https://github.com/DurchEins/apkup)**.
2. Clone your fork and add upstream:
   ```bash
   git clone https://github.com/<your-username>/apkup.git
   cd apkup
   git remote add upstream https://github.com/DurchEins/apkup.git
   ```
3. Create a branch off `main`:
   ```bash
   git checkout -b fix/my-improvement
   ```
4. Make your changes, run `./gradlew lintDebug testDebugUnitTest assembleDebug`, and commit cleanly.
5. Push to your fork (`git push origin fix/my-improvement`) and open a Pull Request against `DurchEins/apkup:main`, completing the checklist in the PR template.

</details>

---

## 📜 License

<a href="LICENSE"><img src="https://img.shields.io/badge/License-GPL--3.0-blue?style=flat" alt="GPL-3.0 License" /></a>

By contributing, you agree that your contributions will be licensed under the **[GNU General Public License v3.0 (GPL-3.0)](LICENSE)**.

<p align="right"><kbd><a href="#top">↑ Back to top</a></kbd></p>
