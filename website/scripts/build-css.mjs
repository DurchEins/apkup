import { readdirSync, readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const root = process.cwd();
const chunks = [
  readFileSync(join(root, 'src/styles/tokens.css'), 'utf8'),
  readFileSync(join(root, 'src/styles/global.css'), 'utf8'),
];

const compDir = join(root, 'src/components');
for (const file of readdirSync(compDir).sort()) {
  if (!file.endsWith('.astro')) continue;
  const src = readFileSync(join(compDir, file), 'utf8');
  const match = src.match(/<style>([\s\S]*?)<\/style>/);
  if (match) {
    chunks.push(`/* ${file} */\n${match[1].trim()}`);
  }
}

writeFileSync(join(root, 'public/assets/styles.css'), chunks.join('\n\n') + '\n', 'utf8');
console.log('Built public/assets/styles.css (' + chunks.length + ' sections)');
