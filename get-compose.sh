#!/bin/bash

set -e

# Configuration
REPO="EmilFlach/instant-compose"
INSTALL_DIR="$HOME/.instant-compose/bin"
JAR_NAME="instant-compose.jar"
WRAPPER_NAME="instant-compose"

# Colors
RED='\033[0;31m'  # Standard red for errors
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m'      # No Color

setup_java() {
    echo "Checking for Java..."
    
    JAVA_REQUIRED_VERSION=17
    JAVA_VERSION=""

    if command -v java &> /dev/null; then
        JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d '"' -f 2 | cut -d '.' -f 1)
        # Handle cases where version is like 1.8.x
        if [ "$JAVA_VERSION" = "1" ]; then
            JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d '"' -f 2 | cut -d '.' -f 2)
        fi
    fi

    if [ -n "$JAVA_VERSION" ] && [ "$JAVA_VERSION" -ge "$JAVA_REQUIRED_VERSION" ]; then
        echo "✓ Java $JAVA_VERSION detected"
        return 0
    fi

    if [ -n "$JAVA_VERSION" ]; then
        echo -e "${YELLOW}Warning: Java $JAVA_VERSION detected, but Java $JAVA_REQUIRED_VERSION or higher is required.${NC}"
    else
        echo -e "${YELLOW}Java not found.${NC}"
    fi

    echo "Attempting to install Java $JAVA_REQUIRED_VERSION..."

    if [[ "$OSTYPE" == "darwin"* ]]; then
        if ! command -v brew &> /dev/null; then
            echo -e "${RED}Error: Homebrew is required to install Java on macOS.${NC}"
            echo "Please install Homebrew from https://brew.sh/ or install Java manually from https://www.oracle.com/java/technologies/downloads/"
            exit 1
        fi

        echo "Installing OpenJDK 17 via Homebrew..."
        brew install openjdk@17

        echo "Linking OpenJDK..."
        ln -sfn /usr/local/opt/openjdk@17/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-17.jdk 2>/dev/null || \
        ln -sfn /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-17.jdk

        # Add to current path for verification
        export PATH="/usr/local/opt/openjdk@17/bin:$PATH"
        export PATH="/opt/homebrew/opt/openjdk@17/bin:$PATH"
    elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
        if command -v apt-get &> /dev/null; then
            echo "Installing OpenJDK 17 via apt..."
            apt-get update && apt-get install -y openjdk-17-jdk
        elif command -v dnf &> /dev/null; then
            echo "Installing OpenJDK 17 via dnf..."
            dnf install -y java-17-openjdk-devel
        elif command -v yum &> /dev/null; then
            echo "Installing OpenJDK 17 via yum..."
            yum install -y java-17-openjdk-devel
        else
            echo -e "${RED}Error: Unsupported package manager.${NC}"
            echo "Please install Java 17 or higher manually."
            exit 1
        fi
    else
        echo -e "${RED}Error: Unsupported operating system ($OSTYPE).${NC}"
        echo "Please install Java 17 or higher manually."
        exit 1
    fi

    # Final verification
    if command -v java &> /dev/null; then
        NEW_JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d '"' -f 2 | cut -d '.' -f 1)
        if [ "$NEW_JAVA_VERSION" = "1" ]; then
            NEW_JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d '"' -f 2 | cut -d '.' -f 2)
        fi
        
        if [ "$NEW_JAVA_VERSION" -ge "$JAVA_REQUIRED_VERSION" ]; then
            echo -e "${GREEN}✓ Java $NEW_JAVA_VERSION installed successfully!${NC}"
            return 0
        fi
    fi

    echo -e "${RED}Error: Java installation failed or version is still incorrect.${NC}"
    exit 1
}

echo "Installing Instant Compose..."

# Check for required commands
if ! command -v curl &> /dev/null; then
    echo -e "${RED}Error: curl is required but not installed${NC}"
    exit 1
fi

setup_java

# Create installation directory
mkdir -p "$INSTALL_DIR"

# Detect latest version
echo "Fetching latest version..."
LATEST_VERSION=$(curl -s "https://api.github.com/repos/$REPO/releases/latest" | grep '"tag_name":' | sed -E 's/.*"([^"]+)".*/\1/')

if [ -z "$LATEST_VERSION" ]; then
    echo -e "${RED}Failed to fetch latest version from GitHub API${NC}"
    echo "Please check your internet connection and try again"
    exit 1
fi

echo "Latest version: $LATEST_VERSION"

# Download JAR
JAR_URL="https://github.com/$REPO/releases/download/$LATEST_VERSION/$JAR_NAME"
echo "Downloading from $JAR_URL..."

if ! curl -fSL --progress-bar "$JAR_URL" -o "$INSTALL_DIR/$JAR_NAME"; then
    echo -e "${RED}Failed to download $JAR_NAME${NC}"
    echo "Please check if the version $LATEST_VERSION exists and contains $JAR_NAME"
    exit 1
fi

# Verify JAR was downloaded
if [ ! -f "$INSTALL_DIR/$JAR_NAME" ]; then
    echo -e "${RED}Error: Downloaded file not found${NC}"
    exit 1
fi

echo "✓ Downloaded $JAR_NAME"

# Create wrapper script
echo "Creating wrapper script..."
cat > "$INSTALL_DIR/$WRAPPER_NAME" << EOF
#!/bin/bash
# Instant Compose CLI wrapper
exec java -jar "\$HOME/.instant-compose/bin/$JAR_NAME" "\$@"
EOF

chmod +x "$INSTALL_DIR/$WRAPPER_NAME"

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
    if ! grep -q ".instant-compose/bin" "$SHELL_RC"; then
        echo "" >> "$SHELL_RC"
        echo "# Instant Compose" >> "$SHELL_RC"
        echo "export PATH=\"\$HOME/.instant-compose/bin:\$PATH\"" >> "$SHELL_RC"
        echo "Added Instant Compose to PATH in $SHELL_RC"
    else
        echo "Instant Compose already in PATH"
    fi
elif [ -n "$SHELL_RC" ]; then
        echo "Shell config file $SHELL_RC not found. Creating it..."
    touch "$SHELL_RC"
    echo "" >> "$SHELL_RC"
    echo "# Instant Compose" >> "$SHELL_RC"
    echo "export PATH=\"\$HOME/.instant-compose/bin:\$PATH\"" >> "$SHELL_RC"
    echo "Created $SHELL_RC and added Instant Compose to PATH"
fi

# Make it immediately available for current session
export PATH="$INSTALL_DIR:$PATH"

# Test installation
echo -e "${BLUE}Testing installation...${NC}"
if command -v instant-compose &> /dev/null; then
    echo "✓ Instant Compose installed successfully!"
    echo ""
    echo "Usage:"
    echo "  instant-compose --help               - Show all available commands"
    echo ""
    if [ -n "$SHELL_RC" ]; then
        echo "Note: Restart your terminal or run 'source $SHELL_RC' to use instant-compose from anywhere"
    fi
else
    echo -e "${RED}Error: Installation verification failed${NC}"
    echo "Please try running: export PATH=\"$INSTALL_DIR:\$PATH\""
    exit 1
fi
