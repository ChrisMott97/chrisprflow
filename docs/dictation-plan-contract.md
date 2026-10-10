---
type: topic
subject: Dictation tool build plan - the API contract between the clients and the backend
author: claude
reviewed:
sources:
  - "[[topics/personal-voice-dictation-tool]]"
  - "conversation: 'dictation tool implementation plan', Oct 2026"
last_updated: 2026-10-10
---

# API contract

**What this is.** The exact requests and responses of the dictation backend. Every agent session
reads this file. It is frozen after session S0.
**Read first.** Nothing. This file stands alone.

**Rule for agents: do not change this file.** If the contract is wrong or incomplete, stop and
tell Chris. A silent change breaks the other workstreams, which are being built in parallel.

## Base URLs

| Name | URL | Behaviour |
|---|---|---|
| Mock | https://dictation-mock.chrisamott97.workers.dev | Same routes, fixed answers, no Groq or Anthropic calls |
| Real | https://dictation.chrisamott97.workers.dev | The full pipeline, once A1 is done. Before that, `POST /v1/dictate` returns `501` |

## Auth

Every route except `GET /v1/health` needs `Authorization: Bearer <token>`. There is one token per
caller: Mac, Android, sync script. A missing or unknown token returns `401`.

## POST /v1/dictate

The only route the clients need.

**Request**

- Body: the audio file as raw bytes. Not multipart.
- `Content-Type`: `audio/wav` or `audio/flac`. `audio/mp4` and `audio/ogg` are also accepted.
- Audio: 16 kHz, mono. WAV is 16-bit PCM. Already trimmed of long silences by the client.
- Limits: 25 MB and 10 minutes.
- Optional header `X-Client`: `mac` or `android`. Stored in history.
- Optional query `?cleanup=0`: skip the cleanup step and return the raw transcript. For debugging.

**Response `200`**

```json
{
  "id": "01JABCDEF...",
  "text": "The text to insert.",
  "raw": "the text to insert um",
  "cleaned": true,
  "empty": false,
  "ms": { "asr": 420, "cleanup": 610 }
}
```

| Field | Meaning |
|---|---|
| `text` | What the client inserts. Equal to `raw` when cleanup failed or was skipped |
| `raw` | The Whisper transcript after the silence filter |
| `cleaned` | `false` when cleanup failed, timed out or was skipped |
| `empty` | `true` when no speech was found. `text` is then `""` and the client inserts nothing |
| `ms` | Time spent in each step, in milliseconds |

**Errors.** Every error body is `{"error": "<code>", "message": "<text>"}`.

| Status | Code | Client does |
|---|---|---|
| 401 | `unauthorized` | Show an error. Do not retry |
| 413 | `too_large` | Show an error. Do not retry |
| 415 | `bad_audio_type` | Show an error. Do not retry |
| 502 | `asr_failed` | Keep the audio and retry later |
| 5xx, timeout, no network | | Keep the audio and retry later |

A cleanup failure is **not** an error. It returns `200` with `cleaned: false`.

## GET /v1/history?limit=50

Returns the most recent dictations, newest first. At most 50 are kept.

```json
{ "items": [ { "id": "...", "created_at": "2026-10-10T09:12:44Z", "client": "mac",
               "raw": "...", "text": "...", "cleaned": true } ] }
```

## PUT /v1/dictionary

Replaces the whole dictionary. Used by the sync script only.

```json
{ "terms": [ { "term": "Akroyd", "misheard": ["Acroyd", "Ackroyd"] },
             { "term": "Ankar", "misheard": [] } ] }
```

Returns `200` with `{"count": 2}`. The order of `terms` is the priority order: earlier terms go
into the Whisper prompt first.

## GET /v1/dictionary

Returns the same shape as the `PUT` body.

## GET /v1/health

No auth. Returns `200` with `{"ok": true, "mode": "mock"}` or `"mode": "real"`.

## Mock behaviour

| Request | Mock answer |
|---|---|
| `POST /v1/dictate`, any audio over 1 KB | `200`, `text` is `"This is a mock dictation."`, `cleaned: true` |
| `POST /v1/dictate`, body under 1 KB | `200`, `empty: true` |
| `POST /v1/dictate?mock=fail` | `502 asr_failed` |
| `POST /v1/dictate?mock=raw` | `200`, `cleaned: false` |
| Bad token | `401` |

The mock token is `mock-token`. It is not a secret.
