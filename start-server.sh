#!/bin/bash

# Kotlin Compilation Server - Unified Start Script
# Usage: ./start-server.sh [--debug]

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Parse arguments
DEBUG_MODE=false
if [[ "$1" == "--debug" ]]; then
    DEBUG_MODE=true
fi

# Configuration
if [ "$DEBUG_MODE" = true ]; then
    SERVER_URL="http://localhost:3001"
    ANDROID_SERVER="http://localhost:8080"
    SERVER_LABEL="DEBUG MODE"
    JAVA_OPTS="-Ddebug=true"
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
echo ""

# Start server in background
java $JAVA_OPTS -cp "build/install/kotlin-compilation-server/lib/*" com.example.compilation.ApplicationKt > /tmp/kotlin-server.log 2>&1 &
SERVER_PID=$!

# Wait for server to start
echo -e "${YELLOW}Waiting for server to start...${NC}"
for i in {1..10}; do
    if curl -s "$SERVER_URL/health" > /dev/null 2>&1; then
        echo -e "${GREEN}Server started successfully!${NC}"
        break
    fi
    if [ $i -eq 10 ]; then
        echo -e "${RED}Server failed to start. Check /tmp/kotlin-server.log${NC}"
        exit 1
    fi
    sleep 1
done

# Step 5: Start the CLI monitor
echo ""
echo -e "${CYAN}═══════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}Server Monitor Active - Press Ctrl+C to stop everything${NC}"
echo -e "${CYAN}═══════════════════════════════════════════════════════${NC}"
echo ""

# Monitor function
monitor_server() {
    local last_cache_count=-1
    local last_status=""
    
    while true; do
        # Clear screen and show header
        clear
        echo -e "${CYAN}╔════════════════════════════════════════════════════╗${NC}"
        echo -e "${CYAN}║     Kotlin Compilation Server Monitor              ║${NC}"
        echo -e "${CYAN}║     Mode: ${SERVER_LABEL}                          ║${NC}"
        echo -e "${CYAN}╚════════════════════════════════════════════════════╝${NC}"
        echo ""
        echo -e "${BLUE}Time: $(date '+%Y-%m-%d %H:%M:%S')${NC}"
        echo -e "${BLUE}Server: $SERVER_URL${NC}"
        echo ""
        
        # Check health
        if HEALTH=$(curl -s "$SERVER_URL/health" 2>/dev/null); then
            # Parse health response
            STATUS=$(echo "$HEALTH" | grep -o '"status":"[^"]*"' | cut -d'"' -f4)
            CACHE_SIZE=$(echo "$HEALTH" | grep -o '"cache_size":[0-9]*' | cut -d':' -f2)
            
            # Show status
            if [ "$STATUS" = "healthy" ]; then
                echo -e "${GREEN}● Server Status: HEALTHY${NC}"
            else
                echo -e "${RED}● Server Status: $STATUS${NC}"
            fi
            
            echo -e "${BLUE}● Cached Interpreters: $CACHE_SIZE${NC}"
            echo ""
            
            # Get detailed cache status
            if CACHE_STATUS=$(curl -s "$SERVER_URL/cache/status" 2>/dev/null); then
                echo -e "${CYAN}Cached Teams:${NC}"
                echo -e "${CYAN}─────────────────────────────────────────${NC}"
                
                # Parse and display teams
                echo "$CACHE_STATUS" | grep -o '"teamId":"[^"]*"' | cut -d'"' -f4 | while read -r team; do
                    if [ ! -z "$team" ]; then
                        COMPILED_AT=$(echo "$CACHE_STATUS" | grep -A1 "\"$team\"" | grep "compiledAt" | cut -d'"' -f4)
                        echo -e "  ${GREEN}✓${NC} $team"
                        if [ ! -z "$COMPILED_AT" ]; then
                            echo -e "    ${CYAN}└─ $COMPILED_AT${NC}"
                        fi
                    fi
                done
                
                # If no teams
                if [ "$CACHE_SIZE" = "0" ] || [ -z "$CACHE_SIZE" ]; then
                    echo -e "  ${YELLOW}No interpreters cached yet${NC}"
                fi
            fi
            
            # Show event if cache count changed
            if [ "$CACHE_SIZE" != "$last_cache_count" ] && [ "$last_cache_count" != "-1" ]; then
                echo ""
                echo -e "${YELLOW}▶ Cache updated: $last_cache_count → $CACHE_SIZE interpreters${NC}"
            fi
            last_cache_count=$CACHE_SIZE
            
        else
            echo -e "${RED}● Server Status: OFFLINE${NC}"
            echo -e "${RED}Cannot connect to server at $SERVER_URL${NC}"
        fi
        
        # Show log tail
        echo ""
        echo -e "${CYAN}Recent Activity:${NC}"
        echo -e "${CYAN}─────────────────────────────────────────${NC}"
        if [ -f /tmp/kotlin-server.log ]; then
            tail -n 5 /tmp/kotlin-server.log | while IFS= read -r line; do
                if echo "$line" | grep -q "ERROR"; then
                    echo -e "${RED}$line${NC}"
                elif echo "$line" | grep -q "WARN"; then
                    echo -e "${YELLOW}$line${NC}"
                elif echo "$line" | grep -q "INFO.*Compiling\|Successfully"; then
                    echo -e "${GREEN}$line${NC}"
                else
                    echo "$line"
                fi
            done
        fi
        
        # Footer
        echo ""
        echo -e "${CYAN}═══════════════════════════════════════════════════════${NC}"
        echo -e "${YELLOW}Press Ctrl+C to stop server and exit${NC}"
        
        # Wait before refresh
        sleep 2
    done
}

# Start monitoring
monitor_server