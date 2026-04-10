#!/bin/bash

# FDPClient Build Script for macOS
# This script sets Java version to 1.8, builds the mod, and opens the build folder

set -e  # Exit on any error

echo "🚀 Starting FDPClient build process..."

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if we're on macOS
if [[ "$OSTYPE" != "darwin"* ]]; then
    print_error "This script is designed for macOS only!"
    exit 1
fi

# Get the directory where the script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

print_status "Working directory: $SCRIPT_DIR"

# Check if build.gradle exists
if [[ ! -f "build.gradle" ]]; then
    print_error "build.gradle not found! Make sure you're in the project root directory."
    exit 1
fi

# Function to find the best Java 8 JDK (excluding browser plugins)
find_java8_jdk() {
    # Get all Java 8 installations, excluding browser plugin
    local java8_homes=$(/usr/libexec/java_home -V 2>&1 | grep "1\.8\." | grep -v "JavaAppletPlugin")
    
    if [[ -z "$java8_homes" ]]; then
        return 1
    fi
    
    # Priority order for Java 8 JDKs
    local preferred_vendors=("Eclipse Temurin" "OpenLogic-OpenJDK" "Oracle Corporation" "BellSoft" "Amazon" "Azul" "AdoptOpenJDK")
    
    # Try to find preferred vendors first
    for vendor in "${preferred_vendors[@]}"; do
        local java_home=$(echo "$java8_homes" | grep "$vendor" | head -n 1 | awk -F' ' '{print $NF}')
        if [[ -n "$java_home" && -d "$java_home" && -f "$java_home/bin/javac" ]]; then
            echo "$java_home"
            return 0
        fi
    done
    
    # Fallback: get any Java 8 JDK that has javac (compiler)
    while IFS= read -r line; do
        local java_home=$(echo "$line" | awk -F' ' '{print $NF}')
        if [[ -n "$java_home" && -d "$java_home" && -f "$java_home/bin/javac" ]]; then
            echo "$java_home"
            return 0
        fi
    done <<< "$java8_homes"
    
    return 1
}

# Set JAVA_HOME to Java 8 JDK
print_status "Searching for Java 8 JDK..."

JAVA8_HOME=$(find_java8_jdk)
if [[ -n "$JAVA8_HOME" ]]; then
    export JAVA_HOME="$JAVA8_HOME"
    
    # Verify it's actually a JDK (has javac)
    if [[ -f "$JAVA_HOME/bin/javac" ]]; then
        print_success "Java 8 JDK found and set: $JAVA_HOME"
        
        # Show the vendor/version info
        VENDOR_INFO=$(/usr/libexec/java_home -V 2>&1 | grep -F "$JAVA_HOME" | head -n 1)
        if [[ -n "$VENDOR_INFO" ]]; then
            VENDOR_NAME=$(echo "$VENDOR_INFO" | sed 's/.*"\([^"]*\)".*/\1/' | head -n 1)
            if [[ -n "$VENDOR_NAME" ]]; then
                print_status "Vendor: $VENDOR_NAME"
            fi
        fi
    else
        print_error "Found Java 8 but it's not a JDK (missing javac compiler)!"
        print_error "Path: $JAVA_HOME"
        exit 1
    fi
else
    print_error "No Java 8 JDK found!"
    print_error "Available Java installations:"
    /usr/libexec/java_home -V 2>&1 | grep -E "(1\.8\.|Matching Java)"
    echo ""
    print_error "Please install Java 8 JDK using one of these methods:"
    print_error "1. Eclipse Temurin 8: brew install --cask temurin8"
    print_error "2. Oracle JDK 8: Download from Oracle website"
    print_error "3. OpenJDK 8: brew install --cask openjdk8"
    print_error "4. SDKMAN: sdk install java 8.0.432-tem"
    exit 1
fi

# Verify Java version and JDK capabilities
JAVA_VERSION=$("$JAVA_HOME/bin/java" -version 2>&1 | head -n 1 | cut -d'"' -f2)
JAVAC_VERSION=$("$JAVA_HOME/bin/javac" -version 2>&1 | cut -d' ' -f2)

