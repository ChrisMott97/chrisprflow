# Dictation tool

Personal voice dictation: Mac + Android clients, a Cloudflare Worker backend, a sync script.
Plans live in `docs/dictation-plan-*.md`. Read `docs/dictation-plan-contract.md` first.

Rules for every session:
- Work only inside your own folder and your own plan file.
- Never edit `docs/dictation-plan-contract.md`. Stop and ask Chris instead.
- Commit only your own folder. Never `git add -A` or `git add .`; other agents work in the same tree.
- End every session by filling in the Status section of your plan file.
- Never read or print secret files (`.dev.vars`, `local.properties`).
- Use quiet build/test commands that print errors only; full Xcode/Gradle output wastes context.
