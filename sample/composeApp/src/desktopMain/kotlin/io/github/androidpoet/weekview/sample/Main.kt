package io.github.androidpoet.weekview.sample

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() =
    application {
        Window(onCloseRequest = ::exitApplication, title = "WeekView Sample") {
            SampleApp()
        }
    }
