param(
    [string]$BaseUrl = 'http://localhost:8081',
    [switch]$ConfirmIsolatedDatabase,
    [string]$AdminUsername,
    [SecureString]$AdminPassword
)
$ErrorActionPreference = 'Stop'
if (-not $ConfirmIsolatedDatabase) { throw 'Start the app against blug_forum_test and pass -ConfirmIsolatedDatabase.' }
if (-not $AdminUsername -or -not $AdminPassword) { throw 'Supply an administrator provisioned in the isolated test database.' }
$script:checks = 0

function Assert-Check($condition, $description) {
    if (-not $condition) { throw "Check failed: $description" }
    $script:checks++
}
function Invoke-Api($actor, [string]$method, [string]$path, $body, [int]$expected = 200, [bool]$sendCsrf = $true, [bool]$form = $false, [bool]$invalidCsrf = $false) {
    # Windows PowerShell stores supplied headers in WebRequestSession; clear the prior token.
    [void]$actor.Headers.Remove('X-CSRF-TOKEN')
    $headers = @{}
    if ($method -ne 'GET' -and $sendCsrf) {
        $csrfResponse = Invoke-WebRequest -UseBasicParsing -Uri "$BaseUrl/api/auth/csrf" -WebSession $actor -ErrorAction Stop
        $token = $csrfResponse.Content | ConvertFrom-Json
        $headers[$token.headerName] = $token.token
        if ($invalidCsrf) { $headers[$token.headerName] = 'invalid' }
    }
    $request = @{ Uri = "$BaseUrl$path"; Method = $method; WebSession = $actor; Headers = $headers; UseBasicParsing = $true; MaximumRedirection = 0; ErrorAction = 'Stop' }
    if ($null -ne $body) {
        if ($form) { $request.Body = $body; $request.ContentType = 'application/x-www-form-urlencoded' }
        else { $request.Body = ($body | ConvertTo-Json -Depth 5 -Compress); $request.ContentType = 'application/json' }
    }
    $responseText = ''
    try {
        $response = Invoke-WebRequest @request; $status = [int]$response.StatusCode
        if ($response.Content -is [byte[]]) { $responseText = [System.Text.Encoding]::UTF8.GetString($response.Content) }
        else { $responseText = [string]$response.Content }
    }
    catch {
        if ($null -eq $_.Exception.Response) { throw }
        $status = [int]$_.Exception.Response.StatusCode
    }
    Assert-Check ($status -eq $expected) "$method $path expected $expected, got $status"
    Assert-Check ($responseText -notmatch '"password"|\$2[aby]\$') "No password or hash in $path"
    if ($responseText -and $responseText.TrimStart().StartsWith('{')) { return ($responseText | ConvertFrom-Json) }
    if ($responseText -and $responseText.TrimStart().StartsWith('[')) { return ,($responseText | ConvertFrom-Json) }
}
function New-Member([string]$name) {
    $actor = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    $null = Invoke-Api $actor POST '/api/auth/register' @{username=$name; email="$name@example.test"; password='synthetic-test-password'; role='ADMIN'} 201
    $null = Invoke-Api $actor POST '/api/auth/login' @{username=$name; password='synthetic-test-password'} 200 $true $true
    $profile = Invoke-Api $actor GET '/api/users/me' $null
    Assert-Check ($profile.role -eq 'USER') 'Registration cannot create ADMIN'
    return @{ Session=$actor; Profile=$profile }
}

