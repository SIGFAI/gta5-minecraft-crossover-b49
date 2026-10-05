# dev: rebuild only the Minecraft side and restart only Minecraft (GTA keeps running), demo armed
$ErrorActionPreference = 'Stop'
$K = 'C:\mod\work\b49_2001\kits\crossover-gta5-minecraft'
. (Join-Path $K 'common.ps1')
Stop-Mc
$m = Build-Mc 'C:\mod\work\b49_2001'
if (-not $m.ok) {
  Write-Output "FAIL: $($m.why)"
  Get-Content $m.log | Select-String -Pattern '^\S.*\.java:\d+: error' -Context 0, 4 | Select-Object -First 8 | ForEach-Object { $_.ToString() }
  exit 1
}
& (Join-Path $K 'play.ps1') -Mod 'C:\mod\work\b49_2001\package' -Demo -Wait
