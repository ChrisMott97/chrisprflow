import { AsrError, transcribe } from "./asr";
import { cleanup } from "./cleanup";
import { saveHistory } from "./history";
import { err, json, ulid } from "./http";
import type { Env } from "./index";

export const NO_SPEECH_THRESHOLD = 0.6;
export const MAX_BYTES = 25 * 1024 * 1024;
export const ACCEPTED_TYPES = ["audio/wav", "audio/flac", "audio/mp4", "audio/ogg"];

export async function dictateReal(
  req: Request,
  url: URL,
  env: Env,
  ctx: Pick<ExecutionContext, "waitUntil">,
  caller: string,
): Promise<Response> {
  const type = (req.headers.get("content-type") ?? "").split(";")[0].trim();
  if (!ACCEPTED_TYPES.includes(type)) {
    return err(415, "bad_audio_type", `Unsupported content type: ${type || "none"}`);
  }
  const declared = Number(req.headers.get("content-length") ?? 0);
  if (declared > MAX_BYTES) return err(413, "too_large", "Audio over 25 MB");
  const audio = await req.arrayBuffer();
  if (audio.byteLength > MAX_BYTES) return err(413, "too_large", "Audio over 25 MB");

  const t0 = Date.now();
  let segments;
  try {
    segments = await transcribe(env, audio, type, "");
  } catch (e) {
    if (e instanceof AsrError) return err(502, "asr_failed", e.message);
    throw e;
  }
  const asrMs = Date.now() - t0;

  const raw = segments
    .filter((s) => s.noSpeechProb <= NO_SPEECH_THRESHOLD)
    .map((s) => s.text.trim())
    .filter(Boolean)
    .join(" ");
  const id = ulid();
  if (!raw) {
    return json({ id, text: "", raw: "", cleaned: false, empty: true, ms: { asr: asrMs, cleanup: 0 } });
  }

  let text = raw;
  let cleaned = false;
  let cleanupMs = 0;
  if (url.searchParams.get("cleanup") !== "0") {
    const t1 = Date.now();
    const result = await cleanup(env, raw);
    cleanupMs = Date.now() - t1;
    if (result !== null) {
      text = result;
      cleaned = true;
    }
  }

  const xClient = req.headers.get("x-client");
  const client = xClient === "mac" || xClient === "android" ? xClient : caller;
  ctx.waitUntil(
    saveHistory(env.DB, { id, client, raw, text, cleaned, asrMs, cleanupMs }).catch(() => {}),
  );

  return json({ id, text, raw, cleaned, empty: false, ms: { asr: asrMs, cleanup: cleanupMs } });
}
