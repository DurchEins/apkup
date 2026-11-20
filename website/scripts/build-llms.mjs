import { readFileSync, writeFileSync, readdirSync } from 'node:fs';
import { join, resolve } from 'node:path';

const websiteDir = resolve(import.meta.dirname, '..');
const repoRoot = resolve(websiteDir, '..');
const publicDir = join(websiteDir, 'public');
const wikiDir = join(websiteDir, 'src', 'data', 'wiki');
const BASE_URL = 'https://durcheins.github.io/apkup';

function readSafe(filePath) {
  try {
    return readFileSync(filePath, 'utf8').trim();
  } catch {
    return '';
  }
}

// Extract fallbackVersion & fallbackBuild from src/data/content.ts
const contentTs = readSafe(join(websiteDir, 'src', 'data', 'content.ts'));
const verMatch = contentTs.match(/fallbackVersion:\s*'([^']+)'/);
const buildMatch = contentTs.match(/fallbackBuild:\s*'([^']+)'/);
const version = verMatch ? verMatch[1] : '1.45.0';
const build = buildMatch ? buildMatch[1] : '211';

// Extract FAQS (qEn / aEn) from content.ts
const faqPairs = [];
const faqBlockMatch = contentTs.match(/export const FAQS[^=]*=\s*\[([\s\S]*?)\n\];/);
if (faqBlockMatch) {
  const itemRegex = /qEn:\s*'((?:\\'|[^'])*)',[\s\S]*?aEn:\s*'((?:\\'|[^'])*)'/g;
  let m;
  while ((m = itemRegex.exec(faqBlockMatch[1])) !== null) {
    faqPairs.push({
      q: m[1].replace(/\\'/g, "'"),
      a: m[2].replace(/\\'/g, "'"),
    });
  }
}

// Ordered Wiki chapters
const WIKI_ORDER = [
  { file: 'Home.md', title: 'Overview & Quick Start' },
  { file: 'Update-Sources,-Repository-Linking-&-Signature-Protection.md', title: 'Update Sources, Repository Linking & Signature Protection' },
  { file: 'F-Droid-&-Index-V2-Repositories.md', title: 'F-Droid & Index V2 Repositories' },
  { file: 'Authentication-Tokens-(PAT)-for-GitHub,-GitLab-&-Codeberg.md', title: 'Authentication Tokens (PAT) for GitHub, GitLab & Codeberg' },
  { file: 'Setting-up-Shizuku.md', title: 'Setting up Shizuku (Quick Guide)' },
  { file: 'Shizuku-&-Background-Installation.md', title: 'Shizuku & Background Installation (Architecture & Safeguards)' },
  { file: 'Obtainium-Migration-&-Sync-Guide.md', title: 'Obtainium Migration & Sync Guide' },
  { file: 'App-Management,-Filters,-Background-Checks-&-Backup.md', title: 'App Management, Filters, Background Checks & Backup' },
  { file: 'Troubleshooting-Source-Errors.md', title: 'Troubleshooting Source Errors & Error Codes' },
  { file: 'SHA-256-&-Signature-Verification.md', title: 'SHA-256 Checksums & APK Signature Verification' },
];

// 1. Build llms.txt (compact llmstxt.org-compliant directory)
const llmsTxt = `# ApkUp

> Open-source, privacy-first multi-source Android app updater (GPL-3.0) supporting Google Play, F-Droid Index V2, IzzyOnDroid, APKMirror, GitHub, GitLab, Codeberg, and direct APK links — with optional silent background installations via Shizuku (no root) or Root (su), zero ads, and zero tracking.

- Latest version: v${version} (${build})
- Platform: Android 10.0 – 17 (API 29+) • arm64-v8a, armeabi-v7a, x86_64
- Languages: 9 UI languages (English, German, Spanish, French, Italian, Polish, Portuguese-BR, Russian, Turkish)
- License: GNU General Public License v3.0 (GPL-3.0)
- Source Code: https://github.com/DurchEins/apkup
- Complete AI Knowledge Base (single-file Markdown): ${BASE_URL}/llms-full.txt

## Main Pages

- [Home & Feature Overview](${BASE_URL}/): Overview of all 8 update sources, 10 app screenshots, Shizuku/Root silent updates, signed APK download, SHA-256 verification, and Android permissions matrix.
- [Downloads, Mirrors & SHA-256 Verification](${BASE_URL}/downloads/): Canonical signed APK download, live SHA256SUMS checksums, ABI/Android requirements, and all 8 official & community mirrors.
- [Wiki & Reference Guides](${BASE_URL}/wiki/): Complete 9-chapter documentation covering source priority, F-Droid Index V2, PAT tokens, Shizuku & Root setup, Obtainium JSON import, and error troubleshooting.
- [Source Compatibility & Comparison](${BASE_URL}/compare/): Feature comparison (ApkUp vs. Obtainium vs. Aurora Store vs. F-Droid) and 100% offline Obtainium JSON compatibility checker.
- [What's New / Changelog](${BASE_URL}/changelog/): Release history and feature highlights.
- [Privacy Policy](${BASE_URL}/privacy/): Zero-telemetry, on-device storage, and encrypted credential details.
- [Security Policy](${BASE_URL}/security/): Supported versions, SHA-256 verification, and vulnerability disclosure.

## Direct Downloads & Mirrors

- [GitHub Releases (Canonical Signed APK + SHA256SUMS)](https://github.com/DurchEins/apkup/releases/latest)
- [GitLab Mirror](https://gitlab.com/DurchEins/apkup)
- [GitHub Wiki](https://github.com/DurchEins/apkup/wiki)
`;

writeFileSync(join(publicDir, 'llms.txt'), llmsTxt, 'utf8');

// 2. Build llms-full.txt (complete single-file Markdown knowledge base)
const sections = [];

sections.push(`# ApkUp — Complete Documentation & Knowledge Base (v${version} / Build ${build})

> ApkUp is a free, ad-free, open-source (GPL-3.0) Android app updater for Android 10.0 – 17 (API 29+). It unifies Google Play, F-Droid Index V2, IzzyOnDroid, APKMirror, GitHub Releases, GitLab Releases, Codeberg Releases, and Direct APK URLs with automatic SHA-256 & signing-certificate verification, Obtainium JSON import, and optional silent background updates via Shizuku or Root (su).

- Website: ${BASE_URL}/
- Repository: https://github.com/DurchEins/apkup
- Latest Signed APK & SHA256SUMS: https://github.com/DurchEins/apkup/releases/latest
- Supported ABIs: arm64-v8a, armeabi-v7a, x86_64
- Minimum Android Version: Android 10.0 (API 29) up to Android 17
`);

sections.push(`## Key Differentiators (ApkUp vs. Obtainium vs. Aurora Store vs. F-Droid)

- **8+ Update Sources in One App:** Google Play Store, F-Droid Index V2 (official & custom repos with SHA-256 verification), IzzyOnDroid, APKMirror, GitHub, GitLab, Codeberg, and Direct APK URLs.
- **No Root Required for Background Updates:** Supports standard Android PackageInstaller, optional Shizuku (no root), and optional Root (\`su\`), with Wi-Fi-only and charging-only rules.
- **Play Store Version Checking Without Play Account:** Checks Play Store updates via Aurora / GPlay API integration with intelligent fallback across sources.
- **Hardware-Backed Credential Storage:** GitHub/GitLab/Codeberg Personal Access Tokens (PATs) are encrypted using Google Tink (AES-256-GCM) backed by Android Keystore and strictly host-isolated.
- **100% Obtainium JSON Import & Live Checker:** Import existing Obtainium JSON configs directly into ApkUp with automatic source mapping.
- **Signing-Certificate & SHA-256 Protection:** Verifies APK signatures and checksums before installation to prevent downgrade or spoofing attacks.
`);

if (faqPairs.length > 0) {
  sections.push(`## Frequently Asked Questions (FAQ)\n\n${faqPairs.map((f) => `### ${f.q}\n${f.a}`).join('\n\n')}`);
}

const changelogEn = readSafe(join(repoRoot, 'app', 'src', 'main', 'assets', 'changelog', '1.45_en.md'));
if (changelogEn) {
  sections.push(`## Latest Release Notes (v${version})\n\n${changelogEn}`);
}

const privacyMd = readSafe(join(repoRoot, 'PRIVACY.md'));
if (privacyMd) {
  sections.push(`## Privacy Policy\n\n${privacyMd}`);
}

const securityMd = readSafe(join(repoRoot, 'SECURITY.md'));
if (securityMd) {
  sections.push(`## Security Policy\n\n${securityMd}`);
}

// Append all 9 Wiki chapters
for (const item of WIKI_ORDER) {
  const md = readSafe(join(wikiDir, item.file));
  if (md) {
    sections.push(`## Wiki: ${item.title}\n\n${md}`);
  }
}

writeFileSync(join(publicDir, 'llms-full.txt'), sections.join('\n\n---\n\n') + '\n', 'utf8');
console.log(`Built public/llms.txt and public/llms-full.txt (${faqPairs.length} FAQs, ${WIKI_ORDER.length} wiki chapters)`);
