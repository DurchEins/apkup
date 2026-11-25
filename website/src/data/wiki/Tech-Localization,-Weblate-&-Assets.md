# 🌍 Technical Overview: Localization, Weblate & Asset Pipeline

This page documents ApkUp's 9-language localization architecture, Weblate synchronization, in-app changelog asset structure, and vector/SVG assets.

---

## 1. 9-Language Localization (`strings.xml` & `website/src/i18n/`)

- **Supported Locales:** English (`values`), German (`values-de`), Spanish (`values-es`), French (`values-fr`), Italian (`values-it`), Japanese (`values-ja`), Portuguese (`values-pt`), Russian (`values-ru`), and Simplified Chinese (`values-zh-rCN`).
- **Hosted Weblate Integration:** Community translations sync via [Hosted Weblate](https://hosted.weblate.org/engage/apkup/), while developer PRs keep all 9 `strings.xml` files and `website/src/i18n/*.json` synchronized.
- **Automated Parity Checks:** `AccessibilityAuditTest` and `lintDebug` (`UnusedResources` / missing translations) verify string consistency across locales.

---

## 2. In-App Changelog Asset Pipeline (`app/src/main/assets/changelog/`)

- **Milestone Asset Pairs:** Each minor milestone maintains `<Major>.<Minor>_de.md` and `<Major>.<Minor>_en.md` (e.g. `1.47_de.md` and `1.47_en.md`), removing superseded milestone files to keep APK assets clean.
- **Parser (`ChangelogBottomSheet.kt`):**
  - Groups releases under collapsible month headers (`## Month Year`) derived from real Git release dates.
  - Parses release metadata (`### Date | Version | Badge`), optional visual highlight banners (`> [Banner] Title | Badge`), main bullets (`- **Title** Description`), and sub-bullets (`- ↳ **Title** Description`).
  - Strips accidental markdown artifacts (`cleanInlineMarkdown`) to keep UI text clean.

---

## 3. Metadata & Vector Assets

- **Fastlane Store Metadata (`fastlane/metadata/android/`):** Maintains `title.txt`, `short_description.txt`, `full_description.txt`, changelogs, and screenshots for store/mirror publishing.
- **Adaptive Dark/Light SVGs (`.github/assets/`):** `README.md` uses `<picture>` elements with `prefers-color-scheme` media queries for crisp dark/light header banners, dividers, and footer pills.
