---
type: topic
subject: Dictation tool build plan - workstream A, the backend
author: claude
reviewed:
sources:
  - "[[topics/personal-voice-dictation-tool]]"
  - "conversation: 'dictation tool implementation plan', Oct 2026"
last_updated: 2026-10-10
---

# Workstream A: backend

**What this is.** The plan for the Cloudflare Worker that turns audio into cleaned text. Two
sessions, A1 then A2.
**Read first.** `dictation-plan-contract.md`. Nothing else is needed.

- A1 makes `POST /v1/dictate` real and deploys it.
- A2 adds the dictionary, the history route and the final prompt.
- The folder is `backend/`. The stack is TypeScript, Wrangler, D1 and plain `fetch`, with no
  framework.

## Session A1: the real pipeline

**Before this session, Chris does**

- Create a Groq API key and an Anthropic API key. Set a spend limit on both accounts.
- Run `npx wrangler secret put` for `GROQ_API_KEY`, `ANTHROPIC_API_KEY`, `MAC_TOKEN`,
  `ANDROID_TOKEN` and `SYNC_TOKEN`, against the real environment and not `mock`. Each token is
  its own random string from `openssl rand -hex 32`. Do this by hand so no production secret
  passes through an agent.
- For local runs, fill in `backend/.dev.vars` with **different** values: made-up client tokens
  such as `dev-mac-token`, and a second Groq key and Anthropic key created only for development.
  An agent can end up seeing this file's contents, so it never holds a production value. The
  file is ignored by git.

**Build**

1. A second Wrangler environment for the real backend. The `mock` environment from S0 stays
   deployed and unchanged.
2. Auth: compare the bearer token against the three token secrets.
3. Size and type checks, returning the contract's `413` and `415`.
4. **ASR.** Send the audio to Groq's transcription endpoint as multipart, with model
   `whisper-large-v3-turbo`, `language=en`, `temperature=0`, `response_format=verbose_json`.
   **Keep the provider swappable.** Put the ASR call behind one function that takes the audio and
   the prompt and returns segments, each with text and a no-speech score. Nothing else in the
   Worker may know it is Groq. Groq's paid Developer plan was closed to new sign-ups on
   2026-10-10, so the free tier's limits are the limits, and the provider may have to change.
   **Rate limit.** When Groq answers `429`, return the contract's `502 asr_failed` with a message
   that says it is a rate limit and includes Groq's retry time. The client then keeps the audio.
   Do not build a second provider in this session.
5. **Silence filter.** Drop every segment whose `no_speech_prob` is above a threshold. Start at
   0.6 and keep it as one named constant. Join what is left. If nothing is left, return
   `empty: true`.
6. **Cleanup.** Call the Anthropic Messages API with model `claude-haiku-5-5`. The system prompt
   is the file `prompt/cleanup.md`, imported at build time as a text module. Workstream B owns
   that file, so do not edit it. Until B1 lands it holds a placeholder. The transcript goes in the
   user message inside `<transcript>` tags.
7. **Cleanup guards.** Treat cleanup as failed, and return the raw transcript with
   `cleaned: false`, when any of these is true: the call errors, it takes longer than 8 seconds,
   or the output is shorter than half or longer than one and a half times the raw transcript. The
   length check catches a model that answered the dictation instead of cleaning it.
8. **History.** One D1 table: `id`, `created_at`, `client`, `raw`, `text`, `cleaned`, `asr_ms`,
   `cleanup_ms`. After each insert, delete all but the newest 50 rows. Never store audio.
9. Deploy. Write the real URL into the Status section below, for Chris to copy into the contract.

**Tests**

- Unit tests with Vitest and a mocked `fetch`: each error code, the silence filter, each cleanup
  guard.
- `docs/smoke.sh <real URL>` with `docs/sample.wav` returns a sensible transcript.

**Done when** the smoke test passes against the deployed real URL and the unit tests pass.

**Watch for.** The Workers free tier allows 10 milliseconds of CPU time per request. Time waiting
on `fetch` does not count. Stream the audio body through to Groq where possible, and do not parse
or copy it. Report the measured CPU time in Status.

## If Groq's limits are too low

Not a planned session. Run it only if Chris keeps hitting the rate limit. Add a second ASR
provider behind the same function and choose between them with one environment variable. Check
each candidate's current price, limits and whether it returns a per-segment no-speech score:

| Candidate | Why consider it |
|---|---|
| Cloudflare Workers AI, Whisper large-v3-turbo | Same Cloudflare account, no new key. Check its free allowance and the CPU cost of passing audio to it |
| Deepgram | Free starting credit, and real keyword boosting for the dictionary |
| OpenAI transcription API | Pay as you go with no plan to join |

## Session A2: dictionary, history route, final prompt

**Waits on:** A1 deployed, and B1 finished.

**Build**

1. A D1 table for the dictionary: `term`, `misheard` (JSON array), `rank`.
2. `PUT /v1/dictionary` and `GET /v1/dictionary`, as in the contract. Only `SYNC_TOKEN` may `PUT`.
3. **Whisper prompt.** Build it from the terms in rank order, correct spellings only, and stop
   before 224 tokens. Use a character budget of about 800 as the estimate, as one named constant.
4. **Cleanup prompt.** Replace the `{{DICTIONARY}}` placeholder in `prompt/cleanup.md` with the
   full list, one line per term: the correct spelling, then its mis-hearings.
5. `GET /v1/history`.
6. Confirm the build now picks up B1's real `prompt/cleanup.md`.

**Done when** a `PUT` of three test terms changes the transcript of a recording that contains one
of them, and the unit tests pass.

## Status

Not started. Each session writes here: what was built, what was not, the deployed URL, measured
CPU time and latency, and anything the next session must know.
