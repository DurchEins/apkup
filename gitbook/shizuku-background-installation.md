---
description: "Deep dive into silent batch installs, WorkManager background checks, optional Root (su) fallback, and Android 14+ BAL handling."
icon: gears
---

# Shizuku & Silent Background Installations in ApkUp

← Back to [Wiki Home](home.md) · Beginner Walkthrough: [Setting up Shizuku](setting-up-shizuku.md)

This document explains how to set up and how **Shizuku** and the background installation service work in ApkUp — explained in plain, everyday language in Part 1, followed by a deep-dive technical reference for power users and developers in Part 2.

---

<a id="-table-of-contents"></a>
<a name="-table-of-contents"></a>
## 📑 Table of Contents
1. [🟢 Part 1: Explained Simply for Everyone (Plain Language)](#-part-1-explained-simply-for-everyone-plain-language)
   - [1.1 Why does Android normally ask for confirmation on every update?](#11-why-does-android-normally-ask-for-confirmation-on-every-update)
   - [1.2 What is Shizuku and how does it benefit me?](#12-what-is-shizuku-and-how-does-it-benefit-me)
   - [1.3 Step-by-Step Guide: Setting up Shizuku without Root](#13-step-by-step-guide-setting-up-shizuku-without-root)
   - [1.4 Enabling Shizuku in ApkUp (The 3-Point Status Check)](#14-enabling-shizuku-in-apkup-the-3-point-status-check)
   - [1.5 What happens if my screen turns off or I leave the app?](#15-what-happens-if-my-screen-turns-off-or-i-leave-the-app)
2. [💻 Part 2: Technical Documentation for Experts & Power Users](#-part-2-technical-documentation-for-experts--power-users)
   - [2.1 Architecture of the Dual-Path Installation Pipeline](#21-architecture-of-the-dual-path-installation-pipeline)
   - [2.2 IPC Communication via Android Binder](#22-ipc-communication-via-android-binder)
   - [2.3 PackageManager Shell Pipeline & Play Store Attribution (`com.android.vending`)](#23-packagemanager-shell-pipeline--play-store-attribution-comandroidvending)
   - [2.4 Foreground Service & Android 14+ BAL Resilience](#24-foreground-service--android-14-bal-resilience)
3. [❓ Part 3: Comprehensive Frequently Asked Questions (FAQ) & Common Issues](#-part-3-comprehensive-frequently-asked-questions-faq--common-issues)
   - [3.1 Do I need Root access on my phone to use Shizuku?](#31-do-i-need-root-access-on-my-phone-to-use-shizuku)
   - [3.2 Will Shizuku void my warranty or break banking apps?](#32-will-shizuku-void-my-warranty-or-break-banking-apps)
   - [3.3 Why do I have to restart Shizuku after rebooting my phone?](#33-why-do-i-have-to-restart-shizuku-after-rebooting-my-phone)
   - [3.4 Issue: "Wireless debugging" is grayed out and cannot be turned on!](#34-issue-wireless-debugging-is-grayed-out-and-cannot-be-turned-on)
   - [3.5 Issue: Shizuku searches endlessly for the pairing service or the code input never appears!](#35-issue-shizuku-searches-endlessly-for-the-pairing-service-or-the-code-input-never-appears)
   - [3.6 Issue: Shizuku stops by itself in the background after a while (Xiaomi, Samsung, OnePlus)!](#36-issue-shizuku-stops-by-itself-in-the-background-after-a-while-xiaomi-samsung-oneplus)
   - [3.7 What happens if Shizuku stops while ApkUp is downloading an update?](#37-what-happens-if-shizuku-stops-while-apkup-is-downloading-an-update)
   - [3.8 Does "Update All" also work without Shizuku?](#38-does-update-all-also-work-without-shizuku)
   - [3.9 Why does my phone recognize apps installed via Shizuku as "Installed from Google Play Store"?](#39-why-does-my-phone-recognize-apps-installed-via-shizuku-as-installed-from-google-play-store)
4. [🔗 Part 4: Official Links & Further Resources](#-part-4-official-links--further-resources)

---

<a id="-part-1-explained-simply-for-everyone-plain-language"></a>
<a name="-part-1-explained-simply-for-everyone-plain-language"></a>
## 📖 User Guide

<a id="11-why-does-android-normally-ask-for-confirmation-on-every-update"></a>
<a name="11-why-does-android-normally-ask-for-confirmation-on-every-update"></a>
### 1.1 Why does Android normally ask for confirmation on every update?
For security reasons, Android does not allow regular apps to silently install other apps in the background. If you start 10 updates in an app without special system permissions, Android pops up a confirmation dialog **10 times in a row** (*"Do you want to update this app? [Cancel] [Update]"*). You have to tap every single dialog manually.

[⬆️ Back to Table of Contents](#-table-of-contents)

---

<a id="12-what-is-shizuku-and-how-does-it-benefit-me"></a>
<a name="12-what-is-shizuku-and-how-does-it-benefit-me"></a>
### 1.2 What is Shizuku and how does it benefit me?
**[Shizuku](https://shizuku.rikka.app/)** is a free, open-source companion app for Android. It uses an official built-in Android system feature (*Wireless Debugging*) to grant trusted apps like **ApkUp** the exact same installation privileges that normally only the pre-installed Google Play Store has.

**Your benefits with Shizuku in ApkUp:**
- ⚡ **One tap on "Update All" is enough:** All available updates are downloaded and **installed completely automatically in the background**, without you having to tap a single confirmation popup!
- 🛡️ **No Root required:** Your smartphone stays completely unmodified.

[⬆️ Back to Table of Contents](#-table-of-contents)

---

<a id="13-step-by-step-guide-setting-up-shizuku-without-root"></a>
<a name="13-step-by-step-guide-setting-up-shizuku-without-root"></a>
### 1.3 Step-by-Step Guide: Setting up Shizuku without Root

> 💡 **New to Shizuku?** For a dedicated, screenshot-friendly beginner walkthrough, see **[Setting up Shizuku](setting-up-shizuku.md)**.
On Android 11 and newer, you can set up Shizuku directly on your phone without a PC (see also the [official Shizuku user guide](https://shizuku.rikka.app/guide/setup/)):

1. **Step 1 – Download Shizuku:**  
   Install the official **Shizuku** app for free from the [Google Play Store](https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api) or via [GitHub](https://github.com/RikkaApps/Shizuku/releases).
2. **Step 2 – Enable Developer Options:**  
   Open **Android Settings ➔ About phone**. Tap the **"Build number"** at the bottom **7 times quickly in a row** until you see the message: *"You are now a developer!"*.
3. **Step 3 – Pair Wireless Debugging (one-time setup):**  
   - Connect your phone to a Wi-Fi network.
   - Open the **Shizuku app** and tap **"Pairing"** under *"Start via Wireless debugging"*.
   - Tap the **"Developer options"** button.
   - Scroll down to **"Wireless debugging"**, toggle it on, and tap directly on the text *"Wireless debugging"* to open its sub-menu.
   - Select **"Pair device with pairing code"** and enter the displayed 6-digit code into the Shizuku notification at the top of your screen.
4. **Step 4 – Start Shizuku:**  
   Go back to the Shizuku app and tap **"Start"**. After 2–3 seconds, Shizuku will show *"Shizuku is running"* at the top. *(After that, you can even turn Wi-Fi off again — Shizuku will keep running!)*

[⬆️ Back to Table of Contents](#-table-of-contents)

---

<a id="14-enabling-shizuku-in-apkup-the-3-point-status-check"></a>
<a name="14-enabling-shizuku-in-apkup-the-3-point-status-check"></a>
### 1.4 Enabling Shizuku in ApkUp (The 3-Point Status Check)
Open **ApkUp** and navigate to **Setup ➔ Installation & Automation**. There you will see a clear status indicator with three checkpoints:

| Status Checkpoint | Meaning | What to do if inactive? |
| :--- | :--- | :--- |
| **1. Shizuku installed** | Checks whether the Shizuku app is installed on your phone. | Install Shizuku from the [Google Play Store](https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api). |
| **2. Service running (Binder)** | Checks whether Shizuku has been started in the background. | Open the Shizuku app and tap *"Start"*. |
| **3. Permission granted** | Checks whether ApkUp is allowed to access Shizuku. | Toggle the switch in ApkUp and tap *"Allow all the time"* in the popup. |

Once all three checkpoints light up green, all installations in ApkUp run completely silently and automatically!

[⬆️ Back to Table of Contents](#-table-of-contents)

---

<a id="15-what-happens-if-my-screen-turns-off-or-i-leave-the-app"></a>
<a name="15-what-happens-if-my-screen-turns-off-or-i-leave-the-app"></a>
### 1.5 What happens if my screen turns off or I leave the app?
Since version **v1.32**, ApkUp includes a dedicated background download service (*Foreground Download Service*):
- When you start a large update or tap *"Update All"*, you can immediately minimize ApkUp, use other apps, or lock your screen.
- In your notification shade, you can always see which app is currently downloading, complete with a percentage progress bar and a **"Cancel"** button.
- **With Shizuku:** Apps finish installing silently even while your screen is turned off.
- **Without Shizuku:** As soon as the background download finishes, ApkUp sends you a notification (*"Ready to install"*) that you simply tap once to confirm.

[⬆️ Back to Table of Contents](#-table-of-contents)

---

<a id="-part-2-technical-documentation-for-experts--power-users"></a>
<a name="-part-2-technical-documentation-for-experts--power-users"></a>
## ❓ Frequently Asked Questions (FAQ)

<a id="31-do-i-need-root-access-on-my-phone-to-use-shizuku"></a>
<a name="31-do-i-need-root-access-on-my-phone-to-use-shizuku"></a>
### 3.1 Do I need Root access on my phone to use Shizuku?
**No!** Starting with Android 11, Shizuku works completely **without Root** using Android's built-in *Wireless Debugging* feature. However, if your device is rooted (e.g., via Magisk, KernelSU, or APatch), Shizuku can optionally be started directly via Root — which is even faster and requires no pairing code.

---

<a id="32-will-shizuku-void-my-warranty-or-break-banking-apps"></a>
<a name="32-will-shizuku-void-my-warranty-or-break-banking-apps"></a>
### 3.2 Will Shizuku void my warranty or break banking apps?
**No, your warranty remains 100% intact and all banking apps continue to work normally.** Because Shizuku neither unlocks the bootloader nor modifies system partitions (Root), Google's security checks (*Play Integrity / SafetyNet*) and Samsung Knox remain completely untouched.

---

<a id="33-why-do-i-have-to-restart-shizuku-after-rebooting-my-phone"></a>
<a name="33-why-do-i-have-to-restart-shizuku-after-rebooting-my-phone"></a>
### 3.3 Why do I have to restart Shizuku after rebooting my phone?
If your phone is not rooted, Android terminates all debugging services whenever the device is completely powered off or restarted for security reasons.
- You do **not** have to repeat the 6-digit pairing process!
- After rebooting your phone, simply open the Shizuku app and tap **"Start"** once (takes about 3 seconds).

---

<a id="34-issue-wireless-debugging-is-grayed-out-and-cannot-be-turned-on"></a>
<a name="34-issue-wireless-debugging-is-grayed-out-and-cannot-be-turned-on"></a>
### 3.4 Issue: "Wireless debugging" is grayed out and cannot be turned on!
**Cause:** Android only allows enabling *Wireless debugging* while your smartphone is connected to a **Wi-Fi network**.
- **Solution:** Connect your phone briefly to your home Wi-Fi (or a mobile hotspot) and start Shizuku. As soon as Shizuku says *"Shizuku is running"*, you can turn Wi-Fi off again — the service will stay active!

---

<a id="35-issue-shizuku-searches-endlessly-for-the-pairing-service-or-the-code-input-never-appears"></a>
<a name="35-issue-shizuku-searches-endlessly-for-the-pairing-service-or-the-code-input-never-appears"></a>
### 3.5 Issue: Shizuku searches endlessly for the pairing service or the code input never appears!
This behavior occurs most frequently on smartphones from **Xiaomi (MIUI / HyperOS)**, **Oppo/Realme**, and **Samsung**:
1. **Notification Style (Xiaomi):** Go to Android Settings ➔ *Notifications & Control center* ➔ *Notification shade* and switch it from "MIUI" to **"Android"** (Classic). Otherwise, the text box for entering the pairing code inside the notification cannot be expanded.
2. **USB Debugging (Security settings):** On Xiaomi devices, open Developer options and enable **"USB debugging (Security settings)"** in addition to standard *USB debugging*.
3. **Notification Permission:** Make sure you have granted the Shizuku app permission to display notifications.

---

<a id="36-issue-shizuku-stops-by-itself-in-the-background-after-a-while-xiaomi-samsung-oneplus"></a>
<a name="36-issue-shizuku-stops-by-itself-in-the-background-after-a-while-xiaomi-samsung-oneplus"></a>
### 3.6 Issue: Shizuku stops by itself in the background after a while (Xiaomi, Samsung, OnePlus)!
If Shizuku stops after a few hours on its own, this is caused by aggressive OEM battery-saving policies or Android 12/13's *Phantom Process Killer*:
- **Solution 1 (Battery Optimization):** Long-press the **Shizuku** app icon ➔ *App info* ➔ *Battery* and select **"Unrestricted"**.
- **Solution 2 (Developer Options on Android 13+):** In Developer options, enable the toggle **"Disable child process restrictions"**.
- You can also find manufacturer-specific instructions on [DontKillMyApp.com](https://dontkillmyapp.com/).

---

<a id="37-what-happens-if-shizuku-stops-while-apkup-is-downloading-an-update"></a>
<a name="37-what-happens-if-shizuku-stops-while-apkup-is-downloading-an-update"></a>
### 3.7 What happens if Shizuku stops while ApkUp is downloading an update?
ApkUp will not crash and your downloaded update will not be lost! If ApkUp detects that the Shizuku service is unresponsive at the moment of installation, the app **automatically and seamlessly** falls back to the standard Android package installer and shows the regular Android confirmation prompt.

---

<a id="38-does-update-all-also-work-without-shizuku"></a>
<a name="38-does-update-all-also-work-without-shizuku"></a>
### 3.8 Does "Update All" also work without Shizuku?
**Yes, of course.** Even without Shizuku, tapping *"Update All"* downloads all updates in the background. The only difference is that without Shizuku, you briefly tap *"Update"* in the Android system dialog for each downloaded app.

---

<a id="39-why-does-my-phone-recognize-apps-installed-via-shizuku-as-installed-from-google-play-store"></a>
<a name="39-why-does-my-phone-recognize-apps-installed-via-shizuku-as-installed-from-google-play-store"></a>
### 3.9 Why does my phone recognize apps installed via Shizuku as "Installed from Google Play Store"?
This is an intentional feature of ApkUp! When ApkUp installs an app via Shizuku, it passes the parameter `-i com.android.vending` (the official package identifier of the Google Play Store) to Android. Some apps otherwise refuse to run or display warnings if they detect they were updated outside the Play Store. Thanks to this attribution, all updated apps behave as if they came directly from the official store.

---

<a id="310-optional-root-installer-su-fallback"></a>
### 3.10 How does the optional Root Installer (`su`) work alongside Shizuku?
Starting with **v1.43.0+**, rooted devices (Magisk, KernelSU, or APatch) can optionally enable **Root Installer (`su`)** under **Setup ➔ Installation & Automation**:
- **Smart Fallback or Standalone:** When both Shizuku and Root are enabled, ApkUp uses Shizuku first and seamlessly falls back to `su` if Shizuku was not restarted after a reboot. If Shizuku is disabled, ApkUp uses `su` directly for silent foreground and autonomous background updates (while excluding uninstalled Watchlist apps from unattended background installs).
- **Pre-Install Integrity Verification & Origin Preservation:** Before opening a `pm install-create` session, `RootInstaller` verifies all staged base and split APKs via `ApkIntegrityVerifier` (fail-closed) and preserves the existing installer package origin (`-i`).
- **Interactive Root Check Bottom Sheet:** Tapping the **Root Installer (`su`)** row opens the dedicated `RootSetupBottomSheet` where you can verify superuser availability live at any time. For security, importing a configuration backup resets `useRootInstaller` to `false` until you re-enable it on the device.
- **Completely Optional:** On non-rooted phones, keep this option turned off and continue using Shizuku or the normal Android installer.

[⬆️ Back to Table of Contents](#-table-of-contents)

---

<a id="-part-4-official-links--further-resources"></a>
<a name="-part-4-official-links--further-resources"></a>
## 🔗 Part 4: Official Links & Further Resources

- 🌐 **Official Shizuku Website & Documentation:** [https://shizuku.rikka.app/](https://shizuku.rikka.app/)
- 📖 **Official Shizuku Setup Guide (with screenshots):** [https://shizuku.rikka.app/guide/setup/](https://shizuku.rikka.app/guide/setup/)
- ▶️ **Shizuku on Google Play Store:** [Play Store Link](https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api)
- 💻 **Shizuku on GitHub (Open Source Code & APK Releases):** [https://github.com/RikkaApps/Shizuku](https://github.com/RikkaApps/Shizuku)
- 🔋 **Preventing Background App Termination by Manufacturer (Samsung, Xiaomi, Pixel, OnePlus):** [https://dontkillmyapp.com/](https://dontkillmyapp.com/)
- 🤖 **Android Developer Documentation (Wireless Debugging):** [developer.android.com/tools/adb#wireless-android11-command-line](https://developer.android.com/tools/adb#wireless-android11-command-line)

[⬆️ Back to Table of Contents](#-table-of-contents)

---

← Back to [Wiki Home](home.md) | Related: [Setting up Shizuku](setting-up-shizuku.md) · [App Management, Filters, Background Checks & Backup](app-management-and-backup.md)

---

> 💻 **Looking for architectural internals, protocols, or security proofs?** Explore the **[Technical Overview](home.md#-technical-overview)** section.
