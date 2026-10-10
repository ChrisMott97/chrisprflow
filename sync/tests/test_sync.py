import contextlib
import io
import json
import os
import sys
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, ".."))
import sync  # noqa: E402

FIX = os.path.join(HERE, "fixture.md")


class SyncTests(unittest.TestCase):
    def parsed(self):
        with open(FIX, encoding="utf-8") as f:
            return sync.parse(f.read())

    def terms(self):
        return {t["term"]: t["misheard"] for t in self.parsed()}

    def test_all_three_shapes(self):
        t = self.terms()
        self.assertEqual(t["Chris Akroyd"], ["Chris Acroyd", "Chris Ackroyd"])
        self.assertEqual(t["KatKin"], ["Cat Kin", "Kat Kin"])
        self.assertEqual(t["Cloudflare Workers"], ["Cloud flare workers"])

    def test_date_note_stripped(self):
        self.assertEqual(self.terms()["Chris Phippen"], ["Chris Fippin"])

    def test_wikilink_term(self):
        self.assertEqual(self.terms()["Calum Murray"], ["Callum"])

    def test_order_misheard_first(self):
        ts = self.parsed()
        flags = [bool(t["misheard"]) for t in ts]
        self.assertEqual(flags, sorted(flags, reverse=True))
        self.assertEqual(len(ts), 8)

    def test_dry_run_has_no_private_columns(self):
        buf = io.StringIO()
        with contextlib.redirect_stdout(buf):
            self.assertEqual(sync.main([FIX, "http://x", "--dry-run"]), 0)
        out = buf.getvalue()
        self.assertNotIn("PRIVATE", out)
        self.assertNotIn("Recorded", out)
        self.assertEqual(set(json.loads(out)), {"terms"})


if __name__ == "__main__":
    unittest.main()
