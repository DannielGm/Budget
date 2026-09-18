### Phase 4.2.2 — Calendar month selector and current-month startup
- Replaced the month dropdown with a Material 3 calendar like the graph's date picker: tapping the top-bar month opens it, a selected day opens that whole month, and the calendar opens on the displayed month. Its state is separate from the graph filter.
- Selectable days are the current month (today and earlier) plus every stored month's days; future dates and months with no data stay disabled, so browsing cannot create empty historical months.
- The app now always opens on the current local month at startup: it is created empty (carrying category names and types only) when missing, and the previously viewed month is never restored. The first install keeps the seed untouched in its own month.
- Added regression tests for empty-current-month startup, preservation of historical and existing current-month data, no reseed after clear-all, calendar UTC round-trips across year boundaries and time zones, and selectable-day rules (18 tests total).
- App version: `0.4.2.2` (`versionCode=5`). No database schema change or data reset.

### Phase 4.2.5 — Calendar behavior checkpoint (in progress)
- The month-label calendar accepts past dates and today without requiring stored data; future dates remain disabled.
- “Ver día” opens/creates the selected month and filters both graph and expenses to the selected day. “Ver mes” opens the month overview and clears the day filter. Cancel does not load or create a month.
- Removed the graph's separate calendar popup. Reopening the month calendar restores the active day, or the displayed month's first day in overview mode.
- 1d uses the reference day; 7d includes its preceding six days clipped at month start; “30d” is now “Mes” and includes the selected calendar month. Choosing a short range from overview defaults to that month's first day rather than today's month.
- Added a time-zone regression test for range boundaries. No database schema change, data reset, dependency change, or version bump in this checkpoint.
- Still pending: Compose Unstyled visual migration and replacement calendar, expanded UI tests, version `0.4.2.5` / code 6, and emulator verification. This is not the completed 4.2.5 release.

## Planned Phase 4 — Multi-month budget model

### Phase 4.3 — Carry-forward logic
- Define how balances, debt, and recurring values pass from one month to the next.
- Implement the opening-balance and debt carry-forward rules in the budget model.
- Make sure any new-month calculations remain consistent with the workbook logic already implemented.

### Phase 4.4 — Validation and regression checks
- Add or update tests covering month switching, month creation, and carry-forward calculations.
- Verify that existing September data remains stable while the multi-month system is introduced.
- Confirm the app still handles zero-rate, missing-rate, and debt logic correctly after the model change.
