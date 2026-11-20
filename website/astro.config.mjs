import { defineConfig } from 'astro/config';

export default defineConfig({
  site: 'https://durcheins.github.io',
  base: '/apkup',
  outDir: '../docs',
  compressHTML: true,
});
