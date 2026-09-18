param([string]$Phase = 'A')
$adb = 'C:\Users\DANIE\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$script:xml = $null
function Dump {
    if ($script:xml) { return $script:xml }
    & $adb -s emulator-5554 shell uiautomator dump /sdcard/window.xml 2>&1 | Out-Null
    cmd /c "`"$adb`" -s emulator-5554 pull /sdcard/window.xml e:\Projects\Presupuesto\_window425.xml >nul 2>&1"
    $script:xml = [xml](Get-Content 'e:\Projects\Presupuesto\_window425.xml' -Encoding UTF8)
    return $script:xml
}
function Fresh { $script:xml = $null; Dump }
function Norm([string]$s) {
    if ($null -eq $s) { return '' }
    $d = $s.Normalize([System.Text.NormalizationForm]::FormD)
    $d = -join ($d.ToCharArray() | Where-Object { [System.Globalization.CharUnicodeInfo]::GetUnicodeCategory($_) -ne [System.Globalization.UnicodeCategory]::NonSpacingMark })
    return $d.Normalize([System.Text.NormalizationForm]::FormC)
}
function Find([string]$text, [int]$minX = 0) {
    foreach ($i in 1..2) {
        $cands = @((Dump).SelectNodes('//node') | Where-Object { (Norm $_.text) -eq (Norm $text) -or (Norm $_.'content-desc') -eq (Norm $text) })
        if ($minX -gt 0) { $cands = @($cands | Where-Object { [int][regex]::Match($_.bounds, '\d+').Value -ge $minX }) }
        if ($cands.Count -gt 0) { return @($cands)[0] }
        Start-Sleep -Milliseconds 250
        $script:xml = $null
    }
    return $null
}
function Tap([string]$text, [int]$minX = 0) {
    $node = Find $text $minX
    if (-not $node) { throw "missing node: $text (minX=$minX)" }
    $n = [regex]::Matches($node.bounds, '\d+') | ForEach-Object { [int]$_.Value }
    & $adb -s emulator-5554 shell input tap ([int](($n[0] + $n[2]) / 2)) ([int](($n[1] + $n[3]) / 2))
    Start-Sleep -Milliseconds 350
    $script:xml = $null
}
function HasText([string]$text, [switch]$Once) {
    foreach ($i in 1..3) {
        $found = @((Dump).SelectNodes('//node') | Where-Object { (Norm $_.text) -eq (Norm $text) })
        if ($found.Count -gt 0) { return $true }
        if ($Once) { return $false }
        Start-Sleep -Milliseconds 250
        $script:xml = $null
    }
    return $false
}
& $adb -s emulator-5554 shell input keyevent 224
& $adb -s emulator-5554 shell wm dismiss-keyguard 2>&1 | Out-Null
Start-Sleep -Milliseconds 600
$script:xml = $null
if ($Phase -eq 'A') {
    & $adb -s emulator-5554 shell am force-stop com.personal.presupuesto
    & $adb -s emulator-5554 shell am start -W -n com.personal.presupuesto/.MainActivity | Out-Null
    Start-Sleep -Milliseconds 1200
    $script:xml = $null
    Tap 'Septiembre'
    if (-not (HasText 'Calendario')) { Write-Output 'FAIL open-calendar'; exit 1 }
    Write-Output 'PASS open-calendar'
    Tap '10'
    if (HasText '10 septiembre 2026') { Write-Output 'PASS select-valid-day' } else { Write-Output 'FAIL select-valid-day'; exit 2 }
    Tap '30'
    if (-not (HasText '30 septiembre 2026' -Once)) { Write-Output 'PASS future-date-disabled' } else { Write-Output 'FAIL future-date-disabled'; exit 3 }
    Tap 'Ver dia'
    Start-Sleep -Milliseconds 500
    $script:xml = $null
    if ((-not (HasText 'Calendario' -Once)) -and (HasText 'Saldo disponible' -Once)) { Write-Output 'PASS confirm-day-closes' } else { Write-Output 'FAIL confirm-day-closes'; exit 4 }
    Tap 'Septiembre'
    if (HasText '10 septiembre 2026') { Write-Output 'PASS day-filter-restored' } else { Write-Output 'FAIL day-filter-restored'; exit 5 }
    Tap 'Ver mes'
    Start-Sleep -Milliseconds 500
    $script:xml = $null
    if (-not (HasText 'Calendario' -Once)) { Write-Output 'PASS ver-mes-closes' } else { Write-Output 'FAIL ver-mes-closes'; exit 6 }
    Write-Output 'PHASE-A DONE'
} else {
    if (-not (HasText 'Calendario' -Once)) { Tap 'Septiembre' }
    if (-not (HasText 'Calendario')) { Write-Output 'FAIL reopen-calendar'; exit 7 }
    function TapArrow([string]$side) {
        $m = @((Dump).SelectNodes('//node') | Where-Object { (Norm $_.text) -match '^\S+ 2026$' } | Select-Object -First 1)
        if (-not $m) { throw 'missing month header' }
        $n = [regex]::Matches($m.bounds, '\d+') | ForEach-Object { [int]$_.Value }
        $x = if ($side -eq 'prev') { [int]($n[0] - 41) } else { [int]($n[2] + 62) }
        $y = [int](($n[1] + $n[3]) / 2)
        & $adb -s emulator-5554 shell input tap $x $y
        Start-Sleep -Milliseconds 350
        $script:xml = $null
    }
    TapArrow prev
    if (HasText 'agosto 2026') { Write-Output 'PASS browse-past-month' } else { Write-Output 'FAIL browse-past-month'; exit 8 }
    Tap 'Ver mes'
    Start-Sleep -Milliseconds 500
    $script:xml = $null
    if ((-not (HasText 'Calendario' -Once)) -and (HasText 'Agosto' -Once)) { Write-Output 'PASS past-month-confirm' } else { Write-Output 'FAIL past-month-confirm'; exit 9 }
    Tap 'Agosto'
    TapArrow next
    if (HasText 'septiembre 2026') { Write-Output 'PASS browse-forward' } else { Write-Output 'FAIL browse-forward'; exit 10 }
    Tap 'Ver mes'
    Start-Sleep -Milliseconds 500
    $script:xml = $null
    if ((-not (HasText 'Calendario' -Once)) -and (HasText 'Septiembre' -Once)) { Write-Output 'PASS restore-current-month' } else { Write-Output 'FAIL restore-current-month'; exit 11 }
    Write-Output 'ACCEPTANCE PASSED'
}

