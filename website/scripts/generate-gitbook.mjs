import { readFileSync, writeFileSync, mkdirSync, rmSync } from 'node:fs';
import { join, resolve } from 'node:path';

const websiteDir = resolve(import.meta.dirname, '..');
const repoRoot = resolve(websiteDir, '..');
const wikiDir = join(websiteDir, 'src', 'data', 'wiki');
const gitbookDir = join(repoRoot, 'gitbook');
const WEBSITE_BASE = 'https://durcheins.github.io/apkup';

const PAGES = [
  {
    src: 'Home.md',
    dest: 'home.md',
    title: 'Overview & Quick Start',
    icon: 'book-open',
    description: 'Central knowledge base for ApkUp: multi-source Android updates, Shizuku automation, SHA-256 verification, and troubleshooting.',
    section: null,
  },
  {
    src: 'Update-Sources,-Repository-Linking-&-Signature-Protection.md',
    dest: 'update-sources.md',
    title: 'Update Sources & Signature Protection',
    icon: 'globe',
    description: 'How Google Play, APKMirror, F-Droid, IzzyOnDroid, GitHub, GitLab, and Codeberg work with source priority, pinning, and signature checks.',
    section: 'Sources & Security',
  },
  {
    src: 'F-Droid-&-Index-V2-Repositories.md',
    dest: 'fdroid-index-v2.md',
    title: 'F-Droid & Index V2 Repositories',
    icon: 'box-archive',
    description: 'F-Droid, IzzyOnDroid, and custom HTTPS repositories with Index V2 incremental diffs, JAR certificate pinning, and SHA-256 checks.',
    section: 'Sources & Security',
  },
  {
    src: 'Configuring-GitHub-&-GitLab-Tokens.md',
    dest: 'tokens.md',
    title: 'GitHub, GitLab & Codeberg Tokens',
    icon: 'key',
    description: 'Configure Personal Access Tokens for higher API rate limits, GraphQL batching, and private or self-hosted repositories.',
    section: 'Sources & Security',
  },
  {
    src: 'SHA-256-&-Signature-Verification.md',
    dest: 'sha256-signature-verification.md',
    title: 'SHA-256 & Signature Verification',
    icon: 'shield-check',
    description: 'Verify ApkUp APK SHA-256 checksums locally on Windows, Linux, and macOS, plus automatic v2/v3 signing certificate protection.',
    section: 'Sources & Security',
  },
  {
    src: 'Setting-up-Shizuku.md',
    dest: 'setting-up-shizuku.md',
    title: 'Setting up Shizuku (Quick Guide)',
    icon: 'bolt',
    description: 'Quick setup checklist to enable silent background app updates in ApkUp using Shizuku via Wireless Debugging, USB ADB, or Root.',
    section: 'Installation & Automation',
  },
  {
    src: 'Shizuku-&-Background-Installation.md',
    dest: 'shizuku-background-installation.md',
    title: 'Shizuku & Background Installation',
    icon: 'gears',
    description: 'Deep dive into silent batch installs, WorkManager background checks, optional Root (su) fallback, and Android 14+ BAL handling.',
    section: 'Installation & Automation',
  },
  {
    src: 'Installing-Unknown-Apps.md',
    dest: 'installing-unknown-apps.md',
    title: 'Installing Unknown Apps (Sideloading)',
    icon: 'download',
    description: 'Step-by-step guide to Android Install Unknown Apps permissions plus power-user installation via Shizuku, ADB, and Root.',
    section: 'Installation & Automation',
  },
  {
    src: 'Google-24h-Lock-&-Keep-Android-Open.md',
    dest: 'google-24h-lock.md',
    title: "Google's 24h Lock & Keep Android Open",
    icon: 'lock',
    description: 'How to permanently unlock unverified developer apps in Android Developer Options and avoid the 7-day reset trap.',
    section: 'Installation & Automation',
  },
  {
    src: 'App-Management,-Filters,-Background-Checks-&-Backup.md',
    dest: 'app-management-and-backup.md',
    title: 'App Management, Filters & Rollback Backups',
    icon: 'sliders',
    description: 'Manage watchlist, ignored apps, APK exports, quiet-hour update windows, rollback backups, and encrypted JSON settings backups.',
    section: 'Management & Troubleshooting',
  },
  {
    src: 'Obtainium-Migration-&-Sync-Guide.md',
    dest: 'obtainium-sync.md',
    title: 'Obtainium Migration & Sync',
    icon: 'arrows-rotate',
    description: 'Import Obtainium JSON exports into ApkUp with interactive preview for repo links, regex filters, F-Droid repos, and watchlist items.',
    section: 'Management & Troubleshooting',
  },
  {
    src: 'Troubleshooting-Source-Errors.md',
    dest: 'troubleshooting.md',
    title: 'Troubleshooting Source Errors',
    icon: 'triangle-exclamation',
    description: 'Direct fixes for DNS errors, HTTP 401/403/429 rate limits, APKMirror Cloudflare challenges, signature mismatches, and repo filters.',
    section: 'Management & Troubleshooting',
  },
];

