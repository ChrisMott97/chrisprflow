---
type: topic
subject: Dictation tool build plan - session S0, repo setup and mock backend
author: claude
reviewed:
sources:
  - "[[topics/personal-voice-dictation-tool]]"
  - "conversation: 'dictation tool implementation plan', Oct 2026"
last_updated: 2026-10-10
---

# Session S0: setup

**What this is.** The one session that must finish before any other starts. It creates the repo
layout and a deployed mock backend, so the four workstreams can then run in parallel.
**Read first.** `dictation-plan-contract.md`.

## Before this session, Chris does

- Create an empty git repo and copy every `dictation-plan-*.md` file into `docs/`.
- Create a free Cloudflare account.
- Run `npx wrangler login` on the machine. It opens a browser to approve access. Check it worked
  with `npx wrangler whoami`.

## After this session, Chris does

Set the secrets on the real Worker. This can only happen after S0, because the Worker has to
exist first. From the `backend/` folder, run each of these and paste the value when asked:

```
npx wrangler secret put GROQ_API_KEY
npx wrangler secret put ANTHROPIC_API_KEY
npx wrangler secret put MAC_TOKEN
npx wrangler secret put ANDROID_TOKEN
npx wrangler secret put SYNC_TOKEN
```

- No `--env` flag. That targets the real Worker. The mock Worker needs no secrets.
- Each token is its own random string from `openssl rand -hex 32`.
- Check with `npx wrangler secret list`. It shows the names, never the values.
- The values are stored by Cloudflare. They are not written to any file in the repo.

## What S0 builds

1. **Folders:** `backend/`, `prompt/`, `mac/`, `android/`, `sync/`, `docs/`.
2. **Root `CLAUDE.md`**, under 20 lines, saying:
   - Work only inside your own folder and your own plan file.
   - Never edit `docs/dictation-plan-contract.md`. Stop and ask instead.
   - Commit only your own folder. Never `git add -A`, because other agents are working in the
     same tree.
   - End every session by filling in the Status section of your plan file.
3. **A `CLAUDE.md` in each folder**, a few lines: what the folder is, how to build, how to test.
   Leave the build and test lines for the first session in that folder to fill in. They must be
   quiet commands that print errors only, because full Xcode and Gradle output wastes context.
4. **`.gitignore`** covering secrets (`.dev.vars`, `local.properties`), build output and audio
   files.
5. **`.claude/settings.json`** with permission rules. Check the current rule syntax in the Claude
   Code documentation before writing them.
   - Deny edits to `docs/dictation-plan-contract.md`. Add this rule last, after both URLs are
     written in at step 8.
   - Deny reads of the secret files: `backend/.dev.vars` and `android/local.properties`.
   - Deny reads of build output folders: `node_modules`, `.wrangler`, `build`, `.build`,
     `DerivedData`.
   - Allow the routine commands without a prompt: the test, build, `wrangler dev` and
     `adb install` commands.
6. **The mock backend** in `backend/`: a TypeScript Cloudflare Worker with every route in the
   contract, returning the answers in the contract's "Mock behaviour" table. No Groq, Anthropic or
   D1 yet.
7. **Two Workers from one codebase,** set up in the Wrangler config file:
   - The top-level Worker, named `dictation`, with the variable `MODE` set to `real`. This is
     the real backend. In S0 it has no pipeline yet, so `POST /v1/dictate` returns `501` and
     `GET /v1/health` returns `"mode": "real"`.
   - An environment named `mock`, which deploys as the Worker `dictation-mock`, with `MODE` set
     to `mock`. It returns the mock answers.
   The real Worker is the top level on purpose. A `wrangler` command with no `--env` flag then
   acts on the real one, which is what Chris needs when he sets the secrets.
8. **Deploy both.** Run `npx wrangler deploy` and `npx wrangler deploy --env mock`. If the first
   deploy asks Chris to choose a `workers.dev` subdomain, stop and let him answer. Write both
   URLs into the contract's Base URLs table. That is the one edit to the contract that S0 is
   allowed.
9. **`docs/smoke.sh`:** `curl` calls for each route against a base URL given as an argument. The
   token comes from the environment variable `DICTATION_TOKEN` and defaults to `mock-token`.
   Agents only ever run it against the mock or a local `wrangler dev`. Chris runs it against the
   real URL, because only he holds the production tokens.
10. **A placeholder `prompt/cleanup.md`:** two lines, an instruction to return the transcript
   with punctuation fixed, and the `{{DICTIONARY}}` placeholder. Session A1 imports this file, and
   it may run before B1 writes the real prompt.
11. **A sample audio file,** `docs/sample.wav`, 16 kHz mono, a few seconds. Chris records it, or
   S0 generates one with the system's text-to-speech command.

## Done when

- `docs/smoke.sh <mock URL>` passes for every row of the mock table.
- `GET /v1/health` on the real URL returns `"mode": "real"`.
- Both URLs are in the contract.
- Everything is committed.

## Status

Done 2026-10-10.
- Mock: https://dictation-mock.chrisamott97.workers.dev. `docs/smoke.sh` passes all checks.
- Real: https://dictation.chrisamott97.workers.dev. Health returns `"mode": "real"`; `/v1/dictate` returns 501 (no pipeline yet).
- Both URLs are in the contract. The contract edit-deny rule is in `.claude/settings.json`.
- Deploys were run by Chris (the auto-mode classifier blocks agent production deploys).
- `@cloudflare/workers-types` is pinned to v5 because wrangler 4.149 requires it.
- Chris still needs to set the five secrets on the real Worker (see "After this session").

### Handover: starting the next sessions

S0 is complete and its prerequisites are met. The five Worker secrets are set (names confirmed with
`wrangler secret list`), so A1 and B1 are unblocked.

Known gap for A1: the real Worker's auth only accepts `mock-token` in mock mode. A1 must add the
per-client token check (`MAC_TOKEN`, `ANDROID_TOKEN`, `SYNC_TOKEN`) and the real pipeline.

Start each session fresh, in its own folder, with this prompt (change the file and session name):

```
Read docs/dictation-plan-contract.md and docs/dictation-plan-<file>.md. Do session <ID> only.
When it is done, fill in the Status section for <ID> and stop.
```

| Session | Plan file | Folder | Talks to |
|---|---|---|---|
| A1 | `dictation-plan-a-backend.md` | `backend/` | Real pipeline |
| B1 | `dictation-plan-b-prompt.md` | `prompt/` | Anthropic key in shell |
| C1 | `dictation-plan-c-mac.md` | `mac/` | Mock URL |
| D1 | `dictation-plan-d-android.md` | `android/` | Mock URL |

On the Pro plan, run one or two at once (for example A1 with C1). See the overview.
