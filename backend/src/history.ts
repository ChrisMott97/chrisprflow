export const HISTORY_KEEP = 50;

export interface HistoryRow {
  id: string;
  client: string;
  raw: string;
  text: string;
  cleaned: boolean;
  asrMs: number;
  cleanupMs: number;
}

export async function listHistory(db: D1Database, limit: number) {
  const { results } = await db
    .prepare("SELECT id, created_at, client, raw, text, cleaned FROM history ORDER BY id DESC LIMIT ?")
    .bind(limit)
    .all<{ id: string; created_at: string; client: string; raw: string; text: string; cleaned: number }>();
  return results.map((r) => ({ ...r, cleaned: r.cleaned === 1 }));
}

export async function saveHistory(db: D1Database, row: HistoryRow): Promise<void> {
  await db.batch([
    db
      .prepare(
        "INSERT INTO history (id, created_at, client, raw, text, cleaned, asr_ms, cleanup_ms) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
      )
      .bind(
        row.id,
        new Date().toISOString().replace(/\.\d+Z$/, "Z"),
        row.client,
        row.raw,
        row.text,
        row.cleaned ? 1 : 0,
        row.asrMs,
        row.cleanupMs,
      ),
    // ids are ULIDs, so id order is time order.
    db
      .prepare("DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY id DESC LIMIT ?)")
      .bind(HISTORY_KEEP),
  ]);
}
