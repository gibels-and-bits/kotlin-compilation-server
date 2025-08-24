#!/bin/bash

# Kotlin Compilation Server - Start Script
# Usage: ./start-server.sh [--debug]

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
MAGENTA='\033[0;35m'
NC='\033[0m' # No Color

# Parse arguments
DEBUG_MODE=false

for arg in "$@"; do
    case $arg in
        --debug)
            DEBUG_MODE=true
            ;;
    esac
done

# Configuration
if [ "$DEBUG_MODE" = true ]; then
    SERVER_URL="http://localhost:3001"
    SERVER_LABEL="DEBUG MODE"
    JAVA_OPTS="-Ddebug=true"
    
    # Prompt for Android server IP in debug mode
    echo -e "${CYAN}Enter Android server IP (or press Enter for localhost:8080):${NC}"
    read -r ANDROID_IP
    if [ -z "$ANDROID_IP" ]; then
        ANDROID_SERVER="http://localhost:8080"
    else
        ANDROID_SERVER="http://$ANDROID_IP:8080"
    fi
else
    SERVER_URL="http://192.168.29.3:3001"
    ANDROID_SERVER="http://192.168.29.2:8080"
    SERVER_LABEL="PRODUCTION"
    JAVA_OPTS=""
fi

# Cleanup function
cleanup() {
    echo -e "\n${YELLOW}Shutting down...${NC}"
    
    # Kill the server process if it exists
    if [ ! -z "$SERVER_PID" ]; then
        echo -e "${YELLOW}Stopping compilation server (PID: $SERVER_PID)...${NC}"
        kill $SERVER_PID 2>/dev/null
        wait $SERVER_PID 2>/dev/null
    fi
    
    # Kill any orphaned processes on port 3001
    lsof -ti:3001 | xargs -r kill 2>/dev/null
    
    echo -e "${GREEN}Shutdown complete${NC}"
    exit 0
}

# Set up trap to cleanup on exit
trap cleanup EXIT INT TERM

# Header
clear
echo -e "${CYAN}╔════════════════════════════════════════════════════╗${NC}"
echo -e "${CYAN}║     Kotlin Compilation Server - ${SERVER_LABEL}     ║${NC}"
echo -e "${CYAN}╚════════════════════════════════════════════════════╝${NC}"
echo ""

# Step 1: Check for existing processes
echo -e "${YELLOW}Checking for existing processes...${NC}"
if lsof -Pi :3001 -sTCP:LISTEN -t >/dev/null ; then
    echo -e "${RED}Port 3001 is already in use. Killing existing process...${NC}"
    lsof -ti:3001 | xargs kill -9 2>/dev/null
    sleep 1
fi

# Step 2: Build the project
echo -e "${YELLOW}Building project...${NC}"
./gradlew build --quiet
if [ $? -ne 0 ]; then
    echo -e "${RED}Build failed!${NC}"
    exit 1
fi
echo -e "${GREEN}Build successful${NC}"

# Step 3: Install distribution
echo -e "${YELLOW}Creating distribution...${NC}"
./gradlew installDist --quiet
if [ $? -ne 0 ]; then
    echo -e "${RED}Distribution creation failed!${NC}"
    exit 1
fi
echo -e "${GREEN}Distribution created${NC}"

# Step 4: Start the server
echo -e "${YELLOW}Starting server...${NC}"
echo -e "${BLUE}Server URL: $SERVER_URL${NC}"
echo -e "${BLUE}Android Server: $ANDROID_SERVER${NC}"

# Check if Android server is available
ANDROID_STATUS="ONLINE"
if ! curl -s "$ANDROID_SERVER/health" --max-time 2 > /dev/null 2>&1; then
    ANDROID_STATUS="OFFLINE"
    echo -e "${YELLOW}Android server not available - ASCII printer mode enabled${NC}"
    export ANDROID_OFFLINE=true
fi

echo ""

# Start server in background
if [ "$ANDROID_STATUS" = "OFFLINE" ]; then
    ANDROID_OFFLINE=true java $JAVA_OPTS -cp "build/install/kotlin-compilation-server/lib/*" com.example.compilation.ApplicationKt > /tmp/kotlin-server.log 2>&1 &
else
    java $JAVA_OPTS -cp "build/install/kotlin-compilation-server/lib/*" com.example.compilation.ApplicationKt > /tmp/kotlin-server.log 2>&1 &
fi
SERVER_PID=$!

