# Composables CLI

Set up new Compose Multiplatform apps with a single command.

## Installation

1. Ensure you have curl installed.
``` shell
apt-get update && apt-get install curl
```
2. Install the CLI tool.
```shell
curl -fsSL https://raw.githubusercontent.com/EmilFlach/composables-cli/refs/heads/main/get-composables.sh | bash
```
3. Restart or refresh your shell terminal.
```shell
source ~/.bashrc
```

## Quick Usage

```shell
composables init composeApp
cd composeApp
./gradlew :dev:run
```
