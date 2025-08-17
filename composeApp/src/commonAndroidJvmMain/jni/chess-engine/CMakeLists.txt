cmake_minimum_required(VERSION 3.4.1)

# Include directories for header files
include_directories(${CMAKE_CURRENT_SOURCE_DIR})

# Collect all source files
file(GLOB_RECURSE SOURCE_FILES
        "*.cpp"
        "jstockfish/*.cpp"
        "jstockfish/syzygy/*.cpp"
)

# Set the library name to match what's loaded in the Kotlin code
add_library(jstockfish SHARED ${SOURCE_FILES})

# Set any compiler flags if needed
if(ANDROID)
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11 -fexceptions")
else()
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11 -O3")
endif()

# Link against required libraries
target_link_libraries(jstockfish)

# Make sure output name matches what's loaded in System.loadLibrary()
set_target_properties(jstockfish PROPERTIES OUTPUT_NAME "jstockfish")