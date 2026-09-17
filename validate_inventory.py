"""Validate extraction and emit a compact local review (may contain private formulas)."""
import json
from pathlib import Path
from hashlib import sha256

ROOT = Path(__file__).resolve().parent
inventory = json.loads((ROOT / "workbook_inventory.json").read_text(encoding="utf-8"))
values = json.loads((ROOT / "workbook_values.private.json").read_text(encoding="utf-8"))
assert sha256((ROOT / inventory["source"]).read_bytes()).hexdigest() == inventory["sha256"]
assert len(inventory["sheets"]) == len(values) == 8
lines = []
for sheet in inventory["sheets"]:
    cells = values[sheet["name"]]
    assert len({c["cell"] for c in cells}) == len(cells)
    assert sum(c["formula"] is not None for c in cells) == len(sheet["formulas"])
    lines.append("\n## " + sheet["name"])
    lines.append("Tables: " + json.dumps(sheet["tables"], ensure_ascii=False))
    lines.append("Validation/conditional counts: " + str((len(sheet["validations"]), len(sheet["conditional_formatting"]))))
    lines.append("Shared formula metadata: " + str([f for f in sheet["formulas"] if f["metadata"]]))
    lines.append("Formulas: " + json.dumps(sheet["formulas"], ensure_ascii=False))
lines.append("Formats: " + str(inventory["custom_number_formats"]))
(ROOT / "review.private.txt").write_text("\n".join(lines), encoding="utf-8")
print("PASS: workbook hash, eight sheets, unique cell addresses, formula counts, JSON parsing.")
print("Total tables:", sum(len(s["tables"]) for s in inventory["sheets"]))
print("Total formulas:", sum(len(s["formulas"]) for s in inventory["sheets"]))
