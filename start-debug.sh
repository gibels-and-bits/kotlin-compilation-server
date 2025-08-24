#!/bin/bash
# Start compilation server in debug mode (uses localhost instead of IP addresses)

echo "Starting Kotlin Compilation Server in DEBUG mode..."
echo "Android server expected at: http://localhost:8080"
echo "This server will run at: http://localhost:3001"

# Run with debug flag
java -Ddebug=true -jar build/libs/compilation-server-all.jar