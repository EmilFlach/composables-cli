# Instant Compose

Start building your Compose app within a minute using any editor. See it in action: https://emilflach.github.io/instant-compose

## Get Started

### 1. Install the CLI

```shell
curl -fsSL https://emilflach.github.io/instant-compose/get.sh | bash
```

### 2. Create your app

```shell
compose init myApp
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
