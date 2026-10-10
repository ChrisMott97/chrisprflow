# sync/
Script that pushes the dictionary to `PUT /v1/dictionary`. Plan: `docs/dictation-plan-e-sync.md`.
- Build: none (Python 3 standard library only). Run: `SYNC_TOKEN=... python3 sync/sync.py <dictionary.md> <base URL> [--dry-run]`
- Test: `python3 -m unittest discover -s sync/tests -q`
