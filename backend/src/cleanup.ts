import cleanupPrompt from "../../prompt/cleanup.md";

export const CLEANUP_MODEL = "claude-haiku-5-5";
export const CLEANUP_TIMEOUT_MS = 8000;
export const MIN_LENGTH_RATIO = 0.5;
export const MAX_LENGTH_RATIO = 1.5;
// Below this many characters the lower bound is skipped: a short dictation with a
// self-correction or heavy filler legitimately shrinks by more than half.
export const MIN_LENGTH_CHECK_CHARS = 100;
const MAX_TOKENS = 4096;

interface CleanupEnv {
  ANTHROPIC_API_KEY: string;
}

/** Returns the cleaned text, or null when cleanup failed any guard. */
export async function cleanup(env: CleanupEnv, raw: string, dictionary: string): Promise<string | null> {
  try {
    const res = await fetch("https://api.anthropic.com/v1/messages", {
      method: "POST",
      headers: {
        "x-api-key": env.ANTHROPIC_API_KEY,
        "anthropic-version": "2023-06-01",
        "content-type": "application/json",
      },
      body: JSON.stringify({
        model: CLEANUP_MODEL,
        max_tokens: MAX_TOKENS,
        // A function replacer, so "$&" and friends in a term are not treated as patterns.
        system: cleanupPrompt.replace("{{DICTIONARY}}", () => dictionary),
        messages: [{ role: "user", content: `<transcript>${raw}</transcript>` }],
      }),
      signal: AbortSignal.timeout(CLEANUP_TIMEOUT_MS),
    });
    if (!res.ok) return null;
    const data = (await res.json()) as { content?: { type: string; text?: string }[] };
    const text = (data.content ?? [])
      .filter((b) => b.type === "text")
      .map((b) => b.text ?? "")
      .join("")
      .trim();
    // An empty answer is never a cleanup, even for short input.
    if (!text) return null;
    if (raw.length >= MIN_LENGTH_CHECK_CHARS && text.length < raw.length * MIN_LENGTH_RATIO) return null;
    if (text.length > raw.length * MAX_LENGTH_RATIO) return null;
    return text;
  } catch {
    return null;
  }
}
