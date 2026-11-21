param(
    [string] $BaseUrl = "https://backend-unipod.onrender.com/api",
    [string] $Department = "CNC",
    [ValidateRange(1, 180)][int] $TimeoutSeconds = 45,
    [switch] $CreateSubmitter,
    [switch] $RunReversibleWriteChecks
)

$ErrorActionPreference = "Stop"
$BaseUrl = $BaseUrl.TrimEnd("/")
Add-Type -AssemblyName System.Net.Http
$HttpClient = [System.Net.Http.HttpClient]::new()
$HttpClient.Timeout = [TimeSpan]::FromSeconds($TimeoutSeconds)
$Results = [System.Collections.Generic.List[object]]::new()
$script:ServiceUnavailable = $false
$SmokeAccountCreated = $false
$SmokeAccountDeactivated = $false

$EndpointInventory = @(
    @{ Method = "GET"; Path = "/services/catalogue"; Access = "Public"; Request = "None"; Success = "200 grouped catalogue object" },
    @{ Method = "POST"; Path = "/services/catalogue"; Access = "Administrator, Innovation Coordinator"; Request = "department, name, description, price, currency, turnaroundEstimate, whatsappGroupUrl, requiresBrief, active, sortOrder"; Success = "201 catalogue item" },
    @{ Method = "GET"; Path = "/facilities"; Access = "Authenticated facility roles"; Request = "None"; Success = "200 facility array" },
    @{ Method = "POST"; Path = "/facilities"; Access = "Administrator, Innovation Coordinator, Department Coordinator, Sector Officer"; Request = "name, location, capacity, status, department, requiresSafetyPass"; Success = "201 facility" },
    @{ Method = "PATCH"; Path = "/facilities/{id}/status"; Access = "Facility managers"; Request = "status"; Success = "200 facility" },
    @{ Method = "PATCH"; Path = "/facilities/{id}/safety-pass"; Access = "Facility managers"; Request = "requiresSafetyPass"; Success = "200 facility" },
    @{ Method = "GET"; Path = "/bookings"; Access = "Administrator, Innovation Coordinator, Department Coordinator, Receptionist, Sector Officer, Finance Officer, Observer"; Request = "page, size, status, date query parameters"; Success = "200 paginated booking object" },
    @{ Method = "POST"; Path = "/bookings"; Access = "Authenticated booking roles"; Request = "facilityId, purpose, startsAt, endsAt, attendees, operator, requestType, isEquipmentRequest, equipmentName, purposeDetail"; Success = "201 booking" },
    @{ Method = "GET"; Path = "/bookings/{id}"; Access = "Booking staff"; Request = "Booking UUID"; Success = "200 booking" },
    @{ Method = "POST"; Path = "/bookings/{id}/approve"; Access = "Booking managers"; Request = "Booking UUID"; Success = "200 booking" },
    @{ Method = "POST"; Path = "/bookings/{id}/reject"; Access = "Booking managers"; Request = "reason"; Success = "200 booking" },
    @{ Method = "POST"; Path = "/bookings/{id}/reschedule"; Access = "Booking managers"; Request = "startsAt, endsAt"; Success = "200 booking" },
    @{ Method = "POST"; Path = "/bookings/{id}/check-in"; Access = "Booking managers"; Request = "Booking UUID"; Success = "200 booking" },
    @{ Method = "POST"; Path = "/bookings/{id}/check-out"; Access = "Booking managers"; Request = "Booking UUID"; Success = "200 booking" },
    @{ Method = "POST"; Path = "/allocations/{id}/decision"; Access = "Booking managers"; Request = "decision, note"; Success = "200 booking" },
    @{ Method = "POST"; Path = "/allocations/{id}/return"; Access = "Booking managers"; Request = "returned_at, condition_returned, notes"; Success = "200 booking" },
    @{ Method = "GET"; Path = "/timetable/blocks"; Access = "Authenticated timetable roles"; Request = "from and to required; facility_id optional"; Success = "200 timetable block array" },
    @{ Method = "POST"; Path = "/timetable/blocks"; Access = "Administrator, Innovation Coordinator, Department Coordinator"; Request = "facility_id, department, title, starts_at, ends_at, recurrence_rule, recurrence_until, source"; Success = "201 timetable block array" },
    @{ Method = "DELETE"; Path = "/timetable/blocks/{id}"; Access = "Timetable managers"; Request = "Block UUID"; Success = "204 empty response" },
    @{ Method = "POST"; Path = "/departments/{dept}/orders"; Access = "Authenticated"; Request = "title, specs, quantity, date_needed, accepted_rules_version"; Success = "201 department order" },
    @{ Method = "GET"; Path = "/departments/{dept}/orders"; Access = "Operations staff"; Request = "page, size query parameters"; Success = "200 kanban/order object" },
    @{ Method = "POST"; Path = "/departments/{dept}/orders/{id}/quote"; Access = "Operations and Finance staff"; Request = "quote object"; Success = "200 department order" },
    @{ Method = "PATCH"; Path = "/departments/{dept}/orders/{id}/stage"; Access = "Operations managers"; Request = "stage, materials_used, machine_time_used, quality_checklist, collection_signature, notes"; Success = "200 department order" },
    @{ Method = "POST"; Path = "/media/requests"; Access = "Administrator, Innovation Coordinator, Department Coordinator, Sector Officer"; Request = "briefId or departmentOrderId, submitterId, title"; Success = "201 media request" },
    @{ Method = "GET"; Path = "/media/requests"; Access = "Authenticated"; Request = "status, briefId query parameters"; Success = "200 media request array" },
    @{ Method = "GET"; Path = "/media/requests/{id}"; Access = "Authenticated, owner or media staff"; Request = "Media request UUID"; Success = "200 media request" },
    @{ Method = "POST"; Path = "/media/requests/{id}/deliver"; Access = "Media staff"; Request = "delivery_links, file_ids, note_to_submitter"; Success = "200 media request" },
    @{ Method = "POST"; Path = "/media/requests/{id}/complete"; Access = "Administrator, Innovation Coordinator"; Request = "Media request UUID"; Success = "200 media request" },
    @{ Method = "GET"; Path = "/media/contracts"; Access = "Operations and Finance staff"; Request = "None"; Success = "200 media contract array" },
    @{ Method = "POST"; Path = "/media/contracts"; Access = "Operations and Finance staff (invoice generation is Finance-only)"; Request = "client, event_name, event_date, venue, agreed_price, currency, customer_email, milestone_payment_dates, signed_contract_doc_id, assigned_staff, generate_invoices"; Success = "201 media contract" },
    @{ Method = "PATCH"; Path = "/media/contracts/{id}/status"; Access = "Operations staff"; Request = "status, signed_contract_doc_id when signing"; Success = "200 media contract" },
    @{ Method = "GET"; Path = "/operations/training/overview"; Access = "Authenticated staff roles"; Request = "departmentId query parameter"; Success = "200 training overview" },
    @{ Method = "POST"; Path = "/operations/training/sessions"; Access = "Administrator, Department Coordinator"; Request = "title, facilitator, date, endsAt, capacity, venue, description, departmentId, fee, safetyCourse, publish"; Success = "201 training session" },
    @{ Method = "GET"; Path = "/operations/training/sessions/{id}"; Access = "Authenticated staff roles"; Request = "Session UUID"; Success = "200 session with participants" },
    @{ Method = "POST"; Path = "/operations/training/sessions/{id}/participants"; Access = "Administrator, Department Coordinator"; Request = "name, regNumber, department, contactEmail"; Success = "201 training session" },
    @{ Method = "DELETE"; Path = "/operations/training/sessions/{id}/participants/{participantId}"; Access = "Administrator, Department Coordinator"; Request = "Session and participant UUIDs"; Success = "204 empty response" },
    @{ Method = "PATCH"; Path = "/operations/training/sessions/{id}/attendance"; Access = "Administrator, Department Coordinator"; Request = "participantId, attendance"; Success = "200 training session" },
    @{ Method = "POST"; Path = "/operations/training/sessions/{id}/participants/{participantId}/confirm-payment"; Access = "Administrator, Department Coordinator"; Request = "Session and participant UUIDs"; Success = "200 training session" },
    @{ Method = "POST"; Path = "/operations/training/sessions/{id}/participants/{participantId}/pass"; Access = "Administrator, Department Coordinator"; Request = "Session and participant UUIDs"; Success = "200 training session" },
    @{ Method = "POST"; Path = "/operations/training/sessions/{id}/attendance/pass"; Access = "Administrator, Department Coordinator"; Request = "userIds UUID array"; Success = "200 bulk pass result" },
    @{ Method = "POST"; Path = "/operations/training/mentorship"; Access = "Administrator, Department Coordinator"; Request = "title, mentee, mentor, scheduledDate, focus, priority, notes"; Success = "201 follow-up" },
    @{ Method = "PATCH"; Path = "/operations/training/mentorship/{id}"; Access = "Administrator, Department Coordinator"; Request = "status, notes, version"; Success = "200 follow-up" }
)

