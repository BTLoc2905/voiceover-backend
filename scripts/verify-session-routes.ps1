param(
    [string]$BaseUrl = 'http://localhost:8080',
    [long]$SessionId = 0,
    [long]$RouteId = 0,
    [long[]]$ExpectedPoiIds = @(),
    [switch]$CheckPersisted
)

$ErrorActionPreference = 'Stop'
$BaseUrl = $BaseUrl.TrimEnd('/')

function Invoke-RouteApi {
    param([string]$Method, [string]$Path, $Body = $null, [int]$ExpectedStatus = 200)
    $parameters = @{ Uri = "$BaseUrl/api$Path"; Method = $Method; UseBasicParsing = $true }
    if ($null -ne $Body) {
        $parameters.ContentType = 'application/json; charset=utf-8'
        $parameters.Body = [System.Text.Encoding]::UTF8.GetBytes((ConvertTo-Json -InputObject $Body -Depth 12 -Compress))
    }
    try {
        $response = Invoke-WebRequest @parameters
        $actualStatus = [int]$response.StatusCode
        $content = [System.Text.Encoding]::UTF8.GetString($response.RawContentStream.ToArray())
    } catch {
        if ($null -eq $_.Exception.Response) { throw }
        $actualStatus = [int]$_.Exception.Response.StatusCode
        $content = $_.ErrorDetails.Message
        if ([string]::IsNullOrWhiteSpace($content)) {
            $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
            try { $content = $reader.ReadToEnd() } finally { $reader.Dispose() }
        }
    }
    if ($actualStatus -ne $ExpectedStatus) { throw "$Method $Path expected HTTP $ExpectedStatus, received $actualStatus : $content" }
    if ($content) {
        # Returning the decoded variable enumerates JSON arrays correctly in Windows PowerShell 5.
        $decoded = ConvertFrom-Json -InputObject $content
        return $decoded
    }
}

function Assert-RouteRule([bool]$Condition, [string]$Message) {
    if (!$Condition) { throw "FAIL: $Message" }
    Write-Output "PASS: $Message"
}

if ($CheckPersisted) {
    if (!$SessionId -or !$RouteId -or $ExpectedPoiIds.Count -lt 2) { throw 'Provide SessionId, RouteId and ExpectedPoiIds from the first verification run.' }
    $rows = @(Invoke-RouteApi GET '/admin/sessions')
    $persisted = $rows | Where-Object id -EQ $SessionId
    $persistedRoute = $persisted.routes | Where-Object id -EQ $RouteId
    Assert-RouteRule ($null -ne $persisted -and $persisted.status -eq 'PUBLISHED') 'Session survives server restart'
    Assert-RouteRule ($null -ne $persistedRoute -and $persistedRoute.status -eq 'PUBLISHED') 'Route survives server restart'
    Assert-RouteRule (($persistedRoute.poiIds -join ',') -eq ($ExpectedPoiIds -join ',')) 'Exact POI order survives server restart'
    $visible = @(Invoke-RouteApi GET '/public/routes')
    Assert-RouteRule (@($visible | Where-Object id -EQ $RouteId).Count -eq 1) 'Persisted route remains visible to visitors'
    $started = Invoke-RouteApi POST "/public/routes/$RouteId/start" @{}
    Assert-RouteRule ($started.travelMode -eq 'WALKING' -and $started.geometry.type -eq 'LineString') 'Visitor can start persisted route after restart'
    return
}

