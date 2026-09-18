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

### Phase 4.2.1 — Month selector visibility bugfix
- Always show the month dropdown arrow, including when only one month is stored.
- Allow opening the selector with one month; selecting it closes the menu without changing data. Interaction remains disabled while saving.
- Keep the existing stored-month-only list, chronological ordering, and implicit creation rules. No database schema change or data reset.
- App version: `0.4.2.1` (`versionCode=4`).

### Phase 4.2.2 — Calendar month selector and current-month startup
- Replaced the month dropdown with a Material 3 calendar like the graph's date picker: tapping the top-bar month opens it, a selected day opens that whole month, and the calendar opens on the displayed month. Its state is separate from the graph filter.
- Selectable days are the current month (today and earlier) plus every stored month's days; future dates and months with no data stay disabled, so browsing cannot create empty historical months.
- The app now always opens on the current local month at startup: it is created empty (carrying category names and types only) when missing, and the previously viewed month is never restored. The first install keeps the seed untouched in its own month.
- Added regression tests for empty-current-month startup, preservation of historical and existing current-month data, no reseed after clear-all, calendar UTC round-trips across year boundaries and time zones, and selectable-day rules (18 tests total).
- App version: `0.4.2.2` (`versionCode=5`). No database schema change or data reset.

### Phase 4.2.5 — Calendar and shared-button migration
- The month-label calendar accepts past dates and today without requiring stored data; future dates remain disabled.
- “Ver día” opens/creates the selected month and filters both graph and expenses to the selected day. “Ver mes” opens the month overview and clears the day filter. Cancel does not load or create a month.
- Removed the graph's separate calendar popup. Reopening the month calendar restores the active day, or the displayed month's first day in overview mode.
- 1d uses the reference day; 7d includes its preceding six days clipped at month start; “30d” is now “Mes” and includes the selected calendar month. Choosing a short range from overview defaults to that month's first day rather than today's month.
- Added a time-zone regression test for range boundaries. No database schema change or data reset.
- Migrated shared filled, text, and outlined buttons and the month calendar dialog to Compose Unstyled 2.9.2. Material3 still supplies theme tokens, text, fields, cards, other dialogs, and the expense date picker; this is an incremental migration, not a complete Material3 replacement.
- Upgraded to AGP 9.1.1, Gradle 9.3.1, Kotlin 2.4.0, KSP 2.3.12, Compose BOM 2026.09.00, and compile SDK 37 (target SDK remains 35). AGP compatibility flags currently emit deprecation warnings.
- Opening the calendar gives initial focus to its heading, not the year editor, so the keyboard stays hidden until the user taps the year field.
- App version: `0.4.2.5` (`versionCode=6`). The migration build passed 19 unit tests and ten emulator acceptance assertions for day selection, future-date rejection, confirmation, and historical-month navigation. Two additional emulator checks verified the year field stays unfocused with the keyboard hidden on open, then gains focus and shows the keyboard when tapped.

