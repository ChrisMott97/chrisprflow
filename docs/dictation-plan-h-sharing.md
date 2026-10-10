---
type: topic
subject: Dictation tool build plan - workstream H, self-hosting from the README and giving the apps to friends
author: claude
reviewed:
sources:
  - "conversation: 'dictation phase 2 planning', Oct 2026"
last_updated: 2026-10-10
---

# Workstream H: sharing

**What this is.** The plan for the two ways someone other than Chris uses the tool: setting up
their own backend from the README, or using Chris's backend with a token he gives them. Two
sessions, H1 and H2.
**Read first.** `dictation-plan-contract.md`. For H2, also `dictation-plan-costs.md`.

- H1 makes the repo something a stranger can deploy. It does not depend on workstream F.
- H2 prepares the apps and the steps for friends on Chris's backend. It depends on F and G2.
- The tool is not being made into a product. There is no signup, no payment and no accounts.

## The two ways to share

| | Own backend (H1) | Chris's backend (H2) |
|---|---|---|
| Who pays | They do, on their own keys | Chris |
| Whose limits | Their own Groq free tier | Chris's, shared with everyone |
| Who can read the text | Only them | It passes through Chris's Worker and sits in history |
| Effort for them | About 30 minutes, needs a terminal | Install the app, paste a token |
| Suits | Anyone technical | A friend trying it for a while |

## Session H1: self-hosting from the README

**Waits on:** nothing. It can run at any time, and is better after F2 so the setup steps include
`add-user.sh`.

**This session touches several folders.** Run it alone, with no other session running.

**Build**

1. **A `README.md` at the repo root**: what the tool is, what it costs with a link to
   `docs/dictation-plan-costs.md`, and the setup steps below.
2. **Setup steps in the README**, each with its exact command:
   - Create a Cloudflare account and run `npx wrangler login`.
   - Create a Groq API key and an Anthropic API key. Set a spend limit on the Anthropic account.
   - Create the D1 database and put its id into `backend/wrangler.toml`.
   - Apply the migrations, set the secrets, deploy.
   - Check `GET /v1/health`.
   - Build the Mac app and the Android app and point each at the new URL.
3. **Mac: a configurable server URL.** `mac/Sources/Dictation/Config.swift` has Chris's Worker
   URLs as constants. Add a menu item **Set Server URL…**. Keep Chris's URL as the default so his
   install does not change.
4. **Android.** The base URL can already be changed in settings. Confirm that and document it.
5. **`backend/wrangler.toml`** holds Chris's `database_id`. Leave it, and have the README say
   which line to replace.
6. **A first dictionary.** Document the `PUT /v1/dictionary` call with a small example file, so a
   new user is not required to have a vault.
7. Check the repo for anything personal that a stranger would get: names in `prompt/cases.jsonl`
   and `prompt/cleanup.md`, and the vault frontmatter on the plan files. **List what you find in
   Status. Do not delete anything.** Chris decides.

**Tests.** Follow the README from a clean clone as far as a local `npx wrangler dev` run and one
dictation. Note every step that was unclear or failed.

**Done when** the README works from a clean clone for the local run, and Chris has read it.

## Session H2: friends on Chris's backend

**Waits on:** F3 deployed, and G2. F2 keeps a friend out of Chris's history and dictionary. F3
caps their use. G2 stops the Mac app retrying a refused request for ever.

**Before this session, Chris decides**

| Decision | Options |
|---|---|
| Mac signing | An Apple Developer ID at $99 a year, which gives a normal install. Or no ID, and friends approve the app by hand in System Settings |
| History for friends | Kept (last 50, readable by Chris in the database), or off with `keep_history` set to 0 |
| Groq's terms | Confirm they allow friends to use a free-tier key |

**Build**

1. **A Mac release build.** A script that builds, signs and zips the app. With a Developer ID,
   also notarise it. Without one, write the exact steps a friend follows to open it.
2. **An Android release APK.** A signed release build. Write the steps for installing it, and for
   the "restricted settings" step Android requires before a sideloaded app can use an
   accessibility service.
3. **First-run setup in both apps.** With no token stored, the app opens a screen asking for the
   server URL and the token. Check both platforms and build what is missing.
4. **`docs/friends.md`**, a page Chris sends to a friend:
   - What the app does and how to install it.
   - **What happens to their speech.** Audio goes to Chris's Worker, then to Groq. The text goes
     to Anthropic. Audio is never stored. Whether text is kept follows the decision above.
   - The daily limit, and what the app shows when it is reached.
5. **`docs/friends-admin.md`**, the steps for Chris: add a user with `add-user.sh`, send the token
   privately, read their usage with `usage.sh`, change a cap, revoke a token.

**Done when** Chris installs both release builds on a second user account or device, sets them up
from `docs/friends.md` alone, and sees that user's rows in `usage.sh`.

## Not decided

| Question | Options | Who decides |
|---|---|---|
| A licence for the repo | None yet. A stranger cannot legally reuse the code without one | Chris, before the repo is public |
| Whether friends get edit detection (G4) | It reads text fields, so it stays off by default | Chris |
| What to do when the Groq pool is full | A second ASR provider, in `dictation-plan-a-backend.md` | Chris, when `usage.sh` shows it is close |

## Status

**H1.** Not started.

**H2.** Not started.
