# Phase 0 — workbook assessment

## Scope and status

Source: `E:\Projects\Presupuesto\presupuesto mensual personal.xlsx`

Standalone future Android project: `E:\Projects\Presupuesto`.
Selected UI stack: Kotlin + Jetpack Compose. Tickets is unrelated and unchanged.

The workbook was inspected locally using Python's standard library. No packages or Android tools were installed. Source SHA-256 was checked before and after extraction and during validation:

`ad9ad5d067ff4cd7e88da5c9251f6cd9ba6c2e10d1edda8a54ddf855b2a3ce40`

## Verified inventory

| Worksheet | Excel tables | Formula cells |
| --- | ---: | ---: |
| Abril | 11 | 82 |
| May | 11 | 78 |
| June | 11 | 81 |
| July | 11 | 79 |
| August | 11 | 82 |
| September | 11 | 83 |
| Modifications_base | 11 | 73 |
| Original | 12 | 104 |
| **Total** | **89** | **662** |

These are repeated worksheet tables, not 89 distinct business entities. The formulas include arithmetic, SUM, IFERROR, SUBTOTAL, structured table references, and shared formula metadata. No cached error cells were found; that does not prove formulas are correct or their cached results current.

The modified monthly layout includes income, exchange rates, expense totals and balances, with categories including home, transport, food, personal expenses, entertainment, loans, savings/investments, and special occasions. It also includes credit-card debt and Binance purchase/sale information with amount, rate, date and USDT fields.

## Important findings before implementation

1. **The column labels do not fully describe their current meaning.** Examples calculate `Costo real` by dividing `Costo proyectado` by `Tasa`; dashboard labels distinguish Bs and $. This appears to be currency conversion rather than simply planned-versus-actual spending. Currency semantics must be confirmed before naming app fields.
2. **Balance rules differ between months.** Abril K6 uses `K5/F4`, whereas May K6 uses `F5-K3`. These need not agree when individual expense rates differ from the income rate.
3. **Some category totals appear omitted.** Abril K3/K4 list eight category tables but do not include Table4 (GASTOS PERSONALES). Confirm whether this is intentional; do not silently reproduce or fix it.
4. **The base is not an empty template.** Modifications_base retains a literal income calculation and uses `F3-K4-Table11_6[[#TOTALS],[Cantidad]]` for K5. Treat it as private source data, not a safe distributable template.
5. **Original uses different logic.** It includes difference columns and SUBTOTAL formulas, unlike the converted-currency layout. It should not automatically define the new app's behavior.
6. **Some formulas encode input data.** Literal additions can represent income components or purchases, not reusable calculation rules. Separate these into editable records rather than hard-coding them in Kotlin.
7. **Shared formulas require expansion during migration.** The inventory preserves their metadata but does not translate follower expressions. The value dump uses an empty formula string to distinguish these cells from non-formula inputs.

## Confirmed scope — latest user decisions

- September is the authoritative behavior for the Android app and new months.
- Ignore Modifications_base: the user confirmed its remaining data is a stub.
- Earlier months may inform additive features, but must not override September's rules.
- Preload September's actual data for personal use. This supersedes the earlier request to import April–September history; April–August will not be preloaded.
- September personal expenses feed the credit-card debt section (E52) rather than the cash-expense dashboard totals (K3/K4). Preserve that separation.
- Proposed new-month initialization: keep September's structure and rules without copying its spending/income amounts; debt carry-forward behavior still needs definition.
- Any APK embedding actual financial data is private: bundled assets can be extracted. Git exclusions are not encryption.
- These are requirements, not implemented app features. The initial assessment below remains historical context.

## Proposed app direction — not implemented

- Editable offline monthly budgets, with local storage (Room proposed).
- Explicit currency and exchange-rate fields, decimal-safe money calculations.
- Monthly overview, expense categories, income, debt, and conversion records.
- Shared data entities across months rather than one database table per sheet.
- Clean distributable seed template; optional private import of historical data.
- Unit tests for agreed financial rules, including zero/missing rates, rounding, category inclusion, and differing rates.

Before scaffolding, confirm which monthly version defines the intended rules, what Bs/$/USDT mean operationally, the historical year, and whether existing amounts should be imported. Especially resolve the balance and category-total differences above.

## Local artifacts and rerun commands

- `E:\Projects\Presupuesto\inspect_workbook.py`: extraction script; does not modify the workbook.
- `E:\Projects\Presupuesto\workbook_inventory.json`: labels, table ranges/columns, formulas, validations, formatting metadata and hash. **Private: formulas can contain financial amounts.**
- `E:\Projects\Presupuesto\workbook_values.private.json`: raw nonempty cell values, cached formula results, style IDs and formula text. Numeric values remain raw strings; date serials are not converted.
- `E:\Projects\Presupuesto\validate_inventory.py`: extraction consistency checks.
- `E:\Projects\Presupuesto\review.private.txt`: local detailed review generated by validation.
- `E:\Projects\Presupuesto\.gitignore`: excludes the workbook, private extraction outputs and common Android local credentials/artifacts. This is not encryption or access control.

Run:

```bat
python E:\Projects\Presupuesto\inspect_workbook.py
python E:\Projects\Presupuesto\validate_inventory.py
```

Both commands were run successfully. Checks cover JSON parsing, eight-sheet coverage, unique extracted cell addresses, matching formula counts and source hash integrity. They do not validate financial correctness or independently recalculate formulas.

## Build prerequisites and limitations

Python is available. Only a Java 11 runtime was found; javac, adb and sdkmanager were not found in PATH, nor an Android SDK in the standard location checked. A compatible JDK and Android SDK must be configured before an APK build. A project Gradle wrapper can provide Gradle; a global Gradle installation is unnecessary.

No Android application or APK has been created in Phase 0. No formula engine was executed. Google-only functionality such as Apps Script may not be preserved in XLSX. Visual layout, charts, extended formatting, and complete financial dependency semantics still require migration-stage review.
