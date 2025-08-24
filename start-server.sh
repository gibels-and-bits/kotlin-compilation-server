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

# ASCII Printer Simulator
ASCII_RECEIPT="/tmp/ascii-receipt.txt"
ASCII_WIDTH=40  # Standard receipt width

# Function to render ASCII receipt
render_ascii_receipt() {
    local commands="$1"
    local output=""
    local current_align="LEFT"
    local current_bold=false
    local current_size="NORMAL"
    
    # Clear receipt file
    > "$ASCII_RECEIPT"
    
    # Process each command
    echo "$commands" | jq -c '.[]' 2>/dev/null | while IFS= read -r cmd; do
        local type=$(echo "$cmd" | jq -r '.type // ""')
        
        case "$type" in
            "ADD_TEXT")
                local text=$(echo "$cmd" | jq -r '.text // ""')
                local line=""
                
                # Apply alignment
                case "$current_align" in
                    "CENTER")
                        local padding=$(( (ASCII_WIDTH - ${#text}) / 2 ))
                        [ $padding -gt 0 ] && line=$(printf "%*s" $padding "")
                        line="${line}${text}"
                        ;;
                    "RIGHT")
                        line=$(printf "%*s" $ASCII_WIDTH "$text")
                        ;;
                    *)
                        line="$text"
                        ;;
                esac
                
                # Apply size (double height for large)
                if [ "$current_size" = "LARGE" ]; then
                    echo "$line" >> "$ASCII_RECEIPT"
                    echo "$line" >> "$ASCII_RECEIPT"
                else
                    echo "$line" >> "$ASCII_RECEIPT"
                fi
                ;;
                
            "ADD_TEXT_STYLE")
                current_bold=$(echo "$cmd" | jq -r '.bold // false')
                current_size=$(echo "$cmd" | jq -r '.size // "NORMAL"')
                ;;
                
            "ADD_TEXT_ALIGN")
                current_align=$(echo "$cmd" | jq -r '.alignment // "LEFT"')
                ;;
                
            "ADD_QR_CODE")
                local data=$(echo "$cmd" | jq -r '.data // ""')
                echo "┌────────────────────────┐" >> "$ASCII_RECEIPT"
                echo "│  [QR: ${data:0:18}...]  │" >> "$ASCII_RECEIPT"
                echo "│  ████ ▄▄▄▄▄ ██▄ ████  │" >> "$ASCII_RECEIPT"
                echo "│  ████ █   █ ███ ████  │" >> "$ASCII_RECEIPT"
                echo "│  ████ █▄▄▄█ ▄▄█ ████  │" >> "$ASCII_RECEIPT"
                echo "└────────────────────────┘" >> "$ASCII_RECEIPT"
                ;;
                
            "ADD_BARCODE")
                local data=$(echo "$cmd" | jq -r '.data // ""')
                echo "║║║║║║║║║║║║║║║║║║║║║║║║" >> "$ASCII_RECEIPT"
                echo "    $data    " >> "$ASCII_RECEIPT"
                ;;
                
            "ADD_FEED_LINE")
                local lines=$(echo "$cmd" | jq -r '.lines // 1')
                for ((i=0; i<lines; i++)); do
                    echo "" >> "$ASCII_RECEIPT"
                done
                ;;
                
            "CUT_PAPER")
                echo "" >> "$ASCII_RECEIPT"
                echo "════════════════════════════════════════" >> "$ASCII_RECEIPT"
                echo "         ✂ CUT HERE ✂" >> "$ASCII_RECEIPT"
                echo "════════════════════════════════════════" >> "$ASCII_RECEIPT"
                ;;
        esac
    done
}

