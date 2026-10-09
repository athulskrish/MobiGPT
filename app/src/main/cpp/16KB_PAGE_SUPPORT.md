# 16KB Page Size Support for MobiGPT Native Code

## Overview
This document describes the 16KB page size support implemented in MobiGPT's native code to ensure compatibility with Android 15+ devices that use 16KB memory pages instead of the traditional 4KB pages.

## Modified Files

### 1. **CMakeLists.txt**
**Location:** `app/src/main/cpp/CMakeLists.txt`

**Changes:**
- Added `-Wl,-z,max-page-size=16384` compiler and linker flags
- Added `GGML_USE_16KB_PAGES=1` compile definition
- Android-specific configuration block

**Purpose:** Ensures the compiled binary is compatible with 16KB page sizes at the linker level.

### 2. **ggml.h**
**Location:** `app/src/main/cpp/llama.cpp/ggml/include/ggml.h`

**Changes:**
- Enhanced `GGML_MEM_ALIGN` definition with documentation
- Added comments explaining 16KB page compatibility
- Clarified that page-level operations are handled in llama-mmap.cpp

**Purpose:** Documents memory alignment strategy for both 4KB and 16KB pages.

### 3. **llama-mmap.cpp**
**Location:** `app/src/main/cpp/llama.cpp/src/llama-mmap.cpp`

**Changes:**
- Enhanced `unmap_fragment()` with page size validation
- Added page size logging in mmap initialization
- Improved `lock_granularity()` with error handling
- Added descriptive page type detection (4KB/16KB/Unknown)

**Key Features:**
- Runtime page size detection using `sysconf(_SC_PAGESIZE)`
- Dynamic alignment for both 4KB and 16KB pages
- Comprehensive logging for debugging
- Fallback to 4096 if sysconf fails

### 4. **llama-android.cpp**
**Location:** `app/src/main/cpp/llama.cpp/examples/llama.android/llama/src/main/cpp/llama-android.cpp`

**Changes:**
- Added page size detection in `backend_init()`
- Logs page size and type during initialization
- Provides early warning if page size cannot be detected

**Purpose:** Helps developers identify page size issues during app initialization.

### 5. **mobigpt-llama-jni.cpp**
**Location:** `app/src/main/cpp/mobigpt-llama-jni.cpp`

**Status:** No changes needed - wrapper functions delegate to llama-android.cpp

## How It Works

### Runtime Detection
The code uses `sysconf(_SC_PAGESIZE)` to detect the system's page size at runtime:
- **4096 bytes** = 4KB pages (traditional Android)
- **16384 bytes** = 16KB pages (Android 15+)

### Memory Alignment Strategy
1. **General allocations:** Use 64-byte alignment (sufficient for both page sizes)
2. **Page-level operations:** Dynamically align to detected page size
3. **mmap operations:** Handle both 4KB and 16KB page boundaries

### Key Functions

#### `align_range()` in llama-mmap.cpp
```cpp
static void align_range(size_t * first, size_t * last, size_t page_size)
```
- Ensures page_size is power of 2
- Aligns memory ranges to page boundaries
- Works correctly with both 4KB and 16KB pages

#### `unmap_fragment()` in llama-mmap.cpp
```cpp
void unmap_fragment(size_t first, size_t last)
```
- Detects page size at runtime
- Validates page size (with fallback to 4096)
- Logs page size for debugging
- Performs page-aligned unmapping

#### `lock_granularity()` in llama-mmap.cpp
```cpp
static size_t lock_granularity()
```
- Returns system page size for memory locking
- Handles invalid page size with fallback
- Supports both 4KB and 16KB pages

## Build Configuration

### Compiler Flags
```cmake
add_compile_options(-Wl,-z,max-page-size=16384)
add_link_options(-Wl,-z,max-page-size=16384)
```

### Compile Definitions
```cmake
add_compile_definitions(GGML_USE_16KB_PAGES=1)
```

## Verification

### Build Time
Look for this message during build:
```
-- Android 16KB page size support enabled
```

### Runtime Logs
Check logcat for these messages:

1. **Backend initialization:**
   ```
   System page size: 16384 bytes (16KB (Android 15+))
   ```
   or
   ```
   System page size: 4096 bytes (4KB (Standard))
   ```

2. **mmap initialization:**
   ```
   mmap: System page size: 16384 bytes (16KB (Android 15+)), file size: XXXXX bytes
   ```

3. **Memory operations:**
   ```
   Memory page size detected: 16384 bytes
   ```

## Testing

### On 4KB Page Devices
- Should log "4KB (Standard)"
- All operations work as before
- No performance impact

### On 16KB Page Devices (Android 15+)
- Should log "16KB (Android 15+)"
- Memory operations properly aligned
- Model loading works correctly
- No crashes due to page alignment issues

## Compatibility

### Backward Compatibility
✅ Fully compatible with 4KB page devices
✅ No changes to API or behavior
✅ Automatic detection and adaptation

### Forward Compatibility
✅ Ready for Android 15+ with 16KB pages
✅ Handles unknown page sizes gracefully
✅ Fallback mechanisms in place

## Troubleshooting

### Issue: "invalid page size from sysconf"
**Cause:** sysconf(_SC_PAGESIZE) returned invalid value
**Solution:** Code automatically falls back to 4096 bytes

### Issue: Model fails to load on 16KB device
**Check:**
1. Verify build flags are present in CMakeLists.txt
2. Check logcat for page size detection messages
3. Ensure binary was built with updated configuration

### Issue: Memory alignment errors
**Check:**
1. Verify GGML_USE_16KB_PAGES=1 is defined
2. Check that linker flags include max-page-size=16384
3. Review logcat for alignment-related warnings

## References

- Android 16KB Page Size Documentation: https://developer.android.com/guide/practices/page-sizes
- POSIX mmap: https://man7.org/linux/man-pages/man2/mmap.2.html
- sysconf: https://man7.org/linux/man-pages/man3/sysconf.3.html

## Summary

All native code files have been updated to support 16KB page sizes:
- ✅ Build configuration (CMakeLists.txt)
- ✅ Memory alignment definitions (ggml.h)
- ✅ Memory mapping operations (llama-mmap.cpp)
- ✅ JNI interface (llama-android.cpp)
- ✅ Runtime detection and logging
- ✅ Fallback mechanisms
- ✅ Backward compatibility maintained
