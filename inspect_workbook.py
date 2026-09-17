"""Read-only XLSX inventory using Python's standard library; no Excel required."""
from collections import Counter
from hashlib import sha256
from pathlib import Path
import json
import posixpath
import re
import sys
import traceback
import xml.etree.ElementTree as ET
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parent
SOURCE = ROOT / "presupuesto mensual personal.xlsx"
NS = {"s": "http://schemas.openxmlformats.org/spreadsheetml/2006/main"}
REL = "{http://schemas.openxmlformats.org/officeDocument/2006/relationships}id"


def relationships(archive, part):
    path = posixpath.join(posixpath.dirname(part), "_rels", posixpath.basename(part) + ".rels")
    if path not in archive.namelist():
        return {}
    return {
        item.get("Id"): posixpath.normpath(posixpath.join(posixpath.dirname(part), item.get("Target"))).lstrip("/")
        for item in ET.fromstring(archive.read(path))
        if item.get("TargetMode") != "External"
    }


def inspect():
    before = sha256(SOURCE.read_bytes()).hexdigest()
    report = {"source": SOURCE.name, "sha256": before, "sheets": [], "limitations": [
        "Formulas are inventoried, not evaluated; cached results may be stale.",
        "Shared formula followers retain their group metadata, not expanded formulas.",
        "An XLSX export may omit Google Apps Script and other Google-only behavior.",
        "Worksheet names alone do not establish dates, currency, or template semantics.",
    ]}
    private_values = {}
    with ZipFile(SOURCE) as archive:
        strings = []
        if "xl/sharedStrings.xml" in archive.namelist():
            strings = ["".join(t.text or "" for t in item.findall(".//s:t", NS))
                       for item in ET.fromstring(archive.read("xl/sharedStrings.xml"))]
        book = ET.fromstring(archive.read("xl/workbook.xml"))
        links = relationships(archive, "xl/workbook.xml")
        report["defined_names"] = [dict(item.attrib, formula=item.text) for item in book.findall("s:definedNames/s:definedName", NS)]
        styles = ET.fromstring(archive.read("xl/styles.xml"))
        report["custom_number_formats"] = [item.attrib for item in styles.findall("s:numFmts/s:numFmt", NS)]
        for tab in book.findall("s:sheets/s:sheet", NS):
            part = links[tab.get(REL)]
            sheet = ET.fromstring(archive.read(part))
            cells = sheet.findall("s:sheetData/s:row/s:c", NS)
            entry = {"name": tab.get("name"), "state": tab.get("state", "visible"),
                     "cell_count": len(cells), "input_types": {}, "labels": [], "formulas": [],
                     "errors": [], "tables": [], "validations": [], "conditional_formatting": []}
            types = Counter()
            private_values[tab.get("name")] = []
            for cell in cells:
                address = cell.get("r")
                value = cell.findtext("s:v", namespaces=NS)
                kind = cell.get("t", "n")
                formula = cell.find("s:f", NS)
                decoded = strings[int(value)] if kind == "s" and value is not None else value
                if kind == "inlineStr":
                    decoded = "".join(t.text or "" for t in cell.findall(".//s:t", NS))
                if decoded is not None or formula is not None:
                    private_values[tab.get("name")].append({"cell": address, "type": kind, "value": decoded, "formula": (formula.text or "") if formula is not None else None, "style": cell.get("s")})
                if formula is not None:
                    entry["formulas"].append({"cell": address, "expression": formula.text or "", "metadata": formula.attrib, "cached_result_present": value is not None})
                elif value is not None or kind == "inlineStr":
                    types[kind] += 1
                if kind == "s" and value is not None:
                    entry["labels"].append({"cell": address, "text": strings[int(value)]})
                elif kind == "inlineStr":
                    entry["labels"].append({"cell": address, "text": "".join(t.text or "" for t in cell.findall(".//s:t", NS))})
                if kind == "e":
                    entry["errors"].append({"cell": address, "error": value})
            entry["input_types"] = dict(types)
            entry["functions"] = dict(Counter(name.upper() for f in entry["formulas"] for name in re.findall(r"([A-Za-z_][A-Za-z0-9_.]*)\s*\(", f["expression"])))
            sheet_links = relationships(archive, part)
            for item in sheet.findall("s:tableParts/s:tablePart", NS):
                table = ET.fromstring(archive.read(sheet_links[item.get(REL)]))
                entry["tables"].append({"name": table.get("displayName"), "range": table.get("ref"), "columns": [c.attrib for c in table.findall("s:tableColumns/s:tableColumn", NS)]})
            entry["merged_ranges"] = [m.get("ref") for m in sheet.findall("s:mergeCells/s:mergeCell", NS)]
            for item in sheet.findall("s:dataValidations/s:dataValidation", NS):
                entry["validations"].append(dict(item.attrib, formulas=[c.text for c in item]))
            for item in sheet.findall("s:conditionalFormatting", NS):
                entry["conditional_formatting"].append({"range": item.get("sqref"), "rules": [dict(r.attrib, formulas=[f.text for f in r.findall("s:formula", NS)]) for r in item]})
            report["sheets"].append(entry)
        report["external_link_parts"] = [p for p in archive.namelist() if p.startswith("xl/externalLinks/")]
    if sha256(SOURCE.read_bytes()).hexdigest() != before:
        raise RuntimeError("Workbook changed during inspection")
    (ROOT / "workbook_values.private.json").write_text(json.dumps(private_values, ensure_ascii=False, indent=2), encoding="utf-8")
    target = ROOT / "workbook_inventory.json"
    target.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    for sheet in report["sheets"]:
        print(json.dumps({k: sheet[k] for k in ("name", "input_types", "functions", "errors")}, ensure_ascii=True))
        print(f"Tables: {len(sheet['tables'])}; formulas: {len(sheet['formulas'])}")
    print("Inventory written; original workbook SHA-256 verified unchanged.")


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8", errors="backslashreplace")
    sys.stderr.reconfigure(encoding="utf-8", errors="backslashreplace")
    try:
        inspect()
    except Exception:
        traceback.print_exc()
        sys.exit(1)
