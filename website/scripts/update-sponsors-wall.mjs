import { readFileSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';

const websiteDir = resolve(import.meta.dirname, '..');
const repoRoot = resolve(websiteDir, '..');
const sponsorsPath = join(repoRoot, 'sponsors.json');
const readmePath = join(repoRoot, 'README.md');

const START_MARKER = '<!-- SPONSORS_WALL:START -->';
const END_MARKER = '<!-- SPONSORS_WALL:END -->';
const SPONSORS_URL = 'https://github.com/sponsors/DurchEins';

function escapeHtml(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function renderLink(item) {
  const name = escapeHtml(item.name);
  if (!item.url) return `<strong>${name}</strong>`;
  const url = escapeHtml(item.url);
  return `<a href="${url}"><strong>${name}</strong></a>`;
}

function renderChampions(champions) {
  if (champions.length === 0) {
    return [
      '### 🏛️ Open-Source Champions ($50+/mo)',
      '',
      `*Privacy-respecting projects & teams welcome — <a href="${SPONSORS_URL}">become the first Open-Source Champion →</a>*`,
    ].join('\n');
  }

  const rows = champions.map((c) => {
    const avatar = c.avatar
      ? `<img src="${escapeHtml(c.avatar)}" width="48" height="48" alt="${escapeHtml(c.name)}" />`
      : '🏛️';
    const tagline = c.tagline ? `<br><sub>${escapeHtml(c.tagline)}</sub>` : '';
    return `| ${avatar} | ${renderLink(c)}${tagline} |`;
  });

  return [
    '### 🏛️ Open-Source Champions ($50+/mo)',
    '',
    '| | Champion |',
    '| :---: | :--- |',
    ...rows,
  ].join('\n');
}

function renderFeatured(featured) {
  const count = featured.length;
  const summary = `⭐ <b>Featured Supporters</b> — $15–$25/mo &amp; $50–$100 one-time${count > 0 ? ` (${count})` : ''}`;

  if (count === 0) {
    return [
      '<details>',
      `<summary>${summary}</summary>`,
      '',
      `*Be the first Featured Supporter on this wall — <a href="${SPONSORS_URL}">become a sponsor →</a>*`,
      '',
      '</details>',
    ].join('\n');
  }

  const rows = featured.map((f) => {
    const img = f.avatar
      ? `<img src="${escapeHtml(f.avatar)}" width="40" height="40" alt="${escapeHtml(f.name)}" />`
      : '⭐';
    const note = f.tagline ? `<br><sub>${escapeHtml(f.tagline)}</sub>` : '';
    return `| ${img} | ${renderLink(f)}${note} |`;
  });

  return [
    '<details>',
    `<summary>${summary}</summary>`,
    '',
    '| | Featured Supporter |',
    '| :---: | :--- |',
    ...rows,
    '',
    '</details>',
  ].join('\n');
}

function renderSupporters(supporters) {
  const count = supporters.length;
  const summary = `🌟 <b>Supporters Wall</b> — $5–$10/mo &amp; $10–$42 one-time${count > 0 ? ` (${count})` : ''}`;

  if (count === 0) {
    return [
      '<details>',
      `<summary>${summary}</summary>`,
      '',
      `*Be the first supporter on this wall — <a href="${SPONSORS_URL}">support ApkUp →</a>*`,
      '',
      '</details>',
    ].join('\n');
  }

  const list = supporters
    .map((s) => (s.url ? `[${escapeHtml(s.name)}](${escapeHtml(s.url)})` : escapeHtml(s.name)))
    .join(' • ');

  return [
    '<details>',
    `<summary>${summary}</summary>`,
    '',
    list,
    '',
    '</details>',
  ].join('\n');
}

const data = JSON.parse(readFileSync(sponsorsPath, 'utf8'));
const champions = Array.isArray(data.champions) ? data.champions : [];
const featured = Array.isArray(data.featured) ? data.featured : [];
const supporters = Array.isArray(data.supporters) ? data.supporters : [];

const wallBlock = [
  START_MARKER,
  renderChampions(champions),
  '',
  renderFeatured(featured),
  '',
  renderSupporters(supporters),
  END_MARKER,
].join('\n');

const readme = readFileSync(readmePath, 'utf8');
const startIdx = readme.indexOf(START_MARKER);
const endIdx = readme.indexOf(END_MARKER);
if (startIdx === -1 || endIdx === -1 || endIdx < startIdx) {
  throw new Error('Could not find SPONSORS_WALL markers in README.md');
}

const updated =
  readme.slice(0, startIdx) + wallBlock + readme.slice(endIdx + END_MARKER.length);

if (updated !== readme) {
  writeFileSync(readmePath, updated, 'utf8');
  console.log(`Updated README.md Supporters Wall (${champions.length} champions, ${featured.length} featured, ${supporters.length} supporters)`);
} else {
  console.log('README.md Supporters Wall unchanged');
}
