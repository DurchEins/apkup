---
description: "Verify ApkUp APK SHA-256 checksums locally on Windows, Linux, and macOS, plus automatic v2/v3 signing certificate protection."
icon: shield-check
---

# SHA-256 Checksums & APK Signature Verification

Every APK you download from GitHub Releases or a mirror can be verified in seconds before you install it. This guide explains what **SHA-256** and **APK signing certificates** are, why they protect you, and how to check them step by step.

---

## What is a SHA-256 Checksum?

A **SHA-256 checksum** (also called a hash or digest) is a unique **64-character digital fingerprint** calculated from the exact bytes of an APK file.

- **Tamper detection:** Changing even a single bit inside an APK produces a completely different 64-character hash.
- **Download integrity:** If a download is corrupted, incomplete, or modified by a third-party mirror, the SHA-256 hash will not match the official `SHA256SUMS` published by ApkUp's GitHub Actions release pipeline.
- **Single-Artifact Principle:** All official ApkUp releases are built and signed once by GitHub Actions ([release.yml](https://github.com/DurchEins/apkup/releases/latest)), so the SHA-256 hash is identical everywhere.

---

## Local Verification (Step-by-Step)

You can verify the downloaded `.apk` file on your PC or Mac in 10 seconds before transferring or installing it:

{% tabs %}
{% tab title="Windows (PowerShell)" %}
1. Open **PowerShell** in your Downloads folder (`cd $HOME\Downloads`).
2. Run:

```powershell
Get-FileHash .\ApkUp-*.apk -Algorithm SHA256
```

3. Compare the printed `Hash` with the checksum shown on [ApkUp Downloads](https://durcheins.github.io/apkup/downloads/) or in `SHA256SUMS` on GitHub Releases.
{% endtab %}

{% tab title="Linux" %}
1. Download both the `.apk` and `SHA256SUMS` from [GitHub Releases](https://github.com/DurchEins/apkup/releases/latest) into the same folder.
2. Run:

```bash
sha256sum -c SHA256SUMS
```

3. Look for **`ApkUp-v....apk: OK`**.
{% endtab %}

{% tab title="macOS" %}
1. Open **Terminal** in your Downloads folder (`cd ~/Downloads`).
2. Run:

```bash
shasum -a 256 ApkUp-*.apk
```

3. Compare the output hash with the official hash on [ApkUp Downloads](https://durcheins.github.io/apkup/downloads/).
{% endtab %}
{% endtabs %}

---

## Automatic Protection Inside ApkUp (APK Signature v2/v3)

You do **not** have to run manual checks when updating apps inside ApkUp:

- **Signing Certificate Pinning:** Before installing any app update, ApkUp cryptographically verifies the APK's **v2/v3 signing certificate** against the currently installed app. Even if a mirror served a modified APK, Android and ApkUp reject it.
- **SHA-256 & F-Droid Index Verification:** ApkUp validates `SHA-256` digests and signed F-Droid Index V2 metadata automatically during downloads.
- **Downgrade & ABI Guards:** Version downgrades and incompatible ABIs/minSdk builds are blocked before installation.

### Related Links

- [Downloads, Live SHA-256 & All Mirrors](https://durcheins.github.io/apkup/downloads/)
- [Security Policy](https://durcheins.github.io/apkup/security/)
- [GitHub Releases (Canonical APK + SHA256SUMS)](https://github.com/DurchEins/apkup/releases/latest)
