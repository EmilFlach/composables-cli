#!/bin/bash

set -e

# Configuration
REPO="EmilFlach/instant-compose"
INSTALL_DIR="$HOME/.compose/bin"
JAR_NAME="compose.jar"
WRAPPER_NAME="compose"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
DIM='\033[2m'
NC='\033[0m'
BOLD='\033[1m'

# Spinner function
spinner() {
    local pid=$1
    local delay=0.1
    local spinstr='|/-\'
    while [ "$(ps -p $pid -o state= 2>/dev/null)" ]; do
        local temp=${spinstr#?}
        printf " [%c]  " "$spinstr"
        local spinstr=$temp${spinstr%"$temp"}
        sleep $delay
        printf "\b\b\b\b\b\b"
    done
    printf "    \b\b\b\b"
}

setup_java() {
    printf "Checking for Java... "
    
    JAVA_REQUIRED_VERSION=21
    JAVA_VERSION=""

    if command -v java &> /dev/null; then
        JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d '"' -f 2 | cut -d '.' -f 1)
        # Handle cases where version is like 1.8.x
        if [ "$JAVA_VERSION" = "1" ]; then
            JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d '"' -f 2 | cut -d '.' -f 2)
        fi
    fi

    if [ -n "$JAVA_VERSION" ] && [ "$JAVA_VERSION" -ge "$JAVA_REQUIRED_VERSION" ]; then
        echo -e "${GREEN}Java $JAVA_VERSION detected${NC}"
        return 0
    fi

    if [ -n "$JAVA_VERSION" ]; then
        echo -e "${YELLOW}Warning: Java $JAVA_VERSION detected, but Java $JAVA_REQUIRED_VERSION or higher is required.${NC}"
    else
        echo -e "${YELLOW}Not found${NC}"
    fi

    printf "Installing Java $JAVA_REQUIRED_VERSION... "

    (
    if [[ "$OSTYPE" == "darwin"* ]]; then
        if ! command -v brew &> /dev/null; then
            echo -e "${RED}Error: Homebrew is required to install Java on macOS.${NC}" >&2
            exit 1
        fi

        brew install openjdk@21 &>/dev/null

        ln -sfn /usr/local/opt/openjdk@21/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-21.jdk 2>/dev/null || \
        ln -sfn /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-21.jdk
    elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
        if command -v apt-get &> /dev/null; then
            apt-get update && apt-get install -y openjdk-21-jdk
        elif command -v dnf &> /dev/null; then
            dnf install -y java-21-openjdk-devel
        elif command -v yum &> /dev/null; then
            yum install -y java-21-openjdk-devel
        else
            echo -e "${RED}Error: Unsupported package manager.${NC}" >&2
            exit 1
        fi
    else
        echo -e "${RED}Error: Unsupported operating system ($OSTYPE).${NC}" >&2
        exit 1
    fi
    ) &> /tmp/compose-install.log &
    
    spinner $!
    
    # Final verification
    if command -v java &> /dev/null || [ -f "/opt/homebrew/opt/openjdk@21/bin/java" ] || [ -f "/usr/local/opt/openjdk@21/bin/java" ]; then
        # Update path for current session if we just installed it on macOS
        export PATH="/usr/local/opt/openjdk@21/bin:$PATH"
        export PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH"
        
        NEW_JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d '"' -f 2 | cut -d '.' -f 1)
        if [ "$NEW_JAVA_VERSION" = "1" ]; then
            NEW_JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d '"' -f 2 | cut -d '.' -f 2)
        fi
        
        if [ "$NEW_JAVA_VERSION" -ge "$JAVA_REQUIRED_VERSION" ]; then
            echo -e "${GREEN}DONE (Java $NEW_JAVA_VERSION)${NC}"
            return 0
        fi
    fi

    echo -e "${RED}FAILED${NC}"
    cat /tmp/compose-install.log
    exit 1
}

echo -e "${BOLD}Installing Instant Compose${NC}"

