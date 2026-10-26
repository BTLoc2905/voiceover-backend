import test from "node:test";
import assert from "node:assert/strict";
import { LANGUAGE_NAMES, LANGUAGE_OPTIONS, languageLabel } from "./languageDisplay.js";

test("all 16 language codes keep their API values and use the specified English labels", () => {
  assert.deepEqual(LANGUAGE_NAMES, {
    vi: "Vietnamese", en: "English", ar: "Arabic", de: "German",
    es: "Spanish", fr: "French", hi: "Hindi", id: "Indonesian",
    it: "Italian", ja: "Japanese", ko: "Korean", ms: "Malay",
    pt: "Portuguese (Brazil)", ru: "Russian", th: "Thai", "zh-CN": "Chinese (Simplified)",
  });
  assert.equal(LANGUAGE_OPTIONS.length, 16);
  for (const { code, label } of LANGUAGE_OPTIONS) assert.equal(languageLabel(code), label);
});
