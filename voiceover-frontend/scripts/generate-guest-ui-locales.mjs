// One-time generation of static UI copy using the same no-key translation endpoint as the backend.
// Review generated wording before release; the frontend itself never calls this service.
import { readFile, writeFile } from "node:fs/promises";

const source = JSON.parse(await readFile(new URL("../src/guestUi.en.json", import.meta.url), "utf8"));
const entries = Object.entries(source);
const languages = ["ar", "de", "es", "fr", "hi", "id", "it", "ja", "ko", "ms", "pt", "ru", "th", "zh-CN"];

async function translate(lines, language) {
  const query = new URLSearchParams({ client: "gtx", sl: "en", tl: language === "pt" ? "pt-BR" : language, dt: "t", q: lines.join("\n") });
  const response = await fetch(`https://translate.googleapis.com/translate_a/single?${query}`, { signal: AbortSignal.timeout(25000) });
  if (!response.ok) throw new Error(`${language}: HTTP ${response.status}`);
  const data = await response.json();
  const translated = data[0].map((part) => part[0]).join("").replace(/\n$/, "").split("\n");
  if (translated.length !== lines.length) throw new Error(`${language}: expected ${lines.length} lines, received ${translated.length}`);
  return translated.map((line) => line.trim());
}

for (const language of languages) {
  const result = {};
  for (let index = 0; index < entries.length; index += 10) {
    const batch = entries.slice(index, index + 10);
    let translated;
    for (let attempt = 0; attempt < 3; attempt++) {
      try { translated = await translate(batch.map(([, value]) => value), language); break; }
      catch (error) { if (attempt === 2) throw error; await new Promise((resolve) => setTimeout(resolve, 1000 * (attempt + 1))); }
    }
    batch.forEach(([key], offset) => { result[key] = translated[offset]; });
  }
  await writeFile(new URL(`../src/guestUi.${language}.json`, import.meta.url), `${JSON.stringify(result, null, 2)}\n`);
  process.stdout.write(`${language}: ${Object.keys(result).length} labels\n`);
}
