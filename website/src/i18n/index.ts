import { getBaseUrl } from '../utils/site';
import en from './en.json';
import de from './de.json';
import es from './es.json';
import fr from './fr.json';
import it from './it.json';
import ja from './ja.json';
import pt from './pt.json';
import ru from './ru.json';
import zhCn from './zh-cn.json';

export const LOCALES = ['en', 'de', 'es', 'fr', 'it', 'ja', 'pt', 'ru', 'zh-cn'] as const;
export const NON_DEFAULT_LOCALES = ['de', 'es', 'fr', 'it', 'ja', 'pt', 'ru', 'zh-cn'] as const;
export type Locale = (typeof LOCALES)[number];
export const DEFAULT_LOCALE: Locale = 'en';

export interface LocaleMeta {
  code: string;
  nativeName: string;
  hreflang: string;
  screenshotDir: 'en' | 'de';
  changelogLang: 'en' | 'de';
}

export const LOCALE_META: Record<Locale, LocaleMeta> = {
  en: {
    code: 'EN',
    nativeName: 'English',
    hreflang: 'en',
    screenshotDir: 'en',
    changelogLang: 'en',
  },
  de: {
    code: 'DE',
    nativeName: 'Deutsch',
    hreflang: 'de',
    screenshotDir: 'de',
    changelogLang: 'de',
  },
  es: {
    code: 'ES',
    nativeName: 'Español',
    hreflang: 'es',
    screenshotDir: 'en',
    changelogLang: 'en',
  },
  fr: {
    code: 'FR',
    nativeName: 'Français',
    hreflang: 'fr',
    screenshotDir: 'en',
    changelogLang: 'en',
  },
  it: {
    code: 'IT',
    nativeName: 'Italiano',
    hreflang: 'it',
    screenshotDir: 'en',
    changelogLang: 'en',
  },
  ja: {
    code: 'JA',
    nativeName: '日本語',
    hreflang: 'ja',
    screenshotDir: 'en',
    changelogLang: 'en',
  },
  pt: {
    code: 'PT',
    nativeName: 'Português',
    hreflang: 'pt',
    screenshotDir: 'en',
    changelogLang: 'en',
  },
  ru: {
    code: 'RU',
    nativeName: 'Русский',
    hreflang: 'ru',
    screenshotDir: 'en',
    changelogLang: 'en',
  },
  'zh-cn': {
    code: 'ZH-CN',
    nativeName: '简体中文',
    hreflang: 'zh-CN',
    screenshotDir: 'en',
    changelogLang: 'en',
  },
};

export type TranslationDict = typeof en;

const DICTS: Record<Locale, Partial<TranslationDict>> = {
  en,
  de,
  es,
  fr,
  it,
  ja,
  pt,
  ru,
  'zh-cn': zhCn,
};

function deepMergeWithFallback<T>(base: T, override: unknown): T {
  if (override === undefined || override === null) return base;
  if (Array.isArray(base)) {
    if (!Array.isArray(override) || override.length !== base.length) return base;
    return base.map((item, i) => deepMergeWithFallback(item, override[i])) as unknown as T;
  }
  if (typeof base === 'object' && base !== null) {
    if (typeof override !== 'object' || override === null || Array.isArray(override)) return base;
    const result: Record<string, unknown> = {};
    const ov = override as Record<string, unknown>;
    for (const key of Object.keys(base as Record<string, unknown>)) {
      result[key] = deepMergeWithFallback((base as Record<string, unknown>)[key], ov[key]);
    }
    return result as T;
  }
  return (typeof override === typeof base ? override : base) as T;
}

export function isLocale(value: string | undefined): value is Locale {
  return Boolean(value && (LOCALES as readonly string[]).includes(value));
}

export function getTranslations(lang: Locale = DEFAULT_LOCALE): TranslationDict {
  if (lang === 'en') return en;
  return deepMergeWithFallback(en, DICTS[lang]);
}

export function localePath(lang: Locale, subPath = ''): string {
  const base = getBaseUrl();
  const clean = subPath.replace(/^\/+/, '');
  return lang === 'en' ? `${base}${clean}` : `${base}${lang}/${clean}`;
}

export function absoluteLocaleUrl(
  lang: Locale,
  subPath = '',
  siteOrigin = 'https://durcheins.github.io',
): string {
  return `${siteOrigin}${localePath(lang, subPath)}`;
}
