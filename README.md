# Presupuesto personal — Android prototype

Project: `E:\Projects\Presupuesto`

Kotlin + Jetpack Compose, Android 8.0 (API 26) or later. The app is an editable monthly budget backed by private local Room storage. Stored months can be switched from the top bar; automatic debt carry-forward is still planned.

## Implemented

- Private September-only seed from the original workbook; no April–August history or Modifications_base stub.
- Nine expense categories with amounts, rates, converted costs, and totals.
- Income, cash expenses, Binance amount deduction, and remaining balances following September's formulas.
- Credit-card opening balances minus payments, plus this month's credit purchases.
- Personal expenses feed debt rather than cash totals to avoid double counting.
- Decimal calculations; missing/zero row rates yield zero as in IFERROR. Unguarded balance/debt conversions display unavailable for zero income rate.
- Editable income, exchange rate, conversion amount, and expense rows with validation.
- Expense changes persist across relaunches and update all derived totals immediately.

## Privacy

`E:\Projects\Presupuesto\app\src\main\assets\september.private.json` contains real financial data. It and APKs are Git-ignored, but **not encrypted**. Anyone with the APK can extract the seed. Keep this personal build private. No Internet permission is requested and Android backup is disabled.

The workbook is preserved unchanged. The source and raw inventory still contain all worksheets for analysis; only September is bundled in the app.

Room stores each month in the app's private database. This is persistence, not encryption; the seed and stored financial data should still be treated as private.

## Finished phases

### Phase 0 — Workbook extraction and validation
- Extracted the September financial seed and kept the original workbook untouched.
- Validated the data against the workbook totals before wiring it into the app.

### Phase 1 — App foundation and persistence
- Created the Android app structure in Kotlin + Jetpack Compose.
- Added Room persistence for the current month and user edits.
- Seeded the initial September budget into the app database.

### Phase 2 — Financial logic parity
- Rebuilt the September cash, credit, debt, and Binance calculations in code.
- Preserved zero-rate, missing-rate, and decimal behavior from the workbook.
- Added validation and recalculation for edited budget values.

### Phase 3 — Budget flow and dashboard UX
- Completed the main budget flow and dashboard for September tracking.
- Improved category navigation, editing, and summary presentation.
- Refined graph styling, debt display, and interaction behavior.

### Phase 3.5 — Graph and debt refinements
- Added graph filters and better visual formatting.
- Restored usable cleared-budget behavior and improved the debt summary.

### Phase 3.6 — Detailed interactions
- Added debt editing and expense detail access.
- Continued graph and dashboard polish for clearer month-level review.

### Phase 3.7 — Final polish
- Added swipe-based category navigation and a dollar-first dashboard structure.
- Repaired category creation and deletion stability.
- Improved conversion behavior and interaction smoothness.

### Phase 3.7.1
- Fixed category swipe and restore flows.
- Corrected dollar conversion issues and refined transaction display.

### Phase 3.7.2
- Recovered category deletion behavior without breaking the budget flow.
- Continued dashboard and category display refinement.

### Phase 3.7.3
- Added the expense type selector.
- Finalized bidirectional animations and interaction polish for the current build.

### Phase 4.1 — Multi-month data model and repository
- Replaced the fixed single-month repository behavior with month-aware loading and saving.
- Removed the hardcoded `september` month id from repository writes and support multiple stored periods.
- Added database queries to list months and load a specific month; saving a new month record is now isolated by `monthId`.
- Kept category, expense, and debt rows scoped to their month.
- Added a repository regression test covering independent September and October records.

### Phase 4.2 — Month selection and the current-month flow
- Added a month selector to the top bar; months are labelled with their name and the year is appended only when it is not the current one.
- Loads the selected month from storage and re-renders its own income, categories, expenses, and debts on every switch.
- Kept sortable `YYYY-MM` month ids so ordering is chronological instead of alphabetical; the seed month became `2026-09` and the database moved to schema v3.
- Dropped the manual “new month” action: months only come into existence through recorded data, created empty with the category names and types carried over.
- Restricted every date entry to today or earlier, in the date picker, the typed date field, and the repository guard that rejects future months.
- Added confirmations to both clear actions: “Limpiar mes actual” zeroes the selected month and keeps its categories and list entry; “Limpiar todos los datos” removes all months, then retains the selected month zeroed with its category definitions.
- **Upgrade warning:** schema v2 → v3 uses destructive migration. Existing on-device edits are deleted and the private September seed is restored. This is not a data-preserving migration.

## Planned Phase 4 — Multi-month budget model

### Phase 4.3 — Carry-forward logic
- Define how balances, debt, and recurring values pass from one month to the next.
- Implement the opening-balance and debt carry-forward rules in the budget model.
- Make sure any new-month calculations remain consistent with the workbook logic already implemented.

### Phase 4.4 — Validation and regression checks
- Add or update tests covering month switching, month creation, and carry-forward calculations.
- Verify that existing September data remains stable while the multi-month system is introduced.
- Confirm the app still handles zero-rate, missing-rate, and debt logic correctly after the model change.

### Phase 5 — Full Binance transaction detail and history
- Replace the aggregate Binance summary with a detailed transaction view.
- Show raw entries, sources, and reconciliation against the monthly budget totals.
- Add filtering and review flows for Binance activity by date and category.

### Phase 6 — Data integrity and export workflows
- Add backup/export support for the local financial state and stored month data.
- Confirm date and year consistency across entries and recurring calculations.
- Add reset, review, and recovery actions for safe local data management.

### Phase 7 — Quality, accessibility, and release readiness
- Improve accessibility across forms, charts, and action buttons.
- Run broader device and emulator testing across common Android screen sizes.
- Validate edge cases with degraded data, invalid entries, and zero-rate inputs.
- Prepare the release build, signing flow, and final app packaging.

### Phase 8 — Release validation and handoff
- Verify the final production APK on a real or emulated device.
- Confirm business logic and UX remain stable after the new-month and export work.
- Finalize documentation, deployment notes, and the handoff checklist for the finished app.

## Verification status


Phase 4.2 (`versionName=0.4.2`, `versionCode=3`) passed a clean `:app:assembleDebug :app:testDebugUnitTest` build on 2026-09-17: 13 tests, zero failures or errors. Tests cover month ordering and names, implicit month creation, cross-month expense movement, isolated clearing, restart after clear-all, and date validation.

The rebuilt debug APK was installed successfully on `emulator-5554` (API 37); the installed version was verified and a cold launch returned `Status: ok`. The dashboard displayed the month name “Septiembre”, and the current-month confirmation text was inspected. Full interactive coverage of month switching and both destructive actions has not been completed. The crash-buffer entries inspected belonged to the emulator's UWB service, not the app.

The original project scope remains: create a usable monthly budget app from the workbook logic and complete the remaining lifecycle features needed for real personal use.
