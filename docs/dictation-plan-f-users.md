---
type: topic
subject: Dictation tool build plan - workstream F, usage tracking, users and limits in the backend
author: claude
reviewed:
sources:
  - "conversation: 'dictation phase 2 planning', Oct 2026"
last_updated: 2026-10-10
---

# Workstream F: usage, users and limits

**What this is.** The plan for the backend changes that let more than one person use one
deployment: a usage log, per-user tokens and data, and daily limits. Three sessions, F1 to F3.
**Read first.** `dictation-plan-contract.md`. For F1 and F3, also `dictation-plan-costs.md`.

- F1 records what each dictation used. It changes no route and can run straight away.
- F2 gives each person their own tokens, dictionary and history.
- F3 adds daily limits and a usage report per person.
- The folder is `backend/`. Follow `backend/CLAUDE.md`.

**Why this exists.** Two things in the backend today stop Chris giving anyone else a token:

| Problem | Where |
|---|---|
| `GET /v1/history` returns the last 50 dictations from every caller | `backend/src/history.ts` |
| One dictionary is fed into every caller's transcription and cleanup | `backend/src/dictate.ts` |

## Session F1: usage log

**Waits on:** nothing. No contract change.

**Build**

1. A D1 table `usage`, one row per `POST /v1/dictate` that reached ASR: `id`, `created_at`,
   `caller`, `client`, `audio_seconds`, `billed_seconds`, `input_tokens`, `output_tokens`,
   `cleaned`. **Never store text or audio in it.**
2. `audio_seconds` comes from the `duration` field of Groq's `verbose_json` answer. Return it from
   `transcribe` in `src/asr.ts` alongside the segments. Nothing outside that file may know it is
   Groq.
3. `billed_seconds` is `audio_seconds` rounded up, with a minimum of 10. Keep the minimum as one
   named constant.
4. `input_tokens` and `output_tokens` come from the `usage` object of the Anthropic answer. Both
   are 0 when cleanup was skipped or failed before an answer.
5. Write the row in the same `waitUntil` as the history row. A failed write must not fail the
   dictation.
6. Do not delete old rows. A row is small and holds nothing private.
7. A script `backend/scripts/usage.sh` that prints a table per caller per day: dictations, audio
   seconds, billed seconds, tokens and estimated cost. It runs one SQL query with
   `npx wrangler d1 execute`. Take the prices from `dictation-plan-costs.md` and keep them as
   named values at the top of the script. `--remote` reads the real database.

**Also measure, and report in Status**

- Run ten varied local dictations and report the average input and output tokens.
- Then try the cleanup call with thinking switched off, and separately with `effort` set to `low`.
  Check each against the test set with `prompt/run.ts`. Do not edit anything in `prompt/`. If the
  runner cannot take the setting, say so and stop there.
- **Do not change the deployed setting.** Report the token counts, the latency and any test case
  that got worse. Chris decides.

**Tests.** Unit tests for the billed-seconds rule, for a row being written with the right numbers,
and for a failed usage write leaving the response unchanged.

**Done when** the tests pass, a local dictation writes a row, and the script prints it. Chris
applies the migration to the remote database and deploys.

## Session F2: users and tokens

**Waits on:** F1. Chris has applied changes 1 and 2 from `dictation-plan-contract-changes.md` to
the contract.

**Build**

1. Table `users`: `id`, `name`, `created_at`, `daily_seconds_cap` (null means no cap),
   `keep_history` (default 1), `disabled` (default 0).
2. Table `tokens`: `hash` (SHA-256 of the token, hex, primary key), `user_id`, `label`,
   `created_at`, `revoked_at`. **Never store a token itself.**
3. **Auth.** Hash the bearer token and look it up. A revoked token or a disabled user gets `401`.
4. **Keep the three token secrets working.** `MAC_TOKEN`, `ANDROID_TOKEN` and `SYNC_TOKEN` map to
   a fixed user with id `owner`, created by the migration. Chris's installed clients keep working
   with no change.
5. Add `user_id` to `history`, `dictionary` and `usage`. The migration gives every existing row to
   `owner`.
6. Scope every read and write to the caller's user: the dictionary load in `dictate.ts`, both
   dictionary routes, the history route, and the trim to 50 rows, which becomes 50 per user.
7. When the user's `keep_history` is 0, write no history row.
8. `PUT /v1/dictionary` keeps its current rule in this session: only `SYNC_TOKEN` may call it. G1
   opens it up.
9. A script `backend/scripts/add-user.sh <name>`. It creates the user, makes a random token of at
   least 32 bytes, stores the hash and prints the token once. A second form adds a token to an
   existing user with a label. A third revokes a token by label. **Chris runs it against the
   remote database. An agent runs it only with `--local`.**

**Tests**

- A token for user A never returns user B's history or dictionary. This is the test that matters.
- User A's dictionary terms do not appear in the Groq prompt or the cleanup prompt of user B's
  dictation.
- A revoked token gets `401`. A disabled user gets `401`.
- The three secrets still work and act as `owner`.
- History keeps 50 rows per user.

**Done when** the tests pass and a local run with two users made by the script shows separate
history and dictionaries. Chris applies the migration to the remote database and deploys.

**Watch for.** Auth now costs one D1 read on every request. Report the added time in Status.

## Session F3: limits and the usage report

**Waits on:** F2. Chris has applied change 3 from `dictation-plan-contract-changes.md` to the
contract.

**Build**

1. **Per-user daily cap.** Before the ASR call, add up the user's `billed_seconds` since
   00:00 UTC. If it is at or over `daily_seconds_cap`, return `429 quota_exceeded`. A null cap
   means no limit.
2. **Shared guard.** Groq's daily audio limit is shared by everyone. Keep one named constant,
   `SHARED_DAILY_SECONDS`, starting at 20,000. When the day's total across all users is over it,
   refuse every user except `owner` with the same `429`. This keeps Chris's own dictation working
   on a day when friends use a lot.
3. `add-user.sh` gives a new user a default cap of 1,800 seconds a day. Add a form that changes a
   user's cap.
4. Add `?mock=quota` to the mock, as the contract change describes.
5. Extend `usage.sh`: group by user, show day and month totals, estimated cost, and each user's
   share of the Groq daily limit.

**Tests.** A user at the cap gets `429` and Groq is not called. A user under the cap is served.
`owner` is served when the shared guard is over. The cap resets at 00:00 UTC.

**Done when** the tests pass and a local run with a cap of 10 seconds is refused on the second
dictation. Chris deploys.

**Watch for.** The Mac client retries a `429` for ever until session G2 changes it. Do not give a
capped user a Mac token before G2.

## Not decided

| Question | Options | Who decides |
|---|---|---|
| The default daily cap for a friend | 1,800 seconds is a starting guess | Chris, after F1 shows real use |
| Whether friends keep history | On, off, or their choice | Chris, in H2 |
| Whether to switch cleanup thinking off | Depends on F1's measurement | Chris |

## Status

**F1.** Not started.

**F2.** Not started.

**F3.** Not started.
