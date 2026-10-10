// Runs every case in cases.jsonl against the Anthropic API using cleanup.md.
// Usage: node run.ts [--runs N] [--verbose]    (needs ANTHROPIC_API_KEY in the environment)
import { readFileSync } from "node:fs";

const MODEL = "claude-haiku-5-5";
const MAX_TOKENS = 4096;
const LONG_CASE_CHARS = 1000;
// Same bounds as backend/src/cleanup.ts: outside these the backend discards the output.
const MIN_LENGTH_RATIO = 0.5;
const MAX_LENGTH_RATIO = 1.5;
// Stand-in for the list the backend builds from the synced dictionary.
const TEST_DICTIONARY = [
  "Akroyd (mis-heard as: Acroyd, Ackroyd)",
  "Ankar",
  "Kubernetes (mis-heard as: Cooper Nettys, Cooper Netties)",
].join("\n");

interface Case {
  raw: string;
  must_contain: string[];
  must_not_contain: string[];
  max_len?: number;
  note: string;
}

const dir = new URL(".", import.meta.url);
const system = readFileSync(new URL("cleanup.md", dir), "utf8").replace("{{DICTIONARY}}", TEST_DICTIONARY);
const cases: Case[] = readFileSync(new URL("cases.jsonl", dir), "utf8")
  .split("\n")
  .filter((l) => l.trim())
  .map((l) => JSON.parse(l));

const args = process.argv.slice(2);
const runs = Number(args[args.indexOf("--runs") + 1]) || 1;
const verbose = args.includes("--verbose");

const apiKey = process.env.ANTHROPIC_API_KEY;
if (!apiKey) {
  console.error("ANTHROPIC_API_KEY is not set");
  process.exit(2);
}

// Word-boundary, case-insensitive match for must_not_contain; punctuation counts as a boundary.
const words = (s: string) => ` ${s.toLowerCase().replace(/[^a-z0-9']+/g, " ").trim()} `;

function check(c: Case, out: string): string[] {
  const fails: string[] = [];
  for (const m of c.must_contain) {
    if (!m.split("|").some((alt) => out.includes(alt))) fails.push(`missing ${JSON.stringify(m)}`);
  }
  for (const m of c.must_not_contain) {
    if (words(out).includes(words(m))) fails.push(`contains ${JSON.stringify(m)}`);
  }
  if (c.max_len !== undefined && out.length > c.max_len) fails.push(`length ${out.length} > ${c.max_len}`);
  if (out.length < c.raw.length * MIN_LENGTH_RATIO) fails.push("shorter than the backend guard allows");
  if (out.length > c.raw.length * MAX_LENGTH_RATIO) fails.push("longer than the backend guard allows");
  return fails;
}

async function clean(raw: string) {
  const start = performance.now();
  const res = await fetch("https://api.anthropic.com/v1/messages", {
    method: "POST",
    headers: { "x-api-key": apiKey!, "anthropic-version": "2023-06-01", "content-type": "application/json" },
    body: JSON.stringify({
      model: MODEL,
      max_tokens: MAX_TOKENS,
      system,
      messages: [{ role: "user", content: `<transcript>${raw}</transcript>` }],
    }),
  });
  const ms = performance.now() - start;
  if (!res.ok) throw new Error(`API ${res.status}: ${(await res.text()).slice(0, 300)}`);
  const data = (await res.json()) as {
    content?: { type: string; text?: string }[];
    usage?: { input_tokens: number; output_tokens: number };
  };
  const text = (data.content ?? []).filter((b) => b.type === "text").map((b) => b.text ?? "").join("").trim();
  return { text, ms, tokens: (data.usage?.input_tokens ?? 0) + (data.usage?.output_tokens ?? 0) };
}

const median = (xs: number[]) => {
  if (!xs.length) return 0;
  const s = [...xs].sort((a, b) => a - b);
  return s.length % 2 ? s[(s.length - 1) / 2] : (s[s.length / 2 - 1] + s[s.length / 2]) / 2;
};

let passed = 0;
let total = 0;
let tokens = 0;
const shortMs: number[] = [];
const longMs: number[] = [];
const failedCases = new Map<string, number>();

for (let run = 1; run <= runs; run++) {
  if (runs > 1) console.log(`--- run ${run} of ${runs} ---`);
  // Cases run in parallel; results print in file order.
  const results = await Promise.all(
    cases.map(async (c) => {
      try {
        return { c, ...(await clean(c.raw)), error: "" };
      } catch (e) {
        return { c, text: "", ms: 0, tokens: 0, error: String(e) };
      }
    }),
  );
  for (const r of results) {
    total++;
    const fails = r.error ? [r.error] : check(r.c, r.text);
    tokens += r.tokens;
    if (!r.error) (r.c.raw.length >= LONG_CASE_CHARS ? longMs : shortMs).push(r.ms);
    if (fails.length === 0) {
      passed++;
      console.log(`PASS  ${r.c.note} (${Math.round(r.ms)} ms)`);
      if (verbose) console.log(`      ${JSON.stringify(r.text)}`);
    } else {
      failedCases.set(r.c.note, (failedCases.get(r.c.note) ?? 0) + 1);
      console.log(`FAIL  ${r.c.note} (${Math.round(r.ms)} ms)`);
      console.log(`      in:  ${JSON.stringify(r.c.raw.length > 200 ? r.c.raw.slice(0, 200) + "..." : r.c.raw)}`);
      console.log(`      out: ${JSON.stringify(r.text)}`);
      for (const f of fails) console.log(`      - ${f}`);
    }
  }
}

console.log(`\n${passed}/${total} passed over ${runs} run(s)`);
console.log(`median latency: ${Math.round(median(shortMs))} ms short, ${Math.round(median(longMs))} ms long`);
console.log(`tokens: ${tokens} (input + output)`);
if (failedCases.size) {
  console.log("failed at least once:");
  for (const [note, n] of failedCases) console.log(`  ${n}x ${note}`);
}
process.exit(passed === total ? 0 : 1);
