import { readdirSync, readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const root = process.cwd();
const chunks = [
  readFileSync(join(root, 'src/styles/tokens.css'), 'utf8'),
  readFileSync(join(root, 'src/styles/global.css'), 'utf8'),
];

for (const dir of ['src/components', 'src/pages']) {
  const fullDir = join(root, dir);
  for (const file of readdirSync(fullDir).sort()) {
    if (!file.endsWith('.astro')) continue;
    const src = readFileSync(join(fullDir, file), 'utf8');
    const match = src.match(/<style>([\s\S]*?)<\/style>/);
    if (match) {
      chunks.push(`/* ${dir}/${file} */\n${match[1].trim()}`);
    }
  }
}

writeFileSync(join(root, 'public/assets/styles.css'), chunks.join('\n\n') + '\n', 'utf8');
console.log('Built public/assets/styles.css (' + chunks.length + ' sections)');