const WIKI_DEST_BY_KEY = new Map();
for (const page of PAGES) {
  const stem = page.src.replace(/\.md$/i, '').toLowerCase();
  WIKI_DEST_BY_KEY.set(stem, page.dest);
  WIKI_DEST_BY_KEY.set(page.src.toLowerCase(), page.dest);
}
WIKI_DEST_BY_KEY.set('authentication-tokens-(pat)-for-github,-gitlab-&-codeberg', 'tokens.md');

function resolveTarget(rawPage) {
  if (!rawPage) return 'home.md';
  let decoded = rawPage;
  try {
    decoded = decodeURIComponent(rawPage);
  } catch {
    // keep raw if malformed
  }
  const key = decoded.replace(/&amp;/g, '&').trim().toLowerCase();
  return WIKI_DEST_BY_KEY.get(key) || null;
}

function rewriteMarkdownLinks(markdown) {
  const lines = markdown.split(/\r?\n/);
  let inFence = false;
  return lines
    .map((line) => {
      if (/^\s*```/.test(line)) {
        inFence = !inFence;
        return line;
      }
      if (inFence) return line;
      return line.replace(/\[([^\]]+)\]\(([^)\s]+)\)/g, (full, label, rawUrl) => {
        const ghWikiMatch = rawUrl.match(/^https:\/\/github\.com\/DurchEins\/apkup\/wiki(?:\/([^#?]+))?(#.*)?$/i);
        if (ghWikiMatch) {
          const targetFile = resolveTarget(ghWikiMatch[1] || 'Home');
          if (!targetFile) return full;
          return `[${label}](${targetFile}${ghWikiMatch[2] || ''})`;
        }
        const siteWikiAnchorMatch = rawUrl.match(/^\/apkup\/wiki\/#([a-z0-9-]+)$/i);
        if (siteWikiAnchorMatch) {
          const anchorMap = {
            'doc-google-lock': 'google-24h-lock.md',
            'doc-sideloading': 'installing-unknown-apps.md',
            'doc-shizuku-setup': 'setting-up-shizuku.md',
            'doc-shizuku-deep': 'shizuku-background-installation.md',
            'doc-sha256': 'sha256-signature-verification.md',
          };
          const mapped = anchorMap[siteWikiAnchorMatch[1].toLowerCase()];
          if (mapped) return `[${label}](${mapped})`;
        }
        if (rawUrl.startsWith('/apkup/')) {
          return `[${label}](${WEBSITE_BASE}${rawUrl.slice('/apkup'.length)})`;
        }
        const relMatch = rawUrl.match(/^([^/:?#]+)(#.*)?$/);
        if (relMatch) {
          const targetFile = resolveTarget(relMatch[1]);
          if (targetFile) {
            return `[${label}](${targetFile}${relMatch[2] || ''})`;
          }
        }
        return full;
      });
    })
    .join('\n')
    .trimEnd()
    .concat('\n');
}

function convertCalloutsToGitBookHints(markdown) {
  const lines = markdown.split(/\r?\n/);
  const out = [];
  let inFence = false;

  const styleMap = {
    NOTE: 'info',
    TIP: 'success',
    IMPORTANT: 'info',
    WARNING: 'warning',
    CAUTION: 'danger',
  };

  for (let i = 0; i < lines.length; i += 1) {
    const line = lines[i];
    if (/^\s*```/.test(line)) {
      inFence = !inFence;
      out.push(line);
      continue;
    }
    if (inFence) {
      out.push(line);
      continue;
    }

    const ghAlert = line.match(/^>\s*\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION)\]\s*(.*)$/i);
    if (ghAlert) {
      const style = styleMap[ghAlert[1].toUpperCase()] || 'info';
      const body = [];
      if (ghAlert[2]) body.push(ghAlert[2]);
      while (i + 1 < lines.length && /^>\s?/.test(lines[i + 1])) {
        i += 1;
        body.push(lines[i].replace(/^>\s?/, ''));
      }
      out.push(`{% hint style="${style}" %}`, ...body, '{% endhint %}');
      continue;
    }

    const inlineTip = line.match(/^>\s*\*\*(Tip|Note|Warning|Caution|Important):\*\*\s*(.+)$/i);
    if (inlineTip) {
      const style = styleMap[inlineTip[1].toUpperCase()] || 'info';
      const body = [inlineTip[2]];
      while (i + 1 < lines.length && /^>\s?/.test(lines[i + 1])) {
        i += 1;
        body.push(lines[i].replace(/^>\s?/, ''));
      }
      out.push(`{% hint style="${style}" %}`, ...body, '{% endhint %}');
      continue;
    }

    out.push(line);
  }
  return out.join('\n');
}

