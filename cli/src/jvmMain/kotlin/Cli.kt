package com.composables.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.versionOption
import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.rendering.TextStyles.*
import com.github.ajalt.mordant.terminal.Terminal
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.jar.JarFile
import java.util.zip.ZipInputStream

private val terminal = Terminal()

private fun echo(message: Any?, err: Boolean = false, trailingNewline: Boolean = true) {
    if (trailingNewline) {
        terminal.println(message, stderr = err)
    } else {
        terminal.print(message, stderr = err)
    }
}

suspend fun main(args: Array<String>) {
    ComposablesCli()
        .subcommands(Init(), Update())
        .main(args)
}

class ComposablesCli : CliktCommand(name = "instant-compose") {
    init {
        versionOption(
            version = BuildConfig.Version,
            names = setOf("-v", "--version"),
            message = { BuildConfig.Version }
        )
    }

    override fun run() {
    }

    override fun help(context: Context) = """
        ${bold("If you have any problems or need help, do not hesitate to ask for help at:")}
            ${cyan("https://github.com/EmilFlach/instant-compose")}
    """.trimIndent()
}

class Update : CliktCommand("update") {
    override fun help(context: Context): String = bold("Updates the CLI tool with the latest version")

    override fun run() {
        try {
            echo(bold(cyan("Instant Compose")) + " " + brightBlue("🔄 Update"))
            echo(white("Updating CLI tool"))
            echo("")

            val currentJarPath = this::class.java.protectionDomain.codeSource.location.path
            val installDir = File(currentJarPath).parent
            val tempJar = File(installDir, "instant-compose.jar.tmp")

            echo(yellow("→ ") + "Updating CLI tool...")
            val latestVersion = ProcessBuilder(
                "bash",
                "-c",
                "curl -s https://api.github.com/repos/EmilFlach/instant-compose/releases/latest | grep '\"tag_name\":' | sed -E 's/.*\"([^\"]+)\".*/\\1/'"
            )
                .redirectErrorStream(true)
                .start()
                .inputStream.bufferedReader()
                .readText()
                .trim()

            if (latestVersion.isEmpty()) {
                echo(red("Failed to fetch latest version"), err = true)
                return
            }

            val downloadProcess = ProcessBuilder(
                "curl", "-fSL",
                "https://github.com/EmilFlach/instant-compose/releases/download/$latestVersion/instant-compose.jar",
                "-o", tempJar.absolutePath
            ).inheritIO().start()

            val downloadExitCode = downloadProcess.waitFor()
            if (downloadExitCode != 0) {
                echo(red("Failed to download new version"), err = true)
                tempJar.delete()
                return
            }

            val currentJar = File(currentJarPath)
            if (tempJar.renameTo(currentJar)) {
                echo(green("✓ ") + "Successfully updated to $latestVersion")
            } else {
                echo(red("Failed to replace current JAR. You might need to manually replace it."), err = true)
                echo("New JAR location: ${tempJar.absolutePath}")
            }
        } catch (e: Exception) {
            echo(red("Update failed: ${e.message}"), err = true)
        }
    }
}

class Init : CliktCommand("init") {
    override fun help(context: Context): String = bold("Initializes a new Instant Compose project")

    private val projectName by argument(name = "project-name").optional()

    override fun run() {
        val name = projectName ?: "KotlinProject"
        val targetDir = File(System.getProperty("user.dir"), name)

        if (targetDir.exists()) {
            echo(red("Directory '$name' already exists. Please choose a different name or delete the directory."))
            return
        }

        echo(bold(cyan("Instant Compose")) + " " + brightBlue("🚀 Init"))
        echo(white("Initializing project: $name"))
        echo("")

        try {
            val wizardUrl = "https://kmp.jetbrains.com/generateKmtProject?name=$name&id=org.example.project&spec=%7B%22template_id%22%3A%22kmt%22%2C%22targets%22%3A%7B%22android%22%3A%7B%22ui%22%3A%5B%22compose%22%5D%7D%2C%22ios%22%3A%7B%22ui%22%3A%5B%22compose%22%5D%7D%2C%22desktop%22%3A%7B%22ui%22%3A%5B%22compose%22%5D%7D%2C%22web%22%3A%7B%22ui%22%3A%5B%22compose%22%5D%7D%2C%22server%22%3A%7B%22engine%22%3A%5B%22ktor%22%5D%7D%7D%2C%22include_tests%22%3Atrue%7D"
            
            val tempZip = File.createTempFile("kmp-wizard", ".zip")
            
            echo(yellow("→ ") + "Creating project structure...")
            downloadFile(wizardUrl, tempZip)
            unzip(tempZip, targetDir)
            tempZip.delete()
            applyModifications(targetDir, name)
            setExecutablePermissions(targetDir)
            echo(green("✓ ") + "Project structure created")

            echo("")
            echo(green("✨ Project initialized successfully!"))
            echo("")
            echo(bold("To get started:"))
            echo(cyan("  cd $name"))
            echo(cyan("  ./gradlew :dev:run"))
        } catch (e: Exception) {
            echo(red("Failed to initialize project: ${e.message}"), err = true)
        }
    }

