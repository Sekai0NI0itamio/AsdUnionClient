#!/bin/bash

# AsdUnion Build and Install Script for macOS
# This script builds the mod and automatically installs it to PrismLauncher
# Usage: ./build_and_install.sh [--clean]

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

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

# Get the directory where the script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Check if 1.8.9-forge directory exists
if [[ ! -d "1.8.9-forge" ]]; then
    print_error "1.8.9-forge directory not found!"
    print_error "Make sure you're in the project root directory where 1.8.9-forge is located."
    exit 1
fi

# Navigate to 1.8.9-forge
cd "1.8.9-forge"
print_status "Working directory: $(pwd)"

# Function to find Java 8 JDK (excluding browser plugins)
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

# Handle clean build option
CLEAN_BUILD="${1:-}"
if [[ "$CLEAN_BUILD" == "--clean" ]]; then
    print_status "Performing clean build..."
    ./gradlew clean
else
    print_status "Using cached build (use --clean flag for full rebuild)"
fi

# Build the mod with fast optimization
print_status "Building AsdUnion mod with fast optimization..."
print_status "Using parallel compilation and aggressive caching..."

# Set optimized Gradle options
export GRADLE_OPTS="-Xmx4096m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+ParallelRefProcEnabled"

NUM_CORES=$(sysctl -n hw.ncpu)
print_status "Using $NUM_CORES CPU cores for parallel compilation"

# Build with maximum optimization - use shadowJar for mod building
print_status "Building mod with shadowJar task..."

# Debug: Check if we can build
print_status "Checking build environment..."
if [[ ! -f "build.gradle" ]]; then
    print_error "build.gradle not found!"
    exit 1
fi

print_status "Build.gradle found, proceeding with build..."

if ./gradlew shadowJar reobfShadowJar \
    --build-cache \
    --parallel \
    --max-workers=$NUM_CORES \
    -x test \
    -x javadoc \
    --configure-on-demand; then
    print_success "Build completed successfully!"
else
    print_error "ShadowJar build failed!"
    print_status "Trying fallback build task..."
    
    # Fallback to regular build task
    if ./gradlew build --build-cache --parallel --max-workers=$NUM_CORES -x test --configure-on-demand; then
        print_success "Fallback build succeeded!"
    else
        print_error "Fallback build also failed!"
        print_status "Running gradle build with verbose output to diagnose..."
        ./gradlew build --info --stacktrace | tail -20
        exit 1
    fi
fi

# Find the built mod file
BUILD_DIR="$SCRIPT_DIR/1.8.9-forge/build/libs"
MOD_FILE=""

if [[ -d "$BUILD_DIR" ]]; then
    # Look for the mod file (specifically AsdUnion-b12.jar)
    MOD_FILE=$(find "$BUILD_DIR" -name "AsdUnion-b12.jar" | head -n 1)
    
    # If specific version not found, look for any AsdUnion jar
    if [[ -z "$MOD_FILE" ]]; then
        MOD_FILE=$(find "$BUILD_DIR" -name "AsdUnion*.jar" -not -name "*-sources.jar" -not -name "*-javadoc.jar" -not -name "*-dev.jar" | head -n 1)
    fi
    
    # If no AsdUnion jar found, look for any jar file
    if [[ -z "$MOD_FILE" ]]; then
        MOD_FILE=$(find "$BUILD_DIR" -name "*.jar" -not -name "*-sources.jar" -not -name "*-javadoc.jar" -not -name "*-dev.jar" | head -n 1)
    fi
    
    if [[ -n "$MOD_FILE" && -f "$MOD_FILE" ]]; then
        MOD_FILENAME=$(basename "$MOD_FILE")
        FILE_SIZE=$(du -h "$MOD_FILE" | cut -f1)
        print_success "Mod built successfully: $MOD_FILENAME ($FILE_SIZE)"
        print_status "Location: $MOD_FILE"
        
        # Get the user's home directory
        USER_HOME="$HOME"
        
        # Define the target PrismLauncher directory
        PRISMLAUNCHER_INST_DIR="$USER_HOME/Library/Application Support/PrismLauncher/instances/1.8.9"
        PRISMLAUNCHER_MODS_DIR="$PRISMLAUNCHER_INST_DIR/minecraft/mods"
        
        print_status "Target installation directory: $PRISMLAUNCHER_MODS_DIR"
        
        # Create the mods directory if it doesn't exist
        mkdir -p "$PRISMLAUNCHER_MODS_DIR"
        
        # Copy the mod file to the mods directory
        print_status "Installing mod to PrismLauncher mods directory..."
        
        # Backup any existing file
        if [[ -f "$PRISMLAUNCHER_MODS_DIR/$MOD_FILENAME" ]]; then
            BACKUP_FILE="$PRISMLAUNCHER_MODS_DIR/$MOD_FILENAME.backup.$(date +%Y%m%d%H%M%S)"
            print_warning "Backing up existing $MOD_FILENAME to $BACKUP_FILE"
            cp "$PRISMLAUNCHER_MODS_DIR/$MOD_FILENAME" "$BACKUP_FILE"
        fi
        
        # Copy the mod file
        cp "$MOD_FILE" "$PRISMLAUNCHER_MODS_DIR/"
        
        # Verify the copy
        if [[ -f "$PRISMLAUNCHER_MODS_DIR/$MOD_FILENAME" ]]; then
            print_success "Mod successfully installed to PrismLauncher!"
            print_status "Installation path: $PRISMLAUNCHER_MODS_DIR/$MOD_FILENAME"
        else
            print_error "Failed to install mod to PrismLauncher!"
            exit 1
        fi
        
        # Open Finder and select the installed mod file
        print_status "Opening Finder and selecting the installed mod file..."
        osascript -e "tell application \"Finder\" to reveal POSIX file \"$PRISMLAUNCHER_MODS_DIR/$MOD_FILENAME\""
        osascript -e "tell application \"Finder\" to activate"
        
        print_success "Build and installation completed!"
        print_status "Your AsdUnion mod is ready to use in PrismLauncher."
        
    else
        print_error "No mod file found in $BUILD_DIR"
        print_status "Available files:"
        find "$BUILD_DIR" -name "*.jar" -o -name "*.zip" | head -10
        exit 1
    fi
else
    print_error "Build directory not found: $BUILD_DIR"
    exit 1
fi

# Display build summary
echo ""
print_status "=== Build and Installation Summary ==="
print_status "Project: AsdUnion (1.8.9 Forge)"
print_status "Java Runtime: $JAVA_VERSION"
print_status "Java Compiler: $JAVAC_VERSION"
print_status "JAVA_HOME: $JAVA_HOME"
print_status "Build Directory: $BUILD_DIR"
print_status "Mod File: $MOD_FILENAME ($FILE_SIZE)"
print_status "Installation Path: $PRISMLAUNCHER_MODS_DIR/$MOD_FILENAME"
print_status "===================="

echo ""
print_success "🎉 All done! Your AsdUnion mod is built and installed to PrismLauncher!"
print_status "You can now launch PrismLauncher and load the 1.8.9 instance."