# Check for required commands
if ! command -v curl &> /dev/null; then
    echo -e "${RED}Error: curl is required but not installed${NC}"
    exit 1
fi

setup_java

# Create installation directory
mkdir -p "$INSTALL_DIR"

# Detect latest version
printf "Fetching latest version... "
LATEST_VERSION=$(curl -s "https://api.github.com/repos/$REPO/releases/latest" | grep '"tag_name":' | sed -E 's/.*"([^"]+)".*/\1/')

if [ -z "$LATEST_VERSION" ]; then
    echo -e "${RED}FAILED${NC}"
    echo "Please check your internet connection and try again"
    exit 1
fi
echo -e "${GREEN}$LATEST_VERSION${NC}"

# Download JAR
JAR_URL="https://github.com/$REPO/releases/download/$LATEST_VERSION/$JAR_NAME"
printf "Downloading... "

if ! curl -fSL -s "$JAR_URL" -o "$INSTALL_DIR/$JAR_NAME"; then
    echo -e "${RED}FAILED${NC}"
    echo "Please check if the version $LATEST_VERSION exists and contains $JAR_NAME"
    exit 1
fi
echo -e "${GREEN}DONE${NC}"

# Create wrapper script
printf "Setting up... "
cat > "$INSTALL_DIR/$WRAPPER_NAME" << EOF
#!/bin/bash
# Compose CLI wrapper
exec java -jar "\$HOME/.compose/bin/$JAR_NAME" "\$@"
EOF

chmod +x "$INSTALL_DIR/$WRAPPER_NAME"
echo -e "${GREEN}DONE${NC}"

# Detect shell and update PATH
SHELL_RC=""
CURRENT_SHELL=$(basename "$SHELL")

case "$CURRENT_SHELL" in
    zsh)
        SHELL_RC="$HOME/.zshrc"
        ;;
    bash)
        # On macOS, bash users often use .bash_profile
        if [[ "$OSTYPE" == "darwin"* ]]; then
            SHELL_RC="$HOME/.bash_profile"
        else
            SHELL_RC="$HOME/.bashrc"
        fi
        ;;
    *)
        echo -e "${RED}Warning: Unsupported shell ($CURRENT_SHELL). You may need to manually add $INSTALL_DIR to your PATH${NC}"
        ;;
esac

# Add to PATH if shell config file exists
if [ -n "$SHELL_RC" ] && [ -f "$SHELL_RC" ]; then
    if ! grep -q ".compose/bin" "$SHELL_RC"; then
        echo "" >> "$SHELL_RC"
        echo "# Compose" >> "$SHELL_RC"
        echo "export PATH=\"\$HOME/.compose/bin:\$PATH\"" >> "$SHELL_RC"
        echo -e "${DIM}Added to PATH in $SHELL_RC${NC}"
    fi
elif [ -n "$SHELL_RC" ]; then
    echo -e "${DIM}Creating $SHELL_RC and adding to PATH...${NC}"
    touch "$SHELL_RC"
    echo "" >> "$SHELL_RC"
    echo "# Compose" >> "$SHELL_RC"
    echo "export PATH=\"\$HOME/.compose/bin:\$PATH\"" >> "$SHELL_RC"
fi

# Make it immediately available for current session
export PATH="$INSTALL_DIR:$PATH"

# Test installation
printf "Verifying... "
if command -v compose &> /dev/null; then
    echo -e "${GREEN}SUCCESS${NC}"
    echo ""
    echo -e "${GREEN}${BOLD}✓ Compose installed successfully!${NC}"
    echo ""
    echo -e "${BOLD}Usage:${NC}"
    echo "  compose --help"
    echo ""
    if [ -n "$SHELL_RC" ]; then
        echo -e "${DIM}Note: Restart your terminal or run 'source $SHELL_RC' to use it from anywhere${NC}"
    fi
else
    echo -e "${RED}FAILED${NC}"
    echo -e "Please try running: export PATH=\"$INSTALL_DIR:\$PATH\""
    exit 1
fi
