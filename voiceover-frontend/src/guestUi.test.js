import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import { LANGUAGE_OPTIONS } from "./languageDisplay.js";

const readCatalog = (code) => JSON.parse(readFileSync(new URL(`./guestUi.${code}.json`, import.meta.url), "utf8"));

test("guest interface has complete copy for every selectable language", () => {
  const reference = readCatalog("en");
  const keys = Object.keys(reference).sort();
  assert.equal(keys.length, 107);
  for (const { code } of LANGUAGE_OPTIONS) {
    const catalog = readCatalog(code);
    assert.deepEqual(Object.keys(catalog).sort(), keys, `Missing or extra labels for ${code}`);
    for (const [key, value] of Object.entries(catalog)) {
      assert.equal(typeof value, "string", `${code}.${key}`);
      assert.ok(value.trim(), `${code}.${key} is blank`);
    }
  }
});