    private fun setExecutablePermissions(targetDir: File) {
        val gradlew = File(targetDir, "gradlew")
        if (gradlew.exists()) {
            gradlew.setExecutable(true)
        }
    }

    private fun downloadFile(url: String, targetFile: File) {
        URL(url).openStream().use { input ->
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun unzip(zipFile: File, targetDir: File) {
        ZipInputStream(zipFile.inputStream()).use { zipInput ->
            var entry = zipInput.nextEntry
            while (entry != null) {
                // The KMP Wizard zips the project with a root directory (e.g., "KotlinProject/")
                // We want to extract the contents of that directory directly into targetDir.
                val parts = entry.name.split("/")
                if (parts.size > 1) {
                    val relativePath = parts.drop(1).joinToString("/")
                    if (relativePath.isNotEmpty()) {
                        val newFile = File(targetDir, relativePath)
                        if (entry.isDirectory) {
                            newFile.mkdirs()
                        } else {
                            newFile.parentFile.mkdirs()
                            FileOutputStream(newFile).use { output ->
                                zipInput.copyTo(output)
                            }
                        }
                    }
                }
                zipInput.closeEntry()
                entry = zipInput.nextEntry
            }
        }
    }

    private fun applyModifications(targetDir: File, name: String) {
        // 1. Copy in the dev module
        copyResourceDirectory("project/dev", File(targetDir, "dev"))

        // 2. Add the dev module to settings.gradle.kts
        val settingsFile = File(targetDir, "settings.gradle.kts")
        if (settingsFile.exists()) {
            var content = settingsFile.readText()
            if (!content.contains("\":dev\"")) {
                content += "\ninclude(\":dev\")\n"
                settingsFile.writeText(content)
            }
        }

        // 3. Copy the .github.workflows
        copyResourceDirectory("project/.github/workflows", File(targetDir, ".github/workflows"))

        // 4. Replace the readme
        copyResourceFile("project/README.md", File(targetDir, "README.md"))

        // 5. Copy the docs folder
        copyResourceDirectory("project/docs", File(targetDir, "docs"))

        // 6. Specifically for web, copy in the changes needed for the project
        val webResourcesDir = File(targetDir, "composeApp/src/webMain/resources")
        webResourcesDir.mkdirs()

        listOf("app.html", "index.html", "preview.html").forEach { fileName ->
            val resourcePath = "project/composeApp/src/webMain/resources/$fileName"
            val inputStream = this::class.java.classLoader.getResourceAsStream(resourcePath)
            if (inputStream != null) {
                val content = inputStream.bufferedReader().use { it.readText() }
                val updatedContent = content.replace("{{app_name}}", name)
                File(webResourcesDir, fileName).writeText(updatedContent)
            }
        }
        copyResourceFile("project/composeApp/src/webMain/resources/styles.css", File(webResourcesDir, "styles.css"))

        // Update main.kt for notifyParent()
        val webKotlinDir = File(targetDir, "composeApp/src/webMain/kotlin/org/example/project")
        webKotlinDir.mkdirs()
        
        val mainKtResource = "project/composeApp/src/webMain/kotlin/org/example/main.kt"
        val mainKtTarget = File(webKotlinDir, "main.kt")
        
        val inputStream = this::class.java.classLoader.getResourceAsStream(mainKtResource)
        if (inputStream != null) {
            val content = inputStream.bufferedReader().use { it.readText() }
            val updatedContent = content.replace("{{namespace}}", "org.example.project")
            mainKtTarget.writeText(updatedContent)
        }
    }

    private fun copyResourceFile(resourcePath: String, targetFile: File) {
        val inputStream = this::class.java.classLoader.getResourceAsStream(resourcePath)
        if (inputStream != null) {
            targetFile.parentFile.mkdirs()
            FileOutputStream(targetFile).use { output ->
                inputStream.copyTo(output)
            }
        }
    }

    private fun copyResourceDirectory(resourcePath: String, targetDir: File) {
        val jarFile = getJarFile()
        if (jarFile != null) {
            val entries = jarFile.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.name.startsWith(resourcePath) && !entry.isDirectory) {
                    val relativePath = entry.name.substring(resourcePath.length)
                    val targetFile = File(targetDir, relativePath)
                    targetFile.parentFile.mkdirs()
                    jarFile.getInputStream(entry).use { input ->
                        FileOutputStream(targetFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        } else {
            val resource = this::class.java.classLoader.getResource(resourcePath)
            if (resource != null) {
                val resFile = File(resource.toURI())
                if (resFile.exists()) {
                    resFile.copyRecursively(targetDir, overwrite = true)
                }
            }
        }
    }

    private fun getJarFile(): JarFile? {
        val codeSource = this::class.java.protectionDomain.codeSource
        val location = codeSource?.location?.toURI()?.path ?: return null
        return if (location.endsWith(".jar")) JarFile(location) else null
    }
}
