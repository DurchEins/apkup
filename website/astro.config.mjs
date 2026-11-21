import { defineConfig } from 'astro/config';

export default defineConfig({
  site: 'https://durcheins.github.io',
  base: '/apkup',
  outDir: '../docs',
  compressHTML: true,
  i18n: {
    defaultLocale: 'en',
    locales: ['en', 'de', 'es', 'fr', 'it', 'ja', 'pt', 'ru', 'zh-cn'],
    routing: {
      prefixDefaultLocale: false,
    },
  },
});
