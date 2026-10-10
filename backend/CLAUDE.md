# backend/
Cloudflare Worker (TypeScript). Real Worker `dictation`, mock Worker `dictation-mock` (`--env mock`).
Plan: `docs/dictation-plan-a-backend.md`. Contract: `docs/dictation-plan-contract.md`.
- Build/typecheck: `npx tsc --noEmit`
- Test: TODO (first session here fills in; quiet, errors only)
- Local run: `npx wrangler dev`
- Smoke: `docs/smoke.sh <base-url>` (mock or local only)
