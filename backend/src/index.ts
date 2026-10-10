interface Env {
  MODE: "mock" | "real";
}

const MOCK_TOKEN = "mock-token";
const MOCK_MIN_BYTES = 1024;
const ACCEPTED_TYPES = ["audio/wav", "audio/flac", "audio/mp4", "audio/ogg"];
const MAX_BYTES = 25 * 1024 * 1024;

const json = (body: unknown, status = 200): Response =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" },
  });

const err = (status: number, error: string, message: string): Response =>
  json({ error, message }, status);

// Mock-only in-memory dictionary (resets with the isolate).
let mockDictionary: { terms: { term: string; misheard: string[] }[] } = { terms: [] };

function ulid(): string {
  const chars = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
  let t = Date.now();
  let time = "";
  for (let i = 0; i < 10; i++) {
    time = chars[t % 32] + time;
    t = Math.floor(t / 32);
  }
  const rand = crypto.getRandomValues(new Uint8Array(16));
  return time + Array.from(rand, (b) => chars[b % 32]).join("");
}

function authorised(req: Request, env: Env): boolean {
  const header = req.headers.get("authorization") ?? "";
  const token = header.startsWith("Bearer ") ? header.slice(7) : "";
  if (!token) return false;
  // S0: only the mock token is known. A1 adds the real per-client tokens.
  return env.MODE === "mock" && token === MOCK_TOKEN;
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
  async fetch(req: Request, env: Env): Promise<Response> {
    const url = new URL(req.url);
    const { pathname: path } = url;
    const method = req.method;

    if (path === "/v1/health" && method === "GET") {
      return json({ ok: true, mode: env.MODE });
    }

    if (!authorised(req, env)) {
      return err(401, "unauthorized", "Missing or unknown token");
    }

    if (path === "/v1/dictate" && method === "POST") {
      if (env.MODE === "mock") return dictateMock(req, url);
      return err(501, "not_implemented", "Pipeline not built yet");
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
