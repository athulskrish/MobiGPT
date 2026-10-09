# 16KB Page Size Support for Android

## Overview

This document describes the 16KB memory page size support implemented in MobiGPT's llama.cpp build for Android. Modern Android devices (Android 15+) may use 16KB memory pages instead of the traditional 4KB pages, and this build is configured to support both.

## Why 16KB Page Size Matters

### Background
- **Traditional Android**: Uses 4KB (4096 bytes) memory pages
- **Modern Android (15+)**: May use 16KB (16384 bytes) memory pages for improved performance
- **Compatibility Issue**: Apps built for 4KB pages may crash or fail on 16KB page devices

### Benefits of 16KB Pages
- Improved memory management efficiency
- Better performance for large memory operations
- Reduced TLB (Translation Lookaside Buffer) misses
- Enhanced system-wide performance

## Implementation Details

### 1. CMake Configuration (`app/src/main/cpp/CMakeLists.txt`)

The build system includes specific flags for 16KB page size compatibility:

```cmake
if(ANDROID)
    # Add compiler flags for 16KB page size compatibility
    add_compile_options(-Wl,-z,max-page-size=16384)
    add_link_options(-Wl,-z,max-page-size=16384)
    
    # Ensure proper alignment for 16KB pages
    add_compile_definitions(GGML_USE_16KB_PAGES=1)
    
    message(STATUS "Android 16KB page size support enabled")
endif()
```

**Key Flags:**
- `-Wl,-z,max-page-size=16384`: Sets maximum page size to 16KB for the linker
- `GGML_USE_16KB_PAGES=1`: Compile-time definition for 16KB page awareness

### 2. Gradle Build Configuration (`app/build.gradle`)

The Gradle build includes linker flags for both executables and shared libraries:

```gradle
externalNativeBuild {
    cmake {
        cppFlags '-std=c++17', '-frtti', '-fexceptions'
        arguments '-DANDROID_STL=c++_static',
                  '-DANDROID_PLATFORM=android-27',
                  '-DLLAMA_CURL=OFF',
                  '-DLLAMA_BUILD_COMMON=ON',
                  '-DGGML_LLAMAFILE=OFF',
                  '-DCMAKE_BUILD_TYPE=Release',
                  '-DCMAKE_EXE_LINKER_FLAGS=-Wl,-z,max-page-size=16384',
                  '-DCMAKE_SHARED_LINKER_FLAGS=-Wl,-z,max-page-size=16384'
    }
}
```

### 3. Runtime Page Size Detection (`llama-mmap.cpp`)

The llama.cpp memory mapping code includes runtime detection and handling of different page sizes:

#### Page Size Detection
```cpp
long page_size = sysconf(_SC_PAGESIZE);

// Validation with fallback
if (page_size <= 0) {
    LLAMA_LOG_WARN("warning: invalid page size from sysconf: %ld, using default 4096\n", page_size);
    page_size = 4096;
}
```

#### Logging for Debugging
```cpp
static bool logged_page_size = false;
if (!logged_page_size) {
    LLAMA_LOG_INFO("Memory page size detected: %ld bytes\n", page_size);
    logged_page_size = true;
}
```

#### Alignment Function
The `align_range()` function ensures proper alignment for any page size:

```cpp
static void align_range(size_t * first, size_t * last, size_t page_size) {
    // Ensure page_size is a power of 2 (required for bit operations)
    GGML_ASSERT((page_size & (page_size - 1)) == 0 && "Page size must be power of 2");
    
    // Align first to next page boundary (round up)
    size_t offset_in_page = *first & (page_size - 1);
    size_t offset_to_page = offset_in_page == 0 ? 0 : page_size - offset_in_page;
    *first += offset_to_page;

    // Align last to page boundary (round down)
    *last = *last & ~(page_size - 1);

    // Ensure we have a valid range
    if (*last <= *first) {
        *last = *first;
    }
}
```

## Building the App

