"""Checks actual September seed calculations against cached workbook results."""
from decimal import Decimal
import json
import unittest
from export_september import ROOT, arithmetic


class SeedTest(unittest.TestCase):
    def test_literal_arithmetic_is_safe(self):
        self.assertEqual(arithmetic("1.1+2.2-(0.1)"), Decimal("3.2"))
        with self.assertRaises(ValueError):
            arithmetic("__import__('os')")

    def test_september_summary_matches_workbook_cache(self):
        seed = json.loads((ROOT / "app/src/main/assets/september.private.json").read_text(encoding="utf-8"))
        cells = {c["cell"]: c for c in json.loads((ROOT / "workbook_values.private.json").read_text(encoding="utf-8"))["September"]}
        self.assertEqual(seed["sourceSheet"], "September")
        self.assertEqual(len(seed["categories"]), 9)
        cash = [r for c in seed["categories"] if c["cashExpense"] for r in c["rows"]]
        cash_bs = sum((Decimal(r["amountBs"]) for r in cash), Decimal(0))
        cash_usd = sum((Decimal(r["amountBs"]) / Decimal(r["rate"]) if Decimal(r["rate"]) else Decimal(0) for r in cash), Decimal(0))
        balance = Decimal(seed["incomeBs"]) - cash_bs - sum(Decimal(r["amountBs"]) for r in seed["conversions"])
        expected = {"F3": Decimal(seed["incomeBs"]), "K3": cash_usd, "K4": cash_bs, "K5": balance, "K6": balance / Decimal(seed["incomeRate"])}
        credit = sum(Decimal(r["amountBs"]) for c in seed["categories"] if not c["cashExpense"] for r in c["rows"])
        debt = credit + sum(Decimal(d["openingBs"]) - Decimal(d["paymentBs"]) for d in seed["debts"])
        expected.update({"E52": credit, "E53": debt, "F53": debt / Decimal(seed["incomeRate"])})
        for address, actual in expected.items():
            with self.subTest(cell=address):
                self.assertLess(abs(actual - Decimal(cells[address]["value"])), Decimal("0.000001"))


if __name__ == "__main__":
    unittest.main()
