# MobiGPT - Development Guide

> Complete guide for building, testing, and troubleshooting MobiGPT

## Table of Contents
- [Build Instructions](#build-instructions)
- [Troubleshooting](#troubleshooting)
- [Security Guidelines](#security-guidelines)
- [Performance Optimization](#performance-optimization)

---

## Build Instructions

### Prerequisites
- **Android Studio**: Arctic Fox or later
- **NDK**: Version 27.0
- **Java**: JDK 11 or later
- **Gradle**: 8.9 (included in wrapper)
- **Device Requirements**: Android 8.1+ (API 27), 3GB+ RAM, 6GB+ storage

### Quick Build
```powershell
# Clean build
.\gradlew clean assembleDebug

# Install on device
.\gradlew installDebug

# Build release with ProGuard
.\gradlew assembleRelease
```

### Build Configuration

#### Build Types
```gradle
debug {
    debuggable true
    minifyEnabled false
    applicationIdSuffix ".debug"
}

release {
    minifyEnabled true
    shrinkResources true
    proguardFiles 'proguard-rules.pro'
}
```

#### ProGuard Configuration
Key rules in `proguard-rules.pro`:
- Keep native methods for JNI
- Keep Room database entities
- Keep model classes for serialization
- Obfuscate with custom dictionaries

#### Signing
Create `keystore.properties` in root:
```properties
storeFile=path/to/keystore.jks
storePassword=your_password
keyAlias=your_alias
keyPassword=your_password
```

### Testing

#### Unit Tests
```powershell
.\gradlew test
```
> **Note:** Unit tests are disabled during full builds in `app/build.gradle` (`testOptions.unitTests.all { enabled = false }`) to speed up routine compilation. To run unit tests locally, either set `enabled = true` in `app/build.gradle` or execute specific test tasks directly:
```powershell
.\gradlew testDeviceDebugUnitTest
```


#### Instrumented Tests
```powershell
.\gradlew connectedAndroidTest
```

#### Manual Testing
```powershell
# View logs
adb logcat -s LlamaEngine ChatManager MainActivity

# Clear app data
# Note: Actual package on-device is com.keralatechreach.mobile_llm5 (or .mobile_llm5.emulator for emulator builds)
adb shell pm clear com.keralatechreach.mobile_llm5

# Check APK size
.\gradlew :app:assembleRelease
Get-Item app\build\outputs\apk\release\*.apk | Select-Object Length
```

---

## Troubleshooting

### Build Issues

#### Problem: NDK Not Found
**Symptom**: Error about missing NDK or native libraries
**Solution**:
```powershell
# Check NDK installation
Get-ChildItem "$env:LOCALAPPDATA\Android\Sdk\ndk"

# Set in local.properties
ndk.dir=C\:\\Users\\YourName\\AppData\\Local\\Android\\Sdk\\ndk\\27.0.12077973
```

#### Problem: Out of Memory During Build
**Symptom**: `OutOfMemoryError` during compilation
**Solution**: Increase heap size in `gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx4096m -XX:MaxMetaspaceSize=512m
```

#### Problem: ProGuard Build Fails
**Symptom**: Release build fails with ProGuard errors
**Solution**: Test ProGuard rules:
```powershell
.\test_proguard.bat
```
Review warnings and add keep rules as needed.

### Runtime Issues

#### Problem: Model Loading Fails
**Symptom**: "Failed to load model" error
**Diagnostics**:
```powershell
adb logcat -s LlamaEngine

# Check model file
# Note: Actual package on-device is com.keralatechreach.mobile_llm5 (or .mobile_llm5.emulator for emulator builds)
adb shell ls -lh /data/data/com.keralatechreach.mobile_llm5/files/models/
```
**Solutions**:
- Verify model file is complete (not corrupted)
- Check available RAM (3GB+ required)
- Ensure model format is GGUF
- Clear app cache and re-download

#### Problem: Slow Inference
**Symptom**: Very slow token generation (< 1 tok/s)
**Diagnostics**:
```powershell
adb logcat | Select-String "Token speed"
```
**Solutions**:
- Check thread count in logs (should be 2-6 based on device)
- Close background apps to free RAM
- Try smaller model (TinyLlama instead of Llama 2)
- Disable battery saver mode
- Check thermal throttling: `adb shell dumpsys thermalservice`

#### Problem: App Crashes
**Symptom**: Random crashes during usage
**Diagnostics**:
```powershell
# Get crash logs
adb logcat -d > crash.log

# Check native crashes
adb logcat -b crash

# Memory usage
# Note: Actual package on-device is com.keralatechreach.mobile_llm5 (or .mobile_llm5.emulator for emulator builds)
adb shell dumpsys meminfo com.keralatechreach.mobile_llm5
```
**Common Causes**:
- Out of memory (model too large)
- Thread synchronization issues
- Native library crashes
- Database corruption

#### Problem: Download Fails
**Symptom**: Model download fails or gets stuck
**Solutions**:
- Check network connectivity
- Verify storage space
- Try WiFi if on mobile data
- Clear download cache: Settings → Storage → Clear Cache
- Check download manager: `adb logcat -s ModelDownloadManager`

### Key Files
- `MainActivity.java` - Main chat interface
- `SettingsActivity.java` - Settings management
- `ThemeManager.java` - Theme system
- `TypographyManager.java` - Font system
- `ModelDownloadManager.java` - Download handling
- `ChatManager.java` - Chat operations
- `SecurityManager.java` - Security and authentication
- `LockScreenActivity.java` - Lock screen UI
- `ExportHelper.java` - Export functionality
- `MessageAdapter.java` - Message display with token statistics

### Database Issues

#### Problem: Chat History Lost
**Symptom**: Messages disappear or don't load
**Diagnostics**:
```powershell
# Check database
# Note: Actual package on-device is com.keralatechreach.mobile_llm5 (or .mobile_llm5.emulator for emulator builds)
adb shell ls -lh /data/data/com.keralatechreach.mobile_llm5/databases/

# Export database for inspection
adb pull /data/data/com.keralatechreach.mobile_llm5/databases/chat_database ./
```
**Solutions**:
- Check auto-delete settings (might be deleting old messages)
- Verify Room database version matches
- Clear app data and start fresh (last resort)

#### Problem: Database Migration Failed
**Symptom**: Error about database schema mismatch
**Solution**: Uninstall and reinstall app (or handle migration in code)

### UI/UX Issues

#### Problem: Dark Theme Text Not Visible
**Symptom**: White text on light background or vice versa
**Solution**: Verify theme application in `MainActivity.onCreate()`:
```java
ThemeManager themeManager = new ThemeManager(this);
themeManager.applyTheme(); // Must be before super.onCreate()
```

#### Problem: Auto-Scroll Not Working
**Symptom**: Chat doesn't scroll during AI response
**Solution**: Check `isUserScrolledUp` flag and scroll state in logs

#### Problem: Search Not Finding Messages
**Symptom**: Search returns no results
**Diagnostics**: Check search query handling in `performSearch()`
**Solution**: Verify database query is case-insensitive and handles special characters

---

## Security Guidelines

### Data Privacy

#### Local Processing
- **All AI inference runs on-device** - No data sent to servers
- **No analytics or tracking** unless explicitly enabled
- **No network requests** except for model downloads

#### Storage Security
- Messages stored in encrypted Room database
- Models stored in app-private directory (`/data/data/...`)
- Exported data should be stored in user-chosen secure location

#### Permissions
Required permissions and their usage:
- `INTERNET` - Only for downloading models
- `WRITE_EXTERNAL_STORAGE` - For exporting conversations (Android < 10)
- `POST_NOTIFICATIONS` - For download notifications (Android 13+)

### Best Practices

#### Code Security
```java
// Use content:// URIs, not file:// paths
Uri fileUri = FileProvider.getUriForFile(context, authority, file);

// Sanitize user input before database queries
String sanitized = input.replace("'", "''");

// Use parameterized queries (Room does this automatically)
@Query("SELECT * FROM messages WHERE content LIKE :query")
List<Message> search(String query);
```

#### ProGuard
- Obfuscates code to prevent reverse engineering
- Removes unused code to reduce APK size
- Custom dictionaries for better obfuscation
- Keep rules for reflection and JNI

#### Secure Communication
```java
// If adding server features, use HTTPS only
URL url = new URL("https://secure-server.com/api");

// Verify SSL certificates
HttpsURLConnection.setDefaultSSLSocketFactory(secureFactory);
```

### Vulnerability Management

#### Known Considerations
- Native library (llama.cpp) - Keep updated to latest version
- Third-party dependencies - Regular updates via Dependabot
- Input validation - Sanitize all user inputs
- File access - Use scoped storage (Android 10+)

#### Reporting
Report security issues to: info@keralatechreach.in
- Do not disclose publicly until patched
- Provide detailed reproduction steps
- Include device and Android version info

---

## Performance Optimization

### Threading

#### Optimal Thread Counts
```java
// Device detection
int cores = Runtime.getRuntime().availableProcessors();
int threads = Math.min(Math.max(cores - 2, 2), 6);

// Battery-aware scaling
if (batteryLevel < 20) threads = Math.max(threads - 2, 2);
```

#### Performance Gains
- 1 thread: ~1.5 tok/s (baseline)
- 2 threads: ~3 tok/s (2x)
- 4 threads: ~6-8 tok/s (4-5x)
- 6 threads: ~9-15 tok/s (6-10x on high-end devices)

### Memory Management

#### Model Size vs RAM
| Model | RAM Needed | Recommended Device |
|-------|-----------|-------------------|
| TinyLlama 1B Q4 | 3GB | Any modern device |
| Llama 2 7B Q4 | 6GB | Mid-range+ |
| Mistral 7B Q4 | 8GB | High-end |

#### Memory Optimization
```java
// Trim memory on low memory warning
@Override
public void onTrimMemory(int level) {
    if (level >= TRIM_MEMORY_MODERATE) {
        clearCaches();
        System.gc();
    }
}

// Lazy loading for heavy UI components
lazyLoadingManager.register("chatHistory", () -> setupChatHistory());
```

### Storage Optimization

#### Cache Management
- Clear image cache periodically
- Remove old temporary files
- Vacuum database: `VACUUM` command
- Limit message history in memory

#### Model Storage
```
models/           (GGUF files)
cache/            (temporary inference data)
databases/        (SQLite)
shared_prefs/     (settings - minimal)
```

### Network Optimization

#### Download Strategy
- Resume support for large files
- Chunk-based downloading (1MB chunks)
- WiFi-only option for large models
- Retry with exponential backoff
- Progress caching

### Benchmarking

#### Built-in Metrics
App logs include:
- Token generation speed (tok/s)
- Model loading time (ms)
- Memory usage (MB)
- Thread count and utilization

#### Manual Benchmarking
```powershell
# Start benchmark
adb logcat -c
adb logcat -s LlamaEngine | Select-String "speed"

# Monitor memory
# Note: Actual package on-device is com.keralatechreach.mobile_llm5 (or .mobile_llm5.emulator for emulator builds)
adb shell dumpsys meminfo com.keralatechreach.mobile_llm5 | Select-String "TOTAL"

# CPU usage
adb shell top -n 1 | Select-String "mobigpt"
```

---

## Security Testing

### App Lock Testing

#### Setup and Basic Authentication
```powershell
# Clean install
# Note: Actual package on-device is com.keralatechreach.mobile_llm5 (or .mobile_llm5.emulator for emulator builds)
adb shell pm clear com.keralatechreach.mobile_llm5

# Test sequence:
# 1. Enable App Lock in Settings → Security
# 2. Set up 4-6 digit PIN
# 3. Press Home button
# 4. Reopen app → Lock screen should appear
# 5. Enter correct PIN → App unlocks
```

#### Timeout Testing
```powershell
# Test Immediately (0s)
Settings → Security → Auto-lock Timeout → Immediately
# Expected: Lock screen on every app resume

# Test 30 seconds
Settings → Security → Auto-lock Timeout → 30 seconds
# Wait < 30s → No lock
# Wait > 30s → Lock screen appears
```

#### Biometric Testing
Prerequisites: Device with fingerprint/face unlock enrolled
```
1. Settings → Security → Lock Type → Biometric
2. Close and reopen app
3. Biometric prompt should appear automatically
4. Can fallback to PIN if biometric fails
```

### Export Security Testing
```powershell
# Test Export Authentication
Settings → Security → Authenticate for Export [ON]
# Try export → Should require PIN/biometric
# Disable → Export works immediately

# Test All Export Types
- Single conversation (Markdown)
- Single conversation (PDF)
- Share conversation
- Export all chats
# All should respect authentication setting
```

### Security Reset Testing
```
1. Set up App Lock with PIN
2. Settings → Security → Reset Security Settings
3. Confirm reset
4. Verify: App Lock OFF, PIN cleared
5. Can set up new PIN from scratch
```

---

## Development Tips

### Debugging

#### Enable Verbose Logging
```java
private static final boolean DEBUG = BuildConfig.DEBUG;
if (DEBUG) Log.d(TAG, "Detailed debug info");
```

#### Common Log Tags
```powershell
adb logcat -s LlamaEngine ChatManager MainActivity ModelDownloadManager ThemeManager
```

### Code Organization

#### Best Practices
- Keep business logic in managers (ChatManager, ModelDownloadManager)
- UI code in Activities/Fragments
- Database operations in DAOs
- Utilities in separate classes

#### Threading Guidelines
- UI operations on main thread
- Database operations on background thread
- Network operations on background thread
- Use `ExecutorService` for background tasks

### Git Workflow

```powershell
# Feature branch
git checkout -b feature/new-feature

# Commit with meaningful messages
git commit -m "feat: Add model caching for faster loads"

# Before PR
git fetch origin
git rebase origin/main
```

---

## Useful Commands

### ADB Commands
```powershell
# Install APK
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Clear app data
# Note: Actual package on-device is com.keralatechreach.mobile_llm5 (or .mobile_llm5.emulator for emulator builds)
adb shell pm clear com.keralatechreach.mobile_llm5

# Take screenshot
adb shell screencap -p /sdcard/screen.png
adb pull /sdcard/screen.png

# Record video
adb shell screenrecord /sdcard/demo.mp4

# Check battery
adb shell dumpsys battery

# Simulate low memory
# Note: Actual package on-device is com.keralatechreach.mobile_llm5 (or .mobile_llm5.emulator for emulator builds)
adb shell am send-trim-memory com.keralatechreach.mobile_llm5 MODERATE
```

### Gradle Commands
```powershell
# List all tasks
.\gradlew tasks

# Dependencies
.\gradlew :app:dependencies

# Build scan
.\gradlew build --scan

# Clean build cache
.\gradlew cleanBuildCache
```

---

**For feature documentation, see [FEATURES.md](FEATURES.md)**  
**For changelog, see [CHANGELOG.md](../CHANGELOG.md)**  
**For main README, see [README.md](README.md)**
