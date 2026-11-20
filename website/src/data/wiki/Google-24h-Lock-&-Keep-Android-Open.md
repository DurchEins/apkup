# Google's 24-Hour Sideloading Lock (2026/2027) & Keep Android Open

Starting with Google's regional enforcement on **September 30, 2026** and its **global rollout in January 2027**, certified Android devices introduce mandatory developer identity verification via Google Play Protect. This page explains how to permanently unlock independent apps in Developer Options and why the open-source community supports **[Keep Android Open](https://keepandroidopen.org)**.

---

## 1. What Changed?

Under Google's **Android Developer Verification** policy (enforced via Google Play Services and Google Play Protect):
- Google requires developers to submit government-issued ID and pay a registration fee to link their signing keys and package IDs to a Google-verified identity.
- Apps signed by **unregistered independent developers** or **compiled from source by FOSS repositories like F-Droid** are flagged as *"from an unverified developer"* and blocked by default.

---

## 2. Step-by-Step: Unlock "Apps from Unverified Developers" (24h Flow)

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
   - Android starts a mandatory **24-hour waiting period** (designed by Google as a "cooling-off" timer).
5. **Choose "Allow Indefinitely" (Important!):**
   - After 24 hours, return to **Developer Options → Apps from unverified developers**.
   - Select **Allow indefinitely** (do **not** pick the *7-day temporary window*, or Android will turn the setting back off after 7 days and force another 24-hour wait!).

---

## 3. What F-Droid, FOSS Developers & Keep Android Open Say

The **[Keep Android Open](https://keepandroidopen.org)** coalition, **F-Droid**, and independent open-source maintainers criticize mandatory developer ID registration because:
- **Reproducible FOSS Builds:** F-Droid builds open-source apps directly from public source code using dedicated repository signing keys, meaning F-Droid cannot register thousands of independent upstream package IDs under a single Google identity.
- **Right to Anonymity & Software Freedom:** Free software developers should not be forced to submit government IDs or pay Google just so users can install open-source code on their own hardware.
- **Open Escape Hatches:** Installing via **ADB** (`adb install -r`) or **Shizuku** continues to use Android's developer installation pipeline without requiring Google Play Protect approval.

### Related Guides & Campaign

- [Dedicated 24h Lock & Keep Android Open Page](/apkup/sideloading/google-lock/)
- [Keep Android Open Campaign](https://keepandroidopen.org) • [Campaign Banner](https://keepandroidopen.org/banner/)
- [Installing Unknown Apps (Standard Guide)](/apkup/wiki/#doc-sideloading)
- [Setting up Shizuku (Quick Guide)](/apkup/wiki/#doc-shizuku-setup)
