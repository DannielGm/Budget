param([string]$Tap = '')
$adb = 'C:\Users\DANIE\AppData\Local\Android\Sdk\platform-tools\adb.exe'
function Dump {
    & $adb -s emulator-5554 shell uiautomator dump /sdcard/window.xml | Out-Null
    & $adb -s emulator-5554 pull /sdcard/window.xml 'e:\Projects\Presupuesto\_window425.xml' 2>$null | Out-Null
    [xml]$xml = Get-Content 'e:\Projects\Presupuesto\_window425.xml' -Encoding UTF8
    return $xml
}
$xml = Dump
if ($Tap) {
    $node = $xml.SelectNodes('//node') | Where-Object { $_.text -eq $Tap -or $_.'content-desc' -eq $Tap } | Select-Object -First 1
    if (-not $node) { throw "Missing UI node: $Tap" }
    $numbers = [regex]::Matches($node.bounds, '\d+') | ForEach-Object { [int]$_.Value }
    & $adb -s emulator-5554 shell input tap ([int](($numbers[0]+$numbers[2])/2)) ([int](($numbers[1]+$numbers[3])/2))
    Start-Sleep -Seconds 1
    $xml = Dump
}
$xml.SelectNodes('//node') | Where-Object { $_.text -or $_.'content-desc' } | ForEach-Object { '{0} | {1} | enabled={2} selected={3} {4}' -f $_.text, $_.'content-desc', $_.enabled, $_.selected, $_.bounds }