function Invoke-ApiRequest {
    param(
        [Parameter(Mandatory = $true)][string] $Method,
        [Parameter(Mandatory = $true)][string] $Path,
        [string] $Token,
        [object] $Body
    )

    $uri = "{0}/{1}" -f $BaseUrl, $Path.TrimStart("/")
    $request = [System.Net.Http.HttpRequestMessage]::new(
        [System.Net.Http.HttpMethod]::new($Method),
        $uri
    )
    if ($Token) {
        $request.Headers.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new("Bearer", $Token)
    }
    if ($null -ne $Body) {
        $json = ConvertTo-Json -InputObject $Body -Compress -Depth 20
        $request.Content = [System.Net.Http.StringContent]::new(
            $json,
            [System.Text.Encoding]::UTF8,
            "application/json"
        )
    }

    $response = $null
    try {
        $response = $HttpClient.SendAsync($request).GetAwaiter().GetResult()
        $raw = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        return [pscustomobject]@{
            StatusCode = [int] $response.StatusCode
            ContentType = [string] $response.Content.Headers.ContentType
            Raw = $raw
        }
    }
    catch [System.Management.Automation.MethodInvocationException] {
        if ($_.Exception.InnerException -is [System.Threading.Tasks.TaskCanceledException] -or $_.Exception.InnerException -is [System.Net.Http.HttpRequestException]) {
            return [pscustomobject]@{ StatusCode = 0; ContentType = ""; Raw = "" }
        }
        throw
    }
    catch [System.Net.Http.HttpRequestException] {
        return [pscustomobject]@{ StatusCode = 0; ContentType = ""; Raw = "" }
    }
    finally {
        $request.Dispose()
        if ($null -ne $response) {
            $response.Dispose()
        }
    }
}

