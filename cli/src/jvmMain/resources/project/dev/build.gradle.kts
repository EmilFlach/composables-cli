plugins {
    kotlin("jvm")
    application
}

dependencies {
    implementation("ch.qos.logback:logback-classic:1.5.16")
    implementation("io.ktor:ktor-server-core-jvm:3.1.1")
    implementation("io.ktor:ktor-server-netty-jvm:3.1.1")
    implementation("io.ktor:ktor-server-websockets-jvm:3.1.1")
    implementation("io.ktor:ktor-server-compression-jvm:3.1.1")
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.google.zxing:javase:3.5.3")
}

application {
    mainClass.set("org.example.web_previews.dev.MainKt")
    applicationDefaultJvmArgs = listOf(
        "-Dlogback.configurationFile=logback.xml",
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.nio=ALL-UNNAMED",
        "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
        "--add-opens=java.base/java.util=ALL-UNNAMED",
        "--enable-native-access=ALL-UNNAMED",
        "-Dio.netty.noUnsafe=true"
    )
}

tasks.withType<JavaExec> {
    standardInput = System.`in`
    // Use --console=plain when running to avoid Gradle progress bar noise
    if (project.hasProperty("plain")) {
        logging.captureStandardOutput(LogLevel.INFO)
    }
}

tasks.named<JavaExec>("run") {
    // Attempt to reduce noise when running the server
    logging.captureStandardOutput(LogLevel.INFO)
}
