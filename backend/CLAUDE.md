# backend/
Cloudflare Worker (TypeScript). Real Worker `dictation`, mock Worker `dictation-mock` (`--env mock`).
Plan: `docs/dictation-plan-a-backend.md`. Contract: `docs/dictation-plan-contract.md`.
- Build/typecheck: `npx tsc --noEmit`
- Test: `npm test` (Vitest, quiet; errors only)
- Local DB: `npx wrangler d1 migrations apply dictation --local` (remote: `--remote`)
- Local run needs `backend/.dev.vars` (never read it); `dev_mac_token` is the dev Mac token
- Local run: `npx wrangler dev`
- Smoke: `docs/smoke.sh <base-url>` (mock only: its dictate checks assert mock text)
