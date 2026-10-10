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
- Set the five secrets on the real Worker, using the commands in `dictation-plan-s0-setup.md`
  under "After this session". Do this by hand so no production secret passes through an agent.
- For local runs, fill in `backend/.dev.vars` with **different** values: made-up client tokens
  such as `dev-mac-token`, and a second Groq key and Anthropic key created only for development.
  An agent can end up seeing this file's contents, so it never holds a production value. The
  file is ignored by git.

**Build**

1. S0 already deployed two Workers from this codebase: `dictation` (the top level, `MODE` is
   `real`) and `dictation-mock` (the `mock` environment). Build the pipeline behind `MODE` being
   `real`. Leave the mock behaviour unchanged and keep it deployed.
   **Create the D1 database** with `npx wrangler d1 create`, add its binding to the top level of
   the Wrangler config, and keep the schema as migration files applied with
   `npx wrangler d1 migrations apply`. Run `npx wrangler secret list` first to confirm the five
   secrets exist. If any is missing, stop and tell Chris.
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
   that file, so do not edit it. Until B1 lands it holds the placeholder S0 wrote. The file
   contains the marker `{{DICTIONARY}}`. In A1 there is no dictionary yet, so replace the marker
   with an empty string. The transcript goes in the user message inside `<transcript>` tags.
7. **Cleanup guards.** Treat cleanup as failed, and return the raw transcript with
   `cleaned: false`, when any of these is true: the call errors, it takes longer than 8 seconds,
   or the output is shorter than half or longer than one and a half times the raw transcript. The
   length check catches a model that answered the dictation instead of cleaning it.
8. **History.** One D1 table: `id`, `created_at`, `client`, `raw`, `text`, `cleaned`, `asr_ms`,
   `cleanup_ms`. After each insert, delete all but the newest 50 rows. Never store audio.
9. Deploy with `npx wrangler deploy`. The real URL does not change, and S0 already wrote it into
   the contract.

**Tests**

- Unit tests with Vitest and a mocked `fetch`: each error code, the silence filter, each cleanup
  guard.
- End to end on the local machine: run `npx wrangler dev`, which loads the development keys
  from `.dev.vars`, and run `docs/smoke.sh` against it with `DICTATION_TOKEN` set to the made-up
  development token. `docs/sample.wav` must return a sensible transcript.
- Do not test against the deployed real URL. That needs a production token, which an agent never
  holds. Chris runs that one check himself after the deploy:
  `DICTATION_TOKEN=<his Mac token> docs/smoke.sh <real URL>`.

**Done when** the unit tests pass, the local end to end test passes, and the Worker is deployed.
Write in Status that Chris still has to run the smoke test against the real URL.

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

**Done when** the unit tests pass, and on a local `npx wrangler dev` a `PUT` of three test terms
shows up in both prompts the Worker sends. Then apply the migration to the remote database and
deploy. As in A1, do not test against the deployed real URL.

## Status

**A1 built 2026-10-10. Deploy pending: Chris runs it (the auto-mode classifier blocks agent deploys).**
- Built: per-client auth (`MAC_TOKEN`, `ANDROID_TOKEN`, `SYNC_TOKEN`; `mock-token` rejected in real mode),
  413/415 checks, ASR behind `src/asr.ts` (Groq `whisper-large-v3-turbo`; 429 becomes `502 asr_failed`
  with the retry time), silence filter (`NO_SPEECH_THRESHOLD = 0.6`), cleanup with `claude-haiku-5-5`
  and the three guards (error, 8 s timeout, length 0.5x to 1.5x), D1 `history` capped at 50 rows.
  Mock behaviour unchanged.
- D1 database `dictation` created; id is in `backend/wrangler.toml`. Migration `0001_history.sql`
  applied locally and to the remote database.
- Tests: 18 Vitest tests pass (`npm test`); `tsc` clean. Local `wrangler dev` end to end with
  `docs/sample.wav`: 200, "This is a short test of the dictation tool.", asr 197 ms, cleanup 1309 ms
  (cold), `?cleanup=0` 134 ms total, 415 and history rows correct.
- Not done: `wrangler deploy`. Chris runs it from `backend/`, then
  `DICTATION_TOKEN=<Mac token> docs/smoke.sh <real URL>` (only the health row is meaningful there;
  its dictate rows assert mock text, so also try one `curl` of `docs/sample.wav`).
- CPU time: not measurable in local dev. Read it from the Cloudflare dashboard (Workers, dictation,
  Metrics) after a few real requests; the 10 ms free-tier limit is unverified. Audio is read with
  `arrayBuffer()` and passed to Groq via `FormData` without parsing (not streamed). If CPU is too high,
  stream a hand-built multipart body instead.
- Notes for later: the 10-minute limit is not checked, only the 25 MB limit (a 10 min 16 kHz mono WAV is
  about 19 MB). A truncated or invalid audio file gets `502 asr_failed` (Groq returns 400).
  `docs/smoke.sh` has no real-pipeline mode; it is outside this folder so I left it.
  A2 should replace the `""` prompt in `src/dictate.ts` and the `""` dictionary in `src/cleanup.ts`.