$anonymous = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$null = Invoke-Api $anonymous POST '/api/auth/login' @{username='missing-synthetic-user';password='wrong-synthetic-password'} 401 $true $true
$null = Invoke-Api $anonymous POST '/api/auth/login' @{username='missing-synthetic-user';password='wrong-synthetic-password'} 403 $false $true
$suffix = [Guid]::NewGuid().ToString('N')
$owner = New-Member "owner$suffix"
$moderator = New-Member "moderator$suffix"
$participant = New-Member "participant$suffix"
$admin = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$taskPassword = [System.Net.NetworkCredential]::new('', $AdminPassword).Password
$null = Invoke-Api $admin POST '/api/auth/login' @{username=$AdminUsername; password=$taskPassword} 200 $true $true
$taskPassword = $null
$forum = Invoke-Api $owner.Session POST '/api/forums' @{name="HTTP smoke $suffix"} 201
$public = Invoke-Api $owner.Session POST "/api/forums/$($forum.id)/threads" @{title='Public synthetic'; content='Public body'; isPublic=$true} 201
$private = Invoke-Api $owner.Session POST "/api/forums/$($forum.id)/threads" @{title='Private metadata'; content='Private synthetic body'; isPublic=$false} 201
$post = Invoke-Api $owner.Session POST "/api/threads/$($public.id)/posts" @{content='Public synthetic post'} 201
$null = Invoke-Api $anonymous GET "/api/threads/$($public.id)" $null
$null = Invoke-Api $anonymous GET "/api/posts/$($post.id)" $null
$null = Invoke-Api $anonymous GET "/api/threads/$($private.id)" $null 404
$null = Invoke-Api $participant.Session GET "/api/threads/$($private.id)" $null 404
$summaries = Invoke-Api $anonymous GET "/api/forums/$($forum.id)/threads" $null
Assert-Check (($summaries | ConvertTo-Json -Depth 5) -notmatch 'Private synthetic body|ownerId|participants|email') 'Private list excludes content and relationships'
$null = Invoke-Api $owner.Session POST '/api/forums' @{name='CSRF rejected'} 403 $false
$null = Invoke-Api $owner.Session POST '/api/forums' @{name='Invalid CSRF rejected'} 403 $true $false $true
$null = Invoke-Api $anonymous POST '/api/forums' @{name='Anonymous rejected'} 401
$null = Invoke-Api $owner.Session POST "/api/threads/$($private.id)/moderators/add" @{id=$moderator.Profile.id}
$null = Invoke-Api $moderator.Session POST "/api/threads/$($private.id)/participants/add" @{id=$participant.Profile.id}
$privatePost = Invoke-Api $participant.Session POST "/api/threads/$($private.id)/posts" @{content='Participant synthetic post'} 201
$null = Invoke-Api $participant.Session GET "/api/threads/$($private.id)/posts" $null
$null = Invoke-Api $moderator.Session POST "/api/posts/$($post.id)/block" $null 403
$null = Invoke-Api $moderator.Session POST "/api/posts/$($privatePost.id)/block" $null 204
$null = Invoke-Api $participant.Session GET "/api/posts/$($privatePost.id)" $null 404
$null = Invoke-Api $admin POST "/api/admin/posts/$($privatePost.id)/restore" $null
$null = Invoke-Api $participant.Session GET "/api/posts/$($privatePost.id)" $null
$null = Invoke-Api $moderator.Session POST "/api/threads/$($private.id)/participants/remove" @{id=$participant.Profile.id}
$null = Invoke-Api $participant.Session GET "/api/threads/$($private.id)" $null 404
$null = Invoke-Api $owner.Session POST "/api/threads/$($private.id)/moderators/remove" @{id=$moderator.Profile.id}
$null = Invoke-Api $moderator.Session GET "/api/threads/$($private.id)" $null 404
$null = Invoke-Api $owner.Session PUT "/api/threads/$($private.id)/visibility" @{isPublic=$true}
$null = Invoke-Api $anonymous GET "/api/threads/$($private.id)" $null
$null = Invoke-Api $owner.Session POST "/api/threads/$($private.id)/transfer-ownership" @{id=$participant.Profile.id}
$null = Invoke-Api $owner.Session PUT "/api/threads/$($private.id)" @{title='Former owner cannot edit'} 403
$null = Invoke-Api $participant.Session PUT "/api/threads/$($private.id)" @{title='Transferred synthetic'; content='Edited body'}
$null = Invoke-Api $participant.Session PUT '/api/users/me' @{email="updated$suffix@example.test"; role='ADMIN'}
$null = Invoke-Api $participant.Session POST "/api/admin/forums/$($forum.id)/block" $null 403
$null = Invoke-Api $admin POST "/api/admin/users/$($participant.Profile.id)/block" $null
$null = Invoke-Api $participant.Session POST '/api/forums' @{name='Blocked session'} 403
$null = Invoke-Api $admin POST "/api/admin/users/$($participant.Profile.id)/restore" $null
$null = Invoke-Api $participant.Session GET '/api/users/me' $null
$null = Invoke-Api $admin POST "/api/admin/forums/$($forum.id)/block" $null
$null = Invoke-Api $anonymous GET "/api/posts/$($post.id)" $null 404
$null = Invoke-Api $admin POST "/api/admin/forums/$($forum.id)/restore" $null
$null = Invoke-Api $anonymous GET "/api/posts/$($post.id)" $null
$null = Invoke-Api $participant.Session DELETE "/api/posts/$($post.id)" $null 403
$null = Invoke-Api $owner.Session PUT "/api/posts/$($post.id)" @{content='Edited synthetic post'}
$null = Invoke-Api $owner.Session DELETE "/api/posts/$($post.id)" $null 204
$null = Invoke-Api $anonymous GET "/api/posts/$($post.id)" $null 404
$null = Invoke-Api $admin POST "/api/admin/posts/$($post.id)/restore" $null 409
$null = Invoke-Api $moderator.Session DELETE '/api/users/me' $null 204
$null = Invoke-Api $owner.Session POST '/api/auth/logout' $null 204
$null = Invoke-Api $owner.Session GET '/api/users/me' $null 401
$result = @{ passedChecks=$script:checks; forumId=$forum.id; publicThreadId=$public.id; privateThreadId=$private.id; privatePostId=$privatePost.id; executedAt=(Get-Date).ToString('o') }
$result | ConvertTo-Json | Set-Content -Encoding UTF8 smoke-result.json
Write-Output "HTTP smoke verification passed: $($script:checks) checks. Synthetic data retained only in the isolated database for restore verification."
