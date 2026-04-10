#!/bin/bash

# FDPClient Build Diagnostic Script
# This script helps diagnose build issues

echo "🔍 FDPClient Build Diagnostics"
echo "=============================="

# Check Java
echo "Java version:"
java -version

echo ""
echo "JAVA_HOME: $JAVA_HOME"

echo ""
echo "Gradle wrapper:"
if [[ -f "gradlew" ]]; then
    echo "✅ gradlew exists"
    chmod +x gradlew
    echo "Gradle version:"
    ./gradlew --version | head -5
else
    echo "❌ gradlew not found"
fi

echo ""
echo "Build files:"
echo "build.gradle exists: $(test -f build.gradle && echo "✅ YES" || echo "❌ NO")"
echo "settings.gradle exists: $(test -f settings.gradle && echo "✅ YES" || echo "❌ NO")"

echo ""
echo "Current directory contents:"
ls -la | head -10

echo ""
echo "Gradle tasks available:"
./gradlew tasks --group="build" | grep -E "(jar|shadowJar|build|reobf)"

echo ""
echo "Build directory status:"
if [[ -d "build" ]]; then
    echo "✅ Build directory exists"
    echo "Build directory contents:"
    find build -name "*.jar" 2>/dev/null || echo "No jar files found"
else
    echo "❌ Build directory does not exist"
fi

echo ""
echo "Testing simple build..."
echo "Running: ./gradlew clean shadowJar --info"
./gradlew clean shadowJar --info | tail -20

echo ""
echo "Final check - looking for jar files:"
find . -name "*.jar" -not -path "./gradle/*" 2>/dev/null | head -10

echo ""
echo "Diagnostics complete!"