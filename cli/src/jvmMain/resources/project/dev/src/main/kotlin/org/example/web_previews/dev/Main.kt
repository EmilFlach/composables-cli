package org.example.web_previews.dev
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.File
import java.net.NetworkInterface
import java.nio.file.*
import java.util.concurrent.atomic.AtomicBoolean

fun main() {
    println("=".repeat(50))
    println("Starting development server...")

    val port = 8080
    val localIp = getLocalIpAddress()
    val localUrl = "http://localhost:$port"
    val networkUrl = localIp?.let { "http://$it:$port" }

    val buildInProgress = AtomicBoolean(false)
    val clients = java.util.Collections.synchronizedSet(mutableSetOf<DefaultWebSocketServerSession>())

    val distDir = File("composeApp/build/dist/wasmJs/developmentExecutable").let {
        if (it.exists()) it else File("../composeApp/build/dist/wasmJs/developmentExecutable")
    }.canonicalFile

    if (!distDir.exists()) {
        distDir.mkdirs()
    }

    // Initial build procedure
    runBlocking {
        performRebuild(distDir, clients, buildInProgress, isInitial = true)
    }

    // Clear the terminal screen to remove Gradle startup noise (if supported by terminal)
    // \u001b[H moves cursor to home, \u001b[2J clears the screen
    print("\u001b[H\u001b[2J")
    System.out.flush()

    println("=".repeat(50))
    println("Server started!")
    println("Local:   $localUrl")
    networkUrl?.let {
        println("Network: $it")
        println("\nScan the QR code below for network access:")
        println(generateQrCodeAscii(it))
    }
    println("=".repeat(50))

    embeddedServer(Netty, port = port) {
        install(WebSockets)
        install(Compression) {
            gzip {
                priority = 1.0
            }
            deflate {
                priority = 10.0
                minimumSize(1024)
            }
        }

        routing {
            webSocket("/dev-server") {
                clients.add(this)
                try {
                    if (buildInProgress.get()) {
                        send("rebuilding")
                    }
                    for (frame in incoming) {
                        // Keep alive
                    }
                } finally {
                    clients.remove(this)
                }
            }

            staticFiles("/", distDir) {
                enableAutoHeadResponse()
                default("index.html")
                // Add headers to encourage browser to use the background-fetched files
                cacheControl {
                    // Use a short max-age for the transition
                    listOf(CacheControl.MaxAge(maxAgeSeconds = 10))
                }
            }
        }

        launch(Dispatchers.IO) {
            val changeChannel = Channel<Unit>(Channel.CONFLATED)

            launch {
                for (change in changeChannel) {
                    delay(500) // Debounce
                    // Consume all pending signals that might have accumulated during delay
                    while (changeChannel.tryReceive().isSuccess) { /* keep skipping */ }

                    performRebuild(distDir, clients, buildInProgress, isInitial = false)
                }
            }

            watchFiles {
                changeChannel.send(Unit)
            }
        }
    }.start(wait = true)
}

