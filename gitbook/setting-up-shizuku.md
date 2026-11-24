---
description: "Quick setup checklist to enable silent background app updates in ApkUp using Shizuku via Wireless Debugging, USB ADB, or Root."
icon: bolt
---

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

## 2. Choose Your Shizuku Start Method

{% tabs %}
{% tab title="Wireless Debugging (Android 11+, No PC)" %}
{% stepper %}
{% step %}
### Install Shizuku

Install **Shizuku** from Google Play, F-Droid, or [GitHub Releases](https://github.com/RikkaApps/Shizuku/releases).
{% endstep %}
{% step %}
### Enable Developer Options & Wi-Fi

Open **Android Settings ➔ About Phone** and tap **Build Number** 7 times, then connect your phone to Wi-Fi.
{% endstep %}
{% step %}
### Pair with Wireless Debugging

Open **Shizuku** ➔ **Pairing** ➔ **Developer Options** ➔ enable **Wireless Debugging**, tap its title, choose **Pair device with pairing code**, and enter the 6-digit code in the Shizuku notification.
{% endstep %}
{% step %}
### Start Shizuku

Return to **Shizuku** and tap **Start**.

{% hint style="success" %}
You only pair once. After a reboot, simply open Shizuku and tap **Start** (takes ~3 seconds).
{% endhint %}
{% endstep %}
{% endstepper %}
{% endtab %}

{% tab title="PC / Mac (USB ADB)" %}
For Android 10 or when Wireless Debugging is unavailable:

1. Enable **USB Debugging** in **Developer Options** and connect via USB.
2. Run the command shown inside Shizuku:

```bash
adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh
```
{% endtab %}

{% tab title="Rooted Devices" %}
If your device uses Magisk, KernelSU, or APatch:

1. Open **Shizuku** ➔ tap **Start (for rooted devices)** and grant superuser permission.

*(Alternatively, ApkUp supports its own optional **Root Installer (`su`)** under **Setup ➔ Installation & Automation** — see [Section 3.10](shizuku-background-installation.md#310-optional-root-installer-su-fallback).)*
{% endtab %}
{% endtabs %}

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
