#!/bin/bash

# AsdUnion Fast Build Script for macOS
# This script uses aggressive caching and skips unnecessary tasks for maximum speed
# Usage: ./build_mod_fast.sh [--clean]

set -e

echo "⚡ Starting AsdUnion FAST build process..."

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

print_status() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Debug function to check build status
debug_build() {
    print_status "=== Build Debug Information ==="
    print_status "Working directory: $(pwd)"
    print_status "Gradle wrapper exists: $(test -f gradlew && echo "YES" || echo "NO")"
    print_status "Build.gradle exists: $(test -f build.gradle && echo "YES" || echo "NO")"
    
    if [[ -d "build" ]]; then
        print_status "Build directory contents:"
        find build -type f -name "*.jar" 2>/dev/null | head -5
    else
        print_status "Build directory does not exist"
    fi
    print_status "================================"
}

# Get the directory where the script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Check if build.gradle exists
if [[ ! -f "build.gradle" ]]; then
    print_error "build.gradle not found!"
    exit 1
fi

# Find Java 8 JDK
find_java8_jdk() {
    local java8_homes=$(/usr/libexec/java_home -V 2>&1 | grep "1\.8\." | grep -v "JavaAppletPlugin")
    
    if [[ -z "$java8_homes" ]]; then
        return 1
    fi
    
    local preferred_vendors=("Eclipse Temurin" "OpenLogic-OpenJDK" "Oracle Corporation" "BellSoft" "Amazon" "Azul" "AdoptOpenJDK")
    
    for vendor in "${preferred_vendors[@]}"; do
        local java_home=$(echo "$java8_homes" | grep "$vendor" | head -n 1 | awk -F' ' '{print $NF}')
        if [[ -n "$java_home" && -d "$java_home" && -f "$java_home/bin/javac" ]]; then
            echo "$java_home"
            return 0
        fi
    done
    
    while IFS= read -r line; do
        local java_home=$(echo "$line" | awk -F' ' '{print $NF}')
        if [[ -n "$java_home" && -d "$java_home" && -f "$java_home/bin/javac" ]]; then
            echo "$java_home"
            return 0
        fi
    done <<< "$java8_homes"
    
    return 1
}

JAVA8_HOME=$(find_java8_jdk)
if [[ -z "$JAVA8_HOME" ]]; then
    print_error "No Java 8 JDK found!"
    exit 1
fi

export JAVA_HOME="$JAVA8_HOME"
print_success "Using Java 8 JDK: $JAVA_HOME"

# Make gradlew executable
chmod +x gradlew

# Handle clean build option
CLEAN_BUILD="${1:-}"
if [[ "$CLEAN_BUILD" == "--clean" ]]; then
    print_status "Performing clean build..."
    ./gradlew clean
else
    print_status "Using cached build (use --clean flag for full rebuild)"
fi

# Debug build environment
debug_build

# Get number of CPU cores
NUM_CORES=$(sysctl -n hw.ncpu)
print_status "Using $NUM_CORES CPU cores for parallel compilation"

# Set optimized Gradle options
export GRADLE_OPTS="-Xmx4096m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+ParallelRefProcEnabled"

print_status "Starting optimized build with caching..."
START_TIME=$(date +%s)

# Build with maximum optimization - use shadowJar for mod building
print_status "Building mod with shadowJar task..."
./gradlew shadowJar reobfShadowJar \
    --build-cache \
    --parallel \
    --max-workers=$NUM_CORES \
    -x test \
    -x javadoc \
    -x check

BUILD_EXIT_CODE=$?

END_TIME=$(date +%s)
BUILD_TIME=$((END_TIME - START_TIME))

if [[ $BUILD_EXIT_CODE -eq 0 ]]; then
    print_success "Build completed in ${BUILD_TIME}s!"