### Phase 4.2.6 — Complete Material3 removal
- Replaced every remaining Material3 widget with app-owned styling built on Compose Foundation and Compose Unstyled 2.9.2: the top bar, overflow menu, cards, filter chips, checkbox, text fields, progress spinner, all dialogs, and the expense date picker (now the shared month calendar in day-pick mode with the picker's time kept).
- Theme tokens moved to an app-owned `BudgetColors`/`BudgetTypography` with the same light and dark palettes as before. `MaterialTheme`, `Scaffold`, `TopAppBar`, and the `material3` dependency are gone from the code and build; foundation, runtime, ui, and animation are now declared explicitly (BOM-managed).
- Behavior preserved: dialogs still dismiss via scrim tap and back press, the calendar still focuses its heading on open so the keyboard stays hidden until the year field is tapped, and all flows and wording are unchanged. No database schema change or data reset.
- App version: `0.4.2.6` (`versionCode=7`). All 19 unit tests pass, and the APK was installed and launched on the emulator: the main screen rendered through the new components (top bar with month and actions, balance card, flow chips, debt card, category card) with no runtime errors.

### Phase 4.3 — Carry-forward and BCV automation
- Implemented opening-balance and debt carry-forward rules: new months now inherit the closing state of their predecessor.
- Automated BCV exchange rate fetching from dolarapi.com with local caching and self-healing month repairs.
- Redesigned the budget graph with a grid, chronological axis labels, and range filters (1d, 7d, Month).
- Moved the "Add Expense" action to category headers and added a Light/Dark theme toggle to the top bar.
- Implemented interactive debt management and detailed expense editing with a custom date/time picker.
- App version: `0.4.3.0` (`versionCode=8`). Database moved to schema v4 (non-destructive migration).
- All 24 unit tests pass, covering carry-forward rules, rate fallbacks, and multi-month consistency.

### Phase 4.3.1 — UI Polish and Bug Fixes
- Added a distinctive `brandHeadline` font (ExtraBold Serif) for the month title in the TopAppBar.
- Updated the header to display the specific day when a filter is active (e.g., "17 - Septiembre").
- Cleaned up the balance card to show only the numeric rate and its unit ("Bs/USD").
- Replaced theme icons with standard Sun (`WbSunny`) and Moon (`NightsStay`) symbols.
- Compacted the Expense Editor dialog: combined date actions into a single row and tightened spacing to fit more content.
- Enabled interactive graph tapping: touching the graph now triggers the calendar picker for day filtering.
- App version: `0.4.3.1` (`versionCode=9`).

### Phase 4.3.2 — UX Consolidation and Dynamic Context
- Consolidated the dashboard: integrated "Ingresos" and "Gastos de caja" metrics directly into the `BalanceCard` and removed the `MetricsRow` to reclaim space.
- Upgraded balance typography to a prominent ExtraBold Serif style (42.sp) for better readability.
- Added a "Return to Today" shortcut icon in the top bar that appears when filtering by day or viewing past months.
- Simplified the "Ingresos" dialog: focused on adding money, with "Saldo inicial" and manual BCV overrides tucked into a toggleable advanced section.
- Removed the legacy `conversionBs` field from the model and database to prepare for the upcoming Binance transaction system.
- App version: `0.4.3.2` (`versionCode=10`). Database moved to schema v5 (destructive migration).
- All unit tests pass, with coverage for the removed conversion logic and updated balance formulas.

## Planned Phase 5 — Full Binance transaction detail and history

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

## Build and current-phase checks

Use JDK 17 and Android SDK platform 37. Configure the local SDK in `E:\Projects\Presupuesto\local.properties` and run from `E:\Projects\Presupuesto`:

```bat
gradlew.bat :app:testDebugUnitTest :app:assembleDebug --console=plain
```

The private seed must be supplied locally; it is not committed. The debug APK is written to `E:\Projects\Presupuesto\app\build\outputs\apk\debug\app-debug.apk`. Do not publish it: it contains the private seed.

Calendar smoke-check checklist:
- Open the month calendar: the year field must not be focused and the keyboard must stay hidden.
- Tap the year field: it must remain editable and the keyboard must open.
- Select a past day, then try a future day: only the past day should remain selected.
- Confirm with “Ver día”, reopen, and verify the selected day is restored.
- Confirm with “Ver mes” to return to the overview; browse and confirm a historical month, then return to the current month.
- Cancel browsing without confirming: the dashboard month must remain unchanged.

The recorded emulator checks are smoke checks, not a committed automated Compose UI test suite. Broader accessibility, screen-size, and lifecycle coverage remains pending. Remaining style migration includes fields, cards, other dialogs, and the expense date picker; carry-forward business rules remain Phase 4.3 work.

## Verification status

Phase 4.3.2 (`versionName=0.4.3.2`, `versionCode=10`) verified on emulator: confirmed "Ingresos" and "Gastos" integration into the balance card, massive balance font, and functional "Home" shortcut. Verified advanced options toggle in the budget editor.

Phase 4.3.1 (`versionName=0.4.3.1`, `versionCode=9`) verified on emulator: confirmed distinctive header font, "Day - Month" title format, clean exchange rate display, and intuitive Sun/Moon theme icons. Verified the compacted expense dialog fits without immediate scrolling.

Phase 4.3 (`versionName=0.4.3.0`, `versionCode=8`) passed `:app:testDebugUnitTest :app:assembleDebug` on 2026-09-17 with 24 tests, zero failures or errors. Verified carry-forward of Bs 800 balance and traceble debt from January to February in unit tests. On the emulator, confirmed the "Tasa BCV" badge updates, the theme toggle works, and the graph grid renders correctly. Used "Llenar datos de prueba" to verify multi-day tracking stability.

Phase 4.2.5 (`versionName=0.4.2.5`, `versionCode=6`) passed `:app:testDebugUnitTest :app:assembleDebug` on 2026-09-17 with 19 tests, zero failures or errors. On `emulator-5554` (API 37) the APK installed and cold-launched, and a scripted acceptance passed all assertions: calendar opens from the month label; tapping a valid day updates the active day; tapping a future day is ignored; "Ver día" closes the dialog and the day filter is restored on reopen; "Ver mes" closes the dialog; browsing to empty August and confirming opens it; navigating forward and confirming restores September. Crash-buffer entries inspected belong to the emulator UWB service, not the app. No emulator data was cleared.

Phase 4.2.2 (`versionName=0.4.2.2`, `versionCode=5`) passed `:app:assembleDebug :app:testDebugUnitTest` on 2026-09-17 with 18 tests, zero failures/errors. Subsequent emulator verification completed successfully: install succeeded; the month label opened the calendar; August opened and reopened on August; July and future September dates were disabled; cancellation left the month unchanged; a cold restart returned to September even with an empty budget. Crash-buffer entries belonged to the emulator UWB service, not the app. No emulator data was cleared.

Phase 4.2.1 (`versionName=0.4.2.1`, `versionCode=4`) passed `:app:assembleDebug :app:testDebugUnitTest` on 2026-09-17: 13 tests, zero failures or errors. The debug APK was installed on `emulator-5554` and its version verified. Interactive checks confirmed the arrow is visible with one stored month, the single-month menu opens and closes on selection, a past-dated expense implicitly creates August, the menu lists September before August, switching both ways shows isolated data, and the selector works after a cold restart. The temporary `Phase421Check` expense was deleted; the empty August month remains. These are emulator checks, not automated Compose UI tests. Clearing flows were not retested in this bugfix.


Phase 4.2 (`versionName=0.4.2`, `versionCode=3`) passed a clean `:app:assembleDebug :app:testDebugUnitTest` build on 2026-09-17: 13 tests, zero failures or errors. Tests cover month ordering and names, implicit month creation, cross-month expense movement, isolated clearing, restart after clear-all, and date validation.

The rebuilt debug APK was installed successfully on `emulator-5554` (API 37); the installed version was verified and a cold launch returned `Status: ok`. The dashboard displayed the month name “Septiembre”, and the current-month confirmation text was inspected. Full interactive coverage of month switching and both destructive actions has not been completed. The crash-buffer entries inspected belonged to the emulator's UWB service, not the app.

The original project scope remains: create a usable monthly budget app from the workbook logic and complete the remaining lifecycle features needed for real personal use.
