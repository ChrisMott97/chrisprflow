---
type: topic
subject: Dictation tool build plan - workstream G, adding dictionary terms from the apps
author: claude
reviewed:
sources:
  - "conversation: 'dictation phase 2 planning', Oct 2026"
last_updated: 2026-10-10
---

# Workstream G: dictionary from the apps

**What this is.** The plan for adding and fixing dictionary terms from the Mac and Android apps,
so the dictionary no longer depends on the knowledge vault. Four sessions, G1 to G4.
**Read first.** `dictation-plan-contract.md`.

- G1 adds the backend routes. G2 and G3 add the screens. G4 is a later experiment.
- After G2, the backend is the source of truth and the sync script from E1 is retired.
- Each session works in one folder: G1 in `backend/`, G2 and G4 in `mac/`, G3 in `android/`.

## How the dictionary is used today

| Part of a term | Sent to | Effect |
|---|---|---|
| The correct spelling | Whisper, as a prompt. Only the first 800 characters of terms fit, in priority order | The word is more likely to be transcribed correctly |
| The correct spelling and its mis-hearings | Haiku cleanup, as "Akroyd (mis-heard as: Acroyd, Ackroyd)" | Those wrong spellings are replaced |

- **A name alone is enough.** Mis-hearings are optional.
- Mis-hearings matter most for a term past the 800-character budget, because cleanup is then the
  only step correcting it.
- Today the only way to add a term is to edit `dictionary.md` in the vault and run the sync
  script. Nothing learns from dictations.

## Session G1: backend routes

**Waits on:** F2. Chris has applied changes 4 and 5 from `dictation-plan-contract-changes.md` to
the contract.

**Build**

1. `POST /v1/dictionary/terms` and `DELETE /v1/dictionary/terms`, as the contract describes.
2. A new term goes to the front of the priority order. An existing term, compared without case,
   has its mis-hearings merged and keeps its position.
3. Any token of the user may call both routes and `PUT /v1/dictionary`. Remove the `SYNC_TOKEN`
   only rule.
4. Keep the limit of 1,000 terms. Over it, return `400 bad_request`.
5. Add both routes to the mock.

**Tests.** Add a new term, add an existing term with a new mis-hearing, add with a different
case, delete, delete a missing term, and one user's change not touching another user's
dictionary.

**Done when** the tests pass and a term added locally shows up in the Groq prompt of the next
dictation. Chris deploys.

## Session G2: Mac

**Waits on:** G1 deployed.

**Build**

1. A menu item **Add to Dictionary…**. It opens a small window with two fields: the correct
   spelling, which is required, and "heard as", which is optional. It calls
   `POST /v1/dictionary/terms`.
2. A submenu **Recent Dictations**, filled from `GET /v1/history?limit=10`. Choosing one opens the
   same window with that dictation's text shown above the fields, so a wrong word can be copied
   into "heard as".
3. A menu item **Dictionary…** that lists the terms from `GET /v1/dictionary`, with a remove
   button on each.
4. **Treat `429` as final.** In `Sources/Dictation/APIClient.swift`, add 429 to the statuses that
   show an error and discard the audio. Today it is retried for ever.

**Tests.** Unit tests for the request bodies and for 429 being final. Chris tests the windows by
hand.

**Done when** Chris adds a name through the menu and his next dictation spells it correctly.

**After this session, Chris does.** Run the E1 sync script one last time, then stop using it. From
here a `PUT` from the script would delete every term added in the apps.

## Session G3: Android

**Waits on:** G1 deployed.

**Build**

1. A **Dictionary** screen reached from settings: the list of terms, a remove action on each, and
   an add button.
2. An add form with the same two fields as the Mac.
3. A **Recent dictations** list from `GET /v1/history?limit=10`. Tapping one opens the add form
   with the dictation's text shown above it.

**Tests.** Unit tests for the request bodies. Chris tests the screens by hand.

**Done when** Chris adds a name on the phone and his next dictation spells it correctly.

## Session G4: suggest a term when the user fixes a word (experiment)

**Waits on:** G2. **Run this only if adding terms by hand proves too slow.** It is the hardest
session in this workstream. Start it on the strongest model.

**The idea.** After the Mac app inserts text, the user sometimes corrects one word in the field.
The app notices and offers to add the correction to the dictionary.

**Build, Mac only**

1. After a paste, remember the inserted text and the focused text field. Use the Accessibility
   permission the app already has.
2. Read the field's text again at two points: when the next dictation starts, and when the focus
   leaves the field. Do not poll on a timer.
3. Find the inserted text in the field and compare. Act only when exactly one word changed and the
   new word is close to the old one, by edit distance or by sound. Ignore everything else. A
   reworded sentence is not a mis-hearing.
4. **Suggest, never add silently.** Show a notification: "Add Akroyd to your dictionary?" with an
   Add button. Add sends the new word as the term and the old word as its mis-hearing.
5. **Privacy rules.** Compare on the device only. Never send field contents to the backend. Send
   only a pair the user accepted. Never read a secure text field.
6. A setting to switch the feature off. It starts switched off.

**First step, before building.** Check which apps expose their text field through Accessibility.
Test at least Notes, Mail, Safari, Chrome, Slack, VS Code, Terminal and Obsidian. Report the
table in Status. **If fewer than half work, stop and tell Chris.**

**Known limits**

| Limit | Effect |
|---|---|
| Many apps do not expose field text: terminals, some Electron apps, some browser fields | No suggestion in those apps |
| An edit made after the focus has moved on and come back | Missed |
| Android | Not planned. It would build on the accessibility service from D4 |

**Done when** Chris fixes a mis-heard name in Notes, sees the suggestion, accepts it, and the next
dictation spells the name correctly.

## Not decided

| Question | Options | Who decides |
|---|---|---|
| Whether the vault file carries on after G2 | Retire the sync script, or change it to read the backend's list first and merge | Chris |
| Whether G4 is worth building | Decide after a few weeks of G2 | Chris |

## Status

**G1.** Not started.

**G2.** Not started.

**G3.** Not started.

**G4.** Not started.
