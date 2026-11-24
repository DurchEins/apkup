# ⚡ Setting up Shizuku for Silent Updates

[Shizuku](https://shizuku.rikka.app) lets **ApkUp** install updates silently in the background **without Root** — no more tapping "Install" and "Update" on 20 consecutive popups.

This page is the **quick setup checklist**. For the technical architecture, dual-path pipeline, and detailed FAQs, see the full **[Shizuku & Background Installation Guide](shizuku-background-installation.md)**.

---

## 📑 Quick Navigation

* [1. Why Use Shizuku with ApkUp?](#1-why-use-shizuku-with-apkup)
* [2. Method A: Wireless Debugging (Android 11+, No PC)](#2-method-a-wireless-debugging-android-11-no-pc)
* [3. Method B: PC / Mac (USB ADB)](#3-method-b-pc--mac-usb-adb)
* [4. Method C: Rooted Devices](#4-method-c-rooted-devices)
* [5. Connecting Shizuku in ApkUp](#5-connecting-shizuku-in-apkup)
* [6. Device-Specific Battery & OEM Quirks](#6-device-specific-battery--oem-quirks)

---

## 1. Why Use Shizuku with ApkUp?

* ✅ **One-Tap Batch Updates:** Update all apps with a single tap on *Update All*.
* ✅ **Silent Background Installs:** Scheduled background checks can install updates automatically while your screen is off.
* ✅ **Seamless `.apkm` / Split Bundles:** Multi-part app bundles install cleanly in one session.
* ✅ **Play Store Attribution:** Preserves Play Store installer metadata (`com.android.vending`) so Play Protect and app checks work normally.

---

## 2. Method A: Wireless Debugging (Android 11+, No PC)

1. Install **Shizuku** from Google Play, F-Droid, or [GitHub Releases](https://github.com/RikkaApps/Shizuku/releases).
2. Enable **Developer Options**: **Android Settings ➔ About Phone** ➔ tap **Build Number** 7 times.
3. Connect your phone to Wi-Fi.
4. Open **Shizuku**:
   * Under *Start via Wireless Debugging*, tap **Pairing**.
   * Tap **Developer Options** in the prompt.
   * Enable **Wireless Debugging**, tap its title to open its submenu, and tap **Pair device with pairing code**.
   * Enter the 6-digit code in the Shizuku notification prompt.
5. Return to Shizuku and tap **Start**.

> **Tip:** You only pair once. After a phone reboot, simply open Shizuku and tap **Start** (takes ~3 seconds).

---

## 3. Method B: PC / Mac (USB ADB)

For Android 10 or if Wireless Debugging is unavailable:
1. Enable **USB Debugging** in **Developer Options** and connect via USB.
2. Run the command shown inside Shizuku:
   ```bash
   adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh
   ```

---

## 4. Method C: Rooted Devices

If your device uses Magisk, KernelSU, or APatch:
1. Open **Shizuku** ➔ tap **Start (for rooted devices)** and grant superuser permission.
*(Alternatively, ApkUp also supports its own optional **Root Installer (`su`)** under **Setup ➔ Installation & Automation** — see [Section 3.10](shizuku-background-installation.md#310-optional-root-installer-su-fallback).)*

---

## 5. Connecting Shizuku in ApkUp

1. Open **ApkUp ➔ Setup ➔ Installation & Automation**.
2. Enable **Use Shizuku for background installations**.
3. Tap **Allow all the time** in the Shizuku permission prompt.
4. In **Setup ➔ System & Permissions**, set **Battery Optimization** for both **ApkUp** and **Shizuku** to **Unrestricted** so background updates aren't killed. *(When all system permissions are granted, the permissions card collapses automatically to "All granted" — tap it anytime to inspect.)*

---

## 6. Device-Specific Battery & OEM Quirks

* **Xiaomi / Redmi / POCO (MIUI & HyperOS):**
  * Enable both *USB Debugging* **and** **USB Debugging (Security Settings)** in Developer Options.
  * If the Shizuku pairing code input doesn't expand in the notification, switch **Notification shade** style from *MIUI* to **Android** temporarily.
* **Samsung (One UI):**
  * Set Battery to **Unrestricted** for both **Shizuku** and **ApkUp** under **Settings ➔ Apps**.
* **OnePlus / OPPO / Realme (ColorOS / OxygenOS):**
  * Disable *Auto-freeze* / *Background execution limits* for both **Shizuku** and **ApkUp**.
