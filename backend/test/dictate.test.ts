import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import worker, { type Env } from "../src/index";

const calls: string[] = [];
const prepared: { sql: string; args: unknown[] }[] = [];

const env = {
  MODE: "real",
  GROQ_API_KEY: "g",
  ANTHROPIC_API_KEY: "a",
  MAC_TOKEN: "mac-t",
  ANDROID_TOKEN: "and-t",
  SYNC_TOKEN: "sync-t",
  DB: {
    prepare(sql: string) {
      return { bind: (...args: unknown[]) => ({ sql, args }) };
    },
    async batch(stmts: { sql: string; args: unknown[] }[]) {
      prepared.push(...stmts);
      return [];
    },
  },
} as unknown as Env;

const ctx = { waitUntil: (p: Promise<unknown>) => void p } as ExecutionContext;
const flush = () => new Promise((r) => setTimeout(r, 0));

const audio = (n = 2000) => new Uint8Array(n);
const dictate = (init: { token?: string; type?: string; body?: BodyInit; query?: string; headers?: Record<string, string> } = {}) =>
  worker.fetch(
    new Request(`https://x/v1/dictate${init.query ?? ""}`, {
      method: "POST",
      headers: {
        authorization: `Bearer ${init.token ?? "mac-t"}`,
        "content-type": init.type ?? "audio/wav",
        ...init.headers,
      },
      body: init.body ?? audio(),
    }),
    env,
    ctx,
  );

type Seg = { text: string; no_speech_prob: number };
function stub(opts: { segments?: Seg[]; groq?: Response; cleanup?: string | Response | Error }) {
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string) => {
      calls.push(url);
      if (url.includes("groq")) {
        return opts.groq ?? Response.json({ segments: opts.segments ?? [{ text: " hello world ", no_speech_prob: 0.01 }] });
      }
      const c = opts.cleanup ?? "Hello world.";
      if (c instanceof Error) throw c;
      if (c instanceof Response) return c;
      return Response.json({ content: [{ type: "text", text: c }] });
    }),
  );
}

beforeEach(() => {
  calls.length = 0;
  prepared.length = 0;
});
afterEach(() => vi.unstubAllGlobals());

describe("auth and request checks", () => {
  it("401 for a missing, wrong or mock token", async () => {
    stub({});
    expect((await dictate({ token: "nope" })).status).toBe(401);
    expect((await dictate({ token: "mock-token" })).status).toBe(401);
    const r = await worker.fetch(new Request("https://x/v1/dictate", { method: "POST" }), env, ctx);
    expect(r.status).toBe(401);
    expect(await r.json()).toMatchObject({ error: "unauthorized" });
  });
  it("accepts all three client tokens", async () => {
    stub({});
    for (const token of ["mac-t", "and-t", "sync-t"]) expect((await dictate({ token })).status).toBe(200);
  });
  it("415 for a bad content type", async () => {
    stub({});
    const r = await dictate({ type: "text/plain" });
    expect(r.status).toBe(415);
    expect(await r.json()).toMatchObject({ error: "bad_audio_type" });
  });
  it("413 when over 25 MB, without calling Groq", async () => {
    stub({});
    const r = await dictate({ body: audio(25 * 1024 * 1024 + 1) });
    expect(r.status).toBe(413);
    expect(await r.json()).toMatchObject({ error: "too_large" });
    expect(calls).toHaveLength(0);
  });
  it("returns empty for a body under 1 KB without calling Groq", async () => {
    stub({});
    const r = await dictate({ body: audio(100) });
    expect(r.status).toBe(200);
    expect(await r.json()).toMatchObject({ empty: true, text: "" });
    expect(calls).toHaveLength(0);
  });
  it("health needs no auth and reports real", async () => {
    const r = await worker.fetch(new Request("https://x/v1/health"), env, ctx);
    expect(await r.json()).toEqual({ ok: true, mode: "real" });
  });
});

describe("ASR", () => {
  it("502 asr_failed on a Groq error", async () => {
    stub({ groq: new Response("boom", { status: 500 }) });
    const r = await dictate();
    expect(r.status).toBe(502);
    expect(await r.json()).toMatchObject({ error: "asr_failed" });
  });
  it("502 with the retry time on a Groq 429", async () => {
    stub({ groq: new Response("", { status: 429, headers: { "retry-after": "42" } }) });
    const r = await dictate();
    expect(r.status).toBe(502);
    const body = (await r.json()) as { error: string; message: string };
    expect(body.error).toBe("asr_failed");
    expect(body.message).toMatch(/rate limit/);
    expect(body.message).toContain("42");
  });
  it("502 when the Groq request throws", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => { throw new Error("net"); }));
    expect((await dictate()).status).toBe(502);
  });
});