### Prerequisites
- Android Studio Arctic Fox or later
- NDK version 27.0.12077973 (specified in build.gradle)
- CMake 3.22.1 or later

### Build Steps

1. **Clean Build** (recommended for first build):
   ```bash
   ./gradlew clean
   ```

2. **Build Debug APK**:
   ```bash
   ./gradlew assembleDebug
   ```

3. **Build Release APK**:
   ```bash
   ./gradlew assembleRelease
   ```

### Verification

After building, you can verify 16KB page size support:

1. **Check Build Logs**: Look for the message:
   ```
   Android 16KB page size support enabled
   ```

2. **Runtime Logs**: When the app loads a model, check logcat for:
   ```
   Memory page size detected: 16384 bytes
   ```
   or
   ```
   Memory page size detected: 4096 bytes
   ```

3. **Verify Binary**: Use `readelf` to check the binary:
   ```bash
   readelf -l app/build/intermediates/cmake/release/obj/arm64-v8a/libmobigpt-llama.so | grep LOAD
   ```
   The alignment should show 0x4000 (16384) for 16KB page size support.

## Testing

### Test on Different Devices

1. **4KB Page Devices** (older Android):
   - App should work normally
   - Logs will show: `Memory page size detected: 4096 bytes`

2. **16KB Page Devices** (Android 15+):
   - App should work without crashes
   - Logs will show: `Memory page size detected: 16384 bytes`

### Common Test Scenarios

1. **Model Loading**: Load a GGUF model file
2. **Inference**: Run text generation
3. **Memory Mapping**: Verify mmap operations succeed
4. **Memory Unmapping**: Test fragment unmapping

## Troubleshooting

### Build Errors

**Error**: `undefined reference to 'GGML_USE_16KB_PAGES'`
- **Solution**: Clean and rebuild the project

**Error**: Linker warnings about page size
- **Solution**: Verify CMake and Gradle configurations match

### Runtime Issues

**Issue**: App crashes on Android 15+ devices
- **Check**: Verify the binary was built with 16KB flags
- **Check**: Review logcat for page size detection logs

**Issue**: Memory mapping failures
- **Check**: Ensure model files are accessible
- **Check**: Verify sufficient device memory

### Debug Logging

Enable verbose logging to see page size information:

```cpp
// In llama-mmap.cpp, logs are already enabled
// Check logcat with:
adb logcat | grep -E "(page size|mmap)"
```

## Performance Considerations

### 16KB vs 4KB Pages

**Advantages of 16KB:**
- Fewer TLB misses
- Better memory throughput
- Reduced page table overhead

**Considerations:**
- Slightly larger binary size due to alignment
- May use more memory for small allocations

### Optimization Tips

1. **Memory Alignment**: Ensure large buffers are 16KB-aligned
2. **Model Files**: Use models optimized for mmap
3. **Prefetching**: Enable mmap prefetching for better performance

## Compatibility Matrix

| Android Version | Default Page Size | Support Status |
|----------------|-------------------|----------------|
| Android 14 and below | 4KB | ✅ Fully Supported |
| Android 15+ | 4KB or 16KB | ✅ Fully Supported |
| Future versions | 16KB (likely) | ✅ Ready |

## References

- [Android 16KB Page Size Documentation](https://developer.android.com/guide/practices/page-sizes)
- [llama.cpp Memory Management](https://github.com/ggerganov/llama.cpp)
- [Linux mmap Documentation](https://man7.org/linux/man-pages/man2/mmap.2.html)

## Changelog

### Version 1.2.3
- ✅ Added 16KB page size support in CMakeLists.txt
- ✅ Updated Gradle build configuration
- ✅ Enhanced llama-mmap.cpp with runtime detection
- ✅ Added comprehensive logging for debugging

## Support

For issues related to 16KB page size support:
1. Check the troubleshooting section above
2. Review build and runtime logs
3. Verify device page size with `getconf PAGESIZE`
4. Report issues with device model and Android version
