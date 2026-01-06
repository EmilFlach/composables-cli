package com.composables.cli

import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test

class CliTest {
    val targetDir = "temp"

    @BeforeTest
    fun cleanTargetDirectory() {
        val targetFile = File(targetDir)
        if (targetFile.exists()) {
            targetFile.deleteRecursively()
        }
    }

    @Test
    fun `test constants are defined correctly`() {
        cloneGradleProject(
            targetDir = targetDir,
            dirName = "newApp",
            packageName = "com.instantcompose",
            moduleName = "composeApp",
            appName = "The App",
            targets = setOf(
                ANDROID,
            )
        )

        // Assert that composeApp directory exists
        val appDir = File(targetDir, "newApp")
        assert(appDir.exists()) { "newApp directory should exist in $targetDir" }
        assert(appDir.isDirectory) { "newApp should be a directory" }
    }
}
