param([int]$ParentPid,[string]$Package,[string]$AppExe,[string]$ExpectedHash,[string]$Result,[string]$Ready,[string]$Armed)
$ErrorActionPreference = 'Stop'
try {
    if ((Get-FileHash -LiteralPath $Package -Algorithm SHA256).Hash.ToLowerInvariant() -ne $ExpectedHash) { throw 'Checksum mismatch' }
    New-Item -ItemType File -Path $Ready -Force | Out-Null
    $parent = Get-Process -Id $ParentPid -ErrorAction SilentlyContinue
    if ($null -ne $parent -and !$parent.WaitForExit(120000)) { throw 'Application is still running' }
    if (!(Test-Path -LiteralPath $Armed)) { throw 'Update was not confirmed before exit' }
    # Windows Installer handles registered product upgrade and rollback; never delete the old app ourselves.
    $msi = Start-Process -FilePath "$env:SystemRoot\System32\msiexec.exe" -ArgumentList @('/i', ('"' + $Package + '"'), '/passive', '/norestart', '/L*v', ('"' + $Result + '.log"')) -Wait -PassThru
    if ($msi.ExitCode -notin @(0,3010)) { throw "Windows Installer returned $($msi.ExitCode)" }
    '更新安装完成' | Set-Content -LiteralPath $Result -Encoding UTF8
    if (Test-Path -LiteralPath $AppExe) { Start-Process -FilePath $AppExe }
} catch {
    ('更新未完成：' + $_.Exception.Message + '。旧版及个人数据均已保留。') | Set-Content -LiteralPath $Result -Encoding UTF8
    if (Test-Path -LiteralPath $AppExe) { Start-Process -FilePath $AppExe }
}
