cmake_minimum_required(VERSION 3.3)

set(CMAKE_CXX_STANDARD 11)
set(CMAKE_CXX_FLAGS -std=c++11)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_C_STANDARD 11)
set(CMAKE_C_FLAGS -std=c11)
set(CMAKE_C_STANDARD_REQUIRED ON)

set(CMAKE_BUILD_TYPE Debug)

find_package(LLVM 4.0 REQUIRED CONFIG)

#
# The following couple of lines are necessary for cmake
# to find LLVM commands such as add_llvm_loadable_module(...)
# on Mac OS X (El Capitan).
#
list(APPEND CMAKE_MODULE_PATH "${LLVM_CMAKE_DIR}")	# for include(AddLLVM) 
include(AddLLVM)			# for add_llvm_loadable_module(...)

add_definitions(${LLVM_DEFINITIONS})
include_directories(${LLVM_INCLUDE_DIRS})
link_directories(${LLVM_LIBRARY_DIRS})

add_subdirectory(pass)
add_subdirectory(runtime)
