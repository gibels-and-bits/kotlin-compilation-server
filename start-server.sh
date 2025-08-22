#!/bin/bash

# Kotlin Compilation Server Startup Script
# This script ensures only one instance of the server runs at a time

echo "==================================="
echo "Kotlin Compilation Server Manager"
echo "==================================="
echo ""

# Function to kill all existing processes
cleanup_processes() {
    echo "🔍 Checking for existing server processes..."
    
    # Count existing processes
    COUNT=$(ps aux | grep -E 'ApplicationKt|gradle.*run|KotlinCompileDaemon|GradleDaemon' | grep -v grep | wc -l)
    
    if [ "$COUNT" -gt 0 ]; then
        echo "⚠️  Found $COUNT existing process(es). Cleaning up..."
        
        # Kill ApplicationKt (the main server)
        pkill -f 'ApplicationKt' 2>/dev/null
        
        # Kill gradle run processes
        pkill -f 'gradle.*run' 2>/dev/null
        
        # Kill Kotlin compiler daemon
        pkill -f 'KotlinCompileDaemon' 2>/dev/null
        
        # Kill Gradle daemon
        pkill -f 'GradleDaemon' 2>/dev/null
        
        # Wait for processes to terminate
        sleep 3
        
        # Force kill if still running
        pkill -9 -f 'ApplicationKt' 2>/dev/null
        pkill -9 -f 'gradle.*run' 2>/dev/null
        pkill -9 -f 'KotlinCompileDaemon' 2>/dev/null
        pkill -9 -f 'GradleDaemon' 2>/dev/null
        
        echo "✅ Cleaned up old processes"
    else
        echo "✅ No existing processes found"
    fi
}

# Function to start the server
start_server() {
    echo ""
    echo "🚀 Starting Kotlin Compilation Server..."
    echo "   Port: 3001"
    echo "   Directory: ~/kotlin-compilation-server"
    echo ""
    
    cd ~/kotlin-compilation-server || exit 1
    
    # Clear old logs
    if [ -f server.log ]; then
        mv server.log "server.log.backup.$(date +%Y%m%d_%H%M%S)"
        echo "📁 Backed up old server.log"
    fi
    
    # Start the server
    nohup ./gradlew run > server.log 2>&1 &
    SERVER_PID=$!
    
    echo "⏳ Server starting with PID: $SERVER_PID"
    echo "   Waiting for server to initialize..."
    
    # Wait and check if server started successfully
    sleep 10
    
    # Check if server is running
    if ps -p $SERVER_PID > /dev/null; then
        # Check if server is responding
        if curl -s -o /dev/null -w "%{http_code}" http://localhost:3001/health | grep -q "200"; then
            echo "✅ Server is running and healthy!"
            echo ""
            echo "📊 Server Status:"
            curl -s http://localhost:3001/health | python3 -m json.tool 2>/dev/null || curl -s http://localhost:3001/health
            echo ""
            echo "📝 Logs: tail -f ~/kotlin-compilation-server/server.log"
            echo "🛑 Stop: pkill -f ApplicationKt"
        else
            echo "⚠️  Server started but not responding on port 3001"
            echo "   Check logs: tail ~/kotlin-compilation-server/server.log"
        fi
    else
        echo "❌ Server failed to start!"
        echo "   Check logs for errors:"
        tail -20 ~/kotlin-compilation-server/server.log
        exit 1
    fi
}

# Main execution
cleanup_processes
start_server

echo ""
echo "==================================="
echo "Server management complete!"
echo "====================================="