else
    print_error "ShadowJar build failed with exit code: $BUILD_EXIT_CODE"
    print_status "Trying fallback build task..."
    
    # Fallback to regular build task
    ./gradlew build --build-cache --parallel --max-workers=$NUM_CORES -x test
    BUILD_EXIT_CODE=$?
    
    if [[ $BUILD_EXIT_CODE -ne 0 ]]; then
        print_error "Fallback build also failed!"
        print_status "Running gradle build with verbose output to diagnose..."
        ./gradlew build --info --stacktrace | tail -20
        exit 1
    else
        print_success "Fallback build succeeded!"
    fi
fi
    
    # Find and display the mod file
    BUILD_DIR="$SCRIPT_DIR/build/libs"
    
    # Check if build directory exists
    if [[ ! -d "$BUILD_DIR" ]]; then
        print_error "Build directory not found: $BUILD_DIR"
        print_status "Checking for alternative build locations..."
        
        # Check common alternative locations
        for alt_dir in "build/libs" "libs" "target" "out/libs"; do
            if [[ -d "$alt_dir" ]]; then
                BUILD_DIR="$SCRIPT_DIR/$alt_dir"
                print_status "Found alternative build directory: $BUILD_DIR"
                break
            fi
        done
    fi
    
    # Look for the mod file
    if [[ -d "$BUILD_DIR" ]]; then
        print_status "Searching for mod file in: $BUILD_DIR"
        
        # List all files in build directory for debugging
        print_status "Files in build directory:"
        ls -la "$BUILD_DIR"
        
        # Find the mod file (look for AsdUnion-b12.jar specifically)
        MOD_FILE=$(find "$BUILD_DIR" -name "AsdUnion-b12.jar" | head -n 1)
        
        # If specific version not found, look for any AsdUnion jar
        if [[ -z "$MOD_FILE" ]]; then
            MOD_FILE=$(find "$BUILD_DIR" -name "AsdUnion*.jar" -not -name "*-sources.jar" -not -name "*-javadoc.jar" -not -name "*-dev.jar" | head -n 1)
        fi
        
        # If no FDPClient jar found, look for any jar file
        if [[ -z "$MOD_FILE" ]]; then
            MOD_FILE=$(find "$BUILD_DIR" -name "*.jar" -not -name "*-sources.jar" -not -name "*-javadoc.jar" -not -name "*-dev.jar" | head -n 1)
        fi
        
        if [[ -n "$MOD_FILE" && -f "$MOD_FILE" ]]; then
            MOD_FILENAME=$(basename "$MOD_FILE")
            FILE_SIZE=$(du -h "$MOD_FILE" | cut -f1)
            print_success "Mod: $MOD_FILENAME ($FILE_SIZE)"
            print_status "Location: $MOD_FILE"
            
            # Open Finder and select the mod file
            print_status "Opening Finder and selecting the mod file..."
            osascript -e "tell application \"Finder\" to reveal POSIX file \"$MOD_FILE\""
            osascript -e "tell application \"Finder\" to activate"
        else
            print_error "No mod file found in $BUILD_DIR"
            print_status "Available files:"
            find "$BUILD_DIR" -name "*.jar" -o -name "*.zip" | head -10
            
            # Try to find any jar files
            ALL_JARS=$(find "$BUILD_DIR" -name "*.jar")
            if [[ -n "$ALL_JARS" ]]; then
                print_status "Found these jar files:"
                echo "$ALL_JARS"
                
                # Use the first jar file found
                FIRST_JAR=$(echo "$ALL_JARS" | head -n 1)
                print_status "Using: $FIRST_JAR"
                osascript -e "tell application \"Finder\" to reveal POSIX file \"$FIRST_JAR\""
                osascript -e "tell application \"Finder\" to activate"
            else
                print_error "No jar files found at all!"
                # Open the build directory anyway
                open "$BUILD_DIR"
            fi
        fi
    else
        print_error "Build directory does not exist: $BUILD_DIR"
        # Try to open the project root
        open "$SCRIPT_DIR"
    fi

echo ""
print_success "⚡ Fast build complete!"