function Add-Result {
    param(
        [string] $Name,
        [int] $ExpectedStatus,
        [int] $ActualStatus,
        [string] $ExpectedShape,
        [string] $Raw,
        [bool] $ContentCheck = $true
    )

    $shapeOk = $true
    if ($ExpectedShape -ne "none" -and $ActualStatus -ge 200 -and $ActualStatus -lt 300) {
        try {
            $parsed = ConvertFrom-Json -InputObject $Raw
            $jsonStart = $Raw.TrimStart()
            if ($ExpectedShape -eq "array") {
                $shapeOk = $jsonStart.StartsWith("[")
            }
            elseif ($ExpectedShape -eq "object") {
                $shapeOk = $jsonStart.StartsWith("{") -and $null -ne $parsed
            }
        }
        catch {
            $shapeOk = $false
        }
    }

    $passed = ($ActualStatus -eq $ExpectedStatus) -and $shapeOk -and $ContentCheck
    $Results.Add([pscustomobject]@{
        Name = $Name
        Passed = $passed
        Status = $ActualStatus
        Expected = $ExpectedStatus
        ShapeOk = $shapeOk
    }) | Out-Null

    $label = if ($passed) { "PASS" } else { "FAIL" }
    $detail = if (-not $shapeOk) { "; unexpected JSON shape" } elseif (-not $ContentCheck) { "; response content check failed" } else { "" }
    Write-Host ("{0} {1}: HTTP {2} (expected {3}){4}" -f $label, $Name, $ActualStatus, $ExpectedStatus, $detail)
}

