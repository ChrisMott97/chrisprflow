export interface Term {
  term: string;
  misheard: string[];
}

// Whisper reads at most 224 tokens of prompt. About 3.5 characters per token puts 224 tokens
// near 800 characters, so stop adding terms at this budget.
export const WHISPER_PROMPT_CHARS = 800;
export const MAX_TERMS = 1000;

export async function loadDictionary(db: D1Database): Promise<Term[]> {
  const { results } = await db
    .prepare("SELECT term, misheard FROM dictionary ORDER BY rank ASC")
    .all<{ term: string; misheard: string }>();
  return results.map((r) => ({ term: r.term, misheard: JSON.parse(r.misheard) as string[] }));
}

/** Replaces the whole dictionary. The array order is the rank. */
export async function saveDictionary(db: D1Database, terms: Term[]): Promise<void> {
  await db.batch([
    db.prepare("DELETE FROM dictionary"),
    ...terms.map((t, i) =>
      db
        .prepare("INSERT INTO dictionary (rank, term, misheard) VALUES (?, ?, ?)")
        .bind(i, t.term, JSON.stringify(t.misheard)),
    ),
  ]);
}

/** Returns the cleaned terms, or null when the body is not `{terms: [{term, misheard?}]}`. */
export function parseTerms(body: unknown): Term[] | null {
  const list = (body as { terms?: unknown } | null)?.terms;
  if (!Array.isArray(list) || list.length > MAX_TERMS) return null;
  const out: Term[] = [];
  for (const item of list) {
    const t = item as { term?: unknown; misheard?: unknown };
    if (typeof t?.term !== "string" || !t.term.trim()) return null;
    const m = t.misheard ?? [];
    if (!Array.isArray(m) || m.some((x) => typeof x !== "string")) return null;
    out.push({ term: t.term.trim(), misheard: (m as string[]).map((x) => x.trim()).filter(Boolean) });
  }
  return out;
}

/** Correct spellings only, in rank order, stopping before the character budget. */
export function whisperPrompt(terms: Term[]): string {
  let out = "";
  for (const { term } of terms) {
    const next = out ? `${out}, ${term}` : term;
    if (next.length > WHISPER_PROMPT_CHARS) break;
    out = next;
  }
  return out;
}

/** One line per term, in the form the cleanup prompt describes: `Term (mis-heard as: a, b)`. */
export function cleanupDictionary(terms: Term[]): string {
  return terms
    .map((t) => (t.misheard.length ? `${t.term} (mis-heard as: ${t.misheard.join(", ")})` : t.term))
    .join("\n");
}
