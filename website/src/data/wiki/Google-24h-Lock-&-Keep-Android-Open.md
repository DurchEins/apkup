# Google's 24-Hour Sideloading Lock (2026/2027) & Keep Android Open

Starting with Google's regional enforcement on **September 30, 2026** (including Brazil, Indonesia, Singapore, and Thailand) and its **global rollout in January 2027**, certified Android devices introduce mandatory developer identity verification via Google Play Protect. This page explains what changes for everyday users, how to permanently unlock independent apps in Developer Options, and why ApkUp supports **[Keep Android Open](https://keepandroidopen.org)**.

---

## 1. What Changed — And When?

Under Google's **Android Developer Verification** policy (enforced via Google Play Services and Google Play Protect):
- **September 30, 2026 (Pilot Regions):** Google Play Protect began blocking APKs from unregistered developers by default in pilot markets.
- **January 2027 (Global Rollout):** Enforcement expands worldwide to certified Android devices — even for apps never distributed through Google Play.
- **Mandatory ID & Key Binding:** Developers are required to submit government-issued ID, pay a registration fee, and link their package IDs and signing keys to a Google identity.

---

## 2. Step-by-Step: Unlock "Apps from Unverified Developers" (24h Flow)

If Play Protect blocks an independent APK with an *"Unverified developer"* notice, Google places the override inside **Developer Options** behind a one-time **24-hour cooldown**:

1. **Enable Developer Options:**
   - Open **Settings → About Phone** (on Samsung: *Software information*).
   - Scroll to **Build Number** and tap it **7 times** until *"You are now a developer!"* appears.
2. **Open the Unverified Developers Setting:**
   - Go to **Settings → System → Developer Options** (or search for *Developer Options* in Settings).
   - Tap **Apps from unverified developers** → **Allow apps from unverified developers**.
3. **Confirm Lock Screen & Restart:**
   - Enter your PIN/password/biometrics and **restart your phone** when prompted.
4. **Wait the 24-Hour Cooldown:**
   - Android starts a mandatory **24-hour waiting period** after reboot.
5. **Choose "Allow Indefinitely" (Avoid the 7-Day Trap!):**
   - After 24 hours, return to **Developer Options → Apps from unverified developers**.
   - Select **Allow indefinitely** (do **not** pick the *7-day temporary window*, or Android will turn the setting back off after 7 days and force another 24-hour wait!).

---

## 3. What This Means for You & Open Escape Hatches

Without unlocking Developer Options, tapping direct APKs from GitHub, GitLab, Codeberg, or independent sites can be blocked by Play Protect. Once you choose **Allow indefinitely** — or install via Android's developer pipeline — ApkUp continues working seamlessly:

- **Direct ADB from PC (no 24h wait):** `adb install -r ApkUp.apk`
- **Shizuku (Android 11+, no root):** Uses Wireless Debugging & elevated `PackageInstaller` sessions inside ApkUp.
- **Root (`su`):** Installs updates directly via Magisk, KernelSU, or APatch.

### Related Guides & Campaign

- [Dedicated 24h Lock & Keep Android Open Page](/apkup/sideloading/google-lock/)
- [Keep Android Open Campaign](https://keepandroidopen.org) • [Campaign Banner](https://keepandroidopen.org/banner/)
- [Installing Unknown Apps (General Wiki)](/apkup/wiki/#doc-sideloading)
- [Root & Background Installation](/apkup/wiki/#doc-shizuku-deep)
- [Setting up Shizuku (Quick Guide)](/apkup/wiki/#doc-shizuku-setup)
