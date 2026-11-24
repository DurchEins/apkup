## Summary
<!-- Briefly describe what this PR changes and why. -->

## Related Issue
<!-- Link any related issue, e.g. Fixes #123 -->

## Type of Change
- [ ] 🐛 Bug fix
- [ ] ✨ New feature
- [ ] 🎨 UI / Material 3 Expressive polish
- [ ] 🔒 Security / privacy improvement
- [ ] 🌐 Translations / i18n
- [ ] 📖 Documentation / website

## Testing Environment
- **Tested on:** <!-- e.g. Physical device (Pixel 9, Android 16) / Emulator (API 35) -->
- **Install mode tested:** <!-- e.g. Standard / Shizuku / Root (su) -->

## Checklist
- [ ] `./gradlew lintDebug testDebugUnitTest assembleDebug` passes locally
- [ ] `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.apkup.ui.ComposeAccessibilityChecksTest` passes on a device/emulator
- [ ] *(If `website/` changed)* `cd website && npm run build && npm run check:a11y` passes with zero errors (AA for Light/Dark + AAA `>= 7:1` for Solar A11y tokens)
- [ ] **Accessibility (`a11y`):** Interactive controls have semantic labels (`contentDescription` / ARIA), >= 48dp / 44px hit targets, keyboard focus support, and >= 4.5:1 AA contrast (>= 7:1 AAA in Accessibility mode)
- [ ] **Security & Privacy:** No new unconsented network requests, trackers, permissions, or hardcoded secrets
- [ ] **Translations:** UI string changes are placed in `app/src/main/res/values/strings.xml` (translations are managed via Hosted Weblate)
- [ ] **Screenshots:** Before/after screenshots attached (for UI changes)
- [ ] *(Optional)* **AI-Assisted:** AI tools were used to assist with this PR; all changes have been personally reviewed, tested, and verified
