export interface LangPair {
  en: string;
  de: string;
}

export const SITE = {
  repoUrl: 'https://github.com/DurchEins/apkup',
  releasesUrl: 'https://github.com/DurchEins/apkup/releases/latest',
  wikiUrl: 'https://github.com/DurchEins/apkup/wiki',
  issuesUrl: 'https://github.com/DurchEins/apkup/issues',
  securityUrl: 'https://github.com/DurchEins/apkup/blob/main/SECURITY.md',
  privacyUrl: 'https://github.com/DurchEins/apkup/blob/main/PRIVACY.md',
  licenseUrl: 'https://github.com/DurchEins/apkup/blob/main/LICENSE',
  fallbackVersion: '1.45.0',
  fallbackBuild: '211',
};

export const SOURCES = [
  {
    id: 'github',
    name: 'GitHub',
    tag: 'GraphQL + Releases',
    en: 'Built-in FOSS mappings, batched GraphQL checks with optional PAT, regex asset filters, and SHA-256 verification.',
    de: 'Integrierte FOSS-Zuordnungen, gebündelte GraphQL-Abfragen mit optionalem Token, Regex-Asset-Filter und SHA-256-Prüfung.',
  },
  {
    id: 'gitlab',
    name: 'GitLab',
    tag: 'Nested Subgroups',
    en: 'Supports gitlab.com and self-hosted instances, including multi-level subgroup paths and host-isolated tokens.',
    de: 'Unterstützt gitlab.com sowie selbst gehostete Instanzen – inklusive mehrstufiger Subgroups und host-isolierter Tokens.',
  },
  {
    id: 'codeberg',
    name: 'Codeberg / Forgejo',
    tag: 'Self-Hosted Ready',
    en: 'Track releases directly from Codeberg, Forgejo, or Gitea instances with automatic architecture matching.',
    de: 'Verfolge Releases direkt von Codeberg, Forgejo oder Gitea inklusive automatischer Geräte-Architektur-Erkennung.',
  },
  {
    id: 'fdroid',
    name: 'F-Droid Index V2',
    tag: 'RFC 7396 Diffs + TOFU',
    en: 'Native Index V2 incremental diffs with cryptographic JAR certificate verification and custom HTTPS repo presets.',
    de: 'Native inkrementelle Index-V2-Diffs mit kryptografischer JAR-Zertifikatsprüfung und benutzerdefinierten HTTPS-Repos.',
  },
  {
    id: 'izzy',
    name: 'IzzyOnDroid',
    tag: 'Signed Index V2',
    en: 'Zero-leak local version comparison against signed IzzyOnDroid indexes — your app list stays on your device.',
    de: 'Lokaler Versionsabgleich gegen signierte IzzyOnDroid-Indizes – deine App-Liste verlässt niemals dein Gerät.',
  },
  {
    id: 'apkmirror',
    name: 'APKMirror',
    tag: '.apkm + Cloudflare',
    en: 'Direct in-app downloads and .apkm split-bundle extraction with automatic Cloudflare WebView & browser fallback.',
    de: 'Direkte In-App-Downloads und .apkm-Split-Entpackung samt automatischer Cloudflare-WebView-Auflösung und Browser-Fallback.',
  },
  {
    id: 'playstore',
    name: 'Google Play Store',
    tag: 'Anonymous Delta',
    en: 'Anonymous session tokens via Aurora Dispenser with a minimized device profile — no Google account required.',
    de: 'Anonyme Sitzungs-Tokens über Aurora Dispenser mit minimiertem Geräteprofil – ganz ohne Google-Konto.',
  },
];

