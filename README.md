# Instant Compose

Start building your Compose app within a minute using any editor.

<video src="docs/instant-cmp.mp4" width="100%" controls autoplay muted loop playsinline></video>

## Get Started

### 1. Install the CLI

```shell
curl -fsSL https://raw.githubusercontent.com/EmilFlach/instant-compose/refs/heads/main/get-instant-compose.sh | bash
```

### 2. Create your app

```shell
instant-compose init myApp
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
