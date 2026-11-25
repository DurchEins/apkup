# 🎨 Technical Overview: UI, Accessibility & Website Architecture

This page documents ApkUp's Jetpack Compose Material 3 Expressive architecture, WCAG 2.2 AA/AAA Accessible Mode (`a11yAccessibleUi`), and the Astro website pipeline.

---

## 1. Material 3 Expressive & Adaptive Navigation

- **Theme Tokens (`ApkUpTheme`):** Unified Brand-Amber palette with soft off-white light mode, optional Android 12+ Dynamic Color (Material You), dark/light/system themes, and `Themer` state flows.
- **Adaptive Chrome:** Floating capsule bottom dock on compact screens with automatic `NavigationRail` on wider/tablet layouts (`screenWidthDp >= 840`), collapsible scroll headers, wavy progress indicators, and `CustomTabsIntent` routing (preferring native installed apps first).
- **Single Permanent Update Card Layout:** Update cards use the full-detail layout (`UpdateCard`) with `TrustShieldBadge` (animated flip on verification) and `MissingPreDownloadHashBadge` (`TooltipBox` + `PlainTooltip` when `expectedSha256` is absent).

---

## 2. WCAG 2.2 Accessible Mode (`a11yAccessibleUi` & `THEME_ACCESSIBILITY`)

- **Accessible UI Mode (`ApkUpTheme.isAccessibleUi`):**
  - Replaces the floating capsule dock with a fixed, non-overlapping `AccessibleDockedBottomBar` on phones, placing notification badges cleanly beside the icon.
  - Activates `AccessibleTypography` (minimum 13 sp for informational text) and disables decorative animations (`ApkUpTheme.isReducedMotion`) while preserving haptic feedback.
  - Enforces single-column lists in **Apps** and **Updates**, `FlowRow` multi-line filter chips (`48.dp` min touch target), labeled scroll-to-top/bottom controls, explicit one-tap **Select** buttons on update cards, and separated **Update all** + **More actions** buttons.
  - Uses `LiveRegionMode.Polite` for status summaries, `heading()` semantics on section titles, vertically stacked confirmation dialog actions, and drag-handle-free accessible bottom sheets (`HiddenAppsBottomSheet`, `IgnoredVersionsBottomSheet`, `ChangelogBottomSheet`).
- **High-Contrast Solar Theme (`THEME_ACCESSIBILITY` & `SolarScheduleCalculator`):**
  - Supports `Auto` (sunrise/sunset), `Light`, and `Dark` sub-modes with `-15 min` / `+15 min` quick steppers and `TimePickerDialog` customization.
  - Automatically pauses Dynamic Color when `a11yAccessibleUi` is enabled so guaranteed high-contrast ratios cannot be overridden by wallpaper colors.

---

## 3. Astro Website (`website/`) & `check-a11y.mjs`

- **9-Language Static Site (`docs/`):** Built with Astro across `en`, `de`, `es`, `fr`, `it`, `ja`, `pt`, `ru`, and `zh-rCN`, including the dedicated `/accessibility/` statement page.
- **3-Way WCAG AAA Solar Switcher:** Supports `Auto / Light / Dark` with `>= 7:1` AAA contrast tokens, ARIA landmark/dialog semantics, and 320 px responsive reflow.
- **Automated Gate (`website/scripts/check-a11y.mjs`):** Audits all 90 built HTML pages for AA/AAA contrast tokens, ARIA attributes, heading hierarchy, and semantic integrity.

---

> 📖 **User-friendly guide:** [Accessibility & Accessible Mode](https://github.com/DurchEins/apkup/wiki/Accessibility-%26-Accessible-Mode) • **Full Statement:** [`ACCESSIBILITY.md`](https://github.com/DurchEins/apkup/blob/main/ACCESSIBILITY.md)
