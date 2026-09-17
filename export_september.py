"""Export September-only inputs. Formula expressions are never executed with eval."""
import ast
from decimal import Decimal, localcontext
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent


def arithmetic(expression):
    def visit(node):
        if isinstance(node, ast.Constant) and type(node.value) in (int, float):
            return Decimal(ast.get_source_segment(expression, node))
        if isinstance(node, ast.UnaryOp) and isinstance(node.op, (ast.UAdd, ast.USub)):
            return visit(node.operand) * (-1 if isinstance(node.op, ast.USub) else 1)
        if isinstance(node, ast.BinOp):
            left, right = visit(node.left), visit(node.right)
            if isinstance(node.op, ast.Add): return left + right
            if isinstance(node.op, ast.Sub): return left - right
            if isinstance(node.op, ast.Mult): return left * right
            if isinstance(node.op, ast.Div): return left / right
        raise ValueError("Not a literal arithmetic input: " + expression)
    with localcontext() as context:
        context.prec = 34
        return visit(ast.parse(expression, mode="eval").body)


def export():
    inventory = json.loads((ROOT / "workbook_inventory.json").read_text(encoding="utf-8"))
    sheet = next(s for s in inventory["sheets"] if s["name"] == "September")
    values = json.loads((ROOT / "workbook_values.private.json").read_text(encoding="utf-8"))["September"]
    cells = {c["cell"]: c for c in values}
    formulas = {f["cell"]: f for f in sheet["formulas"]}
    masters = {f["metadata"]["si"]: f["expression"] for f in sheet["formulas"] if f["metadata"].get("t") == "shared" and f["expression"]}

    def number(address):
        cell = cells.get(address)
        if cell is None: return "0"
        if cell["formula"] is not None:
            expression = cell["formula"]
            if not expression:
                expression = masters[formulas[address]["metadata"]["si"]]
            return str(arithmetic(expression))
        if cell["type"] != "n":
            raise ValueError("Expected numeric input at " + address)
        return str(Decimal(cell["value"] or "0"))

    def label(address):
        return cells.get(address, {}).get("value") or "Sin descripción"

    categories = []
    for table in sheet["tables"]:
        columns = table["columns"]
        if len(columns) != 4 or columns[1]["name"].strip() != "Costo proyectado":
            continue
        match = re.fullmatch(r"([A-Z]+)(\d+):([A-Z]+)(\d+)", table["range"])
        start_col, start_row, _, end_row = match.groups()
        # This workbook's expense tables start in C or H and have a totals row.
        amount_col, rate_col = {"C": ("D", "F"), "H": ("I", "K")}[start_col]
        name = columns[0]["name"].strip()
        rows = [{"id": f"{start_col}{r}", "label": label(f"{start_col}{r}"),
                 "amountBs": number(f"{amount_col}{r}"), "rate": number(f"{rate_col}{r}")}
                for r in range(int(start_row) + 1, int(end_row))]
        categories.append({"name": name, "cashExpense": name != "GASTOS PERSONALES", "rows": rows})
    debts = []
    for row, payment in ((50, "I26"), (51, "I27")):
        expression = formulas[f"E{row}"]["expression"]
        match = re.fullmatch(r"([0-9.]+)-" + payment, expression)
        if not match:
            raise ValueError("Unexpected September debt rule")
        debts.append({"label": label(f"D{row}"), "openingBs": str(Decimal(match.group(1))),
                      "paymentBs": number(payment)})
    seed = {"schemaVersion": 1, "sourceSheet": "September", "monthLabel": "Septiembre", "debts": debts,
            "incomeBs": number("F3"), "incomeRate": number("F4"), "categories": categories,
            "conversions": [{"id": f"I{r}", "dateSerial": cells.get(f"I{r}", {}).get("value"),
                             "amountBs": number(f"J{r}"), "rate": number(f"K{r}"), "usdt": number(f"L{r}")}
                            for r in range(47, 53)]}
    assert len(categories) == 9
    assert sum(c["cashExpense"] for c in categories) == 8
    output = ROOT / "app/src/main/assets/september.private.json"
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(seed, ensure_ascii=False, indent=2), encoding="utf-8")
    print("Exported September-only private seed: 9 categories, 8 cash categories. No earlier months or stub imported.")
    return seed


if __name__ == "__main__":
    export()
