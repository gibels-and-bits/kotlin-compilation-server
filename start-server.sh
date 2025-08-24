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
GUI_MODE=false

for arg in "$@"; do
    case $arg in
        --debug)
            DEBUG_MODE=true
            ;;
        --gui)
            GUI_MODE=true
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
    # Kill HTTP server if running
    if [ ! -z "$HTTP_SERVER_PID" ]; then
        echo -e "${YELLOW}Stopping HTTP server...${NC}"
        kill $HTTP_SERVER_PID 2>/dev/null
    fi
    
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

# Step 5: Start the monitor
if [ "$GUI_MODE" = true ]; then
    # GUI Mode - Open HTML monitor
    echo ""
    echo -e "${CYAN}═══════════════════════════════════════════════════════${NC}"
    echo -e "${GREEN}Opening HTML Monitor in Browser${NC}"
    echo -e "${CYAN}═══════════════════════════════════════════════════════${NC}"
    echo ""
    
    # Start the log server for the monitor
    cd "$(dirname "$0")"
    python3 log-server.py > /dev/null 2>&1 &
    HTTP_SERVER_PID=$!
    
    # Open browser
    if [[ "$OSTYPE" == "darwin"* ]]; then
        open "http://localhost:8888/monitor.html"
    elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
        xdg-open "http://localhost:8888/monitor.html" 2>/dev/null || echo "Please open http://localhost:8888/monitor.html in your browser"
    fi
    
    echo -e "${BLUE}Monitor URL: http://localhost:8888/monitor.html${NC}"
    echo -e "${YELLOW}Server running. Press Ctrl+C to stop.${NC}"
    echo ""
    
    # Keep running
    while true; do
        sleep 1
    done
else
    # CLI Mode - Production style monitoring
    echo ""
    echo -e "${CYAN}═══════════════════════════════════════════════════════${NC}"
    echo -e "${GREEN}Server Monitor Active - Press Ctrl+C to stop everything${NC}"
    echo -e "${CYAN}═══════════════════════════════════════════════════════${NC}"
    echo ""
    
    if [ "$DEBUG_MODE" = false ]; then
        # Production mode - Simple live log
        monitor_production
    else
        # Debug mode - Full CLI monitor
        monitor_server
    fi