function Invoke-JsonSmoke {
    param(
        [string] $Name,
        [string] $Path,
        [string] $Token,
        [string] $ExpectedShape = "object"
    )

    $response = Invoke-ApiRequest -Method "GET" -Path $Path -Token $Token
    if ($response.StatusCode -eq 0 -or $response.StatusCode -eq 503) {
        $script:ServiceUnavailable = $true
    }
    Add-Result -Name $Name -ExpectedStatus 200 -ActualStatus $response.StatusCode -ExpectedShape $ExpectedShape -Raw $response.Raw
}

Write-Host "Fidel operations endpoint inventory:"
$EndpointInventory | ForEach-Object {
    Write-Host ("  {0,-6} {1,-65} Request: {2}; Response: {3}; Access: {4}" -f $_.Method, $_.Path, $_.Request, $_.Success, $_.Access)
}

Write-Host "`nRender read-only endpoint checks:"
Invoke-JsonSmoke -Name "GET /services/catalogue (public)" -Path "/services/catalogue" -ExpectedShape "object"

$adminToken = $null
$adminEmail = $env:BOOTSTRAP_ADMIN_EMAIL
$adminPassword = $env:BOOTSTRAP_ADMIN_PASSWORD
if ($ServiceUnavailable) {
    Write-Host "Skipping further requests: Render returned HTTP 503 or did not respond before the timeout."
}
elseif ($adminEmail -and $adminPassword) {
    $login = Invoke-ApiRequest -Method "POST" -Path "/auth/login" -Body @{
        email = $adminEmail
        password = $adminPassword
    }
    if ($login.StatusCode -eq 0 -or $login.StatusCode -eq 503) {
        $ServiceUnavailable = $true
    }
    $loginJson = $null
    if ($login.Raw) {
        try { $loginJson = ConvertFrom-Json -InputObject $login.Raw } catch { $loginJson = $null }
    }
    if ($login.StatusCode -eq 200 -and $loginJson.accessToken) {
        $adminToken = [string] $loginJson.accessToken
        Add-Result -Name "POST /auth/login" -ExpectedStatus 200 -ActualStatus $login.StatusCode -ExpectedShape "none" -Raw ""
        Invoke-JsonSmoke -Name "GET /me (admin identity)" -Path "/me" -Token $adminToken
        Invoke-JsonSmoke -Name "GET /facilities" -Path "/facilities" -Token $adminToken -ExpectedShape "array"
        Invoke-JsonSmoke -Name "GET /bookings" -Path "/bookings?page=0&size=1" -Token $adminToken
        $from = [DateTime]::UtcNow.ToString("yyyy-MM-dd'T'HH:mm:ss'Z'")
        $to = [DateTime]::UtcNow.AddDays(7).ToString("yyyy-MM-dd'T'HH:mm:ss'Z'")
        $timetablePath = "/timetable/blocks?from={0}&to={1}" -f [uri]::EscapeDataString($from), [uri]::EscapeDataString($to)
        Invoke-JsonSmoke -Name "GET /timetable/blocks (7-day window)" -Path $timetablePath -Token $adminToken -ExpectedShape "array"
        Invoke-JsonSmoke -Name "GET /media/requests" -Path "/media/requests" -Token $adminToken -ExpectedShape "array"
        Invoke-JsonSmoke -Name "GET /media/contracts" -Path "/media/contracts" -Token $adminToken -ExpectedShape "array"
        Invoke-JsonSmoke -Name "GET /departments/{dept}/orders" -Path ("/departments/{0}/orders?page=0&size=1" -f [uri]::EscapeDataString($Department)) -Token $adminToken
        Invoke-JsonSmoke -Name "GET /operations/training/overview" -Path "/operations/training/overview" -Token $adminToken
    }
    else {
        Add-Result -Name "POST /auth/login" -ExpectedStatus 200 -ActualStatus $login.StatusCode -ExpectedShape "none" -Raw "" -ContentCheck $false
        Write-Host "Authenticated endpoint checks skipped because admin login did not return an access token."
    }
}
else {
    Write-Host "SKIP admin checks: set BOOTSTRAP_ADMIN_EMAIL and BOOTSTRAP_ADMIN_PASSWORD in the process environment."
}