$catalog = @(Invoke-RouteApi GET '/public/pois')
if ($catalog.Count -lt 2) { throw 'Need at least two existing published POIs. This script does not create or overwrite POIs.' }
$ids = @([long]$catalog[1].id, [long]$catalog[0].id)
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$sessionName = "Acceptance Session $stamp"
$routeName = "Acceptance Route $stamp"
$session = Invoke-RouteApi POST '/admin/sessions' @{ name = ''; description = 'Session/Routes acceptance'; status = 'DRAFT' }
$SessionId = [long]$session.id
Assert-RouteRule ($session.status -eq 'DRAFT') 'Incomplete Session draft saved'
$null = Invoke-RouteApi PUT "/admin/sessions/$SessionId" @{name=$sessionName; status='PUBLISHED'} 400
Assert-RouteRule $true 'Session cannot publish without a published Route'
$route = Invoke-RouteApi POST "/admin/sessions/$SessionId/routes" @{ name=''; status='DRAFT'; poiIds=@() }
$RouteId = [long]$route.id
Assert-RouteRule ($route.stopCount -eq 0) 'Empty Route draft saved'
$routePath = "/admin/sessions/$SessionId/routes/$RouteId"
$route = Invoke-RouteApi PUT $routePath @{name=$routeName; status='DRAFT'; poiIds=@($ids[0])}
Assert-RouteRule ($route.stopCount -eq 1) 'One-stop Route draft saved'
$null = Invoke-RouteApi PUT $routePath @{name=$routeName; status='PUBLISHED'; poiIds=@($ids[0])} 400
$null = Invoke-RouteApi PUT $routePath @{name=$routeName; status='PUBLISHED'; poiIds=@($ids[0],$ids[0])} 400
Assert-RouteRule $true 'Publication rejects one stop and duplicates'
$eleven = @(1..11 | ForEach-Object { $ids[0] })
$route = Invoke-RouteApi PUT $routePath @{name=$routeName; status='DRAFT'; poiIds=$eleven}
Assert-RouteRule ($route.stopCount -eq 11) 'Over-ten draft saved with migration V12'
$null = Invoke-RouteApi PUT $routePath @{name=$routeName; status='PUBLISHED'; poiIds=$eleven} 400
$route = Invoke-RouteApi PUT $routePath @{name=$routeName; status='PUBLISHED'; poiIds=$ids}
Assert-RouteRule (($route.poiIds -join ',') -eq ($ids -join ',')) 'Published Route saves exact order'
$unpublished = Invoke-RouteApi POST "/admin/sessions/$SessionId/routes" @{name='Unfinished child';status='DRAFT';poiIds=@()}
$visible = @(Invoke-RouteApi GET '/public/routes')
Assert-RouteRule (@($visible | Where-Object id -EQ $RouteId).Count -eq 0) 'Published Route is hidden while parent Session is draft'
$session = Invoke-RouteApi PUT "/admin/sessions/$SessionId" @{name=$sessionName; description='Session/Routes acceptance'; status='PUBLISHED'}
Assert-RouteRule (($session.routes | Where-Object id -EQ $unpublished.id).status -eq 'DRAFT') 'Publishing Session does not publish draft children'
$visible = @(Invoke-RouteApi GET '/public/routes')
Assert-RouteRule (@($visible | Where-Object id -EQ $RouteId).Count -eq 1) 'Visitor sees Route only when both are published'
$start = Invoke-RouteApi POST "/public/routes/$RouteId/start" @{}
Assert-RouteRule ($start.travelMode -eq 'WALKING' -and $start.geometry.type -eq 'LineString' -and $start.geometry.coordinates.Count -gt 1) 'UC04 start returns actual walking geometry'
Assert-RouteRule (($start.pois.id -join ',') -eq ($ids -join ',')) 'UC04 start preserves Route order'
$poi = $catalog | Where-Object id -EQ $ids[0] | Select-Object -First 1
foreach ($status in @('DRAFT', 'HIDDEN')) {
    $payload = $poi | Select-Object *
    $payload.status = $status
    $conflict = Invoke-RouteApi POST '/admin/pois' $payload 409
    Assert-RouteRule ($conflict.message -like "*$routeName*") "POI $status is blocked and identifies affected Route"
}
$conflict = Invoke-RouteApi DELETE "/admin/pois/$($ids[0])" $null 409
Assert-RouteRule ($conflict.message -like "*$routeName*") 'POI deletion blocked with affected Route name'
$null = Invoke-RouteApi PUT $routePath @{name=$routeName; status='DRAFT'; poiIds=$ids} 409
Assert-RouteRule $true 'Last published Route requires closing Session first'
$session = Invoke-RouteApi PUT "/admin/sessions/$SessionId" @{name=$sessionName;description='Session/Routes acceptance';status='DRAFT'}
Assert-RouteRule ($session.routes.Count -eq 2 -and ($session.routes | Where-Object id -EQ $RouteId).status -eq 'PUBLISHED') 'Closing Session preserves Route data and status'
$visible = @(Invoke-RouteApi GET '/public/routes')
Assert-RouteRule (@($visible | Where-Object id -EQ $RouteId).Count -eq 0) 'Closed Session hides Route'
$null = Invoke-RouteApi POST "/public/routes/$RouteId/start" @{} 409
Assert-RouteRule $true 'Stale visitor cannot start a closed Route'
$null = Invoke-RouteApi PUT "/admin/sessions/$SessionId" @{name=$sessionName;description='Session/Routes acceptance';status='PUBLISHED'}
$readback = @(Invoke-RouteApi GET '/admin/sessions') | Where-Object id -EQ $SessionId
Assert-RouteRule ((($readback.routes | Where-Object id -EQ $RouteId).poiIds -join ',') -eq ($ids -join ',')) 'Fresh database read preserves exact order'
Write-Output "Acceptance data retained: SessionId=$SessionId RouteId=$RouteId POIs=$($ids -join ',')"
Write-Output "After restarting backend, run:"
Write-Output ".\scripts\verify-session-routes.ps1 -CheckPersisted -SessionId $SessionId -RouteId $RouteId -ExpectedPoiIds $($ids -join ',')"
