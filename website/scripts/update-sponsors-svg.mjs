import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';

const websiteDir = resolve(import.meta.dirname, '..');
const repoRoot = resolve(websiteDir, '..');
const svgPath = join(repoRoot, '.github', 'assets', 'github-sponsors-button.svg');
const LOGIN = 'DurchEins';

function readExistingCount() {
  try {
    const existing = readFileSync(svgPath, 'utf8');
    const match = existing.match(/data-sponsor-count="(\d+)"/);
    return match ? Number.parseInt(match[1], 10) : 0;
  } catch {
    return 0;
  }
}

async function fetchSponsorCount(token) {
  if (!token) return null;
  try {
    const res = await fetch('https://api.github.com/graphql', {
      method: 'POST',
      headers: {
        Authorization: `bearer ${token}`,
        'Content-Type': 'application/json',
        'User-Agent': 'apkup-sponsors-svg-sync',
      },
      body: JSON.stringify({
        query: 'query($login: String!) { user(login: $login) { sponsors { totalCount } } }',
        variables: { login: LOGIN },
      }),
    });
    if (!res.ok) return null;
    const json = await res.json();
    const count = json?.data?.user?.sponsors?.totalCount;
    return typeof count === 'number' && Number.isFinite(count) && count >= 0 ? count : null;
  } catch {
    return null;
  }
}

function buildSponsorsSvg(count) {
  const safeCount = Math.max(0, Math.floor(count));
  const showCount = safeCount > 0;
  const countText = String(safeCount);
  const baseWidth = 216;
  const badgeExtraWidth = showCount ? Math.max(38, 22 + countText.length * 10) : 0;
  const totalWidth = baseWidth + badgeExtraWidth;
  const countCenterX = baseWidth + badgeExtraWidth / 2;

  return `<svg xmlns="http://www.w3.org/2000/svg" width="${totalWidth}" height="57" viewBox="0 0 ${totalWidth} 57" role="img" aria-label="Sponsor DurchEins on GitHub" data-sponsor-count="${safeCount}">
  <title>Sponsor DurchEins on GitHub${showCount ? ` (${safeCount})` : ''}</title>
  <style>
    .bg { fill: #21262d; stroke: #db61a2; }
    .div { stroke: #30363d; }
    .txt { fill: #f0f6fc; }
    .cnt { fill: #f0f6fc; }
    @media (prefers-color-scheme: light) {
      .bg { fill: #ffffff; stroke: #db61a2; }
      .div { stroke: #d0d7de; }
      .txt { fill: #24292f; }
      .cnt { fill: #24292f; }
    }
  </style>
  <rect class="bg" x="1" y="1" width="${totalWidth - 2}" height="55" rx="12" stroke-width="2" />
  <g transform="translate(20, 16.5) scale(1.5)">
    <path fill="#db61a2" d="m8 14.25.345.666a.75.75 0 0 1-.69 0l-.008-.004-.018-.01a7.152 7.152 0 0 1-.31-.17 22.055 22.055 0 0 1-3.434-2.414C2.045 10.731 0 8.35 0 5.5 0 2.836 2.086 1 4.25 1 5.797 1 7.153 1.802 8 3.02 8.847 1.802 10.203 1 11.75 1 13.914 1 16 2.836 16 5.5c0 2.85-2.045 5.231-3.885 6.818a22.066 22.066 0 0 1-3.744 2.584l-.018.01-.006.003h-.002ZM4.25 2.5c-1.336 0-2.75 1.164-2.75 3 0 2.15 1.58 4.144 3.365 5.682A20.58 20.58 0 0 0 8 13.393a20.58 20.58 0 0 0 3.135-2.211C12.92 9.644 14.5 7.65 14.5 5.5c0-1.836-1.414-3-2.75-3-1.373 0-2.609.986-3.029 2.456a.749.749 0 0 1-1.442 0C6.859 3.486 5.623 2.5 4.25 2.5Z" />
  </g>
  <text class="txt" x="56" y="34.5" font-family="-apple-system, BlinkMacSystemFont, 'Segoe UI', 'Noto Sans', Helvetica, Arial, sans-serif" font-size="18" font-weight="700">GitHub Sponsors</text>${
    showCount
      ? `
  <line class="div" x1="${baseWidth}" y1="12" x2="${baseWidth}" y2="45" stroke-width="1.5" />
  <text class="cnt" x="${countCenterX}" y="34.5" text-anchor="middle" font-family="-apple-system, BlinkMacSystemFont, 'Segoe UI', 'Noto Sans', Helvetica, Arial, sans-serif" font-size="16" font-weight="700">${countText}</text>`
      : ''
  }
</svg>
`;
}

const token = process.env.GITHUB_TOKEN || process.env.GH_TOKEN || '';
const fetchedCount = await fetchSponsorCount(token);
const effectiveCount = fetchedCount !== null ? fetchedCount : readExistingCount();

mkdirSync(dirname(svgPath), { recursive: true });
const nextSvg = buildSponsorsSvg(effectiveCount);

let currentSvg = '';
try {
  currentSvg = readFileSync(svgPath, 'utf8');
} catch {
  // file will be created
}

if (currentSvg !== nextSvg) {
  writeFileSync(svgPath, nextSvg, 'utf8');
  console.log(`Updated ${svgPath} (sponsor count=${effectiveCount}, fetched=${fetchedCount !== null})`);
} else {
  console.log(`No change for ${svgPath} (sponsor count=${effectiveCount})`);
}
