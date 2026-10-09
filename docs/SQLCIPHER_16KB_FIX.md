# SQLCipher 16KB Page Size Compatibility Fix

## Problem
On Android 15+ devices with 16KB memory pages enabled, the app crashes with:
```
java.lang.UnsatisfiedLinkError: dlopen failed: empty/missing DT_HASH/DT_GNU_HASH in "libsqlcipher.so"
```

## Root Cause
SQLCipher versions prior to 4.6.0 were compiled without the `-Wl,-z,max-page-size=16384` linker flag, making them incompatible with Android's 16KB page size requirement.

## Solution Applied

### 1. Updated SQLCipher Dependency
**File:** `app/build.gradle`
```gradle
// Migrated from deprecated android-database-sqlcipher to sqlcipher-android
// Updated to 4.10.0 (confirmed version with proper 16KB support)
implementation "net.zetetic:sqlcipher-android:4.10.0"
```

**Important:** The old `android-database-sqlcipher` package is deprecated. Use `sqlcipher-android` instead.

SQLCipher 4.10.0+ includes:
- Native libraries compiled with 16KB page size support (max-page-size=16384)
- Proper DT_HASH/DT_GNU_HASH tables in .so files
- Correct 16KB alignment in LOAD segments
- Full compatibility with Android 15+ devices

### 2. Enabled Legacy Packaging
**File:** `app/build.gradle`
```gradle
packaging {
    jniLibs {
        useLegacyPackaging = true  // Changed from false
    }
}
```

**Note:** The `android.bundle.enableUncompressedNativeLibs` property was deprecated in AGP 8.1+. The `useLegacyPackaging` setting alone is sufficient.

This ensures:
- Native libraries are extracted to filesystem before loading
- Workaround for third-party libraries without direct 16KB support
- Compatibility with both 4KB and 16KB page sizes

## Verification Steps

### 1. Clean and Rebuild
```bash
./gradlew clean
./gradlew assembleRelease
```

### 2. Check Build Logs
Look for successful SQLCipher integration:
```
> Task :app:mergeReleaseNativeLibs
> Task :app:stripReleaseDebugSymbols
```

### 3. Runtime Verification
Install and run the app. Check logcat for:
```bash
adb logcat | grep -i "sqlcipher\|page.size"
```

Expected output:
- No UnsatisfiedLinkError
- SQLCipher database opens successfully
- App starts without crashes

### 4. Test on 16KB Device
Test on:
- Android 15+ emulator with 16KB pages enabled
- Physical devices: Pixel 8/9, Samsung S24+
- Verify database operations work correctly

## Technical Details

### Why Legacy Packaging?
- **Direct Loading (useLegacyPackaging=false)**: Libraries loaded directly from APK
  - Requires all .so files compiled with 16KB flags
  - Fails if any third-party library lacks support
  
- **Extracted Loading (useLegacyPackaging=true)**: Libraries extracted to filesystem
  - More compatible with mixed library sources
  - Slight performance overhead (one-time extraction)
  - Works with both 4KB and 16KB pages

### SQLCipher Version History
- **android-database-sqlcipher 4.5.4 and earlier**: Deprecated, no 16KB support
- **sqlcipher-android 4.6.0**: Initial attempt at 16KB support (incomplete)
- **sqlcipher-android 4.10.0+**: Confirmed proper 16KB page size support with correct alignment

## Alternative Solutions (Not Recommended)

### Option A: Disable 16KB Pages (Testing Only)
```bash
adb shell device_config put memory_safety_native_boot use_16kb_pages false
adb reboot
```
⚠️ Only for testing, not a production solution

### Option B: Build SQLCipher from Source
Compile SQLCipher with custom flags - complex and maintenance-heavy.

## Related Files
- `app/build.gradle` - Dependency and packaging configuration
- `gradle.properties` - Global build properties
- `app/src/main/cpp/CMakeLists.txt` - Native build configuration (llama.cpp)

## References
- [SQLCipher 16KB Support](https://github.com/sqlcipher/android-database-sqlcipher/releases/tag/v4.6.0)
- [Android 16KB Page Size Guide](https://developer.android.com/guide/practices/page-sizes)
- [MobiGPT 16KB Support](./16KB_PAGE_SIZE_SUPPORT.md)