export const SHOWCASE_SCREENS = [
  {
    id: 'updates',
    enLabel: 'Updates',
    deLabel: 'Updates',
    enTitle: 'One tap to update, or batch-select on long press',
    deTitle: 'Ein Tipp zum Update – oder Mehrfachauswahl per langem Druck',
    enDesc: 'See version deltas, release notes, source pills, and live overall WorkManager ETA progress in one calm Material 3 Expressive view.',
    deDesc: 'Versionssprünge, Release-Notes, Quellen-Pills und der Live-Gesamtfortschritt mit Restzeit-Anzeige in einer klaren Oberfläche.',
    enImg: 'assets/screenshots/en/01_updates.png',
    deImg: 'assets/screenshots/de/01_updates.png',
  },
  {
    id: 'details',
    enLabel: 'Details & Changelogs',
    deLabel: 'Details & Verlauf',
    enTitle: 'Full transparency before every install',
    deTitle: 'Volle Transparenz vor jeder Installation',
    enDesc: 'Inspect release changelogs, choose pre-release channels (Beta, RC, Alpha, Nightly), pin a preferred source, or save single APKs and .apks bundles.',
    deDesc: 'Prüfe Changelogs, wähle Vorabversions-Kanäle (Beta, RC, Alpha, Nightly), pinne eine Quelle oder speichere einzelne APKs und .apks-Bundles.',
    enImg: 'assets/screenshots/en/02_details.png',
    deImg: 'assets/screenshots/de/02_details.png',
  },
  {
    id: 'apps',
    enLabel: 'Apps & Watchlist',
    deLabel: 'Apps & Merkliste',
    enTitle: 'Track installed apps and watch uninstalled ones',
    deTitle: 'Installierte Apps verwalten & neue Releases vormerken',
    enDesc: 'Switch between detailed lists and compact grid tiles, filter by source or Watchlist, and import watchlists directly from App Watcher or Obtainium.',
    deDesc: 'Wechsle zwischen Detail-Liste und kompaktem Raster, filtere nach Quelle oder Merkliste und importiere direkt aus App Watcher oder Obtainium.',
    enImg: 'assets/screenshots/en/03_apps.png',
    deImg: 'assets/screenshots/de/03_apps.png',
  },
  {
    id: 'sources',
    enLabel: 'Sources & Priority',
    deLabel: 'Quellen & Priorität',
    enTitle: 'Reorder sources & configure silent rules',
    deTitle: 'Quellen priorisieren & Hintergrundregeln festlegen',
    enDesc: 'Drag-and-drop source priority, inspect Source Health & cooldowns, set active background time windows, and enforce Wi-Fi or charging guards.',
    deDesc: 'Sortiere Quellen per Drag-and-Drop, prüfe den Quellen-Status, definiere Ruhezeiten und aktiviere Schutzregeln für WLAN und Laden.',
    enImg: 'assets/screenshots/en/04_sources.png',
    deImg: 'assets/screenshots/de/04_sources.png',
  },
  {
    id: 'history',
    enLabel: 'History & Rollback',
    deLabel: 'Verlauf & Rollback',
    enTitle: 'Update history with automatic rollback backups',
    deTitle: 'Update-Verlauf mit automatischen Rollback-Sicherungen',
    enDesc: 'Every update is logged locally with rollback backups (in-place via Root or guided non-Root) and a sanitized diagnostic log export.',
    deDesc: 'Jedes Update wird lokal mit optionaler Rollback-Sicherung (direkt mit Root oder geführt ohne Root) und bereinigtem Diagnose-Log protokolliert.',
    enImg: 'assets/screenshots/en/05_history.png',
    deImg: 'assets/screenshots/de/05_history.png',
  },
];

export const MIRRORS = [
  {
    name: 'Zapstore',
    url: 'https://zapstore.dev/apps/com.apkup',
    badgeEn: 'Signed Nostr Releases',
    badgeDe: 'Signierte Nostr-Releases',
    outdated: false,
  },
  {
    name: 'GitLab Releases',
    url: 'https://gitlab.com/DurchEins1/apkup/-/releases',
    badgeEn: 'Official Mirror',
    badgeDe: 'Offizieller Mirror',
    outdated: false,
  },
  {
    name: 'APKPure',
    url: 'https://apkpure.com/apkup/com.apkup',
    badgeEn: 'Verified Listing',
    badgeDe: 'Verifizierter Eintrag',
    outdated: false,
  },
  {
    name: 'itch.io',
    url: 'https://durcheins.itch.io/apkup',
    badgeEn: 'Direct APK Download',
    badgeDe: 'Direkter APK-Download',
    outdated: false,
  },
  {
    name: 'Appteka',
    url: 'https://appteka.store/app/c0cr341070',
    badgeEn: 'Alternative Store',
    badgeDe: 'Alternativer Store',
    outdated: false,
  },
  {
    name: 'Komi Store',
    url: 'https://komistore.app',
    badgeEn: 'FOSS Store',
    badgeDe: 'FOSS-Store',
    outdated: false,
  },
  {
    name: 'AlternativeTo',
    url: 'https://alternativeto.net/software/apkup/about/',
    badgeEn: 'Reviews & Info',
    badgeDe: 'Bewertungen & Infos',
    outdated: false,
  },
  {
    name: 'Amazon Appstore',
    url: 'https://www.amazon.com/gp/product/B0HR5TXL8R',
    badgeEn: 'Outdated',
    badgeDe: 'Veraltet',
    outdated: true,
  },
];