if ($RunReversibleWriteChecks -and $adminToken -and -not $ServiceUnavailable) {
    $marker = "RENDER-SMOKE-" + [guid]::NewGuid().ToString("N")
    $startsAt = [DateTime]::UtcNow.AddDays(14)
    $endsAt = $startsAt.AddMinutes(30)
    $fromWindow = $startsAt.AddMinutes(-1).ToString("yyyy-MM-dd'T'HH:mm:ss'Z'")
    $toWindow = $endsAt.AddMinutes(1).ToString("yyyy-MM-dd'T'HH:mm:ss'Z'")
    $startsAtText = $startsAt.ToString("yyyy-MM-dd'T'HH:mm:ss'Z'")
    $endsAtText = $endsAt.ToString("yyyy-MM-dd'T'HH:mm:ss'Z'")
    $createdId = $null
    $deleted = $false

    Write-Host "`nRunning the reversible timetable create/read/delete check."
    try {
        $created = Invoke-ApiRequest -Method "POST" -Path "/timetable/blocks" -Token $adminToken -Body @{
            title = $marker
            starts_at = $startsAtText
            ends_at = $endsAtText
            source = "MANUAL"
        }
        $createdItems = @()
        if ($created.Raw) {
            try { $createdItems = @(ConvertFrom-Json -InputObject $created.Raw) } catch { $createdItems = @() }
        }
        $createdBlock = $createdItems | Where-Object { $_.title -eq $marker } | Select-Object -First 1
        if ($createdBlock -and $createdBlock.id) {
            $createdId = [string] $createdBlock.id
        }
        Add-Result -Name "POST /timetable/blocks (temporary marker)" -ExpectedStatus 201 -ActualStatus $created.StatusCode -ExpectedShape "array" -Raw $created.Raw -ContentCheck ($null -ne $createdId)

        if ($createdId) {
            $query = "?from={0}&to={1}" -f [uri]::EscapeDataString($fromWindow), [uri]::EscapeDataString($toWindow)
            $listed = Invoke-ApiRequest -Method "GET" -Path ("/timetable/blocks" + $query) -Token $adminToken
            $listedItems = @()
            if ($listed.Raw) {
                try { $listedItems = @(ConvertFrom-Json -InputObject $listed.Raw) } catch { $listedItems = @() }
            }
            $visible = @($listedItems | Where-Object { [string]$_.id -eq $createdId }).Count -gt 0
            Add-Result -Name "GET /timetable/blocks (find temporary marker)" -ExpectedStatus 200 -ActualStatus $listed.StatusCode -ExpectedShape "array" -Raw $listed.Raw -ContentCheck $visible

            $deletedResponse = Invoke-ApiRequest -Method "DELETE" -Path ("/timetable/blocks/{0}" -f $createdId) -Token $adminToken
            $deleted = $deletedResponse.StatusCode -eq 204
            Add-Result -Name "DELETE /timetable/blocks/{id} (cleanup)" -ExpectedStatus 204 -ActualStatus $deletedResponse.StatusCode -ExpectedShape "none" -Raw ""
        }
    }
    finally {
        if ($createdId -and -not $deleted) {
            $cleanup = Invoke-ApiRequest -Method "DELETE" -Path ("/timetable/blocks/{0}" -f $createdId) -Token $adminToken
            if ($cleanup.StatusCode -eq 204) {
                Write-Host "Cleanup retry succeeded for the temporary timetable block."
            }
            else {
                Write-Host ("WARNING: temporary timetable block cleanup returned HTTP {0}; block id {1}" -f $cleanup.StatusCode, $createdId)
            }
        }
    }
}

