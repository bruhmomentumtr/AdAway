# Android Build Script - Windows PowerShell
# smartbuild.sh'nin Windows karşılığı - Ekstra araç gerektirmez

param(
    [Parameter(Position = 0)]
    [ValidateSet("release", "debug", "unsigned", "clean", "help")]
    [string]$BuildType = "release"
)

$ErrorActionPreference = "Stop"

# ---------------------------------------------------------
# YARDIM
# ---------------------------------------------------------
function Show-Help {
    Write-Host "Kullanım: .\smartbuild.ps1 [SEÇENEK]"
    Write-Host ""
    Write-Host "Seçenekler:"
    Write-Host "  (boş)      İmzalı release APK (secrets.ps1 gerekli)"
    Write-Host "  debug      Debug APK"
    Write-Host "  unsigned   İmzasız release APK"
    Write-Host "  clean      Gradle ve Android cache temizliği"
    Write-Host "  help       Bu yardım mesajını göster"
    Write-Host ""
    exit 0
}

if ($BuildType -eq "help") {
    Show-Help
}

# ---------------------------------------------------------
# CACHE TEMİZLİĞİ
# ---------------------------------------------------------
if ($BuildType -eq "clean") {
    Write-Host "🧹 Cache temizliği başlatılıyor..." -ForegroundColor Cyan
    
    # Mevcut boyutları göster
    $gradlePath = "$env:USERPROFILE\.gradle"
    $androidPath = "$env:USERPROFILE\.android"
    
    $gradleSize = if (Test-Path $gradlePath) { 
        "{0:N2} MB" -f ((Get-ChildItem $gradlePath -Recurse -ErrorAction SilentlyContinue | Measure-Object -Property Length -Sum).Sum / 1MB)
    }
    else { "0" }
    
    $androidSize = if (Test-Path $androidPath) {
        "{0:N2} MB" -f ((Get-ChildItem $androidPath -Recurse -ErrorAction SilentlyContinue | Measure-Object -Property Length -Sum).Sum / 1MB)
    }
    else { "0" }
    
    Write-Host "📊 Mevcut boyutlar: .gradle: $gradleSize, .android: $androidSize"
    
    # Gradle daemon'ları durdur
    Write-Host "⏹️ Gradle daemon'ları durduruluyor..."
    & .\gradlew.bat --stop 2>$null
    
    # Daemon'ların tamamen kapanması için bekle
    Write-Host "⏳ Daemon'ların kapanması bekleniyor (3 saniye)..."
    Start-Sleep -Seconds 3
    
    # Proje build temizliği (daemon başlatmadan)
    Write-Host "🗑️ Proje build klasörü temizleniyor..."
    if (Test-Path "build") { Remove-Item -Recurse -Force "build" -ErrorAction SilentlyContinue }
    if (Test-Path "app\build") { Remove-Item -Recurse -Force "app\build" -ErrorAction SilentlyContinue }
    
    # Gradle cache temizliği
    Write-Host "🗑️ Gradle cache temizleniyor..."
    if (Test-Path "$gradlePath\caches") { 
        Remove-Item -Recurse -Force "$gradlePath\caches" -ErrorAction SilentlyContinue 
    }
    if (Test-Path "$gradlePath\daemon") { 
        Remove-Item -Recurse -Force "$gradlePath\daemon" -ErrorAction SilentlyContinue 
    }
    
    # Android build cache temizliği
    Write-Host "🗑️ Android build cache temizleniyor..."
    if (Test-Path "$androidPath\build-cache") { 
        Remove-Item -Recurse -Force "$androidPath\build-cache" -ErrorAction SilentlyContinue 
    }
    
    Write-Host ""
    Write-Host "✅ Temizlik tamamlandı!" -ForegroundColor Green
    exit 0
}

Write-Host "🚀 Android Build Süreci Başlatılıyor..." -ForegroundColor Cyan

# ---------------------------------------------------------
# 1. KEYSTORE KONTROLÜ (Opsiyonel - Release build için)
# ---------------------------------------------------------
$KEYSTORE_FILE = "app\my-release-key.jks"

# secrets.ps1 varsa yükle (release build için)
if (Test-Path "secrets.ps1") {
    Write-Host "📄 secrets.ps1 yükleniyor..."
    . .\secrets.ps1
}

# ---------------------------------------------------------
# 2. BUILD MODU SEÇİMİ
# ---------------------------------------------------------
$SIGN_APK = $true
$actualBuildType = $BuildType

# unsigned parametresi verilmişse imzasız release build yap
if ($BuildType -eq "unsigned") {
    $actualBuildType = "release"
    $SIGN_APK = $false
    Write-Host "📦 İmzasız release build seçildi." -ForegroundColor Yellow
}

