# Presupuesto personal — Android prototype

Project: `E:\Projects\Presupuesto`

Kotlin + Jetpack Compose, Android 8.0 (API 26) or later. The app is an editable September overview backed by private local Room storage. Month history and automatic debt carry-forward are planned but are not exposed yet.

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

Room stores the current month in the app's private database. This is persistence, not encryption; the seed and stored financial data should still be treated as private.

## Rebuild on this machine

The local JDK and SDK are under `E:\Projects\Presupuesto\.tools`. System Java was not replaced. Run in cmd.exe:

```bat
set "JAVA_HOME=E:\Projects\Presupuesto\.tools\jdk17\jdk-17.0.20.1+1"
set "ANDROID_HOME=E:\Projects\Presupuesto\.tools\sdk"
python E:\Projects\Presupuesto\export_september.py
python E:\Projects\Presupuesto\test_seed.py
E:\Projects\Presupuesto\gradlew.bat -p E:\Projects\Presupuesto --console=plain testDebugUnitTest assembleDebug
```

If regenerating from the workbook, first run:

```bat
python E:\Projects\Presupuesto\inspect_workbook.py
python E:\Projects\Presupuesto\validate_inventory.py
```

Expected debug artifact: `E:\Projects\Presupuesto\app\build\outputs\apk\debug\app-debug.apk`.
JUnit results: `E:\Projects\Presupuesto\app\build\test-results\testDebugUnitTest`.

## Validation scope

Python tests compare September income, cash totals, balances, credit purchases, and debt totals against the workbook's cached results within 0.000001. These are not independent recalculations of all 83 formulas. Kotlin tests cover currency conversion, cash/credit separation, Binance deduction, debt and zero-rate handling.

## Remaining work

- New-month creation and debt carry-forward rules.
- Detailed Binance transaction UI (raw entries are seeded; the current UI shows their aggregate cash amount).
- Date/year confirmation, backup/export, accessibility and device testing.
- Release signing; the initial APK uses a debug signing key.

No device or emulator was connected during the initial build session; a successful compilation alone does not verify on-device behavior. The Phase 0 report describes the earlier inventory stage, not the current implementation status.
