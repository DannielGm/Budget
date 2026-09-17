"""Compare historical schemas to September without changing source data."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent
MONTHS = ("Abril", "May", "June", "July", "August", "September")


def schema(sheet):
    return {table["columns"][0]["name"].strip(): [column["name"].strip() for column in table["columns"]]
            for table in sheet["tables"]}


def main():
    inventory = json.loads((ROOT / "workbook_inventory.json").read_text(encoding="utf-8"))
    sheets = {sheet["name"]: sheet for sheet in inventory["sheets"]}
    baseline = sheets["September"]
    target = schema(baseline)
    lines = ["# September baseline comparison", "", "Authority: September. Excluded: Modifications_base. Original is a legacy template, not a month.", "", "## September schema"]
    for category, columns in target.items():
        lines.append(f"- {category}: " + " | ".join(columns))
    lines += ["", "## September formulas (private source expressions)"]
    for formula in baseline["formulas"]:
        lines.append(f"- {formula['cell']}: {formula['expression']} {formula['metadata'] or ''}")
    lines += ["", "## September input validation", json.dumps(baseline["validations"], ensure_ascii=False), "", "## Historical comparison"]
    for name in MONTHS[:-1]:
        sheet = sheets[name]
        previous = schema(sheet)
        diff = {
            "categories_only_in_earlier_month": sorted(previous.keys() - target.keys()),
            "categories_only_in_september": sorted(target.keys() - previous.keys()),
            "column_changes": {k: {"earlier": previous[k], "september": target[k]} for k in previous.keys() & target.keys() if previous[k] != target[k]},
            "functions_only_in_earlier_month": sorted(sheet["functions"].keys() - baseline["functions"].keys()),
            "functions_only_in_september": sorted(baseline["functions"].keys() - sheet["functions"].keys()),
        }
        diverges = any(diff.values())
        lines += [f"### {name}: {'DIVERGES' if diverges else 'schema/function names align'}", json.dumps(diff, ensure_ascii=False, indent=2)]
        print(f"{name}: {'DIVERGES' if diverges else 'schema/function names align'}")
        old_labels = {c["text"].strip() for c in sheet["labels"]}
        new_labels = {c["text"].strip() for c in baseline["labels"]}
        lines.append("Earlier-only labels (not necessarily features): " + json.dumps(sorted(old_labels - new_labels), ensure_ascii=False))
        lines.append("Earlier dashboard formulas:")
        for f in sheet["formulas"]:
            if f["cell"] in {"F5", "K3", "K4", "K5", "K6"}:
                lines.append(f"- {f['cell']}: {f['expression']}")
    lines += ["", "Limits: schema/function-name alignment does not establish equivalent financial behavior. Formula counts and coordinates may differ. Cached values are not recalculated. Shared formulas are not expanded."]
    (ROOT / "september_comparison.private.txt").write_text("\n".join(lines), encoding="utf-8")
    assert len(baseline["tables"]) == 11
    assert len(baseline["formulas"]) == 83
    assert not baseline["errors"]
    assert all(f["expression"] or f["metadata"].get("t") == "shared" for f in baseline["formulas"])
    print("PASS: September has 11 tables, 83 formula cells, no cached errors, and accounted-for formula expressions.")
    print("Modifications_base excluded; workbook not edited.")


if __name__ == "__main__":
    main()
