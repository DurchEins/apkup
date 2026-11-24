<a id="top"></a>

# Accessibility Statement & Technical Guide

**Last Full Audit:** November 24, 2026  
**Target Standards:** WCAG 2.2 Level AA (with AAA contrast & target sizes where practical), EN 301 549, Android Accessibility Guidelines, Material 3 Expressive, and WAI-ARIA 1.2.

> [!NOTE]
> **Found an accessibility barrier?** Please open a dedicated **[Accessibility Barrier Issue](https://github.com/DurchEins/apkup/issues/new?template=accessibility.yml)** or view the localized web statement on **[ApkUp Accessibility](https://durcheins.github.io/apkup/accessibility/)**.
>
> **A personal note from the solo developer:** ApkUp is developed by a single independent developer. I strive to implement every feature as accessibly as possible to the best of my knowledge and conscience — while continuously learning along the way. Accessibility across the app and website is an ongoing effort, and I am genuinely grateful for every single tip, hint, or helping hand from the community!

---

## 📱 Android App Accessibility (`app/`)

ApkUp is engineered with Jetpack Compose semantics and Material 3 components so users can operate every workflow seamlessly using **TalkBack**, **Voice Access**, **Switch Access**, **hardware keyboards**, **system display/font scaling**, and **Reduced Motion**:

| Area | Implemented Measures |
| :--- | :--- |
| **Screen Readers (`TalkBack`)** | Explicit `contentDescription` on interactive icons and status controls; explicit `contentDescription = null` on decorative images inside merged cards; `Modifier.semantics(mergeDescendants = true)` on update cards, onboarding toggles, scan pills, and list items; `heading()` roles on section titles; `LiveRegionMode.Polite` status updates on batch queues and progress banners. |
| **Voice Access (`WCAG 2.5.3` Label-in-Name)** | Accessible labels preserve the exact visible action text first (e.g., Details/Install buttons on update cards include visible labels plus the app name to disambiguate repeated buttons in long lists). |
| **Motor & Keyboard Alternatives (`WCAG 2.5.7`)** | Click and `CustomAccessibilityAction` alternatives for pull-to-refresh, long-press multi-selection (`UpdateCard`, `CompactUpdatesGrid`), and drag-and-drop source ordering (explicit Move Up / Move Down buttons with `minimumInteractiveComponentSize()`). |
| **Touch Targets & Traversal** | `48.dp` minimum interactive target sizing via `Modifier.minimumInteractiveComponentSize()` on action buttons, sheet controls, and list actions; `selectableGroup()` + `Role.RadioButton` / `Role.Tab` / `Role.Switch` semantics without nested toggle traps. |
| **Contrast, Scaling & Reduced Motion** | Verified Light and Dark Material 3 color schemes with >= 4.5:1 AA contrast (updated Light primary/warning `#92400E` on off-white surfaces); dynamic system font scaling; optional Dynamic Color; `ApkUpTheme.isReducedMotion` support across transitions and animations. |
| **Localization (`i18n`)** | Accessibility state strings (`a11y_state_expanded`, `a11y_state_collapsed`, `a11y_clear_search`, `a11y_close_search`, `a11y_select_item`, `a11y_deselect_item`, `updates_more_actions_cd`) are translated across all **9 supported languages** (`en`, `de`, `es`, `fr`, `it`, `ja`, `pt`, `ru`, `zh-cn`). |

---

## 🌐 Website & Documentation Accessibility (`website/` & `docs/`)

The companion Astro static website (`https://durcheins.github.io/apkup/`) is audited across **all 90 generated pages** (10 routes × 9 locales):

| Area | Implemented Measures |
| :--- | :--- |
| **Landmarks & Keyboard Navigation** | Skip link (`#main-content`), single `<h1>` per page, `<html lang="...">` on every route, `aria-current="page"` navigation indicators, full `Arrow`/`Home`/`End`/`Escape` keyboard support in the Showcase tablist and Footer language menu, and automatic focus return on mobile navigation and modal dialogs (`#qr-modal`, `#shot-lightbox`). |
| **Interactive Widgets & No Nesting** | Valid WAI-ARIA 1.2 roles without nested interactive controls inside `<summary>` (`WikiPage.astro` TOC & `GoogleLockPage.astro` step checkboxes refactored into standalone `<button role="checkbox">` toggles); live status updates via persistent `aria-live="polite"` regions; keyboard-focusable scrollable code blocks and comparison tables (`tabindex="0" role="region"`). |
| **Motion & Carousels (`WCAG 2.2.2`)** | Visible Pause/Play toggle (`#showcase-pause-btn`) in `ScreenShowcase.astro`, automatic pause on hover/focus/tab selection, and full `prefers-reduced-motion: reduce` disables transitions, Easter-egg particle sparks, and auto-rotation. |
| **Light & Dark Contrast + 320px Reflow** | Audited Light and Dark CSS variables (`tokens.css`) plus `:global([data-theme='light'])` overrides across badges, warnings, match/mismatch pills, and copied indicators; flexible container reflow at 320 px viewport width and 400% zoom without horizontal page scrolling. |

---

## 🧪 Automated & Manual Verification

Run accessibility verification locally before submitting changes:

```bash
# 1. Android Lint (strict a11y rules in app/lint.xml) + Kotlin a11y/i18n unit tests + Debug APK
./gradlew lintDebug testDebugUnitTest assembleDebug

# 2. Website static build + 90-page HTML/ARIA/token contrast check
cd website
npm run build
npm run check:a11y
```

---

## ⚠️ Known Limitations

- **Third-Party Upstream Changelogs:** Release notes fetched live from external repositories (GitHub, GitLab, Codeberg, F-Droid) are authored by upstream developers and may include unformatted raw markdown or external images.
- **Android System Flows:** System dialogs for *Install unknown apps*, Developer Options (*24-hour unverified developer lock*), and *Shizuku* pairing are controlled by the device OEM and Android OS.
- **Continuous Hardware Validation:** While static checks, Compose semantics tests, contrast audits, and desktop/mobile browser checks run locally, physical on-device verification across additional OEM Android skins with TalkBack and Voice Access remains an ongoing process.