# Monitor function
monitor_server() {
    local last_cache_count=-1
    local last_status=""
    local last_log_line=""
    local android_available=false
    
    # Check if Android server is available
    if curl -s "$ANDROID_SERVER/health" --max-time 2 > /dev/null 2>&1; then
        android_available=true
    fi
    
    while true; do
        # Clear screen and show header
        clear
        echo -e "${CYAN}╔════════════════════════════════════════════════════╗${NC}"
        echo -e "${CYAN}║     Kotlin Compilation Server Monitor              ║${NC}"
        echo -e "${CYAN}║     Mode: ${SERVER_LABEL}                          ║${NC}"
        echo -e "${CYAN}╚════════════════════════════════════════════════════╝${NC}"
        echo ""
        echo -e "${BLUE}Time: $(date '+%Y-%m-%d %H:%M:%S')${NC}"
        echo ""
        
        # Server Status Section
        echo -e "${CYAN}Server Status:${NC}"
        echo -e "${CYAN}─────────────────────────────────────────${NC}"
        
        # Check Compilation Server
        if HEALTH=$(curl -s "$SERVER_URL/health" 2>/dev/null); then
            STATUS=$(echo "$HEALTH" | grep -o '"status":"[^"]*"' | cut -d'"' -f4)
            CACHE_SIZE=$(echo "$HEALTH" | grep -o '"cache_size":[0-9]*' | cut -d':' -f2)
            
            if [ "$STATUS" = "healthy" ]; then
                echo -e "${GREEN}● Compilation Server: ONLINE${NC} - $SERVER_URL"
            else
                echo -e "${RED}● Compilation Server: $STATUS${NC} - $SERVER_URL"
            fi
            echo -e "  ${BLUE}Cached Interpreters: $CACHE_SIZE${NC}"
        else
            echo -e "${RED}● Compilation Server: OFFLINE${NC} - $SERVER_URL"
            CACHE_SIZE=0
        fi
        
        # Check Android Server
        if curl -s "$ANDROID_SERVER/health" --max-time 2 > /dev/null 2>&1; then
            echo -e "${GREEN}● Android Server: ONLINE${NC} - $ANDROID_SERVER"
            android_available=true
        else
            echo -e "${YELLOW}● Android Server: OFFLINE${NC} - $ANDROID_SERVER"
            echo -e "  ${CYAN}ASCII Printer Mode Active${NC}"
            android_available=false
        fi
        
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
                        echo -e "    ${CYAN}└─ Compiled: ${COMPILED_AT:11:8}${NC}"
                    fi
                fi
            done
            
            # If no teams
            if [ "$CACHE_SIZE" = "0" ] || [ -z "$CACHE_SIZE" ]; then
                echo -e "  ${YELLOW}No interpreters cached yet${NC}"
            fi
        fi
        
        # Activity Log Section
        echo ""
        echo -e "${CYAN}Recent Activity:${NC}"
        echo -e "${CYAN}─────────────────────────────────────────${NC}"
        
        if [ -f /tmp/kotlin-server.log ]; then
            # Parse log for compilation and execution events
            tail -n 10 /tmp/kotlin-server.log | while IFS= read -r line; do
                if echo "$line" | grep -q "Compiling code for team"; then
                    TEAM=$(echo "$line" | grep -o 'team [^ ]*' | cut -d' ' -f2)
                    echo -e "${YELLOW}▶ Compiling: Team $TEAM${NC}"
                elif echo "$line" | grep -q "Successfully compiled"; then
                    TEAM=$(echo "$line" | grep -o 'team [^ ]*' | cut -d' ' -f2)
                    echo -e "${GREEN}✓ Compiled: Team $TEAM${NC}"
                elif echo "$line" | grep -q "Compilation failed"; then
                    echo -e "${RED}✗ Compilation Failed${NC}"
                elif echo "$line" | grep -q "Executing interpreter for team"; then
                    TEAM=$(echo "$line" | grep -o 'team [^ ]*' | cut -d' ' -f2)
                    echo -e "${BLUE}▶ Printing: Team $TEAM${NC}"
                    
                    # If Android is offline, capture and show ASCII receipt
                    if [ "$android_available" = false ]; then
                        # Wait a moment for execution to complete
                        sleep 0.5
                        
                        # Try to get the last execution response from log
                        LAST_EXEC=$(tail -n 20 /tmp/kotlin-server.log | grep -A10 "Generated.*commands" | tail -n 1)
                        if [ ! -z "$LAST_EXEC" ]; then
                            # Extract JSON commands and render ASCII
                            COMMANDS=$(echo "$LAST_EXEC" | sed 's/.*Generated//' | sed 's/commands.*/]/')
                            if [ ! -z "$COMMANDS" ]; then
                                render_ascii_receipt "$COMMANDS"
                            fi
                        fi
                    fi
                elif echo "$line" | grep -q "ERROR"; then
                    echo -e "${RED}✗ Error: $(echo "$line" | sed 's/.*ERROR//')${NC}"
                fi
            done
        fi
        
        # ASCII Receipt Display (if Android offline)
        if [ "$android_available" = false ] && [ -f "$ASCII_RECEIPT" ] && [ -s "$ASCII_RECEIPT" ]; then
            echo ""
            echo -e "${CYAN}ASCII Receipt Preview:${NC}"
            echo -e "${CYAN}┌────────────────────────────────────────┐${NC}"
            tail -n 15 "$ASCII_RECEIPT" | while IFS= read -r line; do
                printf "${CYAN}│${NC} %-38s ${CYAN}│${NC}\n" "${line:0:38}"
            done
            echo -e "${CYAN}└────────────────────────────────────────┘${NC}"
            echo -e "  ${YELLOW}(Showing last 15 lines)${NC}"
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