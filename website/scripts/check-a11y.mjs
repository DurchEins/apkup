import { readdirSync, readFileSync, statSync } from 'node:fs';
import { dirname, join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const websiteDir = join(__dirname, '..');
const docsDir = join(websiteDir, '..', 'docs');
const tokensPath = join(websiteDir, 'src', 'styles', 'tokens.css');
const srcDir = join(websiteDir, 'src');

const errors = [];

function walk(dir, extFilter, out = []) {
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    const st = statSync(full);
    if (st.isDirectory()) walk(full, extFilter, out);
    else if (!extFilter || full.endsWith(extFilter)) out.push(full);
  }
  return out;
}

function hexToRgb(hex) {
  const clean = hex.replace('#', '').trim();
  const full = clean.length === 3 ? clean.split('').map((c) => c + c).join('') : clean;
  const n = parseInt(full, 16);
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
}

function relLuminance([r, g, b]) {
  const lin = [r, g, b].map((c) => {
    const s = c / 255;
    return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
  });
  return 0.2126 * lin[0] + 0.7152 * lin[1] + 0.0722 * lin[2];
}

function contrastRatio(hex1, hex2) {
  const l1 = relLuminance(hexToRgb(hex1));
  const l2 = relLuminance(hexToRgb(hex2));
  return (Math.max(l1, l2) + 0.05) / (Math.min(l1, l2) + 0.05);
}

