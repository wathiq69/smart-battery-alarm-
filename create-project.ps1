param([string]$RepoPath = "")
if ($RepoPath -eq "") { $RepoPath = Read-Host "Enter repository path" }
if (-not (Test-Path $RepoPath)) { Write-Host "ERROR: Path not found" -ForegroundColor Red; exit 1 }
Write-Host "Creating Smart Battery Alarm files..." -ForegroundColor Cyan
 $app = "$RepoPath\app\src\main"
 $java = "$app\java\com\abughaith\batteryalarm"
 $res = "$app\res"
 $dirs = @("$app","$java","$java\tts","$java\prefs","$java\weather","$java\apps","$java\service","$java\receiver","$java\ui","$res\layout","$res\values","$res\values-ar","$res\drawable","$res\animator","$res\mipmap-anydpi-v26","$res\raw","$RepoPath\gradle\wrapper","$RepoPath\.github\workflows")
foreach ($d in $dirs) { New-Item -ItemType Directory -Path $d -Force | Out-Null }
function W([string]$p, [string]$c) { $e = New-Object System.Text.UTF8Encoding $false; [System.IO.File]::WriteAllText($p, $c, $e) }
W "$RepoPath\settings.gradle" "pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = `"Smart Battery Alarm`"
include ':app'"
W "$RepoPath\build.gradle" "plugins {
    id 'com.android.application' version '8.1.0' apply false
    id 'org.jetbrains.kotlin.android' version '1.9.0' apply false
}"
W "$RepoPath\gradle.properties" "org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
org.gradle.daemon=true
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true"
Write-Host "Done!" -ForegroundColor Green