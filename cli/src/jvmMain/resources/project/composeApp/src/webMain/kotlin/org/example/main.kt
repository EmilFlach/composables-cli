package org.example.project

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport

fun notifyParent(): Unit = js("window.parent.postMessage('compose-ready', '*')")

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport("compose-receiver") {
        App()
    }
    notifyParent()
}