function parseTokenThemes(css) {
  const themes = {};
  const blocks = [
    { name: 'dark', re: /:root,\s*\n?\[data-theme='dark'\]\s*\{([\s\S]*?)\}/ },
    { name: 'light', re: /\[data-theme='light'\]\s*\{([\s\S]*?)\}/ },
  ];
  for (const b of blocks) {
    const m = css.match(b.re);
    if (!m) {
      errors.push(`tokens.css: Missing ${b.name} theme block`);
      continue;
    }
    const vars = {};
    for (const line of m[1].split(/\r?\n/)) {
      const vm = line.match(/--([a-z0-9-]+)\s*:\s*(#[0-9a-fA-F]{3,6})\s*;/);
      if (vm) vars[vm[1]] = vm[2].toLowerCase();
    }
    themes[b.name] = vars;
  }
  return themes;
}

function auditTokens() {
  const css = readFileSync(tokensPath, 'utf8');
  const themes = parseTokenThemes(css);
  const pairs = [
    ['text', 'bg', 4.5],
    ['text', 'surface', 4.5],
    ['text', 'surface-low', 4.5],
    ['text', 'surface-high', 4.5],
    ['text-muted', 'bg', 4.5],
    ['text-muted', 'surface', 4.5],
    ['text-muted', 'surface-low', 4.5],
    ['text-muted', 'surface-high', 4.5],
    ['accent', 'bg', 4.5],
    ['accent', 'surface', 4.5],
    ['accent', 'surface-low', 4.5],
    ['accent', 'surface-high', 4.5],
    ['accent-ink', 'accent', 4.5],
    ['accent-soft-ink', 'accent-soft', 4.5],
    ['tertiary-soft-ink', 'tertiary-soft', 4.5],
    ['danger', 'danger-soft', 4.5],
  ];

  for (const [themeName, vars] of Object.entries(themes)) {
    for (const [fg, bg, minRatio] of pairs) {
      if (!vars[fg] || !vars[bg]) {
        errors.push(`tokens.css [${themeName}]: Missing token --${fg} or --${bg}`);
        continue;
      }
      const ratio = contrastRatio(vars[fg], vars[bg]);
      if (ratio < minRatio) {
        errors.push(
          `tokens.css [${themeName}]: --${fg} (${vars[fg]}) on --${bg} (${vars[bg]}) = ${ratio.toFixed(2)}:1 (< ${minRatio}:1)`
        );
      }
    }
  }
}

function stripTags(str) {
  return str.replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').trim();
}

function checkScopedThemeSelectors() {
  const astroFiles = walk(srcDir, '.astro');
  for (const file of astroFiles) {
    const content = readFileSync(file, 'utf8');
    const styleMatch = content.match(/<style[\s\S]*?<\/style>/g) || [];
    for (const block of styleMatch) {
      if (/is:global/.test(block)) continue;
      const lines = block.split(/\r?\n/);
      lines.forEach((line, idx) => {
        if (/\[data-theme=['"](light|dark)['"]\]/.test(line) && !/:global\s*\(/.test(line)) {
          errors.push(
            `${relative(websiteDir, file)}: Scoped <style> uses [data-theme=...] without :global() (match: "${line.trim()}" ~${idx})`
          );
        }
      });
    }
  }
}

function auditHtmlFiles() {
  const htmlFiles = walk(docsDir, '.html');
  if (htmlFiles.length < 90) {
    errors.push(`Expected >= 90 built HTML files in docs/, found ${htmlFiles.length}`);
  }

  for (const file of htmlFiles) {
    const rel = relative(docsDir, file).replace(/\\/g, '/');
    const html = readFileSync(file, 'utf8');

    const langMatch = html.match(/<html[^>]*\blang="([^"]+)"/i);
    if (!langMatch) errors.push(`${rel}: Missing <html lang="...">`);

    const h1Count = (html.match(/<h1[\s>]/gi) || []).length;
    if (h1Count !== 1) errors.push(`${rel}: Expected exactly 1 <h1>, found ${h1Count}`);

    if (!/<main[^>]*\bid="main-content"/i.test(html)) {
      errors.push(`${rel}: Missing <main id="main-content">`);
    }
    if (!/<a[^>]*\bhref="#main-content"[^>]*>/i.test(html)) {
      errors.push(`${rel}: Missing skip-to-content link (#main-content)`);
    }
    if (!/accessibility\/["']/i.test(html)) {
      errors.push(`${rel}: Missing link to accessibility statement page`);
    }

    if (/\btabindex="[1-9][0-9]*"/i.test(html)) {
      errors.push(`${rel}: Positive tabindex found (disrupts keyboard order)`);
    }

    // Disallow interactive controls nested inside <summary> or <button> or <a>
    const summaryRegex = /<summary\b[^>]*>([\s\S]*?)<\/summary>/gi;
    let sm;
    while ((sm = summaryRegex.exec(html)) !== null) {
      if (/<(a|button)\b/i.test(sm[1])) {
        errors.push(`${rel}: Nested interactive <a|button> inside <summary>`);
      }
    }

    const imgRegex = /<img\b([^>]*)>/gi;
    let im;
    while ((im = imgRegex.exec(html)) !== null) {
      if (!/\balt="/i.test(im[1])) {
        errors.push(`${rel}: <img> missing alt attribute: <img ${im[1].slice(0, 80)}>`);
      }
    }

    const btnRegex = /<button\b([^>]*)>([\s\S]*?)<\/button>/gi;
    let bm;
    while ((bm = btnRegex.exec(html)) !== null) {
      const attrs = bm[1];
      const text = stripTags(bm[2]);
      const hasAriaLabel = /\baria-label="[^"]+"/i.test(attrs) || /\baria-labelledby="[^"]+"/i.test(attrs);
      if (!text && !hasAriaLabel) {
        errors.push(`${rel}: Empty <button> without text or aria-label: <button ${attrs.slice(0, 80)}>`);
      }
    }

    // Check unlabelled visible text/url/search inputs
    const labelForIds = new Set();
    const labelRegex = /<label\b[^>]*\bfor="([^"]+)"/gi;
    let lm;
    while ((lm = labelRegex.exec(html)) !== null) labelForIds.add(lm[1]);

    const inputRegex = /<input\b([^>]*)>/gi;
    let ipm;
    while ((ipm = inputRegex.exec(html)) !== null) {
      const attrs = ipm[1];
      if (/\btype="hidden"/i.test(attrs) || /\bhidden\b/i.test(attrs)) continue;
      const idMatch = attrs.match(/\bid="([^"]+)"/i);
      const hasFor = idMatch && labelForIds.has(idMatch[1]);
      const hasAria = /\baria-label="[^"]+"/i.test(attrs) || /\baria-labelledby="[^"]+"/i.test(attrs);
      if (!hasFor && !hasAria) {
        errors.push(`${rel}: Unlabelled <input ${attrs.slice(0, 80)}>`);
      }
    }
  }

  return htmlFiles.length;
}

auditTokens();
checkScopedThemeSelectors();
const htmlCount = auditHtmlFiles();

if (errors.length > 0) {
  console.error(`\n[check-a11y] FAILED with ${errors.length} issue(s):\n`);
  for (const err of errors.slice(0, 50)) console.error(`  - ${err}`);
  if (errors.length > 50) console.error(`  ... and ${errors.length - 50} more.`);
  process.exit(1);
}

console.log(`[check-a11y] PASSED: ${htmlCount} built HTML pages + Light/Dark contrast tokens + ARIA/semantic checks.`);
