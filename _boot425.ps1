$ErrorActionPreference = 'Stop'
$processes = Get-CimInstance Win32_Process | Where-Object { $_.Name -match 'emulator|qemu' }
$processes | Select-Object ProcessId, Name, CommandLine | Format-List
Get-ChildItem 'e:\Projects\Presupuesto' -Force | Select-Object Name
Get-ChildItem "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1\com.composables" -Recurse -File -ErrorAction SilentlyContinue | Select-Object FullName
if (-not $processes) {
    Start-Process -FilePath 'C:\Users\DANIE\AppData\Local\Android\Sdk\emulator\emulator.exe' -ArgumentList '-avd Medium_Phone -no-boot-anim -no-snapshot-load -gpu swiftshader_indirect' -RedirectStandardOutput 'e:\Projects\Presupuesto\_emulator425.log' -RedirectStandardError 'e:\Projects\Presupuesto\_emulator425.err'
}
