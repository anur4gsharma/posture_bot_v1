# Download MediaPipe pose_landmarker_full.task to app/src/main/assets/
$url = "https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_full/float16/latest/pose_landmarker_full.task"
$outDir = "app\src\main\assets"
$outFile = "$outDir\pose_landmarker_full.task"

if (-not (Test-Path $outDir)) {
    New-Item -ItemType Directory -Path $outDir -Force
}

Write-Host "Downloading pose_landmarker_full.task..."
Invoke-WebRequest -Uri $url -OutFile $outFile -UseBasicParsing
Write-Host "Saved to $outFile"
