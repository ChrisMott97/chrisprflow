import { ACCEPTED_TYPES, MAX_BYTES, dictateReal } from "./dictate";
import { err, json, ulid } from "./http";

export interface Env {
  MODE: "mock" | "real";
  DB: D1Database;
  GROQ_API_KEY: string;
  ANTHROPIC_API_KEY: string;
  MAC_TOKEN: string;
  ANDROID_TOKEN: string;
  SYNC_TOKEN: string;
}

const MOCK_TOKEN = "mock-token";
const MOCK_MIN_BYTES = 1024;

// Mock-only in-memory dictionary (resets with the isolate).
let mockDictionary: { terms: { term: string; misheard: string[] }[] } = { terms: [] };

async function sameToken(a: string, b: string | undefined): Promise<boolean> {
  if (!a || !b) return false;
  const enc = new TextEncoder();
  const [da, db] = await Promise.all([
    crypto.subtle.digest("SHA-256", enc.encode(a)),
    crypto.subtle.digest("SHA-256", enc.encode(b)),
  ]);
  // Digests are fixed length, so this loop takes the same time for any input.
  const x = new Uint8Array(da);
  const y = new Uint8Array(db);
  let diff = 0;
  for (let i = 0; i < x.length; i++) diff |= x[i] ^ y[i];
  return diff === 0;
}

/** Returns who is calling ("mac", "android", "sync", "mock"), or null. */
async function authorise(req: Request, env: Env): Promise<string | null> {
  const header = req.headers.get("authorization") ?? "";
  const token = header.startsWith("Bearer ") ? header.slice(7) : "";
  if (!token) return null;
  if (env.MODE === "mock") return token === MOCK_TOKEN ? "mock" : null;
  const checks: [string, string | undefined][] = [
    ["mac", env.MAC_TOKEN],
    ["android", env.ANDROID_TOKEN],
    ["sync", env.SYNC_TOKEN],
  ];
  let caller: string | null = null;
  for (const [name, secret] of checks) {
    if (await sameToken(token, secret)) caller = name;
  }
  return caller;
}

async function dictateMock(req: Request, url: URL): Promise<Response> {
  const type = (req.headers.get("content-type") ?? "").split(";")[0].trim();
  if (!ACCEPTED_TYPES.includes(type)) {
    return err(415, "bad_audio_type", `Unsupported content type: ${type || "none"}`);
  }
  const body = await req.arrayBuffer();
  if (body.byteLength > MAX_BYTES) return err(413, "too_large", "Audio over 25 MB");

  const mock = url.searchParams.get("mock");
  if (mock === "fail") return err(502, "asr_failed", "Mock ASR failure");

  if (body.byteLength < MOCK_MIN_BYTES) {
    return json({
      id: ulid(), text: "", raw: "", cleaned: false, empty: true,
      ms: { asr: 0, cleanup: 0 },
    });
  }

  const text = "This is a mock dictation.";
  const cleaned = mock !== "raw" && url.searchParams.get("cleanup") !== "0";
  return json({
    id: ulid(),
    text,
    raw: cleaned ? "this is a mock dictation um" : text,
    cleaned,
    empty: false,
    ms: { asr: 100, cleanup: cleaned ? 100 : 0 },
  });
}

export default {
  async fetch(req: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
    const url = new URL(req.url);
    const { pathname: path } = url;
    const method = req.method;

    if (path === "/v1/health" && method === "GET") {
      return json({ ok: true, mode: env.MODE });
    }

    const caller = await authorise(req, env);
    if (!caller) {
      return err(401, "unauthorized", "Missing or unknown token");
    }

    if (path === "/v1/dictate" && method === "POST") {
      if (env.MODE === "mock") return dictateMock(req, url);
      return dictateReal(req, url, env, ctx, caller);
    }

    if (env.MODE === "mock") {
      if (path === "/v1/history" && method === "GET") {
        return json({
          items: [{
            id: ulid(),
            created_at: new Date().toISOString().replace(/\.\d+Z$/, "Z"),
            client: "mac",
            raw: "this is a mock dictation um",
            text: "This is a mock dictation.",
            cleaned: true,
          }],
        });
      }
      if (path === "/v1/dictionary" && method === "GET") return json(mockDictionary);
      if (path === "/v1/dictionary" && method === "PUT") {
        const data = (await req.json().catch(() => null)) as typeof mockDictionary | null;
        if (!data || !Array.isArray(data.terms)) {
          return err(400, "bad_request", "Body must be {\"terms\": [...]}");
        }
        mockDictionary = { terms: data.terms };
        return json({ count: data.terms.length });
      }
    }

    return err(404, "not_found", "No such route");
  },
};
