import { readFileSync, writeFileSync, mkdirSync, rmSync } from 'node:fs';
import { join, resolve } from 'node:path';

const websiteDir = resolve(import.meta.dirname, '..');
const repoRoot = resolve(websiteDir, '..');
const wikiDir = join(websiteDir, 'src', 'data', 'wiki');
const gitbookDir = join(repoRoot, 'gitbook');

const PAGES = [
  {
    src: 'Home.md',
    dest: 'home.md',
    title: 'Overview & Quick Start',
    section: null,
  },
  {
    src: 'Update-Sources,-Repository-Linking-&-Signature-Protection.md',
    dest: 'update-sources.md',
    title: 'Update Sources & Signature Protection',
    section: 'Sources & Security',
  },
  {
    src: 'F-Droid-&-Index-V2-Repositories.md',
    dest: 'fdroid-index-v2.md',
    title: 'F-Droid & Index V2 Repositories',
    section: 'Sources & Security',
  },
  {
    src: 'Configuring-GitHub-&-GitLab-Tokens.md',
    dest: 'tokens.md',
    title: 'GitHub, GitLab & Codeberg Tokens',
    section: 'Sources & Security',
  },
  {
    src: 'SHA-256-&-Signature-Verification.md',
    dest: 'sha256-signature-verification.md',
    title: 'SHA-256 & Signature Verification',
    section: 'Sources & Security',
  },
  {
    src: 'Setting-up-Shizuku.md',
    dest: 'setting-up-shizuku.md',
    title: 'Setting up Shizuku (Quick Guide)',
    section: 'Installation & Automation',
  },
  {
    src: 'Shizuku-&-Background-Installation.md',
    dest: 'shizuku-background-installation.md',
    title: 'Shizuku & Background Installation',
    section: 'Installation & Automation',
  },
  {
    src: 'Installing-Unknown-Apps.md',
    dest: 'installing-unknown-apps.md',
    title: 'Installing Unknown Apps (Sideloading)',
    section: 'Installation & Automation',
  },
  {
    src: 'Google-24h-Lock-&-Keep-Android-Open.md',
    dest: 'google-24h-lock.md',
    title: "Google's 24h Lock & Keep Android Open",
    section: 'Installation & Automation',
  },
  {
    src: 'App-Management,-Filters,-Background-Checks-&-Backup.md',
    dest: 'app-management-and-backup.md',
    title: 'App Management, Filters & Rollback Backups',
    section: 'Management & Troubleshooting',
  },
  {
    src: 'Obtainium-Migration-&-Sync-Guide.md',
    dest: 'obtainium-sync.md',
    title: 'Obtainium Migration & Sync',
    section: 'Management & Troubleshooting',
  },
  {
    src: 'Troubleshooting-Source-Errors.md',
    dest: 'troubleshooting.md',
    title: 'Troubleshooting Source Errors',
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

rmSync(gitbookDir, { recursive: true, force: true });
mkdirSync(gitbookDir, { recursive: true });

for (const page of PAGES) {
  const raw = readFileSync(join(wikiDir, page.src), 'utf8');
  const rewritten = rewriteMarkdownLinks(raw);
  writeFileSync(join(gitbookDir, page.dest), rewritten, 'utf8');
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

console.log(`Generated ${PAGES.length} GitBook pages, SUMMARY.md, and .gitbook.yaml in ${gitbookDir}`);
