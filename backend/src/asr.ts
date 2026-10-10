// The only file that knows the ASR provider is Groq.

export interface Segment {
  text: string;
  noSpeechProb: number;
}

export class AsrError extends Error {}

interface AsrEnv {
  GROQ_API_KEY: string;
}

const GROQ_URL = "https://api.groq.com/openai/v1/audio/transcriptions";
const EXTENSIONS: Record<string, string> = {
  "audio/wav": "wav",
  "audio/flac": "flac",
  "audio/mp4": "m4a",
  "audio/ogg": "ogg",
};

export async function transcribe(
  env: AsrEnv,
  audio: ArrayBuffer,
  contentType: string,
  prompt: string,
): Promise<Segment[]> {
  const form = new FormData();
  form.set("file", new Blob([audio], { type: contentType }), `audio.${EXTENSIONS[contentType] ?? "wav"}`);
  form.set("model", "whisper-large-v3-turbo");
  form.set("language", "en");
  form.set("temperature", "0");
  form.set("response_format", "verbose_json");
  if (prompt) form.set("prompt", prompt);

  let res: Response;
  try {
    res = await fetch(GROQ_URL, {
      method: "POST",
      headers: { authorization: `Bearer ${env.GROQ_API_KEY}` },
      body: form,
    });
  } catch (e) {
    throw new AsrError(`ASR request failed: ${e instanceof Error ? e.message : String(e)}`);
  }

  if (res.status === 429) {
    const retry = res.headers.get("retry-after");
    throw new AsrError(
      `ASR rate limit reached${retry ? `, retry after ${retry} seconds` : ", retry later"}`,
    );
  }
  if (!res.ok) throw new AsrError(`ASR returned status ${res.status}`);

  const data = (await res.json().catch(() => null)) as
    | { segments?: { text: string; no_speech_prob?: number }[] }
    | null;
  if (!data || !Array.isArray(data.segments)) throw new AsrError("ASR returned an unreadable answer");
  return data.segments.map((s) => ({ text: s.text, noSpeechProb: s.no_speech_prob ?? 0 }));
}
