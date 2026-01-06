# Instant Compose

Start building your Compose app in under a minute using any editor.

<video src="docs/instant-cmp.mp4" width="100%" controls autoplay muted loop playsinline></video>

## Get Started

### 1. Install the CLI

```shell
curl -fsSL https://raw.githubusercontent.com/EmilFlach/composables-cli/refs/heads/main/get-composables.sh | bash
```

### 2. Create your app

```shell
composables init myApp
cd myApp
./gradlew :dev:run
```

<details>
<summary>Troubleshooting</summary>

#### A. Missing `curl`?

```shell
apt-get update && apt-get install curl
```

#### B. Refresh Terminal

```shell
source ~/.bashrc
```
or
```shell
source ~/.zshrc
```
</details>

---
Built on top of [composables-cli](https://github.com/composablehorizons/composables-cli) by Alex Styl
