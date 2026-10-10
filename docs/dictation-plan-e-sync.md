---
type: topic
subject: Dictation tool build plan - workstream E, the dictionary sync script
author: claude
reviewed:
sources:
  - "[[topics/personal-voice-dictation-tool]]"
  - "[[dictionary]]"
  - "conversation: 'dictation tool implementation plan', Oct 2026"
last_updated: 2026-10-10
---

# Workstream E: dictionary sync

**What this is.** The plan for the script that copies names and their mis-hearings from the
knowledge vault's `dictionary.md` to the backend. One session, E1.
**Read first.** `dictation-plan-contract.md`, the `PUT /v1/dictionary` section only.

- The script reads one Markdown file and makes one `PUT` request.
- It sends two columns only. Everything else in the file is private and stays in the vault.
- The folder is `sync/`. Use Python with the standard library only, so it runs anywhere the vault
  is.

## Session E1

**Waits on:** A2 deployed.

**The input file.** `dictionary.md` has three tables under three headings:

| Heading | Columns |
|---|---|
| People | Name, Relation, Mis-heard as, Note |
| Companies and products | Term, What it is, Mis-heard as |
| Technical terms | Term, Mis-heard as |

**Build**

1. `sync/sync.py <path to dictionary.md> <base URL>`, with the token read from the environment
   variable `SYNC_TOKEN`.
2. Parse every Markdown table in the file. Find the columns by header name, not position, because
   the three tables differ.
3. **Send only the first column (Name or Term) and the Mis-heard as column.** Never send
   Relation, What it is, or Note.
4. Split the Mis-heard as cell on commas. Strip anything that is not a mis-hearing, such as
   "Recorded 2026-09-23". Strip Markdown and wikilinks from the term.
5. **Order:** terms with at least one mis-hearing first, then the rest. The contract uses this
   order to decide what fits in the Whisper prompt.
6. `--dry-run` prints the exact JSON that would be sent and sends nothing.
7. The script never writes to `dictionary.md` or to anything in the vault.

**Tests.** A fixture file with the three table shapes, including a cell with a date note and a
term written as a wikilink.

**Done when** `--dry-run` against the real file shows no private column content, Chris has read
that output, and a real run returns the expected `count`.

## Not decided

When the script runs. The options are by hand, on a timer, or after each inbox processing run in
the vault. That is a decision about the vault's own automation, so Chris makes it there.

## Status

Not started.
