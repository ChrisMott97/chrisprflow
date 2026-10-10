#!/usr/bin/env bash
# Usage: docs/smoke.sh <base-url>   (token from DICTATION_TOKEN, default mock-token)
# Agents run this against the mock or a local `wrangler dev` only.
# Chris runs it against the real URL, because only he holds the production tokens.
# The dictate checks assume the MOCK table; against the real Worker only health is meaningful in S0.
set -u
BASE="${1:?usage: smoke.sh <base-url>}"
BASE="${BASE%/}"
TOKEN="${DICTATION_TOKEN:-mock-token}"
WAV="$(cd "$(dirname "$0")" && pwd)/sample.wav"
SMALL="$(mktemp)"; head -c 100 "$WAV" > "$SMALL"
trap 'rm -f "$SMALL"' EXIT

fail=0
STATUS=""; BODY=""

# call <curl args...>: sets STATUS and BODY
call() {
  local out
  out=$(curl -s -w $'\n%{http_code}' "$@")
  STATUS="${out##*$'\n'}"
  BODY="${out%$'\n'*}"
}

# expect <name> <status> [body-substring]
expect() {
  if [[ "$STATUS" != "$2" ]] || { [[ $# -ge 3 ]] && [[ "$BODY" != *"$3"* ]]; }; then
    echo "FAIL $1: got $STATUS, want $2${3:+ containing $3}; body: $BODY"
    fail=1
  else
    echo "ok   $1"
  fi
}

AUTH=(-H "Authorization: Bearer $TOKEN")
WAVH=(-H "Content-Type: audio/wav" --data-binary)

call "$BASE/v1/health";                                   expect "health (no auth)" 200 '"ok":true'
call "$BASE/v1/history";                                  expect "history without token" 401
call -H "Authorization: Bearer wrong" "$BASE/v1/history"; expect "history bad token" 401 unauthorized
call "${AUTH[@]}" "$BASE/v1/history?limit=5";             expect "history" 200 '"items"'

call "${AUTH[@]}" "${WAVH[@]}" @"$WAV" "$BASE/v1/dictate";            expect "dictate ok" 200 'This is a mock dictation.'
call "${AUTH[@]}" "${WAVH[@]}" @"$WAV" "$BASE/v1/dictate";            expect "dictate cleaned" 200 '"cleaned":true'
call "${AUTH[@]}" "${WAVH[@]}" @"$SMALL" "$BASE/v1/dictate";          expect "dictate tiny body is empty" 200 '"empty":true'
call "${AUTH[@]}" "${WAVH[@]}" @"$WAV" "$BASE/v1/dictate?mock=fail";  expect "dictate mock=fail" 502 asr_failed
call "${AUTH[@]}" "${WAVH[@]}" @"$WAV" "$BASE/v1/dictate?mock=raw";   expect "dictate mock=raw" 200 '"cleaned":false'
call -H "Authorization: Bearer wrong" "${WAVH[@]}" @"$WAV" "$BASE/v1/dictate"; expect "dictate bad token" 401 unauthorized
call "${AUTH[@]}" -H "Content-Type: text/plain" --data-binary @"$WAV" "$BASE/v1/dictate"; expect "dictate bad type" 415 bad_audio_type

call "${AUTH[@]}" -X PUT -H "Content-Type: application/json" \
  -d '{"terms":[{"term":"Akroyd","misheard":["Acroyd"]},{"term":"Ankar","misheard":[]}]}' "$BASE/v1/dictionary"
expect "dictionary put" 200 '"count":2'
call "${AUTH[@]}" "$BASE/v1/dictionary";                  expect "dictionary get" 200 '"terms"'

exit $fail