fi
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
    local last_activity_time=""
    
    # Check if Android server is available
    if curl -s "$ANDROID_SERVER/health" --max-time 2 > /dev/null 2>&1; then
        android_available=true
    fi
    
    while true; do
        # Clear screen and show header
        clear
        
        # Fancy header with better formatting
        echo -e "${CYAN}╔═══════════════════════════════════════════════════════════╗${NC}"
        echo -e "${CYAN}║${NC}         ${GREEN}🚀 Kotlin Compilation Server Monitor${NC}              ${CYAN}║${NC}"
        echo -e "${CYAN}║${NC}                                                           ${CYAN}║${NC}"
        printf "${CYAN}║${NC}  Mode: ${YELLOW}%-15s${NC}  Server: ${BLUE}%-20s${NC}  ${CYAN}║${NC}\n" "$SERVER_LABEL" "${SERVER_URL#http://}"
        echo -e "${CYAN}╚═══════════════════════════════════════════════════════════╝${NC}"
        echo ""
        echo -e "  ${BLUE}📅 $(date '+%Y-%m-%d')  🕐 $(date '+%H:%M:%S')${NC}"
        echo ""
        
        # Server Status Section with better formatting
        echo -e "${CYAN}┌─────────────────────────────────────────────────────────┐${NC}"
        echo -e "${CYAN}│${NC}                    ${GREEN}Server Status${NC}                       ${CYAN}│${NC}"
        echo -e "${CYAN}├─────────────────────────────────────────────────────────┤${NC}"
        
        # Check Compilation Server
        if HEALTH=$(curl -s "$SERVER_URL/health" 2>/dev/null); then
            STATUS=$(echo "$HEALTH" | grep -o '"status":"[^"]*"' | cut -d'"' -f4)
            CACHE_SIZE=$(echo "$HEALTH" | grep -o '"cache_size":[0-9]*' | cut -d':' -f2)
            
            if [ "$STATUS" = "healthy" ]; then
                printf "${CYAN}│${NC}  ${GREEN}✅ Compilation Server${NC} %-31s ${CYAN}│${NC}\n" "ONLINE"
            else
                printf "${CYAN}│${NC}  ${RED}❌ Compilation Server${NC} %-31s ${CYAN}│${NC}\n" "$STATUS"
            fi
            printf "${CYAN}│${NC}     ${BLUE}📦 Cached Interpreters: %-26s${NC} ${CYAN}│${NC}\n" "$CACHE_SIZE"
        else
            printf "${CYAN}│${NC}  ${RED}❌ Compilation Server${NC} %-31s ${CYAN}│${NC}\n" "OFFLINE"
            CACHE_SIZE=0
        fi
        
        # Check Android Server
        if curl -s "$ANDROID_SERVER/health" --max-time 2 > /dev/null 2>&1; then
            printf "${CYAN}│${NC}  ${GREEN}✅ Android Server${NC} %-35s ${CYAN}│${NC}\n" "ONLINE"
            android_available=true
        else
            printf "${CYAN}│${NC}  ${YELLOW}⚠️  Android Server${NC} %-34s ${CYAN}│${NC}\n" "OFFLINE"
            printf "${CYAN}│${NC}     ${CYAN}🖨️  ASCII Printer Mode Active${NC} %-20s ${CYAN}│${NC}\n" ""
            android_available=false
        fi
        echo -e "${CYAN}└─────────────────────────────────────────────────────────┘${NC}"
        echo ""
        
        # Get detailed cache status
        if [ ! -z "$CACHE_SIZE" ] && [ "$CACHE_SIZE" -gt 0 ] && CACHE_STATUS=$(curl -s "$SERVER_URL/cache/status" 2>/dev/null); then
            echo -e "${CYAN}┌─────────────────────────────────────────────────────────┐${NC}"
            echo -e "${CYAN}│${NC}                   ${GREEN}Cached Teams${NC}                         ${CYAN}│${NC}"
            echo -e "${CYAN}├─────────────────────────────────────────────────────────┤${NC}"
            
            # Parse and display teams
            local team_count=0
            echo "$CACHE_STATUS" | grep -o '"teamId":"[^"]*"' | cut -d'"' -f4 | while read -r team; do
                if [ ! -z "$team" ]; then
                    team_count=$((team_count + 1))
                    COMPILED_AT=$(echo "$CACHE_STATUS" | grep -A1 "\"$team\"" | grep "compiledAt" | cut -d'"' -f4)
                    printf "${CYAN}│${NC}  ${GREEN}✅${NC} %-25s ${BLUE}${COMPILED_AT:11:8}${NC} %-15s ${CYAN}│${NC}\n" "$team" ""
                fi
            done
            echo -e "${CYAN}└─────────────────────────────────────────────────────────┘${NC}"
        elif [ "$CACHE_SIZE" = "0" ] || [ -z "$CACHE_SIZE" ]; then
            echo -e "${CYAN}┌─────────────────────────────────────────────────────────┐${NC}"
            echo -e "${CYAN}│${NC}                   ${GREEN}Cached Teams${NC}                         ${CYAN}│${NC}"
            echo -e "${CYAN}├─────────────────────────────────────────────────────────┤${NC}"
            printf "${CYAN}│${NC}  ${YELLOW}⏳ Waiting for teams to submit interpreters...${NC} %-9s ${CYAN}│${NC}\n" ""
            echo -e "${CYAN}└─────────────────────────────────────────────────────────┘${NC}"
        fi
        
        # Activity Log Section
        echo ""
        echo -e "${CYAN}┌─────────────────────────────────────────────────────────┐${NC}"
        echo -e "${CYAN}│${NC}                  ${GREEN}Recent Activity${NC}                       ${CYAN}│${NC}"
        echo -e "${CYAN}├─────────────────────────────────────────────────────────┤${NC}"
        
        local activity_found=false
        if [ -f /tmp/kotlin-server.log ]; then
            # Parse log for compilation and execution events - look for KotlinCompilerService logs
            local activities=$(tail -n 50 /tmp/kotlin-server.log | grep -E "(KotlinCompilerService.*Compiling|KotlinCompilerService.*Successfully|KotlinCompilerService.*Failed|KotlinCompilerService.*Executing)" | tail -n 8)
            
            if [ ! -z "$activities" ]; then
                activity_found=true
                echo "$activities" | while IFS= read -r line; do
                    local timestamp=$(echo "$line" | cut -d' ' -f1)
                    
                    if echo "$line" | grep -q "Compiling interpreter for team:"; then
                        TEAM=$(echo "$line" | sed 's/.*team: //')
                        printf "${CYAN}│${NC} ${YELLOW}🔨${NC} ${timestamp:0:8} Compiling: %-32s ${CYAN}│${NC}\n" "$TEAM"
                    elif echo "$line" | grep -q "Successfully compiled interpreter"; then
                        TEAM=$(echo "$line" | sed 's/.*team: //')
                        printf "${CYAN}│${NC} ${GREEN}✅${NC} ${timestamp:0:8} Compiled:  %-32s ${CYAN}│${NC}\n" "$TEAM"
                    elif echo "$line" | grep -q "Failed to compile"; then
                        TEAM=$(echo "$line" | sed 's/.*team //' | cut -d',' -f1)
                        printf "${CYAN}│${NC} ${RED}❌${NC} ${timestamp:0:8} Failed:    %-32s ${CYAN}│${NC}\n" "$TEAM"
                    elif echo "$line" | grep -q "Executing interpreter for team:"; then
                        TEAM=$(echo "$line" | sed 's/.*team: //' | cut -d',' -f1)
                        ROUND=$(echo "$line" | sed 's/.*round: //')
                        printf "${CYAN}│${NC} ${BLUE}🖨️${NC}  ${timestamp:0:8} Printing:  %-32s ${CYAN}│${NC}\n" "$TEAM (R$ROUND)"
                    elif echo "$line" | grep -q "Successfully executed"; then
                        TEAM=$(echo "$line" | sed 's/.*team //' | cut -d',' -f1)
                        CMDS=$(echo "$line" | sed 's/.*captured //' | cut -d' ' -f1)
                        printf "${CYAN}│${NC} ${GREEN}✅${NC} ${timestamp:0:8} Printed:   %-32s ${CYAN}│${NC}\n" "$TEAM ($CMDS cmds)"
                    fi
                done
            fi
            
            if [ "$activity_found" = false ]; then
                printf "${CYAN}│${NC}  ${YELLOW}⏳ Waiting for activity...${NC} %-25s ${CYAN}│${NC}\n" ""
            fi
        else
            printf "${CYAN}│${NC}  ${RED}❌ Log file not found${NC} %-31s ${CYAN}│${NC}\n" ""
        fi
        
        echo -e "${CYAN}└─────────────────────────────────────────────────────────┘${NC}"
        
        # ASCII Receipt Display (if Android offline)
        if [ "$android_available" = false ]; then
            # Check for most recent ASCII receipt file
            LATEST_RECEIPT=$(ls -t /tmp/ascii-receipt-*.txt 2>/dev/null | head -n1)
            
            if [ ! -z "$LATEST_RECEIPT" ] && [ -s "$LATEST_RECEIPT" ]; then
                echo ""
                echo -e "${CYAN}┌─────────────────────────────────────────────────────────┐${NC}"
                echo -e "${CYAN}│${NC}                 ${GREEN}ASCII Receipt Preview${NC}                  ${CYAN}│${NC}"
                echo -e "${CYAN}├─────────────────────────────────────────────────────────┤${NC}"
                
                tail -n 12 "$LATEST_RECEIPT" | while IFS= read -r line; do
                    printf "${CYAN}│${NC} %-55s ${CYAN}│${NC}\n" "${line:0:55}"
                done
                
                echo -e "${CYAN}└─────────────────────────────────────────────────────────┘${NC}"
                
                TEAM_ID=$(basename "$LATEST_RECEIPT" | sed 's/ascii-receipt-//' | sed 's/.txt//')
                echo -e "  ${BLUE}📄 Team: $TEAM_ID${NC}"
            fi
        fi
        
        # Footer
        echo ""
        echo -e "${CYAN}═══════════════════════════════════════════════════════════${NC}"
        echo -e "         ${YELLOW}Press ${RED}Ctrl+C${YELLOW} to stop server and exit${NC}"
        echo -e "${CYAN}═══════════════════════════════════════════════════════════${NC}"
        
        # Wait before refresh
        sleep 2
    done
}

