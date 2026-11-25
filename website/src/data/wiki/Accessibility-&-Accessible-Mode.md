# ♿ Accessibility & Accessible Mode

**ApkUp** is designed to be comfortable and usable with **TalkBack**, **Voice Access**, **Switch Access**, large display/font scaling, and high-contrast preferences — both in the Android app and on the [ApkUp Website](https://durcheins.github.io/apkup/accessibility/).

---

## 📖 User Guide

### 1.1 Turning on Accessible Mode
You can enable **Accessible Mode** in two places:
- **During First Launch (Onboarding):** Choose **Accessible** instead of **Standard** on the appearance step.
- **Anytime in Setup:** Open **Setup → Appearance & Layout → Accessible UI** (`Barrierefreie Oberfläche`) to turn it on or off immediately.

### 1.2 What Accessible Mode Changes
- **Fixed, Non-Overlapping Navigation Bar:** On phones, the bottom navigation bar stays anchored cleanly below content instead of floating over list items, with notification badges placed clearly beside the icon.
- **Single-Column Lists & Larger Tap Targets:** Both **Apps** and **Updates** switch to clean single-column lists with minimum **48 dp** touch targets, multi-line filter chips, and labeled scroll-to-top/bottom buttons.
- **One-Tap Batch Selection:** Every update card shows an explicit **Select** button so you never have to rely on long-press gestures to select updates.
- **Separated Action Buttons:** Instead of combined split buttons, **Update all** and **More actions** are shown as two separate, clearly labeled buttons.
- **Calm Status Updates & Clean Sheets:** Screen readers announce scan/download status politely without interrupting you, confirmation dialogs stack action buttons vertically, and sheets (Hidden Apps, Skipped Updates, In-App Changelog) remove tiny text, dark gradients, and drag-only handles.
- **No Motion Distractions:** Animations are disabled while gentle haptic feedback remains intact.

### 1.3 High-Contrast Solar Theme (`Auto / Light / Dark`)
Under **Setup → Appearance & Layout → Theme**, selecting **Accessibility** unlocks a dedicated high-contrast palette:
- **Auto:** Automatically switches between high-contrast Light and Dark based on sunrise/sunset. Use the **`-15 min` / `+15 min`** quick buttons or the time picker to customize solar transition times.
- **Light / Dark:** Locks the high-contrast theme permanently to Light or Dark.
- **Dynamic Color** is automatically paused when **Accessible UI** is active so guaranteed contrast ratios are never overridden by wallpaper colors.

## ❓ Frequently Asked Questions (FAQ)

- **Can I use the High-Contrast Theme without changing list layouts?**
  Yes! Select **Accessibility** under **Theme** while keeping **Accessible UI** turned off if you only want the high-contrast colors.
- **Where can I report an accessibility barrier?**
  Please open an issue on **[GitHub Issues](https://github.com/DurchEins/apkup/issues)** or share feedback in **[GitHub Discussions](https://github.com/DurchEins/apkup/discussions)** — every report helps make ApkUp better for everyone.
- **Full Statement:** Read [`ACCESSIBILITY.md`](https://github.com/DurchEins/apkup/blob/main/ACCESSIBILITY.md) in the repository.

---

> 💻 **Looking for Compose tokens, WCAG AAA contrast internals, or `check-a11y.mjs` details?** See **[Technical Overview: UI, Accessibility & Website Architecture](https://github.com/DurchEins/apkup/wiki/Tech-UI,-Accessibility-&-Website-Architecture)**.
