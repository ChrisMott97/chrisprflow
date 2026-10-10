CREATE TABLE dictionary (
  rank     INTEGER PRIMARY KEY,
  term     TEXT NOT NULL,
  misheard TEXT NOT NULL DEFAULT '[]'
);
