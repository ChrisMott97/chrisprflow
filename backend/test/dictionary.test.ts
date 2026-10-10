import { afterEach, describe, expect, it, vi } from "vitest";
import worker, { type Env } from "../src/index";
import { WHISPER_PROMPT_CHARS, cleanupDictionary, whisperPrompt } from "../src/dictionary";

let rows: { term: string; misheard: string }[] = [];
let history: Record<string, unknown>[] = [];
const batches: { sql: string; args: unknown[] }[][] = [];

const env = {
  MODE: "real",
  GROQ_API_KEY: "g",
  ANTHROPIC_API_KEY: "a",
  MAC_TOKEN: "mac-t",
  ANDROID_TOKEN: "and-t",
  SYNC_TOKEN: "sync-t",
  DB: {
    prepare(sql: string) {
      const make = (args: unknown[]) => ({
        sql,
        args,
        all: async () => ({
          results: sql.includes("FROM dictionary") ? rows : history.slice(0, Number(args[0])),
        }),
      });
      return { ...make([]), bind: (...args: unknown[]) => make(args) };
    },
    async batch(stmts: { sql: string; args: unknown[] }[]) {
      batches.push(stmts);
      if (stmts[0].sql.startsWith("DELETE FROM dictionary")) {
        rows = stmts.slice(1).map((s) => ({ term: s.args[1] as string, misheard: s.args[2] as string }));
      }
      return [];
    },
  },
} as unknown as Env;

const ctx = { waitUntil: (p: Promise<unknown>) => void p } as ExecutionContext;
const call = (path: string, method: string, token: string, body?: unknown) =>
  worker.fetch(
    new Request(`https://x${path}`, {
      method,
      headers: { authorization: `Bearer ${token}`, "content-type": "application/json" },
      body: body === undefined ? undefined : JSON.stringify(body),
    }),
    env,
    ctx,
  );

afterEach(() => {
  rows = [];
  history = [];
  batches.length = 0;
  vi.unstubAllGlobals();
});

describe("dictionary routes", () => {
  const terms = [
    { term: "Akroyd", misheard: ["Acroyd", "Ackroyd"] },
    { term: "Ankar", misheard: [] },
  ];

  it("PUT replaces the dictionary and GET returns it in order", async () => {
    const put = await call("/v1/dictionary", "PUT", "sync-t", { terms });
    expect(put.status).toBe(200);
    expect(await put.json()).toEqual({ count: 2 });
    expect(batches[0][0].sql).toBe("DELETE FROM dictionary");
    expect(batches[0].slice(1).map((s) => s.args[0])).toEqual([0, 1]);
    const get = await call("/v1/dictionary", "GET", "mac-t");
    expect(await get.json()).toEqual({ terms });
  });

  it("only the sync token may PUT", async () => {
    for (const token of ["mac-t", "and-t"]) {
      expect((await call("/v1/dictionary", "PUT", token, { terms })).status).toBe(403);
    }
    expect(batches).toHaveLength(0);
  });

  it("400 for a malformed body", async () => {
    for (const body of [{}, { terms: "x" }, { terms: [{ term: "" }] }, { terms: [{ term: "A", misheard: [1] }] }]) {
      expect((await call("/v1/dictionary", "PUT", "sync-t", body)).status).toBe(400);
    }
  });

  it("401 without a known token", async () => {
    expect((await call("/v1/dictionary", "GET", "nope")).status).toBe(401);
  });
});

describe("GET /v1/history", () => {
  it("returns items newest first with a boolean cleaned, capped at 50", async () => {
    history = [{ id: "2", created_at: "t", client: "mac", raw: "r", text: "t", cleaned: 1 }];
    const res = await call("/v1/history?limit=500", "GET", "mac-t");
    const body = (await res.json()) as { items: { cleaned: boolean }[] };
    expect(body.items[0].cleaned).toBe(true);
  });
});

describe("prompt builders", () => {
  it("whisperPrompt lists correct spellings only, in order, within the budget", () => {
    expect(whisperPrompt([{ term: "Akroyd", misheard: ["Acroyd"] }, { term: "Ankar", misheard: [] }])).toBe("Akroyd, Ankar");
    const many = Array.from({ length: 500 }, (_, i) => ({ term: `Term${i}`, misheard: [] }));
    const p = whisperPrompt(many);
    expect(p.length).toBeLessThanOrEqual(WHISPER_PROMPT_CHARS);
    expect(p.startsWith("Term0, Term1,")).toBe(true);
    expect(p.endsWith(",")).toBe(false);
  });

  it("cleanupDictionary writes one line per term", () => {
    expect(cleanupDictionary([{ term: "Akroyd", misheard: ["Acroyd", "Ackroyd"] }, { term: "Ankar", misheard: [] }])).toBe(
      "Akroyd (mis-heard as: Acroyd, Ackroyd)\nAnkar",
    );
  });
});

describe("dictionary reaches both prompts in /v1/dictate", () => {
  it("sends terms to Whisper and the full list to the cleanup system prompt", async () => {
    rows = [
      { term: "Akroyd", misheard: '["Acroyd"]' },
      { term: "Ankar", misheard: "[]" },
      { term: "Kubernetes", misheard: '["Cooper Nettys"]' },
    ];
    let whisper = "";
    let system = "";
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string, init: RequestInit) => {
        if (url.includes("groq")) {
          whisper = String((init.body as FormData).get("prompt"));
          return Response.json({ segments: [{ text: "hello world", no_speech_prob: 0 }] });
        }
        system = JSON.parse(init.body as string).system;
        return Response.json({ content: [{ type: "text", text: "Hello world." }] });
      }),
    );
    const res = await worker.fetch(
      new Request("https://x/v1/dictate", {
        method: "POST",
        headers: { authorization: "Bearer mac-t", "content-type": "audio/wav" },
        body: new Uint8Array(2000),
      }),
      env,
      ctx,
    );
    expect(res.status).toBe(200);
    expect(whisper).toBe("Akroyd, Ankar, Kubernetes");
    expect(system).toContain("Akroyd (mis-heard as: Acroyd)\nAnkar\nKubernetes (mis-heard as: Cooper Nettys)");
    expect(system).not.toContain("{{DICTIONARY}}");
  });
});
