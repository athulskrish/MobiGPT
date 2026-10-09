# SQLCipher Emulator Crash Fix - Hardware AES Issue

## Problem

**Error:** `CHECK failed: host_platform::kHasAES`

SQLCipher 4.10.0 crashes on Android 15+ emulator with 16KB page size when running ARM64 code through NDK translation on x86_64 host.

### Crash Details

```
Fatal signal 6 (SIGABRT), code -1 (SI_QUEUE)
Abort message: 'vendor/unbundled_google/libs/ndk_translation/intrinsics/include/ndk_translation/intrinsics/common/vector_intrinsics.h:398: CHECK failed: host_platform::kHasAES'
```

### Root Cause

1. **SQLCipher 4.10.0** uses ARM crypto extensions (hardware AES instructions)
2. When running on **x86_64 emulator** with ARM64 guest architecture
3. NDK translation layer requires host to have **AES-NI** support
4. Emulator's x86_64 host doesn't have AES-NI enabled/available
5. Crash occurs during `System.loadLibrary("sqlcipher")` before database opens

## Solution

### Downgrade to SQLCipher 4.5.6

SQLCipher 4.5.6 provides:
- ✓ 16KB page size support
- ✓ Software-based crypto (no hardware AES dependency)
- ✓ Emulator compatibility
- ✓ Works on both 4KB and 16KB page devices

### Changes Made

**File:** `app/build.gradle`

```gradle
// Before (4.10.0 - hardware AES)
implementation "net.zetetic:sqlcipher-android:4.10.0@aar"

// After (4.5.6 - software crypto)
implementation "net.zetetic:sqlcipher-android:4.5.6@aar"
```

## Alternative Solutions

### Option 1: Use Real Device
Test on physical Android device where hardware AES is available.

### Option 2: Use x86_64 Emulator with AES-NI
Configure AVD to enable AES-NI support (if available in emulator).

### Option 3: Build Flavors (Future Enhancement)
```gradle
productFlavors {
    emulator {
        // Use 4.5.6 for emulator testing
    }
    production {
        // Use 4.10.0 for real devices (better performance)
    }
}
```

## Verification

### Build Commands
```bash
# Clean build
.\gradlew clean

# Build debug APK
.\gradlew assembleDebug

# Install on emulator
.\gradlew installDebug
```

### Expected Results
- ✓ App launches without crash
- ✓ SQLCipher loads successfully
- ✓ Database operations work correctly
- ✓ Compatible with 16KB page size emulator

## Performance Considerations

### SQLCipher 4.5.6 (Software Crypto)
- Slower encryption/decryption
- No hardware acceleration
- Better compatibility

### SQLCipher 4.10.0 (Hardware Crypto)
- Faster encryption/decryption
- Uses ARM crypto extensions
- Requires AES-NI on emulator

## Testing Checklist

- [ ] App launches on 16KB emulator
- [ ] Database creates successfully
- [ ] CRUD operations work
- [ ] App doesn't crash on database access
- [ ] Test on real device with 4.10.0 for production

## Related Files

- `app/build.gradle` - SQLCipher dependency
- `app/src/main/java/com/keralatechreach/mobigpt/database/SQLCipherHelperFactory.java` - SQLCipher integration
- `docs/SQLCIPHER_16KB_FIX.md` - 16KB page size documentation
- `SQLCIPHER_4.10.0_UPGRADE.md` - Previous upgrade notes

## References

- [SQLCipher Android Releases](https://github.com/sqlcipher/android-database-sqlcipher/releases)
- [Android NDK Translation](https://source.android.com/docs/core/architecture/ndk-translation)
- [ARM Crypto Extensions](https://developer.arm.com/documentation/ddi0487/latest)
- [Android 16KB Page Size Guide](https://developer.android.com/guide/practices/page-sizes)

## Summary

Downgraded from SQLCipher 4.10.0 to 4.5.6 to fix emulator crash caused by hardware AES instructions not being available in NDK translation layer. Version 4.5.6 maintains 16KB page size support while using software-based crypto for better emulator compatibility.

For production builds on real devices, consider using build flavors to switch back to 4.10.0 for better performance.
