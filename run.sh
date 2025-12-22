#!/bin/bash
echo "Mini-IDE - Starting..."
echo

# Check if gradle-wrapper.jar exists
if [ ! -f "gradle/wrapper/gradle-wrapper.jar" ]; then
    echo "Downloading Gradle wrapper..."
    mkdir -p gradle/wrapper
    curl -L -o "gradle/wrapper/gradle-wrapper.jar" "https://github.com/gradle/gradle/raw/v8.5.0/gradle/wrapper/gradle-wrapper.jar"
fi

# Make gradlew executable
chmod +x gradlew

# Run the application
./gradlew run "$@"