if ($actualBuildType -eq "release" -and $SIGN_APK) {
    # Release build için keystore kontrolü
    if ([string]::IsNullOrWhiteSpace($env:ANDROID_KEYSTORE_PASSWORD) -or 
        [string]::IsNullOrWhiteSpace($env:ANDROID_KEY_PASSWORD) -or 
        [string]::IsNullOrWhiteSpace($env:ANDROID_KEYSTORE_ALIAS)) {
        Write-Host "⚠️ Release için keystore şifreleri tanımlanmamış, debug build yapılacak..." -ForegroundColor Yellow
        $actualBuildType = "debug"
    }
    elseif (-not (Test-Path $KEYSTORE_FILE)) {
        if (-not [string]::IsNullOrWhiteSpace($env:ANDROID_KEYSTORE_BASE64)) {
            Write-Host "🔐 Keystore dosyası Base64'ten oluşturuluyor..."
            
            # Base64 decode - Built-in .NET kullanır (ekstra araç gerektirmez)
            try {
                $bytes = [System.Convert]::FromBase64String($env:ANDROID_KEYSTORE_BASE64)
                
                # app klasörünü oluştur (yoksa)
                $appDir = Split-Path $KEYSTORE_FILE -Parent
                if (-not (Test-Path $appDir)) {
                    New-Item -ItemType Directory -Path $appDir -Force | Out-Null
                }
                
                [System.IO.File]::WriteAllBytes($KEYSTORE_FILE, $bytes)
                Write-Host "✅ Keystore oluşturuldu." -ForegroundColor Green
            }
            catch {
                Write-Host "⚠️ Keystore decode hatası: $_, debug build yapılacak..." -ForegroundColor Yellow
                $actualBuildType = "debug"
            }
        }
        else {
            Write-Host "⚠️ Keystore bulunamadı, debug build yapılacak..." -ForegroundColor Yellow
            $actualBuildType = "debug"
        }
    }
    
    # gradle.properties oluştur (signing config için)
    if ($actualBuildType -eq "release") {
        Write-Host "📄 gradle.properties oluşturuluyor..."
        
        @"
# AndroidX configuration
android.useAndroidX=true
android.enableJetifier=true

# Gradle configuration
org.gradle.jvmargs=-Xmx4096m -XX:MaxMetaspaceSize=512m

# Signing configuration
signingStoreLocation=my-release-key.jks
signingStorePassword=$($env:ANDROID_KEYSTORE_PASSWORD)
signingKeyAlias=$($env:ANDROID_KEYSTORE_ALIAS)
signingKeyPassword=$($env:ANDROID_KEY_PASSWORD)
"@ | Out-File -FilePath "gradle.properties" -Encoding ASCII
        
        Write-Host "✅ gradle.properties oluşturuldu." -ForegroundColor Green
    }
}
elseif ($actualBuildType -eq "release" -and -not $SIGN_APK) {
    # İmzasız build - gradle.properties dosyasını sil (varsa)
    if (Test-Path "gradle.properties") {
        Remove-Item "gradle.properties"
        Write-Host "🔓 gradle.properties silindi (imzasız build)." -ForegroundColor Yellow
    }
}

# ---------------------------------------------------------
# 3. GRADLE BUILD
# ---------------------------------------------------------
if ($actualBuildType -eq "release") {
    Write-Host "� Gradle sync & build başlatılıyor..."
    Write-Host "� Release APK build başlatılıyor..." -ForegroundColor Cyan
    & .\gradlew.bat assembleRelease --refresh-dependencies
}
else {
    Write-Host "� Gradle sync & build başlatılıyor..."
    Write-Host "�� Debug APK build başlatılıyor..." -ForegroundColor Cyan
    & .\gradlew.bat assembleDebug --refresh-dependencies
}

# ---------------------------------------------------------
# 4. SONUÇ KONTROLÜ
# ---------------------------------------------------------
if ($actualBuildType -eq "release") {
    # Önce imzalı APK'yı kontrol et
    if (Test-Path "app\build\outputs\apk\release\app-release.apk") {
        $APK_PATH = "app\build\outputs\apk\release\app-release.apk"
        $IS_SIGNED = $true
    }
    elseif (Test-Path "app\build\outputs\apk\release\app-release-unsigned.apk") {
        $APK_PATH = "app\build\outputs\apk\release\app-release-unsigned.apk"
        $IS_SIGNED = $false
    }
    else {
        Write-Host "❌ HATA: Release APK oluşmadı." -ForegroundColor Red
        exit 1
    }
}
else {
    $APK_PATH = "app\build\outputs\apk\debug\app-debug.apk"
    $IS_SIGNED = $true
}

if (Test-Path $APK_PATH) {
    $APK_SIZE = "{0:N2} MB" -f ((Get-Item $APK_PATH).Length / 1MB)
    Write-Host ""
    Write-Host "✅ BUILD TAMAMLANDI!" -ForegroundColor Green
    Write-Host "📦 APK: $APK_PATH"
    Write-Host "📏 Boyut: $APK_SIZE"
    
    # İmza durumu kontrolü
    if ($actualBuildType -eq "release") {
        if ($IS_SIGNED -and $SIGN_APK) {
            Write-Host "✅ APK imzalandı, yüklemeye hazır!" -ForegroundColor Green
            Write-Host ""
            Write-Host "📱 Yüklemek için: adb install $APK_PATH" -ForegroundColor Cyan
        }
        elseif (-not $IS_SIGNED) {
            Write-Host ""
            Write-Host "⚠️ APK imzasız. Android cihazlara yüklenemez!" -ForegroundColor Yellow
            Write-Host "💡 İmzalı build için: .\smartbuild.ps1" -ForegroundColor Cyan
        }
    }
    else {
        Write-Host ""
        Write-Host "📱 Yüklemek için: adb install $APK_PATH" -ForegroundColor Cyan
    }
}
else {
    Write-Host "❌ HATA: Build başarısız, APK oluşmadı." -ForegroundColor Red
    exit 1
}