describe("silence filter", () => {
  it("drops segments above the threshold and joins the rest", async () => {
    stub({
      segments: [
        { text: " one ", no_speech_prob: 0.1 },
        { text: " thank you ", no_speech_prob: 0.95 },
        { text: " two ", no_speech_prob: 0.6 },
      ],
      cleanup: new Error("skip"),
    });
    const body = (await (await dictate()).json()) as { raw: string };
    expect(body.raw).toBe("one two");
  });
  it("returns empty when nothing is left, and skips cleanup", async () => {
    stub({ segments: [{ text: "thanks", no_speech_prob: 0.9 }] });
    const body = await (await dictate()).json();
    expect(body).toMatchObject({ text: "", raw: "", empty: true });
    expect(calls.some((u) => u.includes("anthropic"))).toBe(false);
  });
});

describe("cleanup", () => {
  it("uses the cleaned text and stores history", async () => {
    stub({});
    const body = await (await dictate({ headers: { "x-client": "android" } })).json();
    expect(body).toMatchObject({ text: "Hello world.", raw: "hello world", cleaned: true, empty: false });
    await flush();
    expect(prepared[0].sql).toMatch(/INSERT INTO history/);
    expect(prepared[0].args).toContain("android");
    expect(prepared[1].sql).toMatch(/DELETE FROM history/);
    expect(prepared[1].args).toEqual([50]);
  });
  it("sends the transcript in tags and the prompt without the marker", async () => {
    stub({});
    await dictate();
    const spy = vi.mocked(fetch as unknown as (u: string, i: RequestInit) => Promise<Response>);
    const init = spy.mock.calls.find((c) => String(c[0]).includes("anthropic"))![1];
    const sent = JSON.parse(init.body as string);
    expect(sent.model).toBe("claude-haiku-5-5");
    expect(sent.messages[0].content).toBe("<transcript>hello world</transcript>");
    expect(sent.system).not.toContain("{{DICTIONARY}}");
  });
  it("?cleanup=0 skips cleanup", async () => {
    stub({});
    const body = await (await dictate({ query: "?cleanup=0" })).json();
    expect(body).toMatchObject({ text: "hello world", cleaned: false });
    expect(calls.some((u) => u.includes("anthropic"))).toBe(false);
  });
  const guards: [string, Parameters<typeof stub>[0]["cleanup"]][] = [
    ["API error", new Response("x", { status: 500 })],
    ["network error", new Error("net")],
    ["too long", "Hello world. ".repeat(10)],
  ];
  for (const [name, cleanup] of guards) {
    it(`falls back to raw on ${name}`, async () => {
      stub({ cleanup });
      const r = await dictate();
      expect(r.status).toBe(200);
      expect(await r.json()).toMatchObject({ text: "hello world", cleaned: false });
    });
  }
  it("falls back to raw when a long transcript shrinks below half", async () => {
    const raw = "word ".repeat(30).trim();
    stub({ segments: [{ text: raw, no_speech_prob: 0 }], cleanup: "Word." });
    expect(await (await dictate()).json()).toMatchObject({ text: raw, cleaned: false });
  });
  it("accepts a big shrink on a short transcript", async () => {
    const raw = "meet at tuesday no wait wednesday at um three";
    stub({ segments: [{ text: raw, no_speech_prob: 0 }], cleanup: "Meet Wednesday at 3." });
    expect(await (await dictate()).json()).toMatchObject({ text: "Meet Wednesday at 3.", cleaned: true });
  });
  it("falls back to raw on empty cleanup output", async () => {
    stub({ cleanup: "" });
    expect(await (await dictate()).json()).toMatchObject({ text: "hello world", cleaned: false });
  });
  it("falls back to raw on timeout", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string, init: RequestInit) => {
        if (url.includes("groq")) return Response.json({ segments: [{ text: "hello world", no_speech_prob: 0 }] });
        init.signal!.throwIfAborted();
        throw new DOMException("timed out", "TimeoutError");
      }),
    );
    expect(await (await dictate()).json()).toMatchObject({ cleaned: false, text: "hello world" });
  });
});
