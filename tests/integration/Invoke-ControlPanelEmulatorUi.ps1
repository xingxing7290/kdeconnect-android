#requires -Version 7.4
# SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
param(
    [Parameter(Mandatory)][string]$AdbPath,
    [Parameter(Mandatory)][ValidatePattern('^emulator-[0-9]+$')][string]$Serial,
    [Parameter(Mandatory)][string]$ExpectedAvd,
    [Parameter(Mandatory)][string]$EvidenceDirectory,
    [int]$ServerPort = 5038,
    [ValidateSet('State', 'Tap', 'InputIp', 'DiscoverDesktop')][string]$Action = 'State',
    [string]$Text = '',
    [string]$Description = '',
    [string]$ResourceId = '',
    [string]$IpAddress = '10.0.2.2',
    [string]$DesktopId = '',
    [string]$Package = 'org.kde.kdeconnect_tp.debug'
)
$ErrorActionPreference = 'Stop'
if (!(Test-Path -LiteralPath $AdbPath -PathType Leaf)) { throw 'ADB executable not found' }
if ($ServerPort -lt 1024 -or $ServerPort -gt 65535) { throw 'Invalid dedicated ADB port' }
if ($Action -eq 'Tap' -and (!$Text -and !$Description -and !$ResourceId)) { throw 'Tap requires an exact text/description or resource ID' }
if ($Action -eq 'DiscoverDesktop' -and $DesktopId -notmatch '^[a-zA-Z0-9_-]{32,38}$') { throw 'A verified private desktop device ID is required' }
$evidenceRoot = [IO.Path]::GetFullPath($EvidenceDirectory)
New-Item -ItemType Directory -Path $evidenceRoot -Force | Out-Null

function Invoke-TestAdb([string[]]$Command, [switch]$IncludeErrorOutput, [string]$InputText, [switch]$WriteInput) {
    $start = [Diagnostics.ProcessStartInfo]::new($AdbPath)
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    $start.RedirectStandardInput = $WriteInput.IsPresent
    $start.StandardOutputEncoding = [Text.Encoding]::UTF8
    $start.StandardErrorEncoding = [Text.Encoding]::UTF8
    foreach ($argument in (@('-P', "$ServerPort", '-s', $Serial) + $Command)) {
        [void]$start.ArgumentList.Add($argument)
    }
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $start
    try {
        [void]$process.Start()
        $output = $process.StandardOutput.ReadToEndAsync()
        $errors = $process.StandardError.ReadToEndAsync()
        if ($WriteInput) {
            $process.StandardInput.WriteLine($InputText)
            $process.StandardInput.Close()
        }
        if (!$process.WaitForExit(30000)) {
            $process.Kill($true)
            throw 'ADB command exceeded 30 seconds; no automatic input retry'
        }
        $result = $output.GetAwaiter().GetResult()
        $errorText = $errors.GetAwaiter().GetResult()
        if ($process.ExitCode -ne 0) { throw "ADB failed: $errorText $result" }
        return $(if ($IncludeErrorOutput) { $result + [Environment]::NewLine + $errorText } else { $result }).TrimEnd()
    } finally { $process.Dispose() }
}

if ((Invoke-TestAdb @('shell', 'getprop', 'ro.kernel.qemu')).Trim() -ne '1') {
    throw 'Refusing a target that is not an Android emulator'
}
$actualAvd = (Invoke-TestAdb @('emu', 'avd', 'name')) -split '\r?\n' | Select-Object -First 1
$actualAvd = $actualAvd.Trim()
if ($actualAvd -ne $ExpectedAvd) { throw "AVD mismatch: $actualAvd" }

function Read-TestUi {
    for ($attempt = 1; $attempt -le 3; $attempt++) {
        $token = [guid]::NewGuid().ToString('N')
        $remote = "/sdcard/kde-panel-ui-$token.xml"
        $dump = Invoke-TestAdb -Command @('shell', 'uiautomator', 'dump', $remote) -IncludeErrorOutput
        [IO.File]::WriteAllText((Join-Path $evidenceRoot "dump-$token.txt"), $dump, [Text.UTF8Encoding]::new($false))
        if ($dump -match 'dumped to:') { break }
        if ($attempt -eq 3) { throw "No fresh UI tree after 3 observations; no input retried: $dump" }
        Start-Sleep -Milliseconds 500
    }
    $raw = Invoke-TestAdb @('exec-out', 'cat', $remote)
    $document = [xml]$raw
    if (!$document.hierarchy) { throw 'Invalid UI hierarchy' }
    $path = Join-Path $evidenceRoot "ui-$token.xml"
    [IO.File]::WriteAllText($path, $raw, [Text.UTF8Encoding]::new($false))
    return @{Document = $document; Token = $token; Path = $path}
}

