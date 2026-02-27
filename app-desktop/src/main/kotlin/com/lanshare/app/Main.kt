package com.lanshare.app

import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.lanshare.app.ui.LanShareApp
import com.lanshare.app.ui.theme.LanShareTheme
import com.lanshare.app.viewmodel.MainViewModel

fun main(args: Array<String>) {
    if (args.any { it == "--self-check" }) {
        println("LanShare self-check OK")
        return
    }

    application {
        val viewModel = remember { MainViewModel() }

        Window(
            onCloseRequest = {
                viewModel.shutdown()
                exitApplication()
            },
            title = "LanShare Desktop"
        ) {
            LanShareTheme {
                LanShareApp(viewModel)
            }
        }
    }
}
