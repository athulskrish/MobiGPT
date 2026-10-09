# CMakeLists.txt for MobiGPT - Based on llama.cpp Android example
cmake_minimum_required(VERSION 3.22.1)

# Declare the project name
project("mobigpt-llama")

# Android 16KB page size support
# Modern Android devices (Android 15+) may use 16KB memory pages
# This ensures compatibility with both 4KB and 16KB page sizes
if(ANDROID)
    # Add linker flags for 16KB page size compatibility
    add_link_options(-Wl,-z,max-page-size=16384)
    
    # Ensure proper alignment for 16KB pages
    add_compile_definitions(GGML_USE_16KB_PAGES=1)
    
    message(STATUS "Android 16KB page size support enabled")
endif()

# Add llama.cpp subdirectory - builds the main llama library
add_subdirectory(llama.cpp)

# Create our native library using the proven llama-android implementation
add_library(${CMAKE_PROJECT_NAME} SHARED
    # Proven llama-android implementation
    llama-android.cpp
    # Our JNI wrapper for package mapping
    mobigpt-llama-jni.cpp
)

# Link libraries - same as the working Android example
target_link_libraries(${CMAKE_PROJECT_NAME}
    # llama.cpp libraries
    llama
    common
    
    # Android libraries
    android
    log
)