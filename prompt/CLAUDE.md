# prompt/
The cleanup prompt (`cleanup.md`) with a `{{DICTIONARY}}` placeholder, its cases (`cases.jsonl`) and a runner (`run.ts`). Plan: `docs/dictation-plan-b-prompt.md`.
- Build: none
- Test: `node run.ts` (needs `ANTHROPIC_API_KEY` in the shell; prints one line per case plus failure detail). `--runs 3` repeats the set, `--verbose` prints passing output too. Exits 1 on any failure.
- `cases.jsonl`: `must_contain` is case-sensitive and `a|b` means either; `must_not_contain` is whole-word and case-insensitive; optional `max_len`.
- The backend imports `cleanup.md` as it is. Keep it to the prompt text and the one `{{DICTIONARY}}` marker.
