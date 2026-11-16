param(
    [string]$Serial = '192.168.0.82:45913',
    [string]$AdbPath = (Join-Path $PSScriptRoot '../.tools/android-sdk/platform-tools/adb.exe')
)
$ErrorActionPreference = 'Stop'
$adb = (Resolve-Path -LiteralPath $AdbPath).Path

function Invoke-PrivateAdb([string[]]$Arguments, [string]$InputText = $null) {
    $start = [Diagnostics.ProcessStartInfo]::new($adb)
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    $start.RedirectStandardInput = $true
    $start.StandardInputEncoding = [Text.UTF8Encoding]::new($false)
    foreach ($argument in (@('-s', $Serial) + $Arguments)) { $start.ArgumentList.Add($argument) }
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $start
    try {
        [void]$process.Start()
        if ($null -ne $InputText) { $process.StandardInput.Write($InputText) }
        $process.StandardInput.Close()
        $output = $process.StandardOutput.ReadToEndAsync()
        $errors = $process.StandardError.ReadToEndAsync()
        if (-not $process.WaitForExit(60000)) { $process.Kill(); throw 'The TV request timed out.' }
        if ($process.ExitCode -ne 0) { throw 'The TV connection failed. Check ADB and the diagnostic build.' }
        return $output.GetAwaiter().GetResult()
    } finally { $process.Dispose() }
}

$listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
$listener.Start()
$port = ([Net.IPEndPoint]$listener.LocalEndpoint).Port
$token = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(24)).ToLowerInvariant()
$nonce = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(18))
$origin = "http://127.0.0.1:$port"
$route = "/$token"
$deadline = [DateTime]::UtcNow.AddMinutes(20)
$connected = $false
$html = @'
<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Connect Vista Music</title><style nonce="NONCE">
body{font:17px system-ui;background:#0b0e14;color:#e6eaf2;max-width:760px;margin:50px auto;padding:24px;line-height:1.55}h1{font-size:32px}li{margin:12px 0}textarea{box-sizing:border-box;width:100%;height:170px;background:#1a1f2b;color:white;border:1px solid #6e9bff;border-radius:12px;padding:14px}button{background:#6e9bff;color:#06122b;border:0;border-radius:24px;padding:14px 24px;font-size:17px;margin-top:16px;cursor:pointer}a{color:#9db7ff}.muted{color:#adb8ca}#status{white-space:pre-wrap}
</style></head><body><h1>Connect your Music home</h1>
<p>This page runs only on your computer. Session headers go directly to your paired TV and are encrypted there. They are never saved on this computer or sent to chat.</p>
<ol><li>Open <a href="https://music.youtube.com" target="_blank" rel="noreferrer">YouTube Music</a> and switch to <b>Blanktarget Records</b>.</li>
<li>Press <b>Ctrl+Shift+I</b>, choose <b>Network</b>, and filter for <b>browse</b>. Click Music's Library or Home to generate a request.</li>
<li>Choose a successful <b>browse</b> request, open <b>Headers</b>, then copy the complete <b>Request headers</b> section, including <b>cookie</b>. Firefox also offers right-click → Copy → Copy request headers.</li>
<li>Paste those headers below and connect. Do not paste them into chat: they grant access to your signed-in session.</li></ol>
<textarea id="headers" aria-label="Request headers" autocomplete="off" autocapitalize="off" spellcheck="false" placeholder="Paste request headers here"></textarea>
<button id="connect">Connect to TV</button><p id="status" role="status"></p>
<p class="muted">If a field is missing, confirm you copied Request headers rather than Response headers. Keep your browser session signed in; logging out can expire the TV connection.</p>
<script nonce="NONCE">
const button=document.getElementById('connect'),field=document.getElementById('headers'),status=document.getElementById('status');
button.onclick=async()=>{button.disabled=true;status.textContent='Checking the Music profile and loading categories on your TV…';try{const response=await fetch(location.pathname,{method:'POST',headers:{'Content-Type':'text/plain;charset=UTF-8'},body:field.value});const result=await response.json();status.textContent=result.message;if(result.ok){field.value='';field.remove();button.remove();}else button.disabled=false;}catch(e){status.textContent='Connection stopped. Ask Codex to restart this local setup page.';button.disabled=false;}};
</script></body></html>
'@
$html = $html.Replace('NONCE', $nonce)
Write-Output "MUSIC_SETUP_URL=$origin$route"
try {
    while (-not $connected -and [DateTime]::UtcNow -lt $deadline) {
        if (-not $listener.Pending()) { Start-Sleep -Milliseconds 200; continue }
        $client = $listener.AcceptTcpClient()
        try {
            $client.ReceiveTimeout = 10000
            $stream = $client.GetStream()
            $header = [Collections.Generic.List[byte]]::new()
            while ($header.Count -lt 8192) {
                $next = $stream.ReadByte()
                if ($next -lt 0) { throw 'Incomplete HTTP request.' }
                $header.Add([byte]$next)
                $count = $header.Count
                if ($count -ge 4 -and $header[$count-4] -eq 13 -and $header[$count-3] -eq 10 -and $header[$count-2] -eq 13 -and $header[$count-1] -eq 10) { break }
            }
            $lines = [Text.Encoding]::ASCII.GetString($header.ToArray()).Split("`r`n")
            $request = $lines[0].Split(' ')
            $requestHeaders = @{}
            foreach ($line in $lines | Select-Object -Skip 1) {
                $separator = $line.IndexOf(':')
                if ($separator -gt 0) { $requestHeaders[$line.Substring(0,$separator).Trim().ToLowerInvariant()] = $line.Substring($separator+1).Trim() }
            }
            $code = '200 OK'; $type = 'text/html; charset=utf-8'; $body = $html
            if ($request.Count -ne 3 -or $request[1] -ne $route -or $requestHeaders['host'] -ne "127.0.0.1:$port") {
                $code = '404 Not Found'; $body = 'Not found.'
            } elseif ($request[0] -eq 'POST') {
                $type = 'application/json; charset=utf-8'
                $length = 0
                $validLength = [int]::TryParse($requestHeaders['content-length'], [ref]$length)
                $bytes = $null
                if ($validLength -and $length -gt 0 -and $length -le 32768 -and -not $requestHeaders.ContainsKey('transfer-encoding')) {
                    $bytes = [byte[]]::new($length)
                    $offset = 0
                    while ($offset -lt $length) {
                        $read = $stream.Read($bytes,$offset,$length-$offset)
                        if ($read -le 0) { throw 'Incomplete request body.' }
                        $offset += $read
                    }
                }
                if ($requestHeaders['origin'] -ne $origin -or -not $validLength -or $length -lt 1 -or $length -gt 32768 -or $requestHeaders.ContainsKey('transfer-encoding')) {
                    $code='400 Bad Request'; $body='{"ok":false,"message":"Paste a complete request-header section, under 32 KB."}'
                    if ($null -ne $bytes) { [Array]::Clear($bytes,0,$bytes.Length) }
                } else {
                    try {
                        $raw = [Text.Encoding]::UTF8.GetString($bytes)
                        $byteCount=[Text.Encoding]::UTF8.GetByteCount($raw)
                        [void](Invoke-PrivateAdb @('shell','-T','run-as','dev.drewett.vista','dd','of=files/music-browser-import.txt','bs=1',"count=$byteCount",'status=none') $raw)
                        [void](Invoke-PrivateAdb @('shell','run-as','dev.drewett.vista','chmod','600','files/music-browser-import.txt'))
                        $result = Invoke-PrivateAdb @('shell','content','call','--uri','content://dev.drewett.vista.debug.immich','--method','musicBrowser')
                        if ($result -notmatch 'browserConnected=true') { throw 'Browser session was not accepted.' }
                        $body='{"ok":true,"message":"Connected. Your Music categories are ready on the TV. You can close this page."}'
                        $connected=$true
                        Write-Output 'MUSIC_BROWSER_CONNECTED=true'
                    } catch {
                        $body='{"ok":false,"message":"The TV could not verify this session. Confirm Blanktarget Records is selected, copy the full Request headers including cookie, and try again."}'
                        Write-Output 'MUSIC_BROWSER_CONNECTED=false'
                    } finally {
                        $raw=$null; $result=$null; [Array]::Clear($bytes,0,$bytes.Length)
                        try { [void](Invoke-PrivateAdb @('shell','run-as','dev.drewett.vista','rm','-f','files/music-browser-import.txt')) } catch { }
                    }
                }
            } elseif ($request[0] -ne 'GET') {
                $code='405 Method Not Allowed'; $body='Method not allowed.'
            }
            $content = [Text.Encoding]::UTF8.GetBytes($body)
            $policy="default-src 'none'; script-src 'nonce-$nonce'; style-src 'nonce-$nonce'; connect-src 'self'; frame-ancestors 'none'; form-action 'self'"
            $response=[Text.Encoding]::ASCII.GetBytes("HTTP/1.1 $code`r`nContent-Type: $type`r`nContent-Length: $($content.Length)`r`nCache-Control: no-store`r`nContent-Security-Policy: $policy`r`nReferrer-Policy: no-referrer`r`nX-Content-Type-Options: nosniff`r`nConnection: close`r`n`r`n")
            $stream.Write($response,0,$response.Length);$stream.Write($content,0,$content.Length);$stream.Flush()
        } catch {
            # Never include request data or child-process errors in logs.
            Write-Output ("SETUP_REQUEST_ERROR="+$_.Exception.GetType().Name+" line="+$_.InvocationInfo.ScriptLineNumber)
        } finally { $client.Dispose() }
    }
} finally { $listener.Stop() }