suspend fun performRebuild(
    distDir: File,
    clients: Set<DefaultWebSocketServerSession>,
    buildInProgress: AtomicBoolean,
    isInitial: Boolean = false
) {
    if (buildInProgress.compareAndSet(false, true)) {
        val startTime = System.currentTimeMillis()
        var timerJob: kotlinx.coroutines.Job? = null

        val statusText = if (isInitial) "Building..." else "Rebuilding..."

        kotlinx.coroutines.coroutineScope {
            timerJob = launch {
                while (true) {
                    val elapsed = (System.currentTimeMillis() - startTime) / 1000.0
                    print("\r\u001B[33m$statusText (${"%.1f".format(elapsed)}s)\u001B[0m          ")
                    System.out.flush()
                    delay(100)
                }
            }

            clients.forEach {
                launch {
                    try { it.send("rebuilding") } catch (e: Exception) {}
                }
            }

            val gradlewPath = if (File("./gradlew").exists()) "./gradlew" else "../gradlew"
            val processBuilder = ProcessBuilder(
                gradlewPath,
                ":composeApp:wasmJsBrowserDevelopmentExecutableDistribution",
                "--rerun-tasks",
                "--quiet",
                "--console=rich",
                "-Dorg.gradle.color=true",
                "-Pkotlin.colors.enabled=true"
            )
            processBuilder.environment()["TERM"] = "xterm-256color"

            val process = withContext(Dispatchers.IO) {
                processBuilder
                    .redirectErrorStream(true)
                    .start()
            }

            val output = withContext(Dispatchers.IO) {
                process.inputStream.bufferedReader().use { it.readText() }
            }
            val exitCode = withContext(Dispatchers.IO) {
                process.waitFor()
            }

            timerJob?.cancel()
            val duration = (System.currentTimeMillis() - startTime) / 1000.0
            buildInProgress.set(false)

            if (exitCode == 0) {
                val successText = if (isInitial) "Build successful" else "Rebuild successful"
                print("\r\u001B[32m$successText in ${"%.1f".format(duration)}s, notifying clients.\u001B[0m          ")
                System.out.flush()

                // Copy resources
                val resourcesDir = File("composeApp/src/webMain/resources")
                if (resourcesDir.exists()) {
                    resourcesDir.listFiles()?.forEach { file ->
                        file.copyTo(File(distDir, file.name), overwrite = true)
                    }
                }

                val filesList = distDir.listFiles()?.filter {
                    it.name.endsWith(".js") || it.name.endsWith(".wasm") || it.name == "app.html"
                }?.joinToString(",") { it.name } ?: ""

                clients.forEach {
                    launch {
                        try { it.send("reload:$filesList") } catch (e: Exception) {}
                    }
                }
            } else {
                val failureText = if (isInitial) "Build failed" else "Rebuild failed"
                println("\r\u001B[31m$failureText in ${"%.1f".format(duration)}s\u001B[0m" + " ".repeat(20))
                val filteredOutput = output.lines()
                    .filter { line ->
                        line.isNotBlank() &&
                                !line.contains("> Task :") &&
                                !line.contains("BUILD FAILED") &&
                                !line.contains("Run with --stacktrace") &&
                                !line.contains("Run with --info") &&
                                !line.contains("Run with --debug") &&
                                !line.contains("Run with --scan") &&
                                !line.contains("Get more help at https://help.gradle.org")
                    }
                    .joinToString("\n")

                if (filteredOutput.isNotBlank()) {
                    println("\n$filteredOutput\n")
                }
                clients.forEach {
                    launch {
                        try { it.send("error") } catch (e: Exception) {}
                    }
                }
            }
        }
    }
}

fun watchFiles(onChange: suspend () -> Unit) {
    val watchService = FileSystems.getDefault().newWatchService()
    val root = Paths.get(".").let {
        if (Files.exists(it.resolve("composeApp"))) it else Paths.get("..")
    }.toAbsolutePath().normalize()

    fun registerRecursive(path: Path) {
        val file = path.toFile()
        if (file.isDirectory) {
            val name = file.name
            if (name == "build" || name.startsWith(".") || name == "node_modules" || name == "kotlin-js-store") return

            path.register(
                watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY
            )
            file.listFiles()?.forEach { registerRecursive(it.toPath()) }
        }
    }

    registerRecursive(root)

    runBlocking {
        while (true) {
            val key = watchService.take()
            var changed = false
            for (event in key.pollEvents()) {
                val context = event.context() as? Path ?: continue
                val watchable = key.watchable() as Path
                val resolvedPath = watchable.resolve(context).toAbsolutePath().normalize()

                val pathString = resolvedPath.toString()
                if (pathString.contains("composeApp/src") || pathString.endsWith(".gradle.kts")) {
                    changed = true
                }

                if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                    registerRecursive(resolvedPath)
                }
            }
            if (changed) {
                onChange()
            }
            if (!key.reset()) break
        }
    }
}

fun getLocalIpAddress(): String? {
    return NetworkInterface.getNetworkInterfaces().asSequence()
        .flatMap { it.inetAddresses.asSequence() }
        .filter { !it.isLoopbackAddress && it.hostAddress.indexOf(':') < 0 }
        .map { it.hostAddress }
        .firstOrNull()
}

fun generateQrCodeAscii(text: String): String {
    val qrCodeWriter = QRCodeWriter()
    val bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, 0, 0)
    val width = bitMatrix.width
    val height = bitMatrix.height
    val sb = StringBuilder()
    for (y in 0 until height) {
        for (x in 0 until width) {
            sb.append(if (bitMatrix.get(x, y)) "██" else "  ")
        }
        sb.append("\n")
    }
    return sb.toString()
}

