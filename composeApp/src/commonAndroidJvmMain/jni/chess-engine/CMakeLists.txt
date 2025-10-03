cmake_minimum_required(VERSION 3.10)
project(jstockfish)

# Set C++ standard
set(CMAKE_CXX_STANDARD 11)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Include directories for header files
include_directories(${CMAKE_CURRENT_SOURCE_DIR})

# Collect all source files
file(GLOB_RECURSE shared_library_sources
        "*.cpp"
        "jstockfish/*.cpp"
        "jstockfish/syzygy/*.cpp"
)

file(GLOB_RECURSE shared_library_headers
        "*.h"
        "jstockfish/*.h"
        "jstockfish/syzygy/*.h"
)

list(FILTER shared_library_sources EXCLUDE REGEX "(main\\.cpp|CMakeCXXCompilerId\\.cpp)")

# Platform-specific configurations
if(ANDROID)
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11 -fexceptions")
elseif(APPLE)
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11 -O3 -fPIC")
    set(CMAKE_SHARED_LIBRARY_SUFFIX ".dylib")
elseif(WIN32)
    # MSVC uses different flags than GCC/Clang
    if(MSVC)
        set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} /std:c++11 /O2")
    else()
        set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11 -O3")
    endif()
else() # Linux and others
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11 -O3 -fPIC")
endif()

# Add library with platform-appropriate naming
add_library(jstockfish SHARED ${shared_library_sources} ${shared_library_headers})

# Add JNI if needed
find_package(JNI REQUIRED)
include_directories(${JNI_INCLUDE_DIRS})

# Link against required libraries
target_link_libraries(jstockfish ${JNI_LIBRARIES})

# Make sure output name matches what's loaded
set_target_properties(jstockfish PROPERTIES
    OUTPUT_NAME "jstockfish"
    LIBRARY_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/lib"
    RUNTIME_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/lib"
)