if ($CreateSubmitter -and -not $ServiceUnavailable -and -not $adminToken) {
    Add-Result -Name "CreateSubmitter cleanup prerequisite" -ExpectedStatus 200 -ActualStatus 0 -ExpectedShape "none" -Raw "" -ContentCheck $false
    Write-Host "SKIP -CreateSubmitter: admin credentials are required so the temporary account can be deactivated."
}
elseif ($CreateSubmitter -and -not $ServiceUnavailable) {
    Write-Host "Creating one PUBLIC smoke-test account; it is deactivated after the submitter checks."
    $signupEmail = "render-smoke-{0}@example.com" -f ([guid]::NewGuid().ToString("N"))
    $signupPassword = ([guid]::NewGuid().ToString("N") + "A1!")
    $signup = Invoke-ApiRequest -Method "POST" -Path "/auth/signup" -Body @{
        accountKind = "PUBLIC"
        fullName = "Render API smoke test"
        phone = "+256700000000"
        email = $signupEmail
        password = $signupPassword
        rulesAccepted = $true
        rulesVersion = "smoke-test"
    }
    if ($signup.StatusCode -eq 201) {
        $SmokeAccountCreated = $true
    }
    $signupJson = $null
    if ($signup.Raw) {
        try { $signupJson = ConvertFrom-Json -InputObject $signup.Raw } catch { $signupJson = $null }
    }
    if ($signup.StatusCode -eq 201 -and $signupJson.accessToken) {
        Add-Result -Name "POST /auth/signup" -ExpectedStatus 201 -ActualStatus $signup.StatusCode -ExpectedShape "none" -Raw ""
        Write-Host "PASS POST /auth/signup: HTTP 201 (temporary account and token omitted)"
        $submitterToken = [string] $signupJson.accessToken
        $submitterId = $null
        try {
            $identity = Invoke-ApiRequest -Method "GET" -Path "/me" -Token $submitterToken
            if ($identity.StatusCode -eq 0 -or $identity.StatusCode -eq 503) {
                $ServiceUnavailable = $true
            }
            $identityJson = $null
            if ($identity.Raw) {
                try { $identityJson = ConvertFrom-Json -InputObject $identity.Raw } catch { $identityJson = $null }
            }
            Add-Result -Name "GET /me (signup identity)" -ExpectedStatus 200 -ActualStatus $identity.StatusCode -ExpectedShape "object" -Raw $identity.Raw
            if ($identityJson -and $identityJson.id) {
                $submitterId = [string] $identityJson.id
            }
            Invoke-JsonSmoke -Name "GET /me/bookings" -Path "/me/bookings" -Token $submitterToken
        }
        finally {
            if (-not $submitterId) {
                Add-Result -Name "POST /users/{id}/deactivate (cleanup)" -ExpectedStatus 200 -ActualStatus 0 -ExpectedShape "none" -Raw "" -ContentCheck $false
                Write-Host "WARNING: temporary smoke-test account could not be identified for cleanup."
            }
            else {
                $deactivated = Invoke-ApiRequest -Method "POST" -Path ("/users/{0}/deactivate" -f $submitterId) -Token $adminToken
                Add-Result -Name "POST /users/{id}/deactivate (cleanup)" -ExpectedStatus 200 -ActualStatus $deactivated.StatusCode -ExpectedShape "none" -Raw "" -ContentCheck ($deactivated.StatusCode -eq 200)
                if ($deactivated.StatusCode -eq 200) {
                    $SmokeAccountDeactivated = $true
                    Write-Host "Deactivated the temporary smoke-test account."
                }
                else {
                    Write-Host ("WARNING: temporary smoke-test account deactivation returned HTTP {0}; account id {1}" -f $deactivated.StatusCode, $submitterId)
                }
            }
        }
    }
    else {
        Add-Result -Name "POST /auth/signup" -ExpectedStatus 201 -ActualStatus $signup.StatusCode -ExpectedShape "none" -Raw "" -ContentCheck $false
        Write-Host "Submitter-only checks skipped because signup did not return an access token."
    }
}

$HttpClient.Dispose()
$passedCount = @($Results | Where-Object { $_.Passed }).Count
$failedCount = @($Results | Where-Object { -not $_.Passed }).Count
Write-Host ("`nSummary: {0} passed, {1} failed, {2} executed." -f $passedCount, $failedCount, $Results.Count)
$writeSummary = if ($RunReversibleWriteChecks) { "The opt-in timetable create/read/delete test writes a temporary block" } else { "No timetable write test was run" }
if ($SmokeAccountCreated) {
    $accountSummary = if ($SmokeAccountDeactivated) { "the signup test account was deactivated" } else { "the signup test account was not deactivated and may remain active" }
    $writeSummary += "; " + $accountSummary
}
Write-Host ("{0}; other mutation routes are not exercised. {1} routes are listed above." -f $writeSummary, $EndpointInventory.Count)

if ($failedCount -gt 0) {
    exit 1
}
