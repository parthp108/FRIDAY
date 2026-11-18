param([string]$path)

$files = Get-ChildItem -Path $path -Recurse -Filter '*.kt'
foreach ($f in $files) {
    $content = Get-Content $f.FullName -Raw
    $newContent = $content -replace '\bR\.string\.', 'com.health.friday.R.string.' -replace '\bR\.array\.', 'com.health.friday.R.array.' -replace '\bR\.drawable\.', 'com.health.friday.R.drawable.' -replace '\bR\.mipmap\.', 'com.health.friday.R.mipmap.'
    Set-Content -Path $f.FullName -Value $newContent -NoNewline
}