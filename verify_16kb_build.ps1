# PowerShell script to verify 16KB page size support in MobiGPT build
# Usage: .\verify_16kb_build.ps1

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "MobiGPT 16KB Page Size Build Verification" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$ErrorActionPreference = "Continue"
$buildSuccess = $true

# Function to check if a file contains a string
function Test-FileContains {
    param($FilePath, $SearchString, $Description)
    
    if (Test-Path $FilePath) {
        $content = Get-Content $FilePath -Raw
        if ($content -match $SearchString) {
            Write-Host "✓ $Description" -ForegroundColor Green
            return $true
        } else {
            Write-Host "✗ $Description" -ForegroundColor Red
            return $false
        }
    } else {
        Write-Host "✗ File not found: $FilePath" -ForegroundColor Red
        return $false
    }
}

Write-Host "1. Checking CMakeLists.txt configuration..." -ForegroundColor Yellow
Write-Host ""

$cmakeFile = "app\src\main\cpp\CMakeLists.txt"
$check1 = Test-FileContains $cmakeFile "max-page-size=16384" "CMakeLists.txt contains 16KB page size flag"
$check2 = Test-FileContains $cmakeFile "GGML_USE_16KB_PAGES" "CMakeLists.txt defines GGML_USE_16KB_PAGES"
$check3 = Test-FileContains $cmakeFile "Android 16KB page size support" "CMakeLists.txt has 16KB support comment"

Write-Host ""
Write-Host "2. Checking build.gradle configuration..." -ForegroundColor Yellow
Write-Host ""

$gradleFile = "app\build.gradle"
$check4 = Test-FileContains $gradleFile "CMAKE_EXE_LINKER_FLAGS.*max-page-size=16384" "build.gradle has EXE linker flags"
$check5 = Test-FileContains $gradleFile "CMAKE_SHARED_LINKER_FLAGS.*max-page-size=16384" "build.gradle has SHARED linker flags"

Write-Host ""
Write-Host "3. Checking llama-mmap.cpp runtime support..." -ForegroundColor Yellow
Write-Host ""

$mmapFile = "app\src\main\cpp\llama.cpp\src\llama-mmap.cpp"
$check6 = Test-FileContains $mmapFile "sysconf\(_SC_PAGESIZE\)" "llama-mmap.cpp has page size detection"
$check7 = Test-FileContains $mmapFile "align_range" "llama-mmap.cpp has alignment function"
$check8 = Test-FileContains $mmapFile "Memory page size detected" "llama-mmap.cpp has logging"

Write-Host ""
Write-Host "4. Checking documentation..." -ForegroundColor Yellow
Write-Host ""

$check9 = Test-Path "docs\16KB_PAGE_SIZE_SUPPORT.md"
if ($check9) {
    Write-Host "✓ Full documentation exists" -ForegroundColor Green
} else {
    Write-Host "✗ Full documentation missing" -ForegroundColor Red
}

$check10 = Test-Path "docs/archive/BUILD_16KB_GUIDE.md"
if ($check10) {
    Write-Host "✓ Quick guide exists" -ForegroundColor Green
} else {
    Write-Host "✗ Quick guide missing" -ForegroundColor Red
}

Write-Host ""
Write-Host "5. Checking for build artifacts..." -ForegroundColor Yellow
Write-Host ""

$releaseLib = "app\build\intermediates\cmake\release\obj\arm64-v8a\libmobigpt-llama.so"
$debugLib = "app\build\intermediates\cmake\debug\obj\arm64-v8a\libmobigpt-llama.so"

if (Test-Path $releaseLib) {
    Write-Host "✓ Release build found: $releaseLib" -ForegroundColor Green
    
    # Try to check alignment if readelf is available (WSL or Git Bash)
    Write-Host "  Checking binary alignment..." -ForegroundColor Gray
    try {
        if (Get-Command wsl -ErrorAction SilentlyContinue) {
            $alignment = wsl readelf -l $releaseLib.Replace('\', '/') 2>$null | Select-String "LOAD"
            if ($alignment) {
                Write-Host "  Binary LOAD segments:" -ForegroundColor Gray
                $alignment | ForEach-Object { Write-Host "    $_" -ForegroundColor Gray }
            }
        }
    } catch {
        Write-Host "  (readelf not available - skipping binary check)" -ForegroundColor Gray
    }
} elseif (Test-Path $debugLib) {
    Write-Host "✓ Debug build found: $debugLib" -ForegroundColor Green
} else {
    Write-Host "⚠ No build artifacts found - run './gradlew assembleRelease' first" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Summary" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$totalChecks = 10
$passedChecks = @($check1, $check2, $check3, $check4, $check5, $check6, $check7, $check8, $check9, $check10) | Where-Object { $_ -eq $true } | Measure-Object | Select-Object -ExpandProperty Count

Write-Host "Configuration Checks: $passedChecks/$totalChecks passed" -ForegroundColor $(if ($passedChecks -eq $totalChecks) { "Green" } else { "Yellow" })

if ($passedChecks -eq $totalChecks) {
    Write-Host ""
    Write-Host "✓ All checks passed! Your build is configured for 16KB page size support." -ForegroundColor Green
    Write-Host ""
    Write-Host "Next steps:" -ForegroundColor Cyan
    Write-Host "  1. Build the app: .\gradlew clean assembleRelease" -ForegroundColor White
    Write-Host "  2. Check build log for: 'Android 16KB page size support enabled'" -ForegroundColor White
    Write-Host "  3. Install and test on device" -ForegroundColor White
    Write-Host "  4. Check logcat: adb logcat | Select-String 'page size'" -ForegroundColor White
} else {
    Write-Host ""
    Write-Host "⚠ Some checks failed. Review the output above." -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Troubleshooting:" -ForegroundColor Cyan
    Write-Host "  - Ensure all files are properly saved" -ForegroundColor White
    Write-Host "  - Check file paths are correct" -ForegroundColor White
    Write-Host "  - Review documentation in docs/16KB_PAGE_SIZE_SUPPORT.md" -ForegroundColor White
}

Write-Host ""
Write-Host "For detailed information, see:" -ForegroundColor Cyan
Write-Host "  - docs\16KB_PAGE_SIZE_SUPPORT.md (full documentation)" -ForegroundColor White
Write-Host "  - docs/archive/BUILD_16KB_GUIDE.md (quick reference)" -ForegroundColor White
Write-Host "  - docs/archive/16KB_PAGE_SIZE_CHANGES.md (summary of changes)" -ForegroundColor White
Write-Host ""
