# Installing Unknown Apps, Sideloading & Google's 24-Hour Verification Flow (2026/2027)

Android allows installing apps outside the Google Play Store (**sideloading**). However, starting with Google's regional enforcement on **September 30, 2026** and its **global rollout in January 2027**, certified Android devices introduce additional verification hurdles for independent and open-source (FOSS) apps.

This guide explains **both** the classic Android installation permission and how to permanently unlock Google's new **24-hour Developer Options verification flow**.

---

## 1. Standard Android Installation ("Install Unknown Apps")

On standard Android 10–17 devices without the unverified-developer block active:

1. **Download the signed APK** from [ApkUp Downloads](/apkup/downloads/) or [GitHub Releases](https://github.com/DurchEins/apkup/releases/latest).
2. Tap the downloaded `ApkUp-v....apk` in your browser or file manager.
3. If Android shows *"For your security, your phone currently isn't allowed to install unknown apps from this source"*:
   - Tap **Settings** in the prompt.
   - Toggle **Allow from this source** ON.
   - Return and tap **Install**.
4. **Once ApkUp is installed:** The first time you update an app inside ApkUp (without Shizuku/Root), Android will ask you once to grant **Install unknown apps** to **ApkUp** itself.

---

## 2. Google's 2026/2027 Developer Verification & The 24-Hour Waiting Period ("Advanced Flow")

### What Changed?

Under Google's **Android Developer Verification** policy (enforced via Google Play Services and Google Play Protect):
- Google requires developers to submit government-issued ID and pay a registration fee to link their signing keys to a Google-verified identity.
- Apps signed by **unregistered independent developers** or **compiled from source by FOSS repositories like F-Droid** are flagged as *"from an unverified developer"* and blocked by default.

### Why F-Droid & FOSS Developers Criticize This Policy

Open-source ecosystems such as **F-Droid**, independent GitHub/Codeberg developers, and privacy advocates have criticized this policy because:
- **Reproducible FOSS Builds:** F-Droid builds open-source apps directly from public source code using dedicated repository signing keys, meaning F-Droid cannot register thousands of independent upstream package IDs under a single Google identity.
- **Right to Anonymity & Freedom:** Free software developers should not be forced to submit government IDs or pay Google just so users can install open-source code on their own hardware.

### Step-by-Step: How to Unlock "Apps from Unverified Developers" (24h Flow)

If Play Protect blocks an open-source APK with an *"Unverified developer"* notice, Google places the override inside **Developer Options** behind a one-time **24-hour cooldown**:

1. **Enable Developer Options:**
   - Open **Settings → About Phone**.
   - Scroll to **Build Number** and tap it **7 times** until *"You are now a developer!"* appears.
2. **Open the Unverified Developers Setting:**
   - Go to **Settings → System → Developer Options** (or search for *Developer Options* in Settings).
   - Tap **Apps from unverified developers** → **Allow apps from unverified developers**.
3. **Confirm Lock Screen & Restart:**
   - Enter your PIN/password/biometrics and **restart your phone** when prompted.
4. **Wait the 24-Hour Cooldown:**
   - Android starts a mandatory **24-hour waiting period** (designed by Google as a scam "cooling-off" timer).
5. **Choose "Allow Indefinitely" (Important!):**
   - After 24 hours, return to **Developer Options → Apps from unverified developers**.
   - Select **Allow indefinitely** (do **not** pick the *7-day temporary window*, or Android will turn the setting back off after 7 days and force another 24-hour wait!).

---

## 3. Power-User Bypass: Installing via Shizuku, ADB, or Root (su)

Because ADB and elevated installer sessions operate as developer tools, power users can avoid repeated UI blocks:

- **Shizuku inside ApkUp (No Root, Android 11+):** When Shizuku is running via Wireless Debugging and enabled in ApkUp Setup, ApkUp executes package installations through an elevated `PackageInstaller` session in the background.
- **Direct ADB from PC:**
  ```bash
  adb install -r ApkUp-v1.45.0-211.apk
  ```
- **Root (`su`):** Devices with Magisk, KernelSU, or APatch install updates directly via root package manager calls without consumer installer prompts.

### Related Guides

- [Dedicated Sideloading & 24h Fix Page](/apkup/sideloading/)
- [Setting up Shizuku (Quick Guide)](/apkup/wiki/#doc-shizuku)
- [SHA-256 Checksums & APK Signature Verification](/apkup/wiki/#doc-sha256)
