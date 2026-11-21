/**
 * Normalizes Astro's BASE_URL so relative asset and page links always end with a trailing slash.
 */
export function getBaseUrl(): string {
  const rawBase = import.meta.env.BASE_URL;
  return rawBase.endsWith('/') ? rawBase : `${rawBase}/`;
}
