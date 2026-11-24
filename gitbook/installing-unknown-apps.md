---
description: "Step-by-step guide to Android Install Unknown Apps permissions plus power-user installation via Shizuku, ADB, and Root."
icon: download
---

# Installing Unknown Apps on Android (Sideloading Guide)

Android allows installing apps outside the Google Play Store (**sideloading**). This guide covers Android's classic **"Install unknown apps"** permission as well as power-user installation via **Shizuku**, **ADB**, and **Root (su)**.

> Looking for help with Google's 2026/2027 24-hour Developer Options cooldown? See **[Google's 24h Lock & Keep Android Open](google-24h-lock.md)** or the dedicated [24h Lock Page](https://durcheins.github.io/apkup/sideloading/google-lock/).

---

## 1. Standard Android Installation ("Install Unknown Apps")

On standard Android 10–17 devices:

1. **Download the latest signed APK** from [ApkUp Downloads](https://durcheins.github.io/apkup/downloads/) or [GitHub Releases](https://github.com/DurchEins/apkup/releases/latest).
2. Tap the downloaded APK file in your browser or file manager.
3. If Android shows *"For your security, your phone currently isn't allowed to install unknown apps from this source"*:
   - Tap **Settings** in the prompt.
   - Toggle **Allow from this source** ON.
   - Return and tap **Install**.
4. **Once ApkUp is installed:** The first time you update an app inside ApkUp (without Shizuku/Root), Android will ask you once to grant **Install unknown apps** to **ApkUp** itself.

---

## 2. Power-User Installation: Shizuku, ADB, or Root (su)

Because ADB and elevated installer sessions operate through Android's developer package installer pipeline, power users can streamline or automate installations:

- **Shizuku inside ApkUp (No Root, Android 11+):** When Shizuku is running via Wireless Debugging and enabled in ApkUp Setup, ApkUp executes package installations through an elevated `PackageInstaller` session in the background.
- **Direct ADB from PC:**
  ```bash
  adb install -r ApkUp.apk
  ```
- **Root (`su`):** Devices with Magisk, KernelSU, or APatch install updates directly via root package manager calls without consumer installer prompts.

### Related Guides

- [Dedicated Unknown Apps Page](https://durcheins.github.io/apkup/sideloading/)
- [Google's 24h Lock & Keep Android Open](google-24h-lock.md)
- [Setting up Shizuku (Quick Guide)](setting-up-shizuku.md)
- [SHA-256 Checksums & APK Signature Verification](sha256-signature-verification.md)
