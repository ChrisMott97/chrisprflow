---
type: topic
subject: Dictation tool build plan - proposed changes to the API contract for phase 2
author: claude
reviewed:
sources:
  - "conversation: 'dictation phase 2 planning', Oct 2026"
last_updated: 2026-10-10
---

# Proposed contract changes

**What this is.** The changes phase 2 needs in `dictation-plan-contract.md`. This file is a
proposal. The contract itself is unchanged.
**Read first.** `dictation-plan-contract.md`.

**Rule for agents: this file is not the contract.** Build only what the contract says. If your
session's change is listed here and is not yet in the contract, stop and tell Chris.

**Chris applies each change by hand, before the session that needs it.** Then he tells any other
running session.

| Change | Needed before | Breaks existing clients |
|---|---|---|
| 1. Tokens belong to users | F2 | No |
| 2. History and dictionary are per user | F2 | No |
| 3. `429 quota_exceeded` | F3 | The Mac client retries it for ever until G2 fixes it |
| 4. Routes to add and remove one term | G1 | No |
| 5. `PUT /v1/dictionary` open to any of the user's tokens | G1 | No |

## 1. Tokens belong to users

Replaces the Auth section.

> Every route except `GET /v1/health` needs `Authorization: Bearer <token>`. Each token belongs to
> one user and has a label, such as `mac` or `android`. A user can have several tokens. A missing,
> unknown or revoked token returns `401`.

## 2. History and dictionary are per user

Add one sentence to each of the three sections.

- `GET /v1/history`: "Returns only the caller's own dictations. At most 50 are kept per user."
- `PUT /v1/dictionary` and `GET /v1/dictionary`: "Reads and replaces only the caller's own
  dictionary."
- `POST /v1/dictate`: "Uses only the caller's own dictionary."

## 3. A new error on POST /v1/dictate

Add one row to the error table.

| Status | Code | Client does |
|---|---|---|
| 429 | `quota_exceeded` | Show the message. Do not retry |

The `message` says which limit was reached and when it resets, for example "Daily limit reached.
Resets at 00:00 UTC."

**Client effect.** Android already treats every 4xx as final. The Mac client treats every status
other than 401, 413 and 415 as "retry later", in `mac/Sources/Dictation/APIClient.swift`. It needs
429 added to the final list.

## 4. Routes to add and remove one term

New sections.

**POST /v1/dictionary/terms**

```json
{ "term": "Akroyd", "misheard": ["Acroyd"] }
```

- `misheard` is optional.
- If the term is new, it is added at the front of the priority order.
- If the term exists, compared without case, its mis-hearings are merged and its position is kept.
- Returns `200` with `{"count": <total terms>}`.

**DELETE /v1/dictionary/terms?term=Akroyd**

- Removes the term, compared without case.
- Returns `200` with `{"count": <total terms>}`. A term that is not there is not an error.

Both routes accept any token of the user.

## 5. PUT /v1/dictionary open to any of the user's tokens

Replace "Used by the sync script only" with:

> Replaces the caller's whole dictionary. Used for a first import. To add one term, use
> `POST /v1/dictionary/terms`.

## Mock behaviour to add

| Request | Mock answer |
|---|---|
| `POST /v1/dictate?mock=quota` | `429 quota_exceeded` |
| `POST /v1/dictionary/terms` | Adds to the mock's in-memory dictionary, returns the count |
| `DELETE /v1/dictionary/terms` | Removes from it, returns the count |
