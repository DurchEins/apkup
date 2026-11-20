const SLUG_BY_WIKI_NAME: Record<string, string> = {
  home: 'home',
  'update-sources,-repository-linking-&-signature-protection': 'sources',
  'f-droid-&-index-v2-repositories': 'fdroid',
  'configuring-github-&-gitlab-tokens': 'tokens',
  'setting-up-shizuku': 'shizuku-setup',
  'shizuku-&-background-installation': 'shizuku-deep',
  'obtainium-migration-&-sync-guide': 'obtainium',
  'app-management,-filters,-background-checks-&-backup': 'management',
  'troubleshooting-source-errors': 'troubleshooting',
};

function escapeHtml(s: string): string {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

function stripLeadingEmoji(s: string): string {
  return s.replace(/^[\p{Emoji_Presentation}\p{Extended_Pictographic}\uFE0F\u200D]+\s*/gu, '').trim();
}

function formatInline(raw: string): string {
  let s = escapeHtml(raw);
  s = s.replace(/`([^`]+)`/g, '<code class="wk-code mono">$1</code>');
  s = s.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');
  s = s.replace(/\*([^*]+)\*/g, '<em>$1</em>');
  s = s.replace(/\[([^\]]+)\]\(([^)]+)\)/g, (_m, label, url) => {
    const cleanLabel = stripLeadingEmoji(label);
    const wikiMatch = url.match(/github\.com\/DurchEins\/apkup\/wiki(?:\/([^#?]+))?(#.*)?$/i);
    if (wikiMatch) {
      const rawName = wikiMatch[1] ? decodeURIComponent(wikiMatch[1]).toLowerCase() : 'home';
      const targetSlug = SLUG_BY_WIKI_NAME[rawName] || 'home';
      return `<a href="#doc-${targetSlug}" data-wiki-jump="${targetSlug}">${cleanLabel}</a>`;
    }
    if (url.startsWith('#')) {
      return `<a href="${url}">${cleanLabel}</a>`;
    }
    return `<a href="${url}" target="_blank" rel="noopener noreferrer">${cleanLabel}</a>`;
  });
  return s;
}

export interface WikiHeading {
  id: string;
  text: string;
  level: 2 | 3;
}

export function renderFullWikiHtml(md: string, slugPrefix: string): { html: string; headings: WikiHeading[] } {
  const lines = md.split(/\r?\n/);
  const out: string[] = [];
  const headings: WikiHeading[] = [];
  const usedIds = new Set<string>();
  let skipSection = false;
  let inFaqDetails = false;
  let inCode = false;
  let codeLang = '';
  let codeBuf: string[] = [];
  let inUl = false;
  let inOl = false;
  let inTable = false;
  let tableRows: string[][] = [];
  let inQuote = false;
  let quoteBuf: string[] = [];
  let inFaqPart = false;

  function flushLists() {
    if (inUl) {
      out.push('</ul>');
      inUl = false;
    }
    if (inOl) {
      out.push('</ol>');
      inOl = false;
    }
  }

  function flushTable() {
    if (!inTable) return;
    inTable = false;
    if (!tableRows.length) return;
    const header = tableRows[0];
    const body = tableRows.slice(1).filter((r) => !r.every((c) => /^[-:\s]+$/.test(c)));
    let html = '<div class="wk-table-wrap"><table class="wk-table"><thead><tr>';
    for (const h of header) html += `<th>${formatInline(h)}</th>`;
    html += '</tr></thead><tbody>';
    for (const row of body) {
      html += '<tr>';
      for (const cell of row) html += `<td>${formatInline(cell)}</td>`;
      html += '</tr>';
    }
    html += '</tbody></table></div>';
    out.push(html);
    tableRows = [];
  }

  function flushQuote() {
    if (!inQuote) return;
    inQuote = false;
    const joined = quoteBuf.join(' ').trim();
    const alertMatch = joined.match(/^\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION)\]\s*(.*)$/i);
    if (alertMatch) {
      const kind = alertMatch[1].toUpperCase();
      out.push(
        `<div class="wk-callout wk-callout-${kind.toLowerCase()}"><strong class="mono">${kind}</strong><p>${formatInline(alertMatch[2])}</p></div>`
      );
    } else if (joined) {
      out.push(`<blockquote class="wk-quote"><p>${formatInline(joined)}</p></blockquote>`);
    }
    quoteBuf = [];
  }

  function closeFaqItem() {
    flushLists();
    flushTable();
    flushQuote();
    if (inFaqDetails) {
      out.push('</div></details>');
      inFaqDetails = false;
    }
  }

  for (const rawLine of lines) {
    const line = rawLine.trimEnd();

    if (line.startsWith('```')) {
      flushLists();
      flushTable();
      flushQuote();
      if (!inCode) {
        inCode = true;
        codeLang = line.slice(3).trim();
        codeBuf = [];
      } else {
        inCode = false;
        if (!skipSection) {
          out.push(
            `<div class="wk-codeblock"><button type="button" class="wk-copy-btn mono" data-copy-code>Copy</button><pre class="wk-pre mono"><code data-lang="${escapeHtml(codeLang)}">${escapeHtml(
              codeBuf.join('\n')
            )}</code></pre></div>`
          );
        }
      }
      continue;
    }

    if (inCode) {
      codeBuf.push(rawLine);
      continue;
    }

    function makeId(text: string): string {
      const base = text
        .toLowerCase()
        .replace(/[`*_~()[\]{}:.!?,/\\+]/g, '')
        .trim()
        .replace(/\s+/g, '-');
      let candidate = `${slugPrefix}-${base || 'sec'}`;
      let n = 2;
      while (usedIds.has(candidate)) {
        candidate = `${slugPrefix}-${base}-${n++}`;
      }
      usedIds.add(candidate);
      return candidate;
    }

    // Top H1 is already shown in the chapter header
    if (line.startsWith('# ')) {
      continue;
    }

    const h2Match = line.match(/^##\s+(.*)$/);
    if (h2Match) {
      closeFaqItem();
      const clean = stripLeadingEmoji(h2Match[1]);
      if (/^(table of contents|quick navigation)$/i.test(clean)) {
        skipSection = true;
        continue;
      }
      skipSection = false;
      inFaqPart = /frequently asked questions|faq/i.test(clean);
      const id = makeId(clean);
      headings.push({ id, text: clean, level: 2 });
      const badgeClass = /part 1/i.test(clean)
        ? 'wk-part-green'
        : /part 2/i.test(clean)
          ? 'wk-part-blue'
          : /part 3/i.test(clean)
            ? 'wk-part-amber'
            : 'wk-part-neutral';
      out.push(`<h3 id="${id}" class="wk-h2 ${badgeClass}">${formatInline(clean)}</h3>`);
      continue;
    }

    if (skipSection) continue;

    const h3Match = line.match(/^###\s+(.*)$/);
    if (h3Match) {
      closeFaqItem();
      const clean = stripLeadingEmoji(h3Match[1]);
      const id = makeId(clean);
      headings.push({ id, text: clean, level: 3 });
      if (inFaqPart) {
        inFaqDetails = true;
        out.push(
          `<details id="${id}" class="wk-faq"><summary><span>${formatInline(clean)}</span><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="m6 9 6 6 6-6"/></svg></summary><div class="wk-faq-body">`
        );
      } else {
        out.push(`<h4 id="${id}" class="wk-h3">${formatInline(clean)}</h4>`);
      }
      continue;
    }

    if (line.startsWith('|') && line.endsWith('|')) {
      flushLists();
      flushQuote();
      inTable = true;
      tableRows.push(
        line
          .slice(1, -1)
          .split('|')
          .map((c) => c.trim())
      );
      continue;
    } else if (inTable) {
      flushTable();
    }

    if (line.startsWith('>')) {
      flushLists();
      inQuote = true;
      quoteBuf.push(line.replace(/^>\s?/, ''));
      continue;
    } else if (inQuote) {
      flushQuote();
    }

    if (!line.trim()) {
      flushLists();
      continue;
    }

    if (/^---+$/.test(line.trim())) {
      flushLists();
      out.push('<hr class="wk-hr" />');
      continue;
    }

    const ulMatch = line.match(/^\s*[-*]\s+(.*)$/);
    if (ulMatch) {
      if (inOl) {
        out.push('</ol>');
        inOl = false;
      }
      if (!inUl) {
        out.push('<ul class="wk-ul">');
        inUl = true;
      }
      out.push(`<li>${formatInline(ulMatch[1])}</li>`);
      continue;
    }

    const olMatch = line.match(/^\s*\d+\.\s+(.*)$/);
    if (olMatch) {
      if (inUl) {
        out.push('</ul>');
        inUl = false;
      }
      if (!inOl) {
        out.push('<ol class="wk-ol">');
        inOl = true;
      }
      out.push(`<li>${formatInline(olMatch[1])}</li>`);
      continue;
    }

    flushLists();
    out.push(`<p class="wk-p">${formatInline(line.trim())}</p>`);
  }

  closeFaqItem();
  return { html: out.join('\n'), headings };
}
