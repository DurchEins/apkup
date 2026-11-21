import { readdirSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import { join, relative } from 'node:path';

const root = process.cwd();
const chunks = [
  readFileSync(join(root, 'src/styles/tokens.css'), 'utf8'),
  readFileSync(join(root, 'src/styles/global.css'), 'utf8'),
];

function collectAstroFiles(dirPath) {
  const out = [];
  for (const entry of readdirSync(dirPath).sort()) {
    const full = join(dirPath, entry);
    if (statSync(full).isDirectory()) {
      out.push(...collectAstroFiles(full));
    } else if (entry.endsWith('.astro')) {
      out.push(full);
    }
  }
  return out;
}

for (const dir of ['src/components', 'src/pages']) {
  const fullDir = join(root, dir);
  for (const filePath of collectAstroFiles(fullDir)) {
    const src = readFileSync(filePath, 'utf8');
    const match = src.match(/<style>([\s\S]*?)<\/style>/);
    if (match) {
      const rel = relative(root, filePath).replace(/\\/g, '/');
      chunks.push(`/* ${rel} */\n${match[1].trim()}`);
    }
  }
}

writeFileSync(join(root, 'public/assets/styles.css'), chunks.join('\n\n') + '\n', 'utf8');
console.log('Built public/assets/styles.css (' + chunks.length + ' sections)');