# Wait for server to start
echo -e "${YELLOW}Waiting for server to start...${NC}"
for i in {1..10}; do
    if curl -s "http://localhost:3001/health" > /dev/null 2>&1; then
        echo -e "${GREEN}Server started successfully!${NC}"
        break
    fi
    if [ $i -eq 10 ]; then
        echo -e "${RED}Server failed to start. Check /tmp/kotlin-server.log${NC}"
        exit 1
    fi
    sleep 1
done

# Step 5: Start the monitor
echo ""
echo -e "${CYAN}═══════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}Compilation Server Monitor - Press Ctrl+C to stop${NC}"
echo -e "${CYAN}═══════════════════════════════════════════════════════${NC}"
echo ""

# Monitor the logs with clean job tracking
monitor_logs() {
    echo -e "${CYAN}Server Status:${NC}"
    echo -e "  ${BLUE}Compilation Server:${NC} $SERVER_URL"
    echo -e "  ${BLUE}Android Server:${NC} $ANDROID_SERVER"
    echo ""
    
    # Check Android server status
    if curl -s "$ANDROID_SERVER/health" --max-time 2 > /dev/null 2>&1; then
        echo -e "  ${GREEN}✓ Android Server: ONLINE${NC}"
    else
        echo -e "  ${YELLOW}⚠ Android Server: OFFLINE (ASCII mode enabled)${NC}"
    fi
    echo ""
    echo -e "${CYAN}═══════════════════════════════════════════════════════${NC}"
    echo -e "${GREEN}Live Compilation Jobs:${NC}"
    echo -e "${CYAN}═══════════════════════════════════════════════════════${NC}"
    echo ""
    
    # Tail the log and format compilation events
    tail -f /tmp/kotlin-server.log | while IFS= read -r line; do
        # Extract timestamp if present
        timestamp=$(echo "$line" | grep -o '^[0-9]\{2\}:[0-9]\{2\}:[0-9]\{2\}' || echo "")
        
        # Compilation job received
        if echo "$line" | grep -q "KotlinCompilerService.*Compiling interpreter for team:"; then
            team=$(echo "$line" | sed 's/.*team: //')
            echo -e "${MAGENTA}[$timestamp] JOB RECEIVED${NC}  Team: ${CYAN}$team${NC}"
            
        # Compilation in progress (validation passed)
        elif echo "$line" | grep -q "KotlinCompilerService.*Successfully compiled"; then
            team=$(echo "$line" | sed 's/.*team: //')
            echo -e "${GREEN}[$timestamp] ✓ COMPILED${NC}    Team: ${CYAN}$team${NC}"
            
        # Compilation failed
        elif echo "$line" | grep -q "KotlinCompilerService.*Failed to compile"; then
            team=$(echo "$line" | sed 's/.*team //' | cut -d',' -f1)
            echo -e "${RED}[$timestamp] ✗ FAILED${NC}      Team: ${CYAN}$team${NC}"
            # Show error on next line if available
            if echo "$line" | grep -q "error:"; then
                error=$(echo "$line" | sed 's/.*error: //' | head -c 60)
                echo -e "                  ${RED}Error: $error...${NC}"
            fi
            
        # Execution/Print job
        elif echo "$line" | grep -q "KotlinCompilerService.*Executing interpreter"; then
            team=$(echo "$line" | sed 's/.*team: //' | cut -d',' -f1)
            round=$(echo "$line" | sed 's/.*round: //')
            echo -e "${BLUE}[$timestamp] → EXECUTING${NC}   Team: ${CYAN}$team${NC} (Round $round)"
            
        # Successful execution
        elif echo "$line" | grep -q "KotlinCompilerService.*Successfully executed"; then
            team=$(echo "$line" | sed 's/.*team //' | cut -d',' -f1)
            cmds=$(echo "$line" | sed 's/.*captured //' | cut -d' ' -f1)
            echo -e "${GREEN}[$timestamp] ✓ PRINTED${NC}     Team: ${CYAN}$team${NC} ($cmds commands)"
            
        # ASCII receipt saved notification
        elif echo "$line" | grep -q "ASCII receipt saved to:"; then
            filepath=$(echo "$line" | sed 's/.*saved to: //')
            echo -e "${YELLOW}[$timestamp] 📄 ASCII saved${NC} ${filepath}"
            
        # General errors
        elif echo "$line" | grep -q "ERROR.*KotlinCompilerService"; then
            msg=$(echo "$line" | sed 's/.*ERROR[^-]*- //' | head -c 80)
            echo -e "${RED}[$timestamp] ERROR${NC}         $msg"
        fi
    done
}

# Call the monitor function
monitor_logs