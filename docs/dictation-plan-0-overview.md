---
type: topic
subject: Dictation tool build plan - overview, order of sessions and manual steps
author: claude
reviewed:
sources:
  - "[[topics/personal-voice-dictation-tool]]"
  - "conversation: 'dictation tool implementation plan', Oct 2026"
last_updated: 2026-10-10
---

# Dictation tool build plan: overview

**What this is.** The order in which to build the dictation tool: which Claude Code sessions run
in parallel, which wait, and what Chris does by hand. This file is for Chris. Agents do not need
to read it.
**Read first.** [[topics/personal-voice-dictation-tool]] for the design this plan builds.

- One setup session, S0, then four workstreams in parallel. Twelve sessions in total.
- The Mac client is usable daily after C2. Android is the long chain and finishes last.
- Every plan file is `topics/dictation-plan-*.md`. They are here temporarily and move to the
  implementation repo's `docs/` folder.

![[dictation-plan-order.svg|620]]

## The files

| File | Read by |
|---|---|
| `dictation-plan-0-overview.md` | Chris only |
| `dictation-plan-contract.md` | Every session |
| `dictation-plan-s0-setup.md` | S0 |
| `dictation-plan-a-backend.md` | A1, A2 |
| `dictation-plan-b-prompt.md` | B1 |
| `dictation-plan-c-mac.md` | C1, C2, C3 |
| `dictation-plan-d-android.md` | D1, D2, D3, D4 |
| `dictation-plan-e-sync.md` | E1 |

Each file carries the vault's frontmatter. It does no harm in the repo and can be deleted there.

## Sessions and what each waits on

| Session | Does | Waits on agents | Waits on Chris |
|---|---|---|---|
| S0 | Repo layout, mock backend deployed | Nothing | Repo created, plan files copied in, Cloudflare login |
| A1 | Real pipeline, deployed | S0 | Groq key, Anthropic key, spend limits, Worker secrets |
| B1 | Cleanup prompt and test set | S0 | Anthropic key in the shell |
| C1 | Mac: record, upload, paste, on the mock | S0 | Xcode, hotkey chosen, Mac permissions |
| D1 | Android: record, upload, copy, on the mock | S0 | Android Studio, USB debugging |
| A2 | Dictionary, history route, final prompt | A1, B1 | Nothing |
| C2 | Mac silence trimming, real backend | C1, A1 | Copy the real URL into the contract |
| D2 | Android tile, silence trimming, real backend | D1, A1 | Nothing |
| E1 | Dictionary sync script | A2 | Read the dry-run output before the first real run |
| C3 | Mac tap-to-toggle, cancel, indicator, retry | C2 | Nothing |
| D3 | Android overlay bubble | D2 | Overlay permission |
| D4 | Android accessibility insertion | D3 | Accessibility service on, battery unrestricted |

**Most that can run at once:** four, in the row after S0 (A1, B1, C1, D1).

## What Chris does by hand, in order

1. **Before S0.** Create the repo. Copy the plan files into `docs/`. Create a Cloudflare account
   and run `npx wrangler login`.
2. **Before A1 and B1.** Create the Groq and Anthropic API keys. Set a spend limit on each
   account. Check the Groq free tier limits and the data retention setting on both. Put the keys
   and three random client tokens in as Worker secrets.
3. **Before C1.** Install Xcode and sign in. Choose the hotkey. Grant Microphone and
   Accessibility when macOS asks.
4. **Before D1.** Install Android Studio. Switch on USB debugging.
5. **After C1 and D1.** Test each by hand. An agent cannot press a hotkey and speak.
6. **After A1.** Copy the real URL from the backend plan's Status section into the contract.
7. **After C2.** Use the Mac client for real. Judge Haiku 5.5's output and the latency. This is
   the point to decide whether anything in the design changes.
8. **Before D3 and D4.** Grant the overlay permission, then the accessibility service.
9. **After A2.** Decide when the dictionary sync runs. That is a vault decision.

## How to run the sessions cheaply

- **On the Claude Pro plan, run one or two sessions at a time, not four.** Parallel sessions
  draw on the same usage limit, so four at once save clock time and no tokens. The diagram shows
  what is independent, not what must run together. A good pair is one backend-side session (A, B
  or E) with one device session (C or D), because the device session spends time waiting on a
  manual test.
- **Order when running one at a time:** S0, A1, B1, C1, C2, A2, C3, E1, then D1 to D4. This
  reaches a usable Mac client soonest.
- **Do not use one session that spawns subagents for the workstreams.** A subagent starts with no
  context and its result is paid for again in the parent, so the total is higher. The C and D
  sessions also stop for manual tests, which a subagent cannot do.
- **Keep build output out of the context.** Xcode and Gradle print thousands of lines. Each
  folder's `CLAUDE.md` should give a quiet build command that prints errors only.
- **One session per box, started fresh.** Do not continue C1's session into C2. A session that
  carries the last stage's debugging pays for it on every turn.
- **Open each session with the same prompt, changing only the file and session name:**
  `Read docs/dictation-plan-contract.md and docs/dictation-plan-c-mac.md. Do session C2 only.
  When it is done, fill in the Status section for C2 and stop.`
- **The Status section is the handover.** The next session in the chain reads it in place of the
  previous conversation.
- **One terminal per workstream, each started in its own folder.** Each folder has a small
  `CLAUDE.md`, so a session loads only its own instructions.
- **Agents commit only their own folder.** Four agents share one working tree, so none of them
  runs `git add -A`.
- **The contract is frozen.** If a session says the contract is wrong, change it yourself and tell
  the other running sessions. Do not let one agent change it.
- **Model.** Sonnet for every session except two. Start D4 on Opus, because it is the hardest
  session. E1 is simple enough for Haiku. Move a session up to Opus when one of these happens:
  - the same error comes back after two fix attempts,
  - the session wants to change the contract, drop a requirement or remove a failing test,
  - the problem is a platform rule and not the code: signing, macOS permissions, Android service
    restrictions.
- **How to move up.** Have the Sonnet session write the problem into Status, then start a fresh
  Opus session that reads it. Do not switch model inside the long session, because Opus would then
  be paid for the whole history.
- **Where.** C and D sessions run in Claude Code on the Mac, because they need the microphone, the
  permission prompts and the phone. S0, A, B and E can run anywhere `wrangler` is logged in.

## Risks to check early

| Risk | Checked in | If it fails |
|---|---|---|
| Haiku 5.5 obeys dictated instructions | B1 | Chris decides. The fallback is Haiku 4.5 at ten times the price |
| Groq free tier audio limit is too low, and the paid Developer plan is closed to new sign-ups | A1, then daily use | Add a second ASR provider. The backend plan lists the candidates |
| Workers free tier CPU limit of 10 ms | A1 | The paid plan, about $5 a month |
| macOS drops permissions on rebuild | C1 | Sign with a stable development certificate |
| Android refuses the microphone service from a tile | D2, first step | Report and stop. The bubble in D3 may need to come first |
| An app refuses accessibility text insertion | D4 | That app falls back to the clipboard |

## Related

- [[topics/personal-voice-dictation-tool]] - the design, the cost and the reasons behind each
  choice in this plan.