export const FAQS = [
  {
    qEn: 'Do I need Root for silent background updates?',
    qDe: 'Brauche ich Root für lautlose Hintergrund-Updates?',
    aEn: 'No. ApkUp supports Shizuku out of the box, which grants elevated PackageInstaller permissions on stock Android (Android 11+ via Wireless Debugging or ADB) without rooting your device. Optional Root (su) is also supported for users who already run rooted devices or want single-tap in-place rollbacks.',
    aDe: 'Nein. ApkUp unterstützt Shizuku nativ – damit funktionieren lautlose Hintergrund-Installationen auf ungerooteten Geräten (ab Android 11 bequem per Wireless Debugging oder ADB). Optionales Root (su) wird zusätzlich unterstützt, etwa für direkte In-Place-Rollbacks.',
  },
  {
    qEn: 'Does Google Play Store checking require my Google account?',
    qDe: 'Benötigt die Google-Play-Store-Prüfung mein Google-Konto?',
    aEn: 'No. ApkUp uses anonymous session tokens from the community Aurora Dispenser together with a privacy-minimized device compatibility profile (CPU ABI, Android SDK, density, and language) to fetch compatible updates without any personal login.',
    aDe: 'Nein. ApkUp nutzt anonyme Sitzungs-Tokens des Community-Aurora-Dispensers zusammen mit einem minimierten Geräteprofil (CPU-ABI, Android-SDK, Displaydichte und Sprache) – ganz ohne persönliches Google-Konto.',
  },
  {
    qEn: 'How does ApkUp protect me from tampered or incompatible APKs?',
    qDe: 'Wie schützt mich ApkUp vor manipulierten oder inkompatiblen APKs?',
    aEn: 'Before any installer runs, ApkUp cryptographically verifies APK Signature Scheme v2/v3 certificates against the installed app, validates SHA-256 digests and signed F-Droid indexes, blocks version downgrades, checks minSdk/ABI compatibility, and isolates .apkm extraction against Zip-Slip and decompression bombs.',
    aDe: 'Vor jeder Installation prüft ApkUp kryptografisch die APK-Signaturzertifikate (v2/v3) gegen die installierte App, validiert SHA-256-Prüfsummen sowie signierte F-Droid-Indizes, blockiert Downgrades, prüft minSdk/ABI und härtet die .apkm-Entpackung gegen Zip-Slip ab.',
  },
  {
    qEn: 'Can I migrate my tracked apps from Obtainium or App Watcher?',
    qDe: 'Kann ich meine Apps aus Obtainium oder App Watcher übernehmen?',
    aEn: 'Yes. ApkUp includes an interactive Obtainium JSON import preview (for app bindings, custom F-Droid repos, and watchlist candidates) as well as Obtainium JSON export and direct App Watcher watchlist import.',
    aDe: 'Ja. ApkUp bietet eine interaktive Obtainium-JSON-Importvorschau (inklusive App-Verknüpfungen, eigener F-Droid-Repos und Merklisten-Kandidaten), einen Obtainium-Export sowie den direkten Import von App-Watcher-Listen.',
  },
  {
    qEn: 'How do ApkUp self-updates and the Beta channel work?',
    qDe: 'Wie funktionieren Selbst-Updates und der optionale Beta-Kanal?',
    aEn: 'ApkUp checks official GitHub releases (stable-only by default) and validates SHA256SUMS digests plus signing certificates before updating itself. Under Setup → About, you can opt into the Beta channel at any time and cleanly return to stable on opt-out.',
    aDe: 'ApkUp prüft offizielle GitHub-Releases (standardmäßig nur Stable) und verifiziert vor dem Selbst-Update sowohl SHA256SUMS als auch das Signaturzertifikat. Unter Setup → Über lässt sich jederzeit der Beta-Kanal aktivieren und wieder deaktivieren.',
  },
  {
    qEn: 'Does ApkUp collect any analytics, crash logs, or telemetry?',
    qDe: 'Sammelt ApkUp Analysedaten, Absturzberichte oder Telemetrie?',
    aEn: 'Zero. ApkUp has no developer backend, no ads, no analytics SDKs, and no telemetry. First-launch source consent ensures zero network requests run until you choose your sources, and preferences/tokens are encrypted at rest with Google Tink (AES-256-GCM).',
    aDe: 'Null. ApkUp hat keinen Entwickler-Server, keine Werbung, keine Analyse-SDKs und keine Telemetrie. Durch den Erststart-Dialog werden erst nach deiner Quellenauswahl Anfragen gesendet; Tokens und Einstellungen liegen AES-256-GCM-verschlüsselt auf dem Gerät.',
  },
];
