CREATE TABLE history (
  id         TEXT PRIMARY KEY,
  created_at TEXT NOT NULL,
  client     TEXT NOT NULL,
  raw        TEXT NOT NULL,
  text       TEXT NOT NULL,
  cleaned    INTEGER NOT NULL,
  asr_ms     INTEGER NOT NULL,
  cleanup_ms INTEGER NOT NULL
);