# Production monitoring function - simple and clean
monitor_production() {
    echo -e "${CYAN}Kotlin Compilation Server - Production Mode${NC}"
    echo -e "${CYAN}────────────────────────────────────────────${NC}"
    echo -e "Server: ${BLUE}$SERVER_URL${NC}"
    echo -e "Android: ${BLUE}$ANDROID_SERVER${NC}"
    echo ""
    
    # Check initial status
    if curl -s "$ANDROID_SERVER/health" --max-time 2 > /dev/null 2>&1; then
        echo -e "${GREEN}✓ Android Server: ONLINE${NC}"
    else
        echo -e "${RED}✗ Android Server: OFFLINE${NC}"
    fi
    echo ""
    echo -e "${CYAN}Live Activity Log:${NC}"
    echo -e "${CYAN}────────────────────────────────────────────${NC}"
    
    # Tail the log and filter for important events
    tail -f /tmp/kotlin-server.log | while IFS= read -r line; do
        # Extract timestamp if present
        timestamp=$(echo "$line" | grep -o '^[0-9]\{2\}:[0-9]\{2\}:[0-9]\{2\}' || true)
        
        if echo "$line" | grep -q "KotlinCompilerService.*Compiling interpreter for team:"; then
            team=$(echo "$line" | sed 's/.*team: //')
            echo -e "${YELLOW}[$timestamp] COMPILE ${NC} Team: ${BLUE}$team${NC}"
        elif echo "$line" | grep -q "KotlinCompilerService.*Successfully compiled"; then
            team=$(echo "$line" | sed 's/.*team: //')
            echo -e "${GREEN}[$timestamp] SUCCESS ${NC} Team: ${BLUE}$team${NC} ✓"
        elif echo "$line" | grep -q "KotlinCompilerService.*Failed to compile"; then
            team=$(echo "$line" | sed 's/.*team //' | cut -d',' -f1)
            echo -e "${RED}[$timestamp] FAILED  ${NC} Team: ${BLUE}$team${NC} ✗"
        elif echo "$line" | grep -q "KotlinCompilerService.*Executing interpreter"; then
            team=$(echo "$line" | sed 's/.*team: //' | cut -d',' -f1)
            round=$(echo "$line" | sed 's/.*round: //')
            echo -e "${CYAN}[$timestamp] EXECUTE ${NC} Team: ${BLUE}$team${NC} Round: $round"
        elif echo "$line" | grep -q "KotlinCompilerService.*Successfully executed"; then
            team=$(echo "$line" | sed 's/.*team //' | cut -d',' -f1)
            cmds=$(echo "$line" | sed 's/.*captured //' | cut -d' ' -f1)
            echo -e "${GREEN}[$timestamp] PRINTED ${NC} Team: ${BLUE}$team${NC} ($cmds commands)"
        elif echo "$line" | grep -q "ERROR.*KotlinCompilerService"; then
            msg=$(echo "$line" | sed 's/.*ERROR[^-]*- //')
            echo -e "${RED}[$timestamp] ERROR   ${NC} ${msg:0:60}..."
        fi
    done
}