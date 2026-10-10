---
type: topic
subject: Dictation tool build plan - what the system costs and the limits it runs under
author: claude
reviewed:
sources:
  - "conversation: 'dictation phase 2 planning', Oct 2026"
  - "https://groq.com/pricing"
  - "https://console.groq.com/docs/rate-limits"
  - "https://developers.cloudflare.com/workers/platform/limits/"
  - "https://developers.cloudflare.com/d1/platform/pricing/"
last_updated: 2026-10-10
---

# Costs and limits

**What this is.** What the dictation tool costs to run and which limits it hits first. This file is
for Chris. Sessions F1 and F3 read it for the prices they put into the usage report.
**Read first.** Nothing. This file stands alone.

- The flat cost is $0 a month.
- A 20-second dictation costs about $0.0003, all of it the Claude Haiku 5.5 cleanup call.
- The real limit is Groq's free tier, which every user of one deployment shares.
- **Every per-use figure here is an estimate** until session F1 records real token counts.

Prices and limits were checked on 2026-10-10. Check them again before relying on them.

## Flat cost

| Service | Plan | Cost a month |
|---|---|---|
| Cloudflare Workers | Free | $0 |
| Cloudflare D1 | Free | $0 |
| Groq | Free tier | $0 |
| Anthropic | Prepaid credit, no subscription | $0 |

Two flat costs could appear later:

| Cost | When |
|---|---|
| Workers Paid, about $5 a month | If the 10 ms CPU limit or the 100,000 requests a day limit is hit |
| Apple Developer ID, $99 a year | If friends should install the Mac app without Gatekeeper warnings |

## Cost per use

| Part | Rate | 20-second dictation |
|---|---|---|
| Groq Whisper large-v3-turbo | Free today. List price $0.04 per audio hour, 10-second minimum per request | $0 today, about $0.0002 if paid |
| Claude Haiku 5.5 cleanup | $0.10 per million input tokens, $0.50 per million output tokens | about $0.0003 |
| Cloudflare | Free | $0 |

How the Haiku figure is built:

| Tokens | Count | Cost |
|---|---|---|
| Fixed input: `prompt/cleanup.md` (about 700) and the dictionary (about 400 for 50 terms) | 1,100 | $0.00011 |
| Transcript in and cleaned text out, 20 seconds of speech | 70 in, 70 out | $0.00004 |
| Thinking, billed as output. Not measured. Assumed 200 | 200 | $0.00010 |

- Most of the cost is the fixed prompt and dictionary, which are sent on every request. A longer
  dictionary costs more on every dictation.
- `backend/src/cleanup.ts` does not set `thinking` or `effort`, so Haiku 5.5 thinks at its default
  effort. The thinking tokens are the least certain number here. F1 measures them.
- With the 10-second minimum, a 3-second dictation would be billed by Groq as 10 seconds.

## Cost per person per month

| User | Dictations | Today | If ASR were paid |
|---|---|---|---|
| Light | 10 a day | about $0.08 | about $0.15 |
| Heavy | 100 a day | about $0.75 | about $1.50 |

## Limits

| Service | Limit | What happens at the limit |
|---|---|---|
| Groq | 20 requests a minute | `502 asr_failed`, the client keeps the audio and retries |
| Groq | 2,000 requests a day | The same |
| Groq | 7,200 audio seconds an hour | The same |
| Groq | 28,800 audio seconds a day (8 hours) | The same |
| Workers Free | 100,000 requests a day | Requests fail |
| Workers Free | 10 ms CPU per request | The request fails. Not yet measured for this Worker |
| D1 Free | 5 million rows read a day | Queries fail until 00:00 UTC |
| D1 Free | 100,000 rows written a day | Queries fail until 00:00 UTC |
| Anthropic | The prepaid credit and the spend limit Chris set | Cleanup fails, the raw transcript is returned |

- **Groq's limits apply to the whole organisation**, not to one API key. Chris and every friend on
  his deployment share one pool.
- The daily audio limit fits about 14 heavy users. The limit of 20 requests a minute is the one a
  burst would hit first, and client retries count towards it.
- **More Groq capacity cannot be bought.** The Developer plan upgrade was closed in the console
  when last reported on 2026-09-16, with no reopening date. The way out is a second ASR provider.
  The candidates are in `dictation-plan-a-backend.md` under "If Groq's limits are too low".
- The Groq docs table did not say clearly which plan it describes. **Chris: confirm the four Groq
  numbers in the console**, under Settings, Limits.
- D1 has enforced its free limits since 2026-09-01. Before that date, going over did not fail.
- There is no trial on any of the four services. Groq is free with limits. Anthropic is pay as you
  go from the first request.

## What is not known yet

| Unknown | Settled by |
|---|---|
| Real input, output and thinking tokens per dictation | F1 |
| Whether Groq counts the 10-second minimum against the audio limits | F1, by comparing its log with the Groq console |
| CPU time per request against the 10 ms limit | Chris, from the Cloudflare dashboard (Workers, dictation, Metrics) |
| Whether Groq's terms allow sharing a free-tier key with friends | Chris, before H2 |
