// Display names only. Codes remain the values sent to and received from the API.
export const LANGUAGE_NAMES = Object.freeze({
  vi: "Vietnamese",
  en: "English",
  ar: "Arabic",
  de: "German",
  es: "Spanish",
  fr: "French",
  hi: "Hindi",
  id: "Indonesian",
  it: "Italian",
  ja: "Japanese",
  ko: "Korean",
  ms: "Malay",
  pt: "Portuguese (Brazil)",
  ru: "Russian",
  th: "Thai",
  "zh-CN": "Chinese (Simplified)",
});

export const LANGUAGE_OPTIONS = Object.entries(LANGUAGE_NAMES).map(([code, label]) => ({ code, label }));
export function languageLabel(code) { return LANGUAGE_NAMES[code] || code || ""; }