$state = Read-TestUi
if ($Action -eq 'DiscoverDesktop') {
    $routes = Invoke-TestAdb @('reverse', '--list')
    if ($routes -notmatch '(?m)^\S+\s+tcp:1717\s+tcp:1716\s*$') { throw 'Expected dedicated reverse tcp:1717 tcp:1716 route is missing' }
    $packet = @{id = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds(); type = 'kdeconnect.identity'; body = @{
        deviceId = $DesktopId; deviceName = 'KDE E2E Desktop'; deviceType = 'desktop'; protocolVersion = 8; tcpPort = 1717
    }} | ConvertTo-Json -Depth 3 -Compress
    # Only discovery is injected. TLS identity checks and pairing remain in the production apps.
    [void](Invoke-TestAdb -Command @('shell', 'toybox', 'nc', '-u', '-q', '1', '-w', '2', '-W', '2', '127.0.0.1', '1716') -InputText $packet -WriteInput)
    $state = Read-TestUi
}
if ($Action -eq 'InputIp') {
    $parsedIp = $null
    if (![Net.IPAddress]::TryParse($IpAddress, [ref]$parsedIp) -or $parsedIp.AddressFamily -ne [Net.Sockets.AddressFamily]::InterNetwork) {
        throw 'InputIp accepts only a literal IPv4 address'
    }
    $fields = @($state.Document.SelectNodes('//node') | Where-Object {
        $_.package -eq $Package -and $_.class -eq 'android.widget.EditText' -and $_.focused -eq 'true' -and
        $_.enabled -eq 'true' -and $_.password -eq 'false'
    })
    if ($fields.Count -ne 1 -or $fields[0].text) { throw 'InputIp requires one observed, empty, focused non-password field' }
    [void](Invoke-TestAdb @('shell', 'input', 'text', $IpAddress))
    $state = Read-TestUi
}
if ($Action -eq 'Tap') {
    $targets = @($state.Document.SelectNodes('//node') | Where-Object {
        $_.package -eq $Package -and $_.enabled -eq 'true' -and
        ((!$ResourceId -or $_.'resource-id' -eq $ResourceId) -and
         (!$Description -or $_.'content-desc' -eq $Description) -and
         (!$Text -or $_.text -eq $Text -or $_.'content-desc' -eq $Text))
    })
    if ($targets.Count -ne 1) { throw "Expected one exact enabled target, found $($targets.Count); inspect $($state.Path)" }
    $bounds = [regex]::Match($targets[0].bounds, '^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$')
    if (!$bounds.Success) { throw 'Invalid target bounds' }
    $left, $top, $right, $bottom = 1..4 | ForEach-Object { [int]$bounds.Groups[$_].Value }
    if ($right -le $left -or $bottom -le $top) { throw 'Empty target bounds' }
    $x = [int](($left + $right) / 2)
    $y = [int](($top + $bottom) / 2)
    [void](Invoke-TestAdb @('shell', 'input', 'tap', "$x", "$y"))
    $state = Read-TestUi
}

$remoteImage = "/sdcard/kde-panel-screen-$($state.Token).png"
[void](Invoke-TestAdb @('shell', 'screencap', '-p', $remoteImage))
$imagePath = Join-Path $evidenceRoot "screen-$($state.Token).png"
[void](Invoke-TestAdb @('pull', $remoteImage, $imagePath))
$nodes = @($state.Document.SelectNodes('//node') | Where-Object { $_.text -or $_.'content-desc' -or $_.class -eq 'android.widget.EditText' } | ForEach-Object {
    [ordered]@{text = $_.text; description = $_.'content-desc'; resourceId = $_.'resource-id'; package = $_.package; class = $_.class; focused = $_.focused; bounds = $_.bounds}
})
[ordered]@{serial = $Serial; avd = $actualAvd; action = $Action; rotation = $state.Document.hierarchy.rotation; xml = $state.Path; screenshot = $imagePath; nodes = $nodes} | ConvertTo-Json -Depth 4