function withFrontmatter(page, body) {
  const safeDesc = page.description.replace(/"/g, '\\"');
  return [
    '---',
    `description: "${safeDesc}"`,
    `icon: ${page.icon}`,
    '---',
    '',
    body.trimStart(),
  ].join('\n');
}

function enhancePage(page, content) {
  let text = convertCalloutsToGitBookHints(content);

  if (page.dest === 'home.md') {
    const cardsBlock = [
      '',
      '<table data-view="cards">',
      '<thead><tr><th>Guide</th><th>What you will learn</th><th data-hidden data-card-target data-type="content-ref">Link</th></tr></thead>',
      '<tbody>',
      '<tr><td><strong>🌐 Update Sources & Security</strong></td><td>Play Store, F-Droid Index V2, APKMirror, GitHub/GitLab/Codeberg, Priority &amp; Signatures</td><td><a href="update-sources.md">update-sources.md</a></td></tr>',
      '<tr><td><strong>⚡ Setting up Shizuku</strong></td><td>Silent background updates &amp; one-tap batch installs without Root</td><td><a href="setting-up-shizuku.md">setting-up-shizuku.md</a></td></tr>',
      '<tr><td><strong>🛡️ SHA-256 &amp; Signatures</strong></td><td>Verify APK checksums on Windows, Linux &amp; macOS + automatic v2/v3 checks</td><td><a href="sha256-signature-verification.md">sha256-signature-verification.md</a></td></tr>',
      '<tr><td><strong>🛠️ Management &amp; Backups</strong></td><td>Watchlist, quiet hours, rollback backups &amp; encrypted settings export</td><td><a href="app-management-and-backup.md">app-management-and-backup.md</a></td></tr>',
      '<tr><td><strong>🔄 Obtainium Migration</strong></td><td>Import Obtainium JSON exports with interactive preview in seconds</td><td><a href="obtainium-sync.md">obtainium-sync.md</a></td></tr>',
      '<tr><td><strong>🚨 Troubleshooting</strong></td><td>Quick fixes for DNS, 401/403/429 rate limits, Cloudflare &amp; signature errors</td><td><a href="troubleshooting.md">troubleshooting.md</a></td></tr>',
      '</tbody>',
      '</table>',
      '',
    ].join('\n');

    text = text.replace(
      '## 🗺️ Recommended Reading Order',
      `${cardsBlock}## 🗺️ Recommended Reading Order`,
    );
  }

  if (page.dest === 'setting-up-shizuku.md') {
    const tabsSection = [
      '## 2. Choose Your Shizuku Start Method',
      '',
      '{% tabs %}',
      '{% tab title="Wireless Debugging (Android 11+, No PC)" %}',
      '{% stepper %}',
      '{% step %}',
      '### Install Shizuku',
      '',
      'Install **Shizuku** from Google Play, F-Droid, or [GitHub Releases](https://github.com/RikkaApps/Shizuku/releases).',
      '{% endstep %}',
      '{% step %}',
      '### Enable Developer Options & Wi-Fi',
      '',
      'Open **Android Settings ➔ About Phone** and tap **Build Number** 7 times, then connect your phone to Wi-Fi.',
      '{% endstep %}',
      '{% step %}',
      '### Pair with Wireless Debugging',
      '',
      'Open **Shizuku** ➔ **Pairing** ➔ **Developer Options** ➔ enable **Wireless Debugging**, tap its title, choose **Pair device with pairing code**, and enter the 6-digit code in the Shizuku notification.',
      '{% endstep %}',
      '{% step %}',
      '### Start Shizuku',
      '',
      'Return to **Shizuku** and tap **Start**.',
      '',
      '{% hint style="success" %}',
      'You only pair once. After a reboot, simply open Shizuku and tap **Start** (takes ~3 seconds).',
      '{% endhint %}',
      '{% endstep %}',
      '{% endstepper %}',
      '{% endtab %}',
      '',
      '{% tab title="PC / Mac (USB ADB)" %}',
      'For Android 10 or when Wireless Debugging is unavailable:',
      '',
      '1. Enable **USB Debugging** in **Developer Options** and connect via USB.',
      '2. Run the command shown inside Shizuku:',
      '',
      '```bash',
      'adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh',
      '```',
      '{% endtab %}',
      '',
      '{% tab title="Rooted Devices" %}',
      'If your device uses Magisk, KernelSU, or APatch:',
      '',
      '1. Open **Shizuku** ➔ tap **Start (for rooted devices)** and grant superuser permission.',
      '',
      '*(Alternatively, ApkUp supports its own optional **Root Installer (`su`)** under **Setup ➔ Installation & Automation** — see [Section 3.10](shizuku-background-installation.md#310-optional-root-installer-su-fallback).)*',
      '{% endtab %}',
      '{% endtabs %}',
    ].join('\n');

    text = text.replace(
      /## 2\. Method A: Wireless Debugging[\s\S]*?(?=---\s*\r?\n\s*## 5\. Connecting Shizuku in ApkUp)/,
      `${tabsSection}\n\n`,
    );
  }

  if (page.dest === 'sha256-signature-verification.md') {
    const verifyTabs = [
      '## Local Verification (Step-by-Step)',
      '',
      'You can verify the downloaded `.apk` file on your PC or Mac in 10 seconds before transferring or installing it:',
      '',
      '{% tabs %}',
      '{% tab title="Windows (PowerShell)" %}',
      '1. Open **PowerShell** in your Downloads folder (`cd $HOME\\Downloads`).',
      '2. Run:',
      '',
      '```powershell',
      'Get-FileHash .\\ApkUp-*.apk -Algorithm SHA256',
      '```',
      '',
      `3. Compare the printed \`Hash\` with the checksum shown on [ApkUp Downloads](${WEBSITE_BASE}/downloads/) or in \`SHA256SUMS\` on GitHub Releases.`,
      '{% endtab %}',
      '',
      '{% tab title="Linux" %}',
      '1. Download both the `.apk` and `SHA256SUMS` from [GitHub Releases](https://github.com/DurchEins/apkup/releases/latest) into the same folder.',
      '2. Run:',
      '',
      '```bash',
      'sha256sum -c SHA256SUMS',
      '```',
      '',
      '3. Look for **`ApkUp-v....apk: OK`**.',
      '{% endtab %}',
      '',
      '{% tab title="macOS" %}',
      '1. Open **Terminal** in your Downloads folder (`cd ~/Downloads`).',
      '2. Run:',
      '',
      '```bash',
      'shasum -a 256 ApkUp-*.apk',
      '```',
      '',
      `3. Compare the output hash with the official hash on [ApkUp Downloads](${WEBSITE_BASE}/downloads/).`,
      '{% endtab %}',
      '{% endtabs %}',
    ].join('\n');

    text = text.replace(
      /## Local Verification \(Step-by-Step\)[\s\S]*?(?=---\s*\r?\n\s*## Automatic Protection Inside ApkUp)/,
      `${verifyTabs}\n\n`,
    );
  }

  if (page.dest === 'google-24h-lock.md') {
    const stepper = [
      '## 2. Step-by-Step: Unlock "Apps from Unverified Developers" (24h Flow)',
      '',
      'If Play Protect blocks an independent APK with an *"Unverified developer"* notice, Google places the override inside **Developer Options** behind a one-time **24-hour cooldown**:',
      '',
      '{% stepper %}',
      '{% step %}',
      '### Enable Developer Options',
      '',
      'Open **Settings → About Phone** (on Samsung: *Software information*) and tap **Build Number** **7 times** until *"You are now a developer!"* appears.',
      '{% endstep %}',
      '{% step %}',
      '### Open Unverified Developers Setting',
      '',
      'Go to **Settings → System → Developer Options** → **Apps from unverified developers** → **Allow apps from unverified developers**.',
      '{% endstep %}',
      '{% step %}',
      '### Confirm Lock Screen & Restart',
      '',
      'Enter your PIN/password/biometrics and **restart your phone** when prompted.',
      '{% endstep %}',
      '{% step %}',
      '### Wait the 24-Hour Cooldown',
      '',
      'Android starts a mandatory **24-hour waiting period** after reboot.',
      '{% endstep %}',
      '{% step %}',
      '### Choose "Allow Indefinitely"',
      '',
      'After 24 hours, return to **Developer Options → Apps from unverified developers** and select **Allow indefinitely**.',
      '',
      '{% hint style="danger" %}',
      'Do **not** pick the *7-day temporary window*, or Android will turn the setting back off after 7 days and force another 24-hour wait!',
      '{% endhint %}',
      '{% endstep %}',
      '{% endstepper %}',
    ].join('\n');

    text = text.replace(
      /## 2\. Step-by-Step: Unlock[\s\S]*?(?=---\s*\r?\n\s*## 3\. What This Means)/,
      `${stepper}\n\n`,
    );
  }

  if (page.dest === 'obtainium-sync.md') {
    text = text.replace(
      '*(Note: Personal Access Tokens are stored encrypted on-device via Google Tink `AES-256-GCM` and are never exported in plaintext JSON backups.)*',
      '{% hint style="info" %}\nPersonal Access Tokens are stored encrypted on-device via Google Tink `AES-256-GCM` and are never exported in plaintext JSON backups.\n{% endhint %}',
    );
  }

  return withFrontmatter(page, text);
}

rmSync(gitbookDir, { recursive: true, force: true });
mkdirSync(gitbookDir, { recursive: true });

for (const page of PAGES) {
  const raw = readFileSync(join(wikiDir, page.src), 'utf8');
  const rewritten = rewriteMarkdownLinks(raw);
  const enhanced = enhancePage(page, rewritten);
  writeFileSync(join(gitbookDir, page.dest), enhanced, 'utf8');
}

const rootPages = PAGES.filter((p) => p.section === null);
const sectionsOrder = [...new Set(PAGES.map((p) => p.section).filter(Boolean))];

const summaryLines = ['# Table of contents', ''];
for (const page of rootPages) {
  summaryLines.push(`* [${page.title}](${page.dest})`);
}
for (const section of sectionsOrder) {
  summaryLines.push('', `## ${section}`, '');
  for (const page of PAGES.filter((p) => p.section === section)) {
    summaryLines.push(`* [${page.title}](${page.dest})`);
  }
}
summaryLines.push('');
writeFileSync(join(gitbookDir, 'SUMMARY.md'), summaryLines.join('\n'), 'utf8');

const gitbookYaml = `root: ./

structure:
  readme: home.md
  summary: SUMMARY.md
`;
writeFileSync(join(gitbookDir, '.gitbook.yaml'), gitbookYaml, 'utf8');

console.log(`Generated ${PAGES.length} enhanced GitBook pages, SUMMARY.md, and .gitbook.yaml in ${gitbookDir}`);
