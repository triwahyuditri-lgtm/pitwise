$bytes = [System.IO.File]::ReadAllBytes("d:\MyApplication2\assets\mes.pdf")
$text = [System.Text.Encoding]::GetEncoding("ISO-8859-1").GetString($bytes)

# Find ALL occurrences of /Measure
$idx = 0
$count = 0
while (($idx = $text.IndexOf("/Type/Measure", $idx)) -ge 0) {
    $count++
    $start = [Math]::Max(0, $idx - 20)
    $len = [Math]::Min(500, $text.Length - $start)
    $ctx = $text.Substring($start, $len)
    $ctx = $ctx -replace "[^\x20-\x7e]", "."
    Write-Output "=== MEASURE #$count at pos $idx ==="
    Write-Output $ctx
    Write-Output ""
    $idx += 10
}

# Find Viewport
$idx = $text.IndexOf("/VP[")
if ($idx -lt 0) { $idx = $text.IndexOf("/VP [") }
if ($idx -ge 0) {
    $start = [Math]::Max(0, $idx - 50)
    $len = [Math]::Min(400, $text.Length - $start)
    $ctx = $text.Substring($start, $len)
    $ctx = $ctx -replace "[^\x20-\x7e]", "."
    Write-Output "=== VP ==="
    Write-Output $ctx
    Write-Output ""
}

# Find MediaBox
$idx = $text.IndexOf("/MediaBox")
if ($idx -ge 0) {
    $start = [Math]::Max(0, $idx - 10)
    $len = [Math]::Min(200, $text.Length - $start)
    $ctx = $text.Substring($start, $len)
    $ctx = $ctx -replace "[^\x20-\x7e]", "."
    Write-Output "=== MEDIABOX ==="
    Write-Output $ctx
    Write-Output ""
}

# Find BBox
$idx = 0
$count = 0
while (($idx = $text.IndexOf("/BBox", $idx)) -ge 0) {
    $count++
    $start = [Math]::Max(0, $idx - 10)
    $len = [Math]::Min(150, $text.Length - $start)
    $ctx = $text.Substring($start, $len)
    $ctx = $ctx -replace "[^\x20-\x7e]", "."
    Write-Output "=== BBOX #$count at pos $idx ==="
    Write-Output $ctx
    Write-Output ""
    $idx += 5
    if ($count -ge 5) { break }
}

# PROJCS WKT
$idx = $text.IndexOf("/Type/PROJCS")
if ($idx -ge 0) {
    $start = [Math]::Max(0, $idx - 20)
    $len = [Math]::Min(500, $text.Length - $start)
    $ctx = $text.Substring($start, $len)
    $ctx = $ctx -replace "[^\x20-\x7e]", "."
    Write-Output "=== PROJCS ==="
    Write-Output $ctx
}
