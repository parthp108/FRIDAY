cmake_minimum_required(VERSION 3.22.1)

project("friday_memory_manager")

# Create the shared library from your C++ source
add_library(
        ${CMAKE_PROJECT_NAME}
        SHARED
        MemoryManager.cpp)

# Support 16 KB page sizes for Android 15+ devices
target_link_options(${CMAKE_PROJECT_NAME} PRIVATE "-Wl,-z,max-page-size=16384")

# Link Android standard libraries (like logging)
target_link_libraries(
        ${CMAKE_PROJECT_NAME}
        android
        log)