print_status "Using Java runtime version: $JAVA_VERSION"
print_status "Using Java compiler version: $JAVAC_VERSION"

# Verify it's actually Java 8
if [[ "$JAVA_VERSION" == 1.8.* ]]; then
    print_success "✓ Confirmed Java 8 JDK with compiler"
else
    print_error "Expected Java 8 but got version: $JAVA_VERSION"
    exit 1
fi

# Make gradlew executable
if [[ -f "gradlew" ]]; then
    chmod +x gradlew
    print_status "Made gradlew executable"
else
    print_error "gradlew not found!"
    exit 1
fi

# Option to do a clean build
CLEAN_BUILD="${1:-}"
if [[ "$CLEAN_BUILD" == "--clean" ]]; then
    print_status "Performing clean build..."
    ./gradlew clean
else
    print_status "Using cached build (use --clean flag for full rebuild)"
fi

# Build the mod with aggressive caching and parallel compilation
print_status "Building FDPClient mod..."
print_status "Using parallel compilation and build cache..."

# Gradle build flags for faster compilation:
# --build-cache: Use build cache for faster incremental builds
# --parallel: Use parallel compilation
# -x test: Skip tests
# -Dorg.gradle.workers.max=<cores>: Use all CPU cores
# -Dorg.gradle.caching=true: Enable build caching
# -Dorg.gradle.parallel=true: Enable parallel builds

GRADLE_OPTS="-Xmx4096m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
export GRADLE_OPTS

NUM_CORES=$(sysctl -n hw.ncpu)
print_status "Using $NUM_CORES CPU cores for parallel compilation"

if ./gradlew build \
    --build-cache \
    --parallel \
    --max-workers=$NUM_CORES \
    -x test \
    -Dorg.gradle.caching=true \
    -Dorg.gradle.parallel=true \
    -Dorg.gradle.workers.max=$NUM_CORES; then
    print_success "Build completed successfully!"
else
    print_error "Build failed!"
    exit 1
fi

# Find the built mod file
BUILD_DIR="$SCRIPT_DIR/build/libs"
MOD_FILE=""

if [[ -d "$BUILD_DIR" ]]; then
    # Look for the main mod file (not sources or javadoc)
    MOD_FILE=$(find "$BUILD_DIR" -name "*.jar" -not -name "*-sources.jar" -not -name "*-javadoc.jar" | head -n 1)
    
    if [[ -n "$MOD_FILE" ]]; then
        MOD_FILENAME=$(basename "$MOD_FILE")
        print_success "Mod built successfully: $MOD_FILENAME"
        print_status "Location: $MOD_FILE"
        
        # Get file size
        FILE_SIZE=$(du -h "$MOD_FILE" | cut -f1)
        print_status "File size: $FILE_SIZE"
        
        # Open Finder and select the mod file
        print_status "Opening Finder and selecting the mod file..."
        osascript -e "tell application \"Finder\" to reveal POSIX file \"$MOD_FILE\""
        osascript -e "tell application \"Finder\" to activate"
        
        print_success "Build process completed!"
        print_status "The mod file has been selected in Finder."
        
    else
        print_warning "Build completed but no mod file found in $BUILD_DIR"
        # Still open the build directory
        open "$BUILD_DIR"
    fi
else
    print_error "Build directory not found: $BUILD_DIR"
    exit 1
fi

# Optional: Show build summary
echo ""
print_status "=== Build Summary ==="
print_status "Project: FDPClient"
print_status "Java Runtime: $JAVA_VERSION"
print_status "Java Compiler: $JAVAC_VERSION"
print_status "JAVA_HOME: $JAVA_HOME"
print_status "Build Directory: $BUILD_DIR"
if [[ -n "$MOD_FILE" ]]; then
    print_status "Mod File: $MOD_FILENAME ($FILE_SIZE)"
fi
print_status "===================="

echo ""
print_success "🎉 All done! Your FDPClient mod is ready to